package com.yuzhi.dts.addax;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.SecretKey;

public final class AddaxEnvRunner {

    private static final int EX_USAGE = 64;
    private static final int EX_NOINPUT = 66;
    private static final int EX_CONFIG = 78;
    private static final int EX_COMMAND_NOT_FOUND = 127;
    private static final String DEFAULT_ADDAX_BIN = "/opt/addax/bin/addax.sh";
    private static final List<String> CREDENTIAL_NAMES = List.of(
        "DTS_ADDAX_READER_PASSWORD",
        "DTS_TARGET_DB_PASSWORD"
    );

    private AddaxEnvRunner() {}

    public static void main(String[] args) throws Exception {
        int exitCode = run(args, System.getenv(), System.err);
        System.exit(exitCode);
    }

    static int run(String[] args, Map<String, String> env, PrintStream err) throws IOException, InterruptedException {
        if (args.length < 1) {
            err.println("Usage: AddaxEnvRunner <job.json>");
            return EX_USAGE;
        }

        Path jobFile = Path.of(args[0]);
        if (!Files.isRegularFile(jobFile) || !Files.isReadable(jobFile)) {
            err.println("ERROR: Addax job file is not readable: " + jobFile);
            return EX_NOINPUT;
        }

        String storedJob = Files.readString(jobFile, StandardCharsets.UTF_8);
        String template = unsealJobIfNeeded(storedJob, env, err);
        if (template == null) {
            return EX_CONFIG;
        }
        String rendered = renderTemplate(template, env, err);
        if (rendered == null) {
            return EX_CONFIG;
        }

        List<Path> decryptedPlaintexts = new ArrayList<>();
        String prepared;
        try {
            prepared = decryptEncryptedInputs(rendered, env, decryptedPlaintexts);
        } catch (GeneralSecurityException | IOException ex) {
            err.println("ERROR: failed to decrypt encrypted upload input: " + ex.getMessage());
            erasePlaintexts(decryptedPlaintexts);
            return EX_CONFIG;
        }

        // P1 修复：把写临时 job 纳入 try/finally，确保 writeTempJob 失败时已解密明文仍被擦除
        Path tempFile = null;
        try {
            tempFile = writeTempJob(jobFile, prepared, env);
            return runAddax(tempFile, env, err);
        } finally {
            try {
                if (tempFile != null) {
                    Files.deleteIfExists(tempFile);
                }
            } catch (IOException | RuntimeException cleanupFailure) {
                err.println("WARN: failed to delete temporary Addax job");
            } finally {
                erasePlaintexts(decryptedPlaintexts);
            }
        }
    }

    private static final Pattern ENC_REFERENCE = Pattern.compile("\"([^\"]*\\.enc)\"");
    private static final String ENCRYPTION_KEY_ENV = "DTS_INFRA_ENCRYPTION_KEY";
    private static final String KEY_VERSION_ENV = "DTS_INFRA_KEY_VERSION";

    private static String unsealJobIfNeeded(String storedJob, Map<String, String> env, PrintStream err) {
        if (!AddaxFileCrypto.isSealedJob(storedJob)) {
            return storedJob;
        }
        String base64Key = env.get(ENCRYPTION_KEY_ENV);
        if (base64Key == null || base64Key.isBlank()) {
            err.println("ERROR: sealed Addax job requires " + ENCRYPTION_KEY_ENV);
            return null;
        }
        String expectedVersion = env.get(KEY_VERSION_ENV);
        if (expectedVersion == null || expectedVersion.isBlank()) {
            err.println("ERROR: sealed Addax job requires " + KEY_VERSION_ENV);
            return null;
        }
        byte[] plain = null;
        try {
            SecretKey key = AddaxFileCrypto.keyFromBase64(base64Key);
            AddaxFileCrypto.EncryptedPayload payload = AddaxFileCrypto.parseSealedJob(storedJob);
            if (!expectedVersion.equals(payload.keyVersion())) {
                err.println("ERROR: sealed Addax job key version mismatch");
                return null;
            }
            plain = AddaxFileCrypto.decrypt(payload, key);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException ex) {
            err.println("ERROR: failed to unseal Addax job");
            return null;
        } finally {
            if (plain != null) {
                java.util.Arrays.fill(plain, (byte) 0);
            }
        }
    }

    /**
     * 把 job 中引用的 *.enc 密文解密到 TMPDIR（tmpfs）明文，并将路径改写为明文路径。
     * 无 .enc 引用时原样返回（向后兼容）。明文文件登记到 created，调用方负责擦除。
     */
    private static String decryptEncryptedInputs(String rendered, Map<String, String> env, List<Path> created)
        throws IOException, GeneralSecurityException {
        Matcher matcher = ENC_REFERENCE.matcher(rendered);
        Set<String> encPaths = new LinkedHashSet<>();
        while (matcher.find()) {
            encPaths.add(matcher.group(1));
        }
        if (encPaths.isEmpty()) {
            return rendered;
        }
        String base64Key = env.get(ENCRYPTION_KEY_ENV);
        if (base64Key == null || base64Key.isBlank()) {
            throw new GeneralSecurityException("encrypted input present but " + ENCRYPTION_KEY_ENV + " is not set");
        }
        SecretKey key = AddaxFileCrypto.keyFromBase64(base64Key);
        String expectedVersion = env.get(KEY_VERSION_ENV);
        Path tmpDir = Path.of(env.getOrDefault("TMPDIR", System.getProperty("java.io.tmpdir")));
        String result = rendered;
        for (String encPath : encPaths) {
            Path enc = Path.of(encPath);
            if (!Files.isReadable(enc)) {
                throw new IOException("encrypted input not readable: " + encPath);
            }
            AddaxFileCrypto.EncryptedPayload parsed = AddaxFileCrypto.parse(Files.readAllBytes(enc));
            if (expectedVersion == null || expectedVersion.isBlank()) {
                throw new GeneralSecurityException("encrypted input present but " + KEY_VERSION_ENV + " is not set");
            }
            // P0-3：密钥版本一致性校验，避免多 key 轮转场景误用错误版本密钥
            if (!expectedVersion.equals(parsed.keyVersion())) {
                throw new GeneralSecurityException(
                    "key version mismatch for " + encPath + ": file=" + parsed.keyVersion() + " env=" + expectedVersion);
            }
            byte[] plain = AddaxFileCrypto.decrypt(parsed, key);
            Path plaintext = writePlaintext(tmpDir, enc.getFileName().toString(), plain);
            created.add(plaintext);
            String quotedEncPath = "\"" + encPath + "\"";
            String quotedPlainPath = "\"" + plaintext + "\"";
            result = result.replace(quotedEncPath, quotedPlainPath);
        }
        return result;
    }

    private static Path writePlaintext(Path tmpDir, String encFileName, byte[] plain) throws IOException {
        String base = encFileName.endsWith(".enc") ? encFileName.substring(0, encFileName.length() - 4) : encFileName;
        int dot = base.lastIndexOf('.');
        String suffix = dot >= 0 ? base.substring(dot) : "";
        Path out = Files.createTempFile(tmpDir, "addax-plain-", suffix);
        try {
            Files.setPosixFilePermissions(out, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException ignored) {
            // 非 POSIX 文件系统（部分测试平台）；内容仍位于运行期 tmpfs，作业结束擦除
        }
        Files.write(out, plain);
        return out;
    }

    private static void erasePlaintexts(List<Path> created) {
        for (Path plaintext : created) {
            try {
                Files.deleteIfExists(plaintext);
            } catch (IOException ignored) {
                // best-effort：容器 tmpfs 随 auto_remove 一并销毁
            }
        }
    }

    private static String renderTemplate(String template, Map<String, String> env, PrintStream err) {
        String rendered = template;
        for (String credentialName : CREDENTIAL_NAMES) {
            String placeholder = "${" + credentialName + "}";
            if (!rendered.contains(placeholder)) {
                continue;
            }
            String value = env.getOrDefault(credentialName, "");
            if (value.isEmpty()) {
                err.println("ERROR: required Addax credential env var is missing: " + credentialName);
                return null;
            }
            rendered = rendered.replace(placeholder, jsonStringEscape(value));
        }
        return rendered;
    }

    private static Path writeTempJob(Path jobFile, String rendered, Map<String, String> env) throws IOException {
        Path tempDir = Path.of(env.getOrDefault("TMPDIR", System.getProperty("java.io.tmpdir")));
        String baseName = jobFile.getFileName() == null ? "job" : jobFile.getFileName().toString();
        String safeBaseName = baseName.replaceAll("[^A-Za-z0-9._-]", "_");
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile(tempDir, "addax-job-" + safeBaseName + "-", ".json");
            Files.setPosixFilePermissions(
                tempFile,
                Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
            );
            Files.writeString(tempFile, rendered, StandardCharsets.UTF_8);
            return tempFile;
        } catch (IOException | RuntimeException writeFailure) {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException | RuntimeException ignored) {
                    // Best-effort only; preserve the original write failure.
                }
            }
            throw writeFailure;
        }
    }

    private static int runAddax(Path tempFile, Map<String, String> env, PrintStream err) throws InterruptedException {
        String addaxBin = env.getOrDefault("ADDAX_BIN", DEFAULT_ADDAX_BIN);
        ProcessBuilder pb = new ProcessBuilder(addaxBin, tempFile.toString());
        pb.inheritIO();
        try {
            Process process = pb.start();
            return process.waitFor();
        } catch (IOException ex) {
            err.println("ERROR: failed to execute Addax binary: " + addaxBin + " (" + ex.getMessage() + ")");
            return EX_COMMAND_NOT_FOUND;
        }
    }

    private static String jsonStringEscape(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) ch));
                    } else {
                        escaped.append(ch);
                    }
                }
            }
        }
        return escaped.toString();
    }
}
