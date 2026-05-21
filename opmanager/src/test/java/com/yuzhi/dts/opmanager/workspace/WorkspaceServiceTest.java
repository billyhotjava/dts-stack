package com.yuzhi.dts.opmanager.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.opmanager.config.OpManagerProperties;
import com.yuzhi.dts.opmanager.runtime.CommandResult;
import com.yuzhi.dts.opmanager.runtime.CommandRunner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void layoutListsFixedWorkspaceDirectoriesAndImageTars() throws Exception {
        TestContext context = newContext();
        Files.writeString(context.packageRoot().resolve("images/dts-admin.tar"), "admin");
        Files.writeString(context.packageRoot().resolve("images/readme.txt"), "ignored");

        WorkspaceStatus status = context.service().status();

        assertThat(status.packageRoot()).isEqualTo(context.packageRoot().toString());
        assertThat(status.imagesDirExists()).isTrue();
        assertThat(status.stackDirExists()).isTrue();
        assertThat(status.miscDirExists()).isTrue();
        assertThat(status.images()).extracting(WorkspaceImage::fileName).containsExactly("dts-admin.tar");
    }

    @Test
    void updatesWorkspaceRootAndPersistsItAcrossServiceInstances() throws Exception {
        TestContext context = newContext();
        Path alternate = tempDir.resolve("alternate-packages");
        Files.createDirectories(alternate.resolve("images"));
        Files.createDirectories(alternate.resolve("dts-stack"));
        Files.createDirectories(alternate.resolve("misc"));
        Files.writeString(alternate.resolve("images/dts-platform.tar"), "platform");

        WorkspaceStatus updated = context.service().updatePackageRoot(alternate.toString());

        assertThat(updated.packageRoot()).isEqualTo(alternate.toString());
        assertThat(updated.images()).extracting(WorkspaceImage::fileName).containsExactly("dts-platform.tar");

        WorkspaceService restarted = new WorkspaceService(context.properties(), context.runner());

        assertThat(restarted.status().packageRoot()).isEqualTo(alternate.toString());
    }

    @Test
    void updatesTargetStackDirAndPersistsItAcrossServiceInstances() throws Exception {
        TestContext context = newContext();
        Path alternate = tempDir.resolve("alternate-target-stack");
        Files.createDirectories(alternate);
        Files.writeString(alternate.resolve("docker-compose.legacy.yml"), "services: {}\n");

        context.service().updateTargetStackDir(alternate.toString());

        WorkspaceService restarted = new WorkspaceService(context.properties(), context.runner());
        restarted.recreateContainers();

        assertThat(context.properties().getTargetStackDir()).isEqualTo(alternate);
        assertThat(context.runner().commands()).contains(
            List.of("docker", "compose", "-f", alternate.resolve("docker-compose.legacy.yml").toString(), "up", "-d", "--force-recreate")
        );
    }

    @Test
    void loadImagesRunsDockerLoadForEachTar() throws Exception {
        TestContext context = newContext();
        Files.writeString(context.packageRoot().resolve("images/dts-admin.tar"), "admin");
        Files.writeString(context.packageRoot().resolve("images/dts-platform.tar"), "platform");

        WorkspaceOperationResult result = context.service().loadImages();

        assertThat(result.success()).isTrue();
        assertThat(context.runner().commands()).containsExactly(
            List.of("docker", "load", "-i", context.packageRoot().resolve("images/dts-admin.tar").toString()),
            List.of("docker", "load", "-i", context.packageRoot().resolve("images/dts-platform.tar").toString())
        );
    }

    @Test
    void recreateContainersUsesTargetComposeAndEnvFile() throws Exception {
        TestContext context = newContext();
        Files.writeString(context.targetStack().resolve(".env"), "BASE_DOMAIN=site.local\n");
        Files.writeString(context.targetStack().resolve("docker-compose-app.yml"), "services: {}\n");

        WorkspaceOperationResult result = context.service().recreateContainers();

        assertThat(result.success()).isTrue();
        assertThat(context.runner().commands()).containsExactly(
            List.of("docker", "compose", "version", "--short"),
            List.of("docker", "compose", "--env-file", context.targetStack().resolve(".env").toString(), "-f", context.targetStack().resolve("docker-compose-app.yml").toString(), "up", "-d", "--force-recreate")
        );
    }

    private TestContext newContext() throws Exception {
        Path packageRoot = tempDir.resolve("packages");
        Path targetStack = tempDir.resolve("target-stack");
        Files.createDirectories(packageRoot.resolve("images"));
        Files.createDirectories(packageRoot.resolve("dts-stack"));
        Files.createDirectories(packageRoot.resolve("misc"));
        Files.createDirectories(targetStack);
        OpManagerProperties properties = new OpManagerProperties();
        properties.setDataDir(tempDir.resolve("data"));
        properties.setPackageRoots(List.of(packageRoot));
        properties.setTargetStackDir(targetStack);
        properties.setDockerEnabled(true);
        RecordingRunner runner = new RecordingRunner();
        return new TestContext(packageRoot, targetStack, properties, runner, new WorkspaceService(properties, runner));
    }

    private record TestContext(Path packageRoot, Path targetStack, OpManagerProperties properties, RecordingRunner runner, WorkspaceService service) {}

    private static final class RecordingRunner implements CommandRunner {
        private final List<List<String>> commands = new ArrayList<>();

        @Override
        public CommandResult run(List<String> command, Duration timeout) {
            commands.add(command);
            return new CommandResult(command, 0, "ok", "", false, Duration.ofMillis(1));
        }

        List<List<String>> commands() {
            return commands;
        }
    }
}
