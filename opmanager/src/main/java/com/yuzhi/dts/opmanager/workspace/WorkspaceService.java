package com.yuzhi.dts.opmanager.workspace;

import com.yuzhi.dts.opmanager.config.OpManagerProperties;
import com.yuzhi.dts.opmanager.runtime.CommandResult;
import com.yuzhi.dts.opmanager.runtime.CommandRunner;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

@Service
public class WorkspaceService {

    private static final Duration DOCKER_LOAD_TIMEOUT = Duration.ofMinutes(30);
    private static final Duration COMPOSE_TIMEOUT = Duration.ofMinutes(15);
    private static final Duration DETECT_TIMEOUT = Duration.ofSeconds(5);
    private static final String PLATFORM_MIN_API_VERSION = "1.41";
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);

    private final OpManagerProperties properties;
    private final CommandRunner commandRunner;

    public WorkspaceService(OpManagerProperties properties, CommandRunner commandRunner) {
        this.properties = properties;
        this.commandRunner = commandRunner;
        restorePersistedSettings();
    }

    public WorkspaceStatus status() {
        Path root = packageRoot();
        Path imagesDir = imagesDir();
        Path stackDir = stackDir();
        Path miscDir = miscDir();
        return new WorkspaceStatus(
            root.toString(),
            imagesDir.toString(),
            stackDir.toString(),
            miscDir.toString(),
            Files.isDirectory(root),
            Files.isDirectory(imagesDir),
            Files.isDirectory(stackDir),
            Files.isDirectory(miscDir),
            imageTars()
        );
    }

    public synchronized WorkspaceStatus updatePackageRoot(String packageRoot) {
        if (packageRoot == null || packageRoot.isBlank()) {
            throw new IllegalArgumentException("workspace package root is required");
        }
        Path root = Path.of(packageRoot.trim()).toAbsolutePath().normalize();
        try {
            Path stateFile = packageRootStateFile();
            Files.createDirectories(stateFile.getParent());
            Files.writeString(stateFile, root.toString(), StandardCharsets.UTF_8);
            properties.setPackageRoots(List.of(root));
            return status();
        } catch (IOException e) {
            throw new IllegalStateException("failed to save workspace package root", e);
        }
    }

    public synchronized WorkspaceStatus updateTargetStackDir(String targetStackDir) {
        if (targetStackDir == null || targetStackDir.isBlank()) {
            throw new IllegalArgumentException("target dts-stack directory is required");
        }
        Path root = Path.of(targetStackDir.trim()).toAbsolutePath().normalize();
        try {
            Path stateFile = targetStackStateFile();
            Files.createDirectories(stateFile.getParent());
            Files.writeString(stateFile, root.toString(), StandardCharsets.UTF_8);
            properties.setTargetStackDir(root);
            return status();
        } catch (IOException e) {
            throw new IllegalStateException("failed to save target dts-stack directory", e);
        }
    }

    public WorkspaceOperationResult loadImages() {
        if (!properties.isDockerEnabled()) {
            return new WorkspaceOperationResult(false, "docker access is disabled", List.of());
        }
        List<WorkspaceImage> images = imageTars();
        if (images.isEmpty()) {
            return new WorkspaceOperationResult(false, "no image tar files found", List.of());
        }
        List<WorkspaceCommandOutput> outputs = new ArrayList<>();
        for (WorkspaceImage image : images) {
            CommandResult result = commandRunner.run(List.of("docker", "load", "-i", image.path()), DOCKER_LOAD_TIMEOUT);
            outputs.add(new WorkspaceCommandOutput(result.command(), result.success(), result.summary()));
            if (!result.success()) {
                return new WorkspaceOperationResult(false, "docker load failed: " + image.fileName(), List.copyOf(outputs));
            }
        }
        return new WorkspaceOperationResult(true, "images loaded: " + images.size(), List.copyOf(outputs));
    }

    public WorkspaceOperationResult recreateContainers() {
        if (!properties.isDockerEnabled()) {
            return new WorkspaceOperationResult(false, "docker access is disabled", List.of());
        }
        Path composeFile = composeFile();
        if (composeFile == null) {
            return new WorkspaceOperationResult(false, "target compose file is missing", List.of());
        }
        CommandResult compose = detectCompose();
        List<WorkspaceCommandOutput> outputs = new ArrayList<>();
        outputs.add(new WorkspaceCommandOutput(compose.command(), compose.success(), compose.summary()));
        if (!compose.success()) {
            return new WorkspaceOperationResult(false, "docker compose is unavailable", List.copyOf(outputs));
        }

        List<String> command = new ArrayList<>();
        if (compose.command().equals(List.of("docker", "compose", "version", "--short"))) {
            command.addAll(List.of("docker", "compose"));
        } else {
            command.add("docker-compose");
        }
        Path targetStackDir = targetStackDir();
        Path envFile = targetStackDir.resolve(".env").toAbsolutePath().normalize();
        if (Files.isRegularFile(envFile)) {
            command.addAll(List.of("--env-file", envFile.toString()));
        }
        Path effectiveComposeFile = composeFile;
        if (composeContainsPlatform(composeFile)) {
            CommandResult dockerApi = commandRunner.run(List.of("docker", "version", "--format", "{{.Server.APIVersion}}"), DETECT_TIMEOUT);
            outputs.add(new WorkspaceCommandOutput(dockerApi.command(), dockerApi.success(), dockerApi.summary()));
            if (dockerApi.success() && isApiVersionLessThan(dockerApi.trimmedStdout(), PLATFORM_MIN_API_VERSION)) {
                effectiveComposeFile = platformCompatibleComposeFile(composeFile);
                outputs.add(
                    new WorkspaceCommandOutput(
                        List.of("opmanager", "compose", "compat", composeFile.toString()),
                        true,
                        "generated Docker API " + dockerApi.trimmedStdout() + " compatible compose without platform: " + effectiveComposeFile
                    )
                );
                command.addAll(List.of("--project-directory", targetStackDir.toString()));
            }
        }
        command.addAll(List.of("-f", effectiveComposeFile.toString(), "up", "-d", "--force-recreate"));
        CommandResult recreate = commandRunner.run(command, COMPOSE_TIMEOUT);
        outputs.add(new WorkspaceCommandOutput(recreate.command(), recreate.success(), recreate.summary()));
        return new WorkspaceOperationResult(recreate.success(), recreate.success() ? "containers recreated" : "container recreate failed", List.copyOf(outputs));
    }

    private Path packageRoot() {
        Path persisted = persistedPackageRoot();
        if (persisted != null) {
            return persisted;
        }
        List<Path> roots = properties.getPackageRoots();
        Path root = roots.isEmpty() ? properties.getDataDir().resolve("packages") : roots.getFirst();
        return root.toAbsolutePath().normalize();
    }

    private Path targetStackDir() {
        Path persisted = persistedTargetStackDir();
        if (persisted != null) {
            properties.setTargetStackDir(persisted);
            return persisted;
        }
        return properties.getTargetStackDir().toAbsolutePath().normalize();
    }

    private Path persistedPackageRoot() {
        Path stateFile = packageRootStateFile();
        if (!Files.isRegularFile(stateFile)) {
            return null;
        }
        try {
            String value = Files.readString(stateFile, StandardCharsets.UTF_8).trim();
            return value.isBlank() ? null : Path.of(value).toAbsolutePath().normalize();
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    private Path persistedTargetStackDir() {
        Path stateFile = targetStackStateFile();
        if (!Files.isRegularFile(stateFile)) {
            return null;
        }
        try {
            String value = Files.readString(stateFile, StandardCharsets.UTF_8).trim();
            return value.isBlank() ? null : Path.of(value).toAbsolutePath().normalize();
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    private void restorePersistedSettings() {
        Path packageRoot = persistedPackageRoot();
        if (packageRoot != null) {
            properties.setPackageRoots(List.of(packageRoot));
        }
        Path targetStackDir = persistedTargetStackDir();
        if (targetStackDir != null) {
            properties.setTargetStackDir(targetStackDir);
        }
    }

    private Path packageRootStateFile() {
        return properties.getDataDir().resolve("state/workspace-package-root.txt").toAbsolutePath().normalize();
    }

    private Path targetStackStateFile() {
        return properties.getDataDir().resolve("state/target-stack-dir.txt").toAbsolutePath().normalize();
    }

    private Path imagesDir() {
        return packageRoot().resolve("images").normalize();
    }

    private Path stackDir() {
        return packageRoot().resolve("dts-stack").normalize();
    }

    private Path miscDir() {
        return packageRoot().resolve("misc").normalize();
    }

    private List<WorkspaceImage> imageTars() {
        Path imagesDir = imagesDir();
        if (!Files.isDirectory(imagesDir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(imagesDir)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".tar"))
                .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                .map(this::toImage)
                .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private WorkspaceImage toImage(Path path) {
        try {
            return new WorkspaceImage(path.getFileName().toString(), path.toString(), Files.size(path));
        } catch (IOException e) {
            return new WorkspaceImage(path.getFileName().toString(), path.toString(), 0);
        }
    }

    private Path composeFile() {
        Path target = targetStackDir();
        for (String candidate : List.of("docker-compose-app.yml", "docker-compose.legacy.yml", "docker-compose.yml")) {
            Path compose = target.resolve(candidate).normalize();
            if (Files.isRegularFile(compose) && compose.startsWith(target)) {
                return compose;
            }
        }
        return null;
    }

    private CommandResult detectCompose() {
        CommandResult plugin = commandRunner.run(List.of("docker", "compose", "version", "--short"), DETECT_TIMEOUT);
        if (plugin.success()) {
            return plugin;
        }
        return commandRunner.run(List.of("docker-compose", "version", "--short"), DETECT_TIMEOUT);
    }

    private boolean composeContainsPlatform(Path composeFile) {
        try (Stream<String> lines = Files.lines(composeFile, StandardCharsets.UTF_8)) {
            return lines.anyMatch(line -> line.matches("^\\s*platform\\s*:.*$"));
        } catch (IOException e) {
            return false;
        }
    }

    private Path platformCompatibleComposeFile(Path composeFile) {
        try {
            Path outputDir = properties.getDataDir().resolve("compose-compat").toAbsolutePath().normalize();
            Files.createDirectories(outputDir);
            Path output = outputDir.resolve(composeFile.getFileName().toString() + ".no-platform-" + FILE_TIME.format(Instant.now()) + ".yml");
            List<String> lines = Files.readAllLines(composeFile, StandardCharsets.UTF_8);
            List<String> compatible = lines.stream().filter(line -> !line.matches("^\\s*platform\\s*:.*$")).toList();
            Files.write(output, compatible, StandardCharsets.UTF_8);
            return output;
        } catch (IOException e) {
            throw new IllegalStateException("failed to generate Docker API compatible compose file", e);
        }
    }

    private boolean isApiVersionLessThan(String actual, String required) {
        int[] actualParts = parseVersion(actual);
        int[] requiredParts = parseVersion(required);
        int max = Math.max(actualParts.length, requiredParts.length);
        for (int index = 0; index < max; index += 1) {
            int actualValue = index < actualParts.length ? actualParts[index] : 0;
            int requiredValue = index < requiredParts.length ? requiredParts[index] : 0;
            if (actualValue != requiredValue) {
                return actualValue < requiredValue;
            }
        }
        return false;
    }

    private int[] parseVersion(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            return new int[] { 0 };
        }
        String[] parts = normalized.split("\\.");
        int[] parsed = new int[parts.length];
        for (int index = 0; index < parts.length; index += 1) {
            try {
                parsed[index] = Integer.parseInt(parts[index].replaceAll("[^0-9].*$", ""));
            } catch (NumberFormatException e) {
                parsed[index] = 0;
            }
        }
        return parsed;
    }
}
