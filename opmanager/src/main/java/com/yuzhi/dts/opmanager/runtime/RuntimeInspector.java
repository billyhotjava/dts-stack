package com.yuzhi.dts.opmanager.runtime;

import com.yuzhi.dts.opmanager.config.OpManagerProperties;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RuntimeInspector {

    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(5);

    private final OpManagerProperties properties;
    private final CommandRunner commandRunner;

    public RuntimeInspector(OpManagerProperties properties, CommandRunner commandRunner) {
        this.properties = properties;
        this.commandRunner = commandRunner;
    }

    public RuntimeStatus inspect() {
        RuntimeProbe docker = properties.isDockerEnabled()
            ? probe(commandRunner.run(List.of("docker", "version", "--format", "{{.Server.Version}}"), COMMAND_TIMEOUT))
            : RuntimeProbe.unavailable("docker disabled by OPMANAGER_DOCKER_ENABLED=false");
        RuntimeProbe compose = properties.isDockerEnabled() ? detectCompose() : RuntimeProbe.unavailable("docker disabled by OPMANAGER_DOCKER_ENABLED=false");

        return new RuntimeStatus(
            System.getProperty("os.name", "unknown"),
            System.getProperty("os.arch", "unknown"),
            System.getProperty("java.version", "unknown"),
            properties.getDataDir().toString(),
            properties.getTargetStackDir().toString(),
            properties.isDockerEnabled(),
            docker.available(),
            docker.version(),
            docker.message(),
            compose.available(),
            compose.version(),
            compose.message(),
            properties.getPortainerUrl()
        );
    }

    private RuntimeProbe detectCompose() {
        CommandResult plugin = commandRunner.run(List.of("docker", "compose", "version", "--short"), COMMAND_TIMEOUT);
        if (plugin.success()) {
            return probe(plugin);
        }
        CommandResult legacy = commandRunner.run(List.of("docker-compose", "version", "--short"), COMMAND_TIMEOUT);
        if (legacy.success()) {
            return probe(legacy);
        }
        return RuntimeProbe.unavailable("docker compose plugin: " + plugin.summary() + "; docker-compose: " + legacy.summary());
    }

    private RuntimeProbe probe(CommandResult result) {
        if (result.success()) {
            String version = result.trimmedStdout();
            return new RuntimeProbe(true, version, version);
        }
        return RuntimeProbe.unavailable(result.summary());
    }

    private record RuntimeProbe(boolean available, String version, String message) {
        static RuntimeProbe unavailable(String message) {
            return new RuntimeProbe(false, "", message == null || message.isBlank() ? "unavailable" : message);
        }
    }
}
