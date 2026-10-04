package com.yuzhi.dts.addax;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class AddaxEnvRunnerTest {

    private static final String RUNNER_CLASS = "com.yuzhi.dts.addax.AddaxEnvRunner";
    private static final Path RUNNER_CLASSES = Path.of(System.getProperty("runner.classes"));

    public static void main(String[] args) throws Exception {
        requiresJobPathArgument();
        rejectsMissingJobFile();
        writerOnlyJobDoesNotRequireReaderPasswordAndEscapesJson();
        requiresReaderPasswordWhenReaderPlaceholderExists();
        propagatesAddaxExitCode();
        decryptsSealedJobOnlyInsideTmpfsAndErasesIt();
        rejectsSealedJobWithoutKey();
        decryptsEncryptedInputToTmpfsAndErases();
        tempJobDeleteFailureStillErasesDecryptedInput();
        failsWhenEncryptedInputButNoKey();
        failsWhenEncryptedInputButNoVersion();
        failsWhenKeyVersionMismatch();
        System.out.println("AddaxEnvRunnerTest: all tests passed");
    }

    private static void decryptsSealedJobOnlyInsideTmpfsAndErasesIt() throws Exception {
        Path dir = Files.createTempDirectory("addax-runner-sealed-job-");
        Path tmpfs = Files.createDirectory(dir.resolve("tmpfs"));
        Path captured = dir.resolve("captured.json");
        Path fakeAddax = fakeAddax(dir, 0, captured);
        String b64Key = java.util.Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
        javax.crypto.SecretKey key = AddaxFileCrypto.keyFromBase64(b64Key);
        byte[] iv = new byte[12];
        new java.security.SecureRandom().nextBytes(iv);
        byte[] payload = AddaxFileCrypto.encrypt(
            "{\"job\":{\"password\":\"sealed-secret\"}}".getBytes(StandardCharsets.UTF_8),
            key,
            iv,
            "v1"
        );
        Path job = dir.resolve("job.json");
        Files.writeString(
            job,
            AddaxFileCrypto.SEALED_JOB_PREFIX + java.util.Base64.getEncoder().encodeToString(payload),
            StandardCharsets.UTF_8
        );

        RunResult result = runRunner(
            Map.of(
                "ADDAX_BIN", fakeAddax.toString(),
                "TMPDIR", tmpfs.toString(),
                "DTS_INFRA_ENCRYPTION_KEY", b64Key,
                "DTS_INFRA_KEY_VERSION", "v1"
            ),
            job.toString()
        );

        assertEquals(0, result.exitCode(), "sealed job exit code");
        assertNotContains(Files.readString(job), "sealed-secret", "managed job remains sealed");
        assertContains(Files.readString(captured), "sealed-secret", "Addax receives runtime plaintext");
        try (java.util.stream.Stream<Path> stream = Files.list(tmpfs)) {
            if (stream.findAny().isPresent()) {
                throw new AssertionError("sealed job plaintext not erased from tmpfs after run");
            }
        }
    }

    private static void rejectsSealedJobWithoutKey() throws Exception {
        Path dir = Files.createTempDirectory("addax-runner-sealed-job-nokey-");
        Path job = dir.resolve("job.json");
        Files.writeString(job, AddaxFileCrypto.SEALED_JOB_PREFIX + "AAAA", StandardCharsets.UTF_8);

        RunResult result = runRunner(Map.of(), job.toString());

        assertEquals(78, result.exitCode(), "sealed job without key exit code");
        assertContains(result.stderr(), "DTS_INFRA_ENCRYPTION_KEY", "missing sealed job key message");
    }

    private static void decryptsEncryptedInputToTmpfsAndErases() throws Exception {
        java.nio.file.Path dir = Files.createTempDirectory("addax-runner-decrypt-");
        java.nio.file.Path tmpfs = Files.createDirectory(dir.resolve("tmpfs"));
        java.nio.file.Path captured = dir.resolve("captured.json");
        java.nio.file.Path fakeAddax = fakeAddax(dir, 0, captured);

        String b64Key = java.util.Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
        javax.crypto.SecretKey key = AddaxFileCrypto.keyFromBase64(b64Key);
        byte[] plaintext = "col_a,col_b\n1,2\n".getBytes(StandardCharsets.UTF_8);
        byte[] iv = new byte[12];
        new java.security.SecureRandom().nextBytes(iv);
        java.nio.file.Path enc = dir.resolve("data.csv.enc");
        Files.write(enc, AddaxFileCrypto.encrypt(plaintext, key, iv, "v1"));

        java.nio.file.Path job = dir.resolve("job.json");
        Files.writeString(job, "{\"reader\":{\"path\":[\"" + enc + "\"]}}", StandardCharsets.UTF_8);

        RunResult result = runRunner(
            Map.of(
                "ADDAX_BIN", fakeAddax.toString(),
                "TMPDIR", tmpfs.toString(),
                "DTS_INFRA_ENCRYPTION_KEY", b64Key,
                "DTS_INFRA_KEY_VERSION", "v1"
            ),
            job.toString()
        );

        assertEquals(0, result.exitCode(), "decrypt job exit code");
        String rendered = Files.readString(captured, StandardCharsets.UTF_8);
        assertNotContains(rendered, ".enc", "enc path replaced with tmpfs plaintext");
        assertContains(rendered, "addax-plain-", "tmpfs plaintext path injected");
        try (java.util.stream.Stream<java.nio.file.Path> stream = Files.list(tmpfs)) {
            if (stream.findAny().isPresent()) {
                throw new AssertionError("plaintext/tempjob not erased from tmpfs after run");
            }
        }
    }

    private static void tempJobDeleteFailureStillErasesDecryptedInput() throws Exception {
        Path dir = Files.createTempDirectory("addax-runner-delete-failure-");
        Path tmpfs = Files.createDirectory(dir.resolve("tmpfs"));
        Path fakeAddax = fakeAddaxThatBlocksTempJobDeletion(dir);
        String b64Key = java.util.Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
        javax.crypto.SecretKey key = AddaxFileCrypto.keyFromBase64(b64Key);
        byte[] iv = new byte[12];
        new java.security.SecureRandom().nextBytes(iv);
        Path encryptedInput = dir.resolve("data.csv.enc");
        Files.write(
            encryptedInput,
            AddaxFileCrypto.encrypt("x,y\n1,2\n".getBytes(StandardCharsets.UTF_8), key, iv, "v1")
        );
        Path job = dir.resolve("job.json");
        Files.writeString(job, "{\"reader\":{\"path\":[\"" + encryptedInput + "\"]}}", StandardCharsets.UTF_8);

        RunResult result = runRunner(
            Map.of(
                "ADDAX_BIN", fakeAddax.toString(),
                "TMPDIR", tmpfs.toString(),
                "DTS_INFRA_ENCRYPTION_KEY", b64Key,
                "DTS_INFRA_KEY_VERSION", "v1"
            ),
            job.toString()
        );

        assertEquals(0, result.exitCode(), "temp job cleanup failure exit code");
        assertContains(result.stderr(), "failed to delete temporary Addax job", "cleanup warning");
        try (java.util.stream.Stream<Path> stream = Files.walk(tmpfs)) {
            boolean leakedPlaintext = stream
                .map(Path::getFileName)
                .filter(java.util.Objects::nonNull)
                .map(Path::toString)
                .anyMatch(name -> name.startsWith("addax-plain-"));
            if (leakedPlaintext) {
                throw new AssertionError("decrypted input cleanup was blocked by temp job deletion failure");
            }
        }
    }

    private static void failsWhenEncryptedInputButNoKey() throws Exception {
        java.nio.file.Path dir = Files.createTempDirectory("addax-runner-nokey-");
        java.nio.file.Path fakeAddax = fakeAddax(dir, 0, dir.resolve("captured.json"));
        java.nio.file.Path enc = dir.resolve("data.csv.enc");
        Files.write(enc, new byte[] { 1, 2, 3 });
        java.nio.file.Path job = dir.resolve("job.json");
        Files.writeString(job, "{\"reader\":{\"path\":[\"" + enc + "\"]}}", StandardCharsets.UTF_8);

        RunResult result = runRunner(Map.of("ADDAX_BIN", fakeAddax.toString()), job.toString());

        assertEquals(78, result.exitCode(), "missing key exit code");
        assertContains(result.stderr(), "DTS_INFRA_ENCRYPTION_KEY", "missing key message");
    }

    private static void failsWhenEncryptedInputButNoVersion() throws Exception {
        java.nio.file.Path dir = Files.createTempDirectory("addax-runner-noversion-");
        java.nio.file.Path fakeAddax = fakeAddax(dir, 0, dir.resolve("captured.json"));
        String b64Key = java.util.Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
        javax.crypto.SecretKey key = AddaxFileCrypto.keyFromBase64(b64Key);
        byte[] plain = "x,y\n1,2\n".getBytes(StandardCharsets.UTF_8);
        byte[] iv = new byte[12];
        new java.security.SecureRandom().nextBytes(iv);
        java.nio.file.Path enc = dir.resolve("data.csv.enc");
        Files.write(enc, AddaxFileCrypto.encrypt(plain, key, iv, "v1"));
        java.nio.file.Path job = dir.resolve("job.json");
        Files.writeString(job, "{\"reader\":{\"path\":[\"" + enc + "\"]}}", StandardCharsets.UTF_8);

        RunResult result = runRunner(
            Map.of("ADDAX_BIN", fakeAddax.toString(), "DTS_INFRA_ENCRYPTION_KEY", b64Key),
            job.toString()
        );

        assertEquals(78, result.exitCode(), "missing key version exit code");
        assertContains(result.stderr(), "DTS_INFRA_KEY_VERSION", "missing key version message");
    }

    private static void failsWhenKeyVersionMismatch() throws Exception {
        java.nio.file.Path dir = Files.createTempDirectory("addax-runner-vermismatch-");
        java.nio.file.Path fakeAddax = fakeAddax(dir, 0, dir.resolve("captured.json"));
        String b64Key = java.util.Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
        javax.crypto.SecretKey key = AddaxFileCrypto.keyFromBase64(b64Key);
        byte[] iv = new byte[12];
        new java.security.SecureRandom().nextBytes(iv);
        java.nio.file.Path enc = dir.resolve("data.csv.enc");
        Files.write(enc, AddaxFileCrypto.encrypt("x,y\n1,2\n".getBytes(StandardCharsets.UTF_8), key, iv, "v1"));
        java.nio.file.Path job = dir.resolve("job.json");
        Files.writeString(job, "{\"reader\":{\"path\":[\"" + enc + "\"]}}", StandardCharsets.UTF_8);

        // 文件用 v1 加密，环境声明 v2 → 必须在解密前以清晰错误拒绝
        RunResult result = runRunner(
            Map.of(
                "ADDAX_BIN", fakeAddax.toString(),
                "DTS_INFRA_ENCRYPTION_KEY", b64Key,
                "DTS_INFRA_KEY_VERSION", "v2"
            ),
            job.toString()
        );

        assertEquals(78, result.exitCode(), "key version mismatch exit code");
        assertContains(result.stderr(), "key version mismatch", "version mismatch message");
    }

    private static void requiresJobPathArgument() throws Exception {
        RunResult result = runRunner(Map.of());

        assertEquals(64, result.exitCode(), "missing argument exit code");
        assertContains(result.stderr(), "Usage: AddaxEnvRunner <job.json>", "usage message");
    }

    private static void rejectsMissingJobFile() throws Exception {
        RunResult result = runRunner(Map.of(), "/tmp/dts-addax-runner-missing.json");

        assertEquals(66, result.exitCode(), "missing job file exit code");
        assertContains(result.stderr(), "Addax job file is not readable", "missing file message");
    }

    private static void writerOnlyJobDoesNotRequireReaderPasswordAndEscapesJson() throws Exception {
        Path dir = Files.createTempDirectory("addax-runner-writer-only-");
        Path captured = dir.resolve("captured.json");
        Path fakeAddax = fakeAddax(dir, 0, captured);
        Path job = dir.resolve("job.json");
        Files.writeString(
            job,
            "{\"writer\":{\"password\":\"${DTS_TARGET_DB_PASSWORD}\"}}",
            StandardCharsets.UTF_8
        );

        RunResult result = runRunner(
            Map.of(
                "ADDAX_BIN", fakeAddax.toString(),
                "DTS_TARGET_DB_PASSWORD", "pa\"ss\\word&pipe|tab\tend"
            ),
            job.toString()
        );

        assertEquals(0, result.exitCode(), "writer-only job exit code");
        assertNotContains(result.stderr(), "DTS_ADDAX_READER_PASSWORD", "reader password should not be required");
        String rendered = Files.readString(captured, StandardCharsets.UTF_8);
        assertContains(rendered, "pa\\\"ss\\\\word&pipe|tab\\tend", "JSON-escaped writer password");
        assertNotContains(rendered, "${DTS_TARGET_DB_PASSWORD}", "writer placeholder removed");
    }

    private static void requiresReaderPasswordWhenReaderPlaceholderExists() throws Exception {
        Path dir = Files.createTempDirectory("addax-runner-reader-required-");
        Path fakeAddax = fakeAddax(dir, 0, dir.resolve("captured.json"));
        Path job = dir.resolve("job.json");
        Files.writeString(
            job,
            "{\"reader\":{\"password\":\"${DTS_ADDAX_READER_PASSWORD}\"},"
                + "\"writer\":{\"password\":\"${DTS_TARGET_DB_PASSWORD}\"}}",
            StandardCharsets.UTF_8
        );

        RunResult result = runRunner(
            Map.of(
                "ADDAX_BIN", fakeAddax.toString(),
                "DTS_TARGET_DB_PASSWORD", "writer-secret"
            ),
            job.toString()
        );

        assertEquals(78, result.exitCode(), "missing reader password exit code");
        assertContains(result.stderr(), "DTS_ADDAX_READER_PASSWORD", "missing reader password message");
    }

    private static void propagatesAddaxExitCode() throws Exception {
        Path dir = Files.createTempDirectory("addax-runner-exit-code-");
        Path fakeAddax = fakeAddax(dir, 23, dir.resolve("captured.json"));
        Path job = dir.resolve("job.json");
        Files.writeString(job, "{\"job\":{}}", StandardCharsets.UTF_8);

        RunResult result = runRunner(Map.of("ADDAX_BIN", fakeAddax.toString()), job.toString());

        assertEquals(23, result.exitCode(), "Addax child exit code propagation");
    }

    private static Path fakeAddax(Path dir, int exitCode, Path captured) throws IOException {
        Path fakeAddax = dir.resolve("fake-addax.sh");
        Files.writeString(
            fakeAddax,
            "#!/usr/bin/env sh\n"
                + "set -eu\n"
                + "cp \"$1\" \"" + captured + "\"\n"
                + "exit " + exitCode + "\n",
            StandardCharsets.UTF_8
        );
        fakeAddax.toFile().setExecutable(true);
        return fakeAddax;
    }

    private static Path fakeAddaxThatBlocksTempJobDeletion(Path dir) throws IOException {
        Path fakeAddax = dir.resolve("fake-addax-block-delete.sh");
        Files.writeString(
            fakeAddax,
            "#!/usr/bin/env sh\n"
                + "set -eu\n"
                + "rm -f \"$1\"\n"
                + "mkdir \"$1\"\n"
                + "touch \"$1/keep\"\n",
            StandardCharsets.UTF_8
        );
        fakeAddax.toFile().setExecutable(true);
        return fakeAddax;
    }

    private static RunResult runRunner(Map<String, String> env, String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("java");
        command.add("-cp");
        command.add(RUNNER_CLASSES.toString());
        command.add(RUNNER_CLASS);
        command.addAll(List.of(args));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.environment().putAll(env);
        Process process = pb.start();
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        return new RunResult(exitCode, stdout, stderr);
    }

    private static void assertEquals(int expected, int actual, String label) {
        if (expected != actual) {
            throw new AssertionError(label + ": expected " + expected + " but got " + actual);
        }
    }

    private static void assertContains(String actual, String expected, String label) {
        if (!actual.contains(expected)) {
            throw new AssertionError(label + ": expected to contain [" + expected + "] but was [" + actual + "]");
        }
    }

    private static void assertNotContains(String actual, String expected, String label) {
        if (actual.contains(expected)) {
            throw new AssertionError(label + ": expected not to contain [" + expected + "] but was [" + actual + "]");
        }
    }

    private record RunResult(int exitCode, String stdout, String stderr) {}
}
