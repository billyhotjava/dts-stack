package com.yuzhi.dts.opmanager.runtime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.opmanager.config.OpManagerProperties;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class DockerService {

    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(10);

    private final OpManagerProperties properties;
    private final CommandRunner commandRunner;
    private final ObjectMapper objectMapper;

    public DockerService(OpManagerProperties properties, CommandRunner commandRunner, ObjectMapper objectMapper) {
        this.properties = properties;
        this.commandRunner = commandRunner;
        this.objectMapper = objectMapper;
    }

    public DockerContainersResponse listContainers() {
        if (!properties.isDockerEnabled()) {
            return new DockerContainersResponse(false, "docker access is disabled", List.of());
        }
        CommandResult result = commandRunner.run(List.of("docker", "ps", "--all", "--format", "{{json .}}"), COMMAND_TIMEOUT);
        if (!result.success()) {
            return new DockerContainersResponse(false, result.summary(), List.of());
        }
        return new DockerContainersResponse(true, "", parseContainers(result.stdout()));
    }

    private List<DockerContainer> parseContainers(String stdout) {
        if (stdout == null || stdout.isBlank()) {
            return List.of();
        }
        List<DockerContainer> containers = new ArrayList<>();
        for (String line : stdout.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            try {
                Map<String, String> row = objectMapper.readValue(line, new TypeReference<>() {});
                containers.add(
                    new DockerContainer(row.getOrDefault("ID", ""), row.getOrDefault("Names", ""), row.getOrDefault("Image", ""), row.getOrDefault("State", ""), row.getOrDefault("Status", ""))
                );
            } catch (Exception ignored) {
                // One malformed docker output line should not hide the rest of the runtime view.
            }
        }
        return List.copyOf(containers);
    }
}
