package com.yuzhi.dts.platform.service.etl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.config.AirflowProperties;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class DbtDagService {

    private static final Logger LOG = LoggerFactory.getLogger(DbtDagService.class);
    private static final Pattern MANAGED_DAG_ID = Pattern.compile(
        "^[a-z][a-z0-9_]{2,199}$"
    );
    private static final String MANAGED_TEMPLATE_VERSION = "sprint76-v1";

    private final AirflowProperties airflowProperties;
    private final ObjectMapper objectMapper;

    public DbtDagService(
        AirflowProperties airflowProperties,
        ObjectMapper objectMapper
    ) {
        this.airflowProperties = airflowProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * Deploys the stable RELEASE_BUILD executor DAG.
     *
     * <p>This canonical Sprint-76 DAG is intentionally only a versioned import of the Airflow
     * runtime factory. Retired per-tag Bash renderers are not retained as a compatibility path.
     */
    public ManagedDagDeployment ensureReleaseBuildDag(String dagId) {
        String id = requireManagedDagId(dagId);
        String deploymentChecksum = sha256(
            String.join(
                "\u0000",
                MANAGED_TEMPLATE_VERSION,
                id,
                "RELEASE_BUILD",
                "schedule:none"
            )
        );
        String source = buildManagedReleaseDagSource(id, deploymentChecksum);
        Path directory = Path.of(resolveDagsDir(null)).toAbsolutePath().normalize();
        Path dagFile = directory.resolve(id + ".py").normalize();
        if (!directory.equals(dagFile.getParent())) {
            throw new IllegalArgumentException("Managed DAG path is invalid");
        }
        writeAtomically(dagFile, source);
        return new ManagedDagDeployment(
            id,
            MANAGED_TEMPLATE_VERSION,
            deploymentChecksum,
            dagFile
        );
    }

    /**
     * Deploys one stable plan-level OPERATIONAL_RUN DAG.
     *
     * <p>The caller supplies persisted binding metadata only. Runtime selector, project path,
     * target and credentials are intentionally absent and are resolved by the shared task factory
     * after a durable pipeline run has been opened.
     */
    public ManagedDagDeployment ensurePlanDag(
        String dagId,
        UUID bindingId,
        String schedule,
        String timezone,
        String desiredDeploymentChecksum
    ) {
        String id = requireManagedDagId(dagId);
        UUID binding = java.util.Objects.requireNonNull(
            bindingId,
            "bindingId is required"
        );
        String zone = requireTimezone(timezone);
        String cron = requireSchedule(schedule);
        String deploymentChecksum = requireChecksum(
            desiredDeploymentChecksum,
            "desiredDeploymentChecksum"
        );
        String source = buildManagedPlanDagSource(
            id,
            binding,
            cron,
            zone,
            deploymentChecksum
        );
        Path directory = Path.of(resolveDagsDir(null))
            .toAbsolutePath()
            .normalize();
        Path dagFile = directory.resolve(id + ".py").normalize();
        if (!directory.equals(dagFile.getParent())) {
            throw new IllegalArgumentException("Managed DAG path is invalid");
        }
        writeAtomically(dagFile, source);
        return new ManagedDagDeployment(
            id,
            MANAGED_TEMPLATE_VERSION,
            deploymentChecksum,
            dagFile
        );
    }

    private String resolveDagsDir(String layerGroup) {
        String dagsDir = airflowProperties.getDagsDir();
        if (StringUtils.hasText(dagsDir)) {
            return dagsDir.trim();
        }
        return "/opt/airflow/dags";
    }


    private String buildManagedReleaseDagSource(
        String dagId,
        String deploymentChecksum
    ) {
        return """
            # airflow DAG discovery marker; execution stays in the shared factory.
            from dts_runtime.dbt_task_factory import build_dbt_dag

            dag = build_dbt_dag(
                dag_id="%s",
                purpose="RELEASE_BUILD",
                schedule=None,
                timezone="UTC",
                template_version="%s",
                deployment_checksum="%s",
                tags=["dts-managed", "dbt", "release-build"],
            )
            """.formatted(
            dagId,
            MANAGED_TEMPLATE_VERSION,
            deploymentChecksum
        );
    }

    private String buildManagedPlanDagSource(
        String dagId,
        UUID bindingId,
        String schedule,
        String timezone,
        String deploymentChecksum
    ) {
        return """
            # airflow DAG discovery marker; shared factory enforces max_active_runs=1.
            from dts_runtime.dbt_task_factory import build_dbt_dag

            dag = build_dbt_dag(
                dag_id=%s,
                purpose="OPERATIONAL_RUN",
                schedule=%s,
                timezone=%s,
                template_version=%s,
                deployment_checksum=%s,
                binding_id=%s,
                tags=["dts-managed", "dbt", "plan-run"],
            )
            """.formatted(
            pythonLiteral(dagId),
            schedule == null ? "None" : pythonLiteral(schedule),
            pythonLiteral(timezone),
            pythonLiteral(MANAGED_TEMPLATE_VERSION),
            pythonLiteral(deploymentChecksum),
            pythonLiteral(bindingId.toString())
        );
    }

    private void writeAtomically(Path target, String content) {
        Path temporary = null;
        try {
            Files.createDirectories(target.getParent());
            if (
                Files.isRegularFile(target) &&
                Files.readString(target, StandardCharsets.UTF_8).equals(content)
            ) {
                return;
            }
            temporary = target
                .getParent()
                .resolve(
                    "." +
                    target.getFileName() +
                    ".tmp-" +
                    UUID.randomUUID()
                );
            Files.writeString(
                temporary,
                content,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE
            );
            try (
                FileChannel file = FileChannel.open(
                    temporary,
                    StandardOpenOption.WRITE
                )
            ) {
                file.force(true);
            }
            try {
                Files.move(
                    temporary,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                );
            } catch (
                java.nio.file.AtomicMoveNotSupportedException unsupported
            ) {
                Files.move(
                    temporary,
                    target,
                    StandardCopyOption.REPLACE_EXISTING
                );
            }
            temporary = null;
            try (
                FileChannel directory = FileChannel.open(
                    target.getParent(),
                    StandardOpenOption.READ
                )
            ) {
                directory.force(true);
            }
            LOG.info(
                "[dbt] deployed managed DAG id={} template={} checksum={}",
                target.getFileName(),
                MANAGED_TEMPLATE_VERSION,
                sha256(content)
            );
        } catch (IOException failure) {
            throw new IllegalStateException(
                "Managed Airflow DAG could not be deployed: " +
                target.getFileName(),
                failure
            );
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // A same-directory orphan is harmless and never has a .py suffix.
                }
            }
        }
    }

    private static String requireManagedDagId(String dagId) {
        String id = dagId == null ? "" : dagId.trim();
        if (!MANAGED_DAG_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Managed DAG id is invalid");
        }
        return id;
    }

    private static String requireSchedule(String schedule) {
        if (schedule == null) return null;
        String cron = schedule.trim();
        if (
            cron.isEmpty() ||
            cron.length() > 128 ||
            cron.contains("\n") ||
            cron.contains("\r") ||
            cron.split("\\s+").length != 5
        ) {
            throw new IllegalArgumentException(
                "Airflow cron schedule must contain five fields"
            );
        }
        return cron;
    }

    private static String requireTimezone(String timezone) {
        String zone = timezone == null ? "" : timezone.trim();
        try {
            if (zone.isEmpty()) throw new DateTimeException("empty timezone");
            return ZoneId.of(zone).getId();
        } catch (DateTimeException invalid) {
            throw new IllegalArgumentException(
                "Airflow timezone is invalid",
                invalid
            );
        }
    }

    private static String requireChecksum(String value, String name) {
        String checksum = value == null ? "" : value.trim();
        if (!checksum.matches("^[0-9a-f]{64}$")) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return checksum;
    }

    private String pythonLiteral(String value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException impossible) {
            throw new IllegalStateException(
                "Managed DAG metadata could not be encoded",
                impossible
            );
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of()
                .formatHex(
                    MessageDigest.getInstance("SHA-256")
                        .digest(value.getBytes(StandardCharsets.UTF_8))
                );
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(
                "SHA-256 is unavailable",
                impossible
            );
        }
    }


    public record ManagedDagDeployment(
        String dagId,
        String templateVersion,
        String deploymentChecksum,
        Path file
    ) {}
}
