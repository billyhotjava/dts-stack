package com.yuzhi.dts.opmanager.workspace;

import com.yuzhi.dts.opmanager.config.OpManagerProperties;
import com.yuzhi.dts.opmanager.runtime.CommandResult;
import com.yuzhi.dts.opmanager.runtime.CommandRunner;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
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

    private final OpManagerProperties properties;
    private final CommandRunner commandRunner;

    public WorkspaceService(OpManagerProperties properties, CommandRunner commandRunner) {
        this.properties = properties;
        this.commandRunner = commandRunner;
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
        Path envFile = properties.getTargetStackDir().resolve(".env").toAbsolutePath().normalize();
        if (Files.isRegularFile(envFile)) {
            command.addAll(List.of("--env-file", envFile.toString()));
        }
        command.addAll(List.of("-f", composeFile.toString(), "up", "-d", "--force-recreate"));
        CommandResult recreate = commandRunner.run(command, COMPOSE_TIMEOUT);
        outputs.add(new WorkspaceCommandOutput(recreate.command(), recreate.success(), recreate.summary()));
        return new WorkspaceOperationResult(recreate.success(), recreate.success() ? "containers recreated" : "container recreate failed", List.copyOf(outputs));
    }

    private Path packageRoot() {
        List<Path> roots = properties.getPackageRoots();
        Path root = roots.isEmpty() ? properties.getDataDir().resolve("packages") : roots.getFirst();
        return root.toAbsolutePath().normalize();
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
        Path target = properties.getTargetStackDir().toAbsolutePath().normalize();
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
}
