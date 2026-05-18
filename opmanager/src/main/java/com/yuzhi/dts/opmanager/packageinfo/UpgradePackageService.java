package com.yuzhi.dts.opmanager.packageinfo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.opmanager.config.OpManagerProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UpgradePackageService {

    private static final DateTimeFormatter ID_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").withZone(ZoneOffset.UTC);

    private final OpManagerProperties properties;
    private final ObjectMapper objectMapper;

    public UpgradePackageService(OpManagerProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public PackageRegistration registerServerPath(Path serverPath) {
        Path normalizedPath = serverPath.toAbsolutePath().normalize();
        ensureAllowedPackagePath(normalizedPath);
        PackageValidationResult validation = validateDirectoryPackage(normalizedPath);
        PackageRegistration registration = new PackageRegistration(
            newRegistrationId(validation, normalizedPath),
            normalizedPath.toString(),
            Instant.now().toString(),
            validation
        );
        persistRegistration(registration);
        return registration;
    }

    public UploadResult storeUploadedFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("uploaded file is empty");
        }
        String originalName = Optional.ofNullable(file.getOriginalFilename()).orElse("package.bin");
        String safeName = sanitizeFileName(originalName);
        Path target = properties.uploadDir().resolve(ID_TIME.format(Instant.now()) + "-" + safeName).toAbsolutePath().normalize();
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
            return new UploadResult(
                originalName,
                target.toString(),
                Files.size(target),
                "uploaded archive saved; server-path registration or archive extraction can validate it"
            );
        } catch (IOException e) {
            throw new IllegalStateException("failed to store uploaded package", e);
        }
    }

    public Optional<PackageRegistration> find(String id) {
        Path file = registrationFile(id);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(file.toFile(), PackageRegistration.class));
        } catch (IOException e) {
            throw new IllegalStateException("failed to read package registration " + id, e);
        }
    }

    public List<PackageRegistration> list() {
        Path registryDir = properties.packageRegistryDir();
        if (!Files.isDirectory(registryDir)) {
            return List.of();
        }
        try (var stream = Files.list(registryDir)) {
            return stream
                .filter(path -> path.getFileName().toString().endsWith(".json"))
                .sorted()
                .map(path -> find(path.getFileName().toString().replaceFirst("\\.json$", "")))
                .flatMap(Optional::stream)
                .toList();
        } catch (IOException e) {
            throw new IllegalStateException("failed to list package registrations", e);
        }
    }

    private PackageValidationResult validateDirectoryPackage(Path packageRoot) {
        List<String> messages = new ArrayList<>();
        if (!Files.isDirectory(packageRoot)) {
            return new PackageValidationResult(false, null, null, null, null, packageRoot.toString(), List.of("package path is not a directory"));
        }

        Path manifestPath = packageRoot.resolve("manifest.json");
        if (!Files.isRegularFile(manifestPath)) {
            return new PackageValidationResult(false, null, null, null, null, packageRoot.toString(), List.of("manifest.json is missing"));
        }

        PackageManifest manifest;
        try {
            manifest = objectMapper.readValue(manifestPath.toFile(), PackageManifest.class);
        } catch (IOException e) {
            return new PackageValidationResult(false, null, null, null, null, packageRoot.toString(), List.of("manifest.json cannot be parsed"));
        }

        validateRequired(manifest, messages);
        validateFiles(packageRoot, manifest.files(), "files", messages);
        List<PackageManifestFile> imageFiles = Optional.ofNullable(manifest.images()).orElse(List.of()).stream()
            .map(image -> new PackageManifestFile(image.file(), image.sha256(), image.size()))
            .toList();
        validateFiles(packageRoot, imageFiles, "images", messages);

        return new PackageValidationResult(
            messages.isEmpty(),
            manifest.packageId(),
            manifest.product(),
            manifest.version(),
            manifest.targetArch(),
            packageRoot.toString(),
            List.copyOf(messages)
        );
    }

    private void validateRequired(PackageManifest manifest, List<String> messages) {
        if (!Objects.equals(manifest.schemaVersion(), 1)) {
            messages.add("schemaVersion must be 1");
        }
        if (isBlank(manifest.packageId())) {
            messages.add("packageId is required");
        }
        if (isBlank(manifest.product())) {
            messages.add("product is required");
        }
        if (isBlank(manifest.version())) {
            messages.add("version is required");
        }
        if (isBlank(manifest.targetArch())) {
            messages.add("targetArch is required");
        }
    }

    private void validateFiles(Path packageRoot, List<PackageManifestFile> files, String section, List<String> messages) {
        for (PackageManifestFile declaredFile : Optional.ofNullable(files).orElse(List.of())) {
            if (isBlank(declaredFile.path())) {
                messages.add(section + " contains an empty path");
                continue;
            }
            if (isUnsafeRelativePath(declaredFile.path())) {
                messages.add("unsafe manifest path: " + declaredFile.path());
                continue;
            }
            Path actual = packageRoot.resolve(declaredFile.path()).normalize();
            if (!actual.startsWith(packageRoot)) {
                messages.add("unsafe manifest path: " + declaredFile.path());
                continue;
            }
            if (!Files.isRegularFile(actual)) {
                messages.add("declared file is missing: " + declaredFile.path());
                continue;
            }
            validateSize(actual, declaredFile, messages);
            validateSha256(actual, declaredFile, messages);
        }
    }

    private void validateSize(Path actual, PackageManifestFile declaredFile, List<String> messages) {
        if (declaredFile.size() == null) {
            return;
        }
        try {
            long actualSize = Files.size(actual);
            if (actualSize != declaredFile.size()) {
                messages.add("size mismatch for " + declaredFile.path() + ": expected " + declaredFile.size() + ", got " + actualSize);
            }
        } catch (IOException e) {
            messages.add("cannot read size for " + declaredFile.path());
        }
    }

    private void validateSha256(Path actual, PackageManifestFile declaredFile, List<String> messages) {
        if (isBlank(declaredFile.sha256())) {
            return;
        }
        try {
            String actualDigest = sha256(actual);
            if (!actualDigest.equalsIgnoreCase(declaredFile.sha256())) {
                messages.add("sha256 mismatch for " + declaredFile.path());
            }
        } catch (IOException | NoSuchAlgorithmException e) {
            messages.add("cannot calculate sha256 for " + declaredFile.path());
        }
    }

    private void ensureAllowedPackagePath(Path normalizedPath) {
        List<Path> roots = properties.getPackageRoots().isEmpty() ? List.of(properties.getDataDir().resolve("packages")) : properties.getPackageRoots();
        boolean allowed = roots
            .stream()
            .map(root -> root.toAbsolutePath().normalize())
            .anyMatch(normalizedPath::startsWith);
        if (!allowed) {
            throw new IllegalArgumentException("package path is not under an allowed package root");
        }
    }

    private void persistRegistration(PackageRegistration registration) {
        try {
            Files.createDirectories(properties.packageRegistryDir());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(registrationFile(registration.id()).toFile(), registration);
        } catch (IOException e) {
            throw new IllegalStateException("failed to persist package registration", e);
        }
    }

    private Path registrationFile(String id) {
        return properties.packageRegistryDir().resolve(sanitizeFileName(id) + ".json").toAbsolutePath().normalize();
    }

    private String newRegistrationId(PackageValidationResult validation, Path packageRoot) {
        String base = validation.packageId() == null || validation.packageId().isBlank() ? packageRoot.getFileName().toString() : validation.packageId();
        return sanitizeFileName(base) + "-" + ID_TIME.format(Instant.now());
    }

    private String sanitizeFileName(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "-");
    }

    private boolean isUnsafeRelativePath(String value) {
        Path path = Path.of(value);
        Path normalized = path.normalize();
        return path.isAbsolute() || normalized.startsWith("..") || normalized.toString().isBlank();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String sha256(Path path) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[1024 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
