package com.yuzhi.dts.opmanager.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.opmanager.config.OpManagerProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class FileJobStore {

    private static final DateTimeFormatter ID_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").withZone(ZoneOffset.UTC);

    private final OpManagerProperties properties;
    private final ObjectMapper objectMapper;

    public FileJobStore(OpManagerProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public UpgradeJob createPlanJob(String packageId, String version, String message) {
        return createPlanJob(packageId, packageId, version, message);
    }

    public UpgradeJob createPlanJob(String packageRegistrationId, String packageId, String version, String message) {
        String now = Instant.now().toString();
        UpgradeJob job = new UpgradeJob("plan-" + ID_TIME.format(Instant.now()), packageRegistrationId, packageId, version, UpgradeJobState.PLANNED, now, now);
        persist(job);
        appendEvent(new JobEvent(job.id(), now, UpgradeJobState.PLANNED, message));
        return job;
    }

    public Optional<UpgradeJob> find(String id) {
        Path file = jobFile(id);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(file.toFile(), UpgradeJob.class));
        } catch (IOException e) {
            throw new IllegalStateException("failed to read job " + id, e);
        }
    }

    public List<UpgradeJob> list() {
        Path jobsDir = properties.jobsDir();
        if (!Files.isDirectory(jobsDir)) {
            return List.of();
        }
        try (var stream = Files.list(jobsDir)) {
            return stream
                .filter(Files::isDirectory)
                .map(path -> find(path.getFileName().toString()))
                .flatMap(Optional::stream)
                .sorted((left, right) -> right.createdAt().compareTo(left.createdAt()))
                .toList();
        } catch (IOException e) {
            throw new IllegalStateException("failed to list jobs", e);
        }
    }

    public List<JobEvent> events(String jobId) {
        Path file = eventsFile(jobId);
        if (!Files.exists(file)) {
            return List.of();
        }
        try {
            return Files.readAllLines(file)
                .stream()
                .filter(line -> !line.isBlank())
                .map(this::readEvent)
                .toList();
        } catch (IOException e) {
            throw new IllegalStateException("failed to read events for job " + jobId, e);
        }
    }

    private void persist(UpgradeJob job) {
        try {
            Files.createDirectories(jobDir(job.id()));
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(jobFile(job.id()).toFile(), job);
        } catch (IOException e) {
            throw new IllegalStateException("failed to persist job " + job.id(), e);
        }
    }

    private void appendEvent(JobEvent event) {
        try {
            Files.createDirectories(jobDir(event.jobId()));
            String line = objectMapper.writeValueAsString(event) + System.lineSeparator();
            Files.writeString(eventsFile(event.jobId()), line, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new IllegalStateException("failed to append event for job " + event.jobId(), e);
        }
    }

    private JobEvent readEvent(String line) {
        try {
            return objectMapper.readValue(line, JobEvent.class);
        } catch (IOException e) {
            throw new IllegalStateException("failed to parse job event", e);
        }
    }

    private Path jobDir(String jobId) {
        return properties.jobsDir().resolve(sanitize(jobId)).toAbsolutePath().normalize();
    }

    private Path jobFile(String jobId) {
        return jobDir(jobId).resolve("job.json");
    }

    private Path eventsFile(String jobId) {
        return jobDir(jobId).resolve("events.jsonl");
    }

    private String sanitize(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "-");
    }
}
