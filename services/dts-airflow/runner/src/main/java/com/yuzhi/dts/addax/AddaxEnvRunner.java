package com.yuzhi.dts.addax;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

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

        String template = Files.readString(jobFile, StandardCharsets.UTF_8);
        String rendered = renderTemplate(template, env, err);
        if (rendered == null) {
            return EX_CONFIG;
        }

        Path tempFile = writeTempJob(jobFile, rendered, env);
        try {
            return runAddax(tempFile, env, err);
        } finally {
            Files.deleteIfExists(tempFile);
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
        Path tempFile = Files.createTempFile(tempDir, "addax-job-" + safeBaseName + "-", ".json");
        Files.writeString(tempFile, rendered, StandardCharsets.UTF_8);
        return tempFile;
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
