package com.yuzhi.dts.opmanager.configfiles;

import com.yuzhi.dts.opmanager.config.OpManagerProperties;
import com.yuzhi.dts.opmanager.packageinfo.PackageRegistration;
import com.yuzhi.dts.opmanager.packageinfo.UpgradePackageService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

@Service
public class ProtectedConfigService {

    private static final long PREVIEW_LIMIT_BYTES = 512 * 1024;
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);
    private static final Pattern ENV_KEY = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*\\s*=.*$");

    private final OpManagerProperties properties;
    private final UpgradePackageService packageService;

    public ProtectedConfigService(OpManagerProperties properties, UpgradePackageService packageService) {
        this.properties = properties;
        this.packageService = packageService;
    }

    public ConfigPrecheckResponse precheck(String packageRegistrationId) {
        PackageRegistration registration = resolveRegistration(packageRegistrationId);
        Path packageRoot = packageService.resolveStackRoot(registration);
        Path targetRoot = properties.getTargetStackDir().toAbsolutePath().normalize();
        List<ConfigFileReview> files = collectProtectedPaths(targetRoot, packageRoot).stream().map(path -> reviewFile(targetRoot, packageRoot, path)).toList();
        int changed = (int) files.stream().filter(file -> file.status() == ConfigFileStatus.MODIFIED || file.status() == ConfigFileStatus.PACKAGE_ONLY).count();
        int highRisk = (int) files.stream().filter(file -> file.risk() == ConfigRisk.HIGH).count();
        return new ConfigPrecheckResponse(
            registration.id(),
            registration.validation().packageId(),
            targetRoot.toString(),
            packageRoot.toString(),
            files.size(),
            changed,
            highRisk,
            files
        );
    }

    public ConfigApplyResult apply(String packageRegistrationId, String relativePath, ConfigApplyAction action) {
        PackageRegistration registration = resolveRegistration(packageRegistrationId);
        Path safePath = safeRelativePath(relativePath);
        Path packageRoot = packageService.resolveStackRoot(registration);
        Path targetRoot = properties.getTargetStackDir().toAbsolutePath().normalize();
        ConfigFileReview review = reviewFile(targetRoot, packageRoot, safePath.toString().replace('\\', '/'));
        if (!review.allowedActions().contains(action)) {
            throw new IllegalArgumentException("action is not allowed for protected config file");
        }
        return switch (action) {
            case KEEP_LOCAL -> new ConfigApplyResult(false, review.path(), action, "kept local file", "", "");
            case MERGE_ENV_ADD_KEYS -> mergeEnvAddKeys(targetRoot, packageRoot, safePath);
            case WRITE_PACKAGE_COPY -> writePackageCopy(targetRoot, packageRoot, safePath);
            case USE_PACKAGE -> usePackageFile(targetRoot, packageRoot, safePath);
        };
    }

    private PackageRegistration resolveRegistration(String id) {
        return packageService.find(id).orElseThrow(() -> new NoSuchElementException("package registration not found"));
    }

    private List<String> collectProtectedPaths(Path targetRoot, Path packageRoot) {
        LinkedHashSet<String> paths = new LinkedHashSet<>();
        paths.add(".env");
        collectRootComposeFiles(targetRoot, paths);
        collectRootComposeFiles(packageRoot, paths);
        collectDirectory(targetRoot, "config/mdm", paths);
        collectDirectory(packageRoot, "config/mdm", paths);
        collectDirectory(targetRoot, "data/mdm", paths);
        collectDirectory(packageRoot, "data/mdm", paths);
        collectDirectory(targetRoot, "services/mdm", paths);
        collectDirectory(packageRoot, "services/mdm", paths);
        return paths.stream().sorted().toList();
    }

    private void collectRootComposeFiles(Path root, Set<String> paths) {
        if (!Files.isDirectory(root)) {
            return;
        }
        try (Stream<Path> stream = Files.list(root)) {
            stream
                .filter(Files::isRegularFile)
                .map(path -> path.getFileName().toString())
                .filter(this::isComposeFile)
                .forEach(paths::add);
        } catch (IOException ignored) {
            // Missing or unreadable optional roots are reported when a concrete file is reviewed.
        }
    }

    private void collectDirectory(Path root, String relativeDir, Set<String> paths) {
        Path dir = root.resolve(relativeDir).normalize();
        if (!Files.isDirectory(dir) || !dir.startsWith(root)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(dir)) {
            stream
                .filter(Files::isRegularFile)
                .map(path -> root.relativize(path).toString().replace('\\', '/'))
                .forEach(paths::add);
        } catch (IOException ignored) {
            // Optional protected directories may not exist at every site.
        }
    }

    private ConfigFileReview reviewFile(Path targetRoot, Path packageRoot, String relativePath) {
        Path safePath = safeRelativePath(relativePath);
        Path local = targetRoot.resolve(safePath).normalize();
        Path packaged = packageRoot.resolve(safePath).normalize();
        ensureUnderRoot(targetRoot, local);
        ensureUnderRoot(packageRoot, packaged);

        boolean localExists = Files.isRegularFile(local);
        boolean packageExists = Files.isRegularFile(packaged);
        ConfigFileStatus status = status(local, packaged, localExists, packageExists);
        ConfigCategory category = category(relativePath);
        ConfigRisk risk = risk(category, status);
        FilePreview localPreview = preview(local, localExists);
        FilePreview packagePreview = preview(packaged, packageExists);
        boolean omitted = localPreview.omitted() || packagePreview.omitted();
        return new ConfigFileReview(
            relativePath,
            category,
            status,
            risk,
            localExists,
            packageExists,
            localPreview.size(),
            packagePreview.size(),
            localPreview.lines(),
            packagePreview.lines(),
            omitted,
            message(category, status),
            allowedActions(category, status, localExists, packageExists)
        );
    }

    private ConfigApplyResult mergeEnvAddKeys(Path targetRoot, Path packageRoot, Path safePath) {
        if (!safePath.toString().equals(".env")) {
            throw new IllegalArgumentException("MERGE_ENV_ADD_KEYS is only allowed for .env");
        }
        Path local = targetRoot.resolve(safePath).normalize();
        Path packaged = packageRoot.resolve(safePath).normalize();
        if (!Files.isRegularFile(packaged)) {
            throw new IllegalArgumentException("package .env is missing");
        }
        try {
            List<String> localLines = Files.exists(local) ? Files.readAllLines(local, StandardCharsets.UTF_8) : new ArrayList<>();
            List<String> packageLines = Files.readAllLines(packaged, StandardCharsets.UTF_8);
            Set<String> existingKeys = envKeys(localLines);
            List<String> missingLines = packageLines
                .stream()
                .filter(line -> ENV_KEY.matcher(line).matches())
                .filter(line -> !existingKeys.contains(line.substring(0, line.indexOf('=')).trim()))
                .toList();
            if (missingLines.isEmpty()) {
                return new ConfigApplyResult(false, ".env", ConfigApplyAction.MERGE_ENV_ADD_KEYS, "no missing .env keys", "", "");
            }
            String backup = backupIfExists(local, safePath);
            Files.createDirectories(local.getParent());
            List<String> next = new ArrayList<>(localLines);
            if (!next.isEmpty() && !next.getLast().isBlank()) {
                next.add("");
            }
            next.add("# Added by dts-opmanager from upgrade package at " + Instant.now());
            next.addAll(missingLines);
            Files.write(local, next, StandardCharsets.UTF_8);
            return new ConfigApplyResult(true, ".env", ConfigApplyAction.MERGE_ENV_ADD_KEYS, "added missing .env keys", backup, local.toString());
        } catch (IOException e) {
            throw new IllegalStateException("failed to merge .env keys", e);
        }
    }

    private ConfigApplyResult writePackageCopy(Path targetRoot, Path packageRoot, Path safePath) {
        Path packaged = packageRoot.resolve(safePath).normalize();
        if (!Files.isRegularFile(packaged)) {
            throw new IllegalArgumentException("package file is missing");
        }
        Path local = targetRoot.resolve(safePath).normalize();
        Path copy = local.resolveSibling(local.getFileName().toString() + ".opmanager-" + FILE_TIME.format(Instant.now()));
        try {
            Files.createDirectories(copy.getParent());
            Files.copy(packaged, copy, StandardCopyOption.REPLACE_EXISTING);
            return new ConfigApplyResult(true, safePath.toString().replace('\\', '/'), ConfigApplyAction.WRITE_PACKAGE_COPY, "wrote package copy for review", "", copy.toString());
        } catch (IOException e) {
            throw new IllegalStateException("failed to write package copy", e);
        }
    }

    private ConfigApplyResult usePackageFile(Path targetRoot, Path packageRoot, Path safePath) {
        Path packaged = packageRoot.resolve(safePath).normalize();
        if (!Files.isRegularFile(packaged)) {
            throw new IllegalArgumentException("package file is missing");
        }
        Path local = targetRoot.resolve(safePath).normalize();
        try {
            String backup = backupIfExists(local, safePath);
            Files.createDirectories(local.getParent());
            Files.copy(packaged, local, StandardCopyOption.REPLACE_EXISTING);
            return new ConfigApplyResult(true, safePath.toString().replace('\\', '/'), ConfigApplyAction.USE_PACKAGE, "applied package file", backup, local.toString());
        } catch (IOException e) {
            throw new IllegalStateException("failed to apply package file", e);
        }
    }

    private String backupIfExists(Path local, Path safePath) throws IOException {
        if (!Files.exists(local)) {
            return "";
        }
        Path backup = properties
            .getDataDir()
            .resolve("config-backups")
            .resolve(FILE_TIME.format(Instant.now()))
            .resolve(safePath)
            .toAbsolutePath()
            .normalize();
        Files.createDirectories(backup.getParent());
        Files.copy(local, backup, StandardCopyOption.REPLACE_EXISTING);
        return backup.toString();
    }

    private Set<String> envKeys(List<String> lines) {
        Set<String> keys = new HashSet<>();
        for (String line : lines) {
            if (ENV_KEY.matcher(line).matches()) {
                keys.add(line.substring(0, line.indexOf('=')).trim());
            }
        }
        return keys;
    }

    private ConfigFileStatus status(Path local, Path packaged, boolean localExists, boolean packageExists) {
        if (localExists && packageExists) {
            try {
                return Files.mismatch(local, packaged) == -1 ? ConfigFileStatus.UNCHANGED : ConfigFileStatus.MODIFIED;
            } catch (IOException e) {
                return ConfigFileStatus.MODIFIED;
            }
        }
        if (localExists) {
            return ConfigFileStatus.LOCAL_ONLY;
        }
        if (packageExists) {
            return ConfigFileStatus.PACKAGE_ONLY;
        }
        return ConfigFileStatus.MISSING;
    }

    private ConfigRisk risk(ConfigCategory category, ConfigFileStatus status) {
        if (status == ConfigFileStatus.UNCHANGED || status == ConfigFileStatus.MISSING || status == ConfigFileStatus.LOCAL_ONLY) {
            return ConfigRisk.LOW;
        }
        return category == ConfigCategory.OTHER ? ConfigRisk.MEDIUM : ConfigRisk.HIGH;
    }

    private String message(ConfigCategory category, ConfigFileStatus status) {
        if (status == ConfigFileStatus.UNCHANGED) {
            return "same as package";
        }
        if (status == ConfigFileStatus.LOCAL_ONLY) {
            return "local-only protected file; keep it";
        }
        if (category == ConfigCategory.ENV) {
            return "site values are protected; only missing keys can be appended automatically";
        }
        if (category == ConfigCategory.COMPOSE) {
            return "compose files may contain site ports, volumes, networks, and domains";
        }
        if (category == ConfigCategory.MDM) {
            return "MDM files are treated as site data/config and are not overwritten";
        }
        return "review before applying";
    }

    private List<ConfigApplyAction> allowedActions(ConfigCategory category, ConfigFileStatus status, boolean localExists, boolean packageExists) {
        if (!packageExists) {
            return List.of(ConfigApplyAction.KEEP_LOCAL);
        }
        if (status == ConfigFileStatus.UNCHANGED || status == ConfigFileStatus.LOCAL_ONLY || status == ConfigFileStatus.MISSING) {
            return List.of(ConfigApplyAction.KEEP_LOCAL);
        }
        if (category == ConfigCategory.ENV) {
            return List.of(ConfigApplyAction.KEEP_LOCAL, ConfigApplyAction.MERGE_ENV_ADD_KEYS, ConfigApplyAction.WRITE_PACKAGE_COPY);
        }
        if (!localExists && category != ConfigCategory.MDM) {
            return List.of(ConfigApplyAction.WRITE_PACKAGE_COPY, ConfigApplyAction.USE_PACKAGE);
        }
        return List.of(ConfigApplyAction.KEEP_LOCAL, ConfigApplyAction.WRITE_PACKAGE_COPY);
    }

    private ConfigCategory category(String relativePath) {
        if (relativePath.equals(".env")) {
            return ConfigCategory.ENV;
        }
        if (isComposeFile(Path.of(relativePath).getFileName().toString())) {
            return ConfigCategory.COMPOSE;
        }
        if (relativePath.startsWith("config/mdm/") || relativePath.startsWith("data/mdm/") || relativePath.startsWith("services/mdm/")) {
            return ConfigCategory.MDM;
        }
        return ConfigCategory.OTHER;
    }

    private boolean isComposeFile(String fileName) {
        return fileName.matches("docker-compose.*\\.ya?ml") || fileName.matches("compose.*\\.ya?ml");
    }

    private FilePreview preview(Path path, boolean exists) {
        if (!exists) {
            return new FilePreview(0, List.of(), false);
        }
        try {
            long size = Files.size(path);
            if (size > PREVIEW_LIMIT_BYTES || isBinary(path)) {
                return new FilePreview(size, List.of(), true);
            }
            return new FilePreview(size, Files.readAllLines(path, StandardCharsets.UTF_8), false);
        } catch (IOException e) {
            return new FilePreview(0, List.of(), true);
        }
    }

    private boolean isBinary(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        int limit = Math.min(bytes.length, 4096);
        for (int i = 0; i < limit; i++) {
            if (bytes[i] == 0) {
                return true;
            }
        }
        return false;
    }

    private Path safeRelativePath(String relativePath) {
        Path path = Path.of(Optional.ofNullable(relativePath).orElse("")).normalize();
        if (path.isAbsolute() || path.startsWith("..") || path.toString().isBlank()) {
            throw new IllegalArgumentException("unsafe config path");
        }
        String normalized = path.toString().replace('\\', '/');
        if (!normalized.equals(".env") && !isComposeFile(path.getFileName().toString()) && !normalized.startsWith("config/mdm/") && !normalized.startsWith("data/mdm/") && !normalized.startsWith("services/mdm/")) {
            throw new IllegalArgumentException("config path is not protected by opmanager");
        }
        return path;
    }

    private void ensureUnderRoot(Path root, Path path) {
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("unsafe config path");
        }
    }

    private record FilePreview(long size, List<String> lines, boolean omitted) {}
}
