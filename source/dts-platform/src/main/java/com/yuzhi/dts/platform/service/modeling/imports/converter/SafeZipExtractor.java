package com.yuzhi.dts.platform.service.modeling.imports.converter;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public final class SafeZipExtractor {

    public static final long MAX_ARCHIVE_BYTES = 32L * 1024 * 1024;

    private static final Logger LOG = LoggerFactory.getLogger(SafeZipExtractor.class);
    private static final int MAX_ENTRIES = 2_000;
    private static final long MAX_ENTRY_BYTES = 32L * 1024 * 1024;
    private static final long MAX_TOTAL_BYTES = 64L * 1024 * 1024;
    private static final long MIN_EXPANDED_BYTES = 4L * 1024 * 1024;
    private static final long MAX_COMPRESSION_RATIO = 40L;
    private static final Set<String> SENSITIVE_FILE_NAMES = Set.of(
        "profiles.yml",
        "profiles.yaml",
        "credentials",
        "credentials.json",
        "credentials.yml",
        "credentials.yaml",
        "service-account.json",
        "service_account.json",
        "application-default-credentials.json",
        "id_rsa",
        "id_dsa",
        "id_ecdsa",
        "id_ed25519",
        ".netrc"
    );
    private static final Set<String> SENSITIVE_FILE_EXTENSIONS = Set.of(
        ".pem",
        ".key",
        ".p8",
        ".p12",
        ".pfx",
        ".jks",
        ".keystore"
    );

    private final CleanupPass cleanupPass;

    public SafeZipExtractor() {
        this(SafeZipExtractor::deleteRecursivelyOnce);
    }

    SafeZipExtractor(CleanupPass cleanupPass) {
        this.cleanupPass = cleanupPass;
    }

    public ExtractedArchive extract(MultipartFile archive) {
        validateUpload(archive);

        Path root = null;
        try {
            validateMagic(archive);
            root = Files.createTempDirectory("dts-model-import-");
            extractEntries(archive, root, maxExpandedBytes(archive.getSize()));
            return new ExtractedArchive(root, this);
        } catch (ArchiveException exception) {
            deleteRecursively(root);
            throw exception;
        } catch (IOException exception) {
            deleteRecursively(root);
            throw new ArchiveException("INVALID", "Unable to read ZIP archive", exception);
        } catch (RuntimeException exception) {
            deleteRecursively(root);
            throw exception;
        }
    }

    private static void validateUpload(MultipartFile archive) {
        if (archive == null || archive.isEmpty() || archive.getSize() <= 0) {
            throw new ArchiveException("EMPTY", "Archive must not be empty");
        }
        if (archive.getSize() > MAX_ARCHIVE_BYTES) {
            throw new ArchiveException("TOO_LARGE", "Archive exceeds the maximum allowed size");
        }
        String filename = archive.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw new ArchiveException("INVALID", "Archive filename must end in .zip");
        }
    }

    private static void validateMagic(MultipartFile archive) throws IOException {
        try (InputStream input = archive.getInputStream()) {
            byte[] signature = input.readNBytes(4);
            if (signature.length < 2 || signature[0] != 'P' || signature[1] != 'K') {
                throw new ArchiveException("INVALID", "Archive does not have a ZIP signature");
            }
        }
    }

    private static void extractEntries(MultipartFile archive, Path root, long maxExpandedBytes) throws IOException {
        int entryCount = 0;
        long totalBytes = 0;
        Set<Path> extractedPaths = new HashSet<>();

        try (ZipInputStream input = new ZipInputStream(archive.getInputStream())) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (++entryCount > MAX_ENTRIES) {
                    throw new ArchiveException("TOO_LARGE", "Archive contains too many entries");
                }

                Path target = safeTarget(root, entry.getName());
                if (!extractedPaths.add(target)) {
                    throw new ArchiveException("UNSAFE_PATH", "Archive contains duplicate normalized paths");
                }
                if (isNestedArchive(entry.getName())) {
                    throw new ArchiveException("UNSAFE_PATH", "Nested archives are not allowed");
                }
                if (isSensitiveFile(entry.getName())) {
                    throw new ArchiveException(
                        "SENSITIVE_FILE",
                        "Archive contains prohibited credential material"
                    );
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    long entryBytes = 0;
                    try (var output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = input.read(buffer)) != -1) {
                            entryBytes += read;
                            totalBytes += read;
                            if (entryBytes > MAX_ENTRY_BYTES || totalBytes > maxExpandedBytes) {
                                throw new ArchiveException("TOO_LARGE", "Archive uncompressed content exceeds the allowed size");
                            }
                            output.write(buffer, 0, read);
                        }
                    }
                }
                input.closeEntry();
            }
        }
    }

    private static long maxExpandedBytes(long archiveBytes) {
        long ratioLimit = Math.max(MIN_EXPANDED_BYTES, archiveBytes * MAX_COMPRESSION_RATIO);
        return Math.min(MAX_TOTAL_BYTES, ratioLimit);
    }

    private static Path safeTarget(Path root, String name) {
        if (name == null || name.isEmpty() || containsControlCharacter(name) || name.indexOf('\\') >= 0 || isAbsolutePath(name)) {
            throw new ArchiveException("UNSAFE_PATH", "Archive entry path is unsafe");
        }
        for (String segment : name.split("/", -1)) {
            if ("..".equals(segment)) {
                throw new ArchiveException("UNSAFE_PATH", "Archive entry path contains traversal");
            }
        }
        Path target = root.resolve(name).normalize();
        if (!target.startsWith(root)) {
            throw new ArchiveException("UNSAFE_PATH", "Archive entry escapes extraction root");
        }
        return target;
    }

    private static boolean containsControlCharacter(String value) {
        return value.chars().anyMatch(character -> character == 0 || Character.isISOControl(character));
    }

    private static boolean isAbsolutePath(String value) {
        return value.startsWith("/") || (value.length() >= 3 && Character.isLetter(value.charAt(0)) && value.charAt(1) == ':');
    }

    private static boolean isNestedArchive(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        return normalized.endsWith(".zip") || normalized.endsWith(".jar") || normalized.endsWith(".war");
    }

    private static boolean isSensitiveFile(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        int separator = normalized.lastIndexOf('/');
        String fileName = separator < 0 ? normalized : normalized.substring(separator + 1);
        if (fileName.equals(".env") || fileName.startsWith(".env.")) {
            return true;
        }
        if (SENSITIVE_FILE_NAMES.contains(fileName)) {
            return true;
        }
        return SENSITIVE_FILE_EXTENSIONS.stream().anyMatch(fileName::endsWith);
    }

    private void deleteRecursively(Path root) {
        if (root == null || Files.notExists(root)) {
            return;
        }
        try {
            cleanupPass.delete(root);
            return;
        } catch (IOException firstFailure) {
            LOG.warn("Temporary model import archive cleanup failed; retrying once");
        }
        try {
            cleanupPass.delete(root);
        } catch (IOException secondFailure) {
            LOG.error("Temporary model import archive cleanup still failed after retry");
            throw new ArchiveException(
                "CLEANUP_FAILED",
                "Temporary model import archive cleanup failed"
            );
        }
    }

    static void deleteRecursivelyOnce(Path root) throws IOException {
        if (Files.notExists(root)) {
            return;
        }
        List<Path> paths;
        try (var pathStream = Files.walk(root)) {
            paths = pathStream.sorted(Comparator.reverseOrder()).toList();
        }
        IOException failure = null;
        for (Path path : paths) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException exception) {
                if (failure == null) {
                    failure = new IOException("One or more temporary archive paths could not be deleted");
                }
                failure.addSuppressed(exception);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    @FunctionalInterface
    interface CleanupPass {
        void delete(Path root) throws IOException;
    }

    public static final class ArchiveException extends RuntimeException {

        private final String code;

        public ArchiveException(String code, String message) {
            super(message);
            this.code = code;
        }

        public ArchiveException(String code, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    public static final class ExtractedArchive implements AutoCloseable {

        private final Path root;
        private final SafeZipExtractor extractor;

        private ExtractedArchive(Path root, SafeZipExtractor extractor) {
            this.root = root;
            this.extractor = extractor;
        }

        public Path root() {
            return root;
        }

        @Override
        public void close() {
            extractor.deleteRecursively(root);
        }
    }
}
