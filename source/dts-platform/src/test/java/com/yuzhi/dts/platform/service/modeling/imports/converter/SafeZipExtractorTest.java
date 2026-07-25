package com.yuzhi.dts.platform.service.modeling.imports.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(OutputCaptureExtension.class)
class SafeZipExtractorTest {

    private final SafeZipExtractor extractor = new SafeZipExtractor();

    @Test
    void extractsRegularFilesIntoAnOwnedTemporaryDirectory() throws Exception {
        var archive = zip(Map.of("models/orders.sql", "select 1", "manifest.yml", "version: 1"));

        try (var extracted = extractor.extract(file("model.zip", archive))) {
            assertThat(Files.readString(extracted.root().resolve("models/orders.sql"))).isEqualTo("select 1");
            assertThat(Files.readString(extracted.root().resolve("manifest.yml"))).isEqualTo("version: 1");
        }
    }

    @Test
    void rejectsBadMagic() {
        assertCode("INVALID", () -> extractor.extract(file("model.zip", "not a zip".getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void rejectsTraversalAndWindowsAbsolutePaths() throws Exception {
        assertCode("UNSAFE_PATH", () -> extractor.extract(file("model.zip", zip(Map.of("../outside.yml", "bad")))));
        assertCode("UNSAFE_PATH", () -> extractor.extract(file("model.zip", zip(Map.of("C:/outside.yml", "bad")))));
    }

    @Test
    void rejectsBackslashPathsAndDuplicateNormalizedTargets() throws Exception {
        assertCode("UNSAFE_PATH", () -> extractor.extract(file("model.zip", zip(Map.of("models\\outside.yml", "bad")))));

        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("models/", null);
        entries.put("models", "not-a-directory");
        assertCode("UNSAFE_PATH", () -> extractor.extract(file("model.zip", zip(entries))));
    }

    @Test
    void rejectsNestedArchives() throws Exception {
        assertCode("UNSAFE_PATH", () -> extractor.extract(file("model.zip", zip(Map.of("dependency.jar", "bad")))));
    }

    @Test
    void rejectsExtremeCompressionExpansionPastTheFourMiBFloor() throws Exception {
        byte[] expanded = new byte[(4 * 1024 * 1024) + 1];

        assertCode("TOO_LARGE", () -> extractor.extract(file("model.zip", zip("models/expanded.sql", expanded))));
    }

    @Test
    void closesByDeletingTheTemporaryDirectory() throws Exception {
        var extracted = extractor.extract(file("model.zip", zip(Map.of("manifest.yml", "version: 1"))));
        var root = extracted.root();

        extracted.close();

        assertThat(Files.exists(root)).isFalse();
    }

    @Test
    void warnsAndRetriesWhenTheFirstCleanupPassFails(CapturedOutput output) throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        SafeZipExtractor retryingExtractor = new SafeZipExtractor(root -> {
            if (attempts.incrementAndGet() == 1) {
                throw new IOException("simulated cleanup failure");
            }
            SafeZipExtractor.deleteRecursivelyOnce(root);
        });
        var extracted = retryingExtractor.extract(file("model.zip", zip(Map.of("manifest.yml", "version: 1"))));
        var root = extracted.root();

        extracted.close();

        assertThat(attempts).hasValue(2);
        assertThat(Files.exists(root)).isFalse();
        assertThat(output.getAll()).contains("Temporary model import archive cleanup failed; retrying once");
    }

    private static MockMultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("archive", name, "application/zip", content);
    }

    private static byte[] zip(Map<String, String> entries) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var output = new ZipOutputStream(bytes)) {
            for (var entry : entries.entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey()));
                if (entry.getValue() != null) {
                    output.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                }
                output.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static byte[] zip(String name, byte[] content) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var output = new ZipOutputStream(bytes)) {
            output.putNextEntry(new ZipEntry(name));
            output.write(content);
            output.closeEntry();
        }
        return bytes.toByteArray();
    }

    private static void assertCode(String code, ThrowingCall call) {
        assertThatThrownBy(call::run)
            .isInstanceOf(SafeZipExtractor.ArchiveException.class)
            .satisfies(exception -> assertThat(((SafeZipExtractor.ArchiveException) exception).code()).isEqualTo(code));
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run() throws Exception;
    }
}
