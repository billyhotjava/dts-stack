package com.yuzhi.dts.opmanager.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.opmanager.config.OpManagerProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class RuntimeInspectorTest {

    @Test
    void reportsDockerAndComposeAvailability() {
        CommandRunner runner = (command, timeout) -> {
            if (command.equals(List.of("docker", "version", "--format", "{{.Server.Version}}"))) {
                return new CommandResult(command, 0, "26.1.0\n", "", false, Duration.ofMillis(12));
            }
            if (command.equals(List.of("docker", "compose", "version", "--short"))) {
                return new CommandResult(command, 0, "2.27.0\n", "", false, Duration.ofMillis(10));
            }
            return new CommandResult(command, 127, "", "not found", false, Duration.ofMillis(2));
        };

        RuntimeInspector inspector = new RuntimeInspector(new OpManagerProperties(), runner);

        RuntimeStatus status = inspector.inspect();

        assertThat(status.dockerAvailable()).isTrue();
        assertThat(status.dockerVersion()).isEqualTo("26.1.0");
        assertThat(status.composeAvailable()).isTrue();
        assertThat(status.composeVersion()).isEqualTo("2.27.0");
    }

    @Test
    void fallsBackToLegacyDockerComposeBinary() {
        CommandRunner runner = (command, timeout) -> {
            if (command.equals(List.of("docker-compose", "version", "--short"))) {
                return new CommandResult(command, 0, "1.29.2\n", "", false, Duration.ofMillis(10));
            }
            return new CommandResult(command, 1, "", "missing", false, Duration.ofMillis(2));
        };

        RuntimeInspector inspector = new RuntimeInspector(new OpManagerProperties(), runner);

        RuntimeStatus status = inspector.inspect();

        assertThat(status.composeAvailable()).isTrue();
        assertThat(status.composeVersion()).isEqualTo("1.29.2");
    }
}
