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
        CommandResult docker = properties.isDockerEnabled()
            ? commandRunner.run(List.of("docker", "version", "--format", "{{.Server.Version}}"), COMMAND_TIMEOUT)
            : unavailable("docker disabled");
        CommandResult compose = properties.isDockerEnabled() ? detectCompose() : unavailable("docker disabled");

        return new RuntimeStatus(
            System.getProperty("os.name", "unknown"),
            System.getProperty("os.arch", "unknown"),
            System.getProperty("java.version", "unknown"),
            properties.getDataDir().toString(),
            properties.getTargetStackDir().toString(),
            properties.isDockerEnabled(),
            docker.success(),
            docker.success() ? docker.trimmedStdout() : docker.summary(),
            compose.success(),
            compose.success() ? compose.trimmedStdout() : compose.summary(),
            properties.getPortainerUrl()
        );
    }

    private CommandResult detectCompose() {
        CommandResult plugin = commandRunner.run(List.of("docker", "compose", "version", "--short"), COMMAND_TIMEOUT);
        if (plugin.success()) {
            return plugin;
        }
        return commandRunner.run(List.of("docker-compose", "version", "--short"), COMMAND_TIMEOUT);
    }

    private CommandResult unavailable(String message) {
        return new CommandResult(List.of(), 1, "", message, false, Duration.ZERO);
    }
}
