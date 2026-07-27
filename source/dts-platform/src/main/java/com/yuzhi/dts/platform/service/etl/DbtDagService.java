package com.yuzhi.dts.platform.service.etl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.config.AirflowProperties;
import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
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
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class DbtDagService {

    private static final Logger LOG = LoggerFactory.getLogger(DbtDagService.class);
    private static final Pattern NON_SAFE = Pattern.compile("[^a-z0-9_]+");
    private static final Pattern MANAGED_DAG_ID = Pattern.compile(
        "^[a-z][a-z0-9_]{2,199}$"
    );
    private static final String MANAGED_TEMPLATE_VERSION = "sprint76-v1";

    private final AirflowProperties airflowProperties;
    private final DbtConfigService dbtConfigService;
    private final InfraDataSourceRepository dataSourceRepository;
    private final ObjectMapper objectMapper;

    public DbtDagService(
        AirflowProperties airflowProperties,
        DbtConfigService dbtConfigService,
        InfraDataSourceRepository dataSourceRepository,
        ObjectMapper objectMapper
    ) {
        this.airflowProperties = airflowProperties;
        this.dbtConfigService = dbtConfigService;
        this.dataSourceRepository = dataSourceRepository;
        this.objectMapper = objectMapper;
    }

    public String ensureDagForSelector(String selector) {
        SelectorContext context = parseSelector(selector);
        if (context == null || !StringUtils.hasText(context.tag())) {
            return airflowProperties.getDagId();
        }
        return ensureDagForTag(context.tag(), context.layerGroup());
    }

    public String ensureDagForTag(String tag, String layerGroup) {
        String normalizedTag = normalizeTag(tag);
        InfraDataSource matched = resolveSourceByTag(normalizedTag);
        String sourceKey = resolveSourceKey(matched, normalizedTag);
        String layer = normalizeLayerGroup(layerGroup);
        String freq = "manual";
        String dagId = layer + "_" + slugify(sourceKey) + "_dbt_" + freq;
        writeDagFile(dagId, normalizedTag, sourceKey, layer);
        return dagId;
    }

    /**
     * Deploys the stable RELEASE_BUILD executor DAG.
     *
     * <p>This canonical Sprint-76 DAG is intentionally only a versioned import of the Airflow
     * runtime factory. The legacy per-tag renderer remains isolated below for compatibility and is
     * not used by release-candidate builds.
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

    public String extractTag(String selector) {
        if (!StringUtils.hasText(selector)) {
            return null;
        }
        String[] parts = selector.trim().split("[,\\s]+");
        for (String part : parts) {
            if (!StringUtils.hasText(part)) {
                continue;
            }
            String lower = part.toLowerCase(Locale.ROOT);
            if (lower.startsWith("tag:")) {
                String value = part.substring(4).trim();
                if (StringUtils.hasText(value)) {
                    return value;
                }
            }
        }
        return null;
    }

    private SelectorContext parseSelector(String selector) {
        if (!StringUtils.hasText(selector)) {
            return null;
        }
        String tag = extractTag(selector);
        String layer = extractLayer(selector);
        String group = resolveLayerGroup(layer);
        return new SelectorContext(tag, group);
    }

    private String extractLayer(String selector) {
        if (!StringUtils.hasText(selector)) {
            return null;
        }
        String[] parts = selector.trim().toLowerCase(Locale.ROOT).split("[,\\s]+");
        for (String part : parts) {
            if (!StringUtils.hasText(part)) {
                continue;
            }
            String value = part;
            if (value.startsWith("tag:")) {
                value = value.substring(4);
            } else if (value.startsWith("layer:")) {
                value = value.substring(6);
            }
            value = value.trim();
            if (isLayerToken(value)) {
                return value;
            }
        }
        return null;
    }

    private boolean isLayerToken(String value) {
        return "ods".equals(value) || "dwd".equals(value) || "dws".equals(value) || "ads".equals(value);
    }

    private String resolveLayerGroup(String layer) {
        if (!StringUtils.hasText(layer)) {
            return "dwh";
        }
        String normalized = layer.trim().toLowerCase(Locale.ROOT);
        if ("ads".equals(normalized)) {
            return "ads";
        }
        if ("ods".equals(normalized)) {
            return "ods";
        }
        if ("dwd".equals(normalized) || "dws".equals(normalized)) {
            return "dwh";
        }
        return "dwh";
    }

    private String normalizeLayerGroup(String layerGroup) {
        if (!StringUtils.hasText(layerGroup)) {
            return "dwh";
        }
        String normalized = layerGroup.trim().toLowerCase(Locale.ROOT);
        if ("ads".equals(normalized)) {
            return "ads";
        }
        if ("ods".equals(normalized)) {
            return "ods";
        }
        return "dwh";
    }

    private InfraDataSource resolveSourceByTag(String tag) {
        if (!StringUtils.hasText(tag)) {
            return null;
        }
        String slug = slugify(tag);
        for (InfraDataSource source : dataSourceRepository.findByStatusIgnoreCase("ACTIVE")) {
            String sourceKey = resolveSourceKey(source, null);
            if (StringUtils.hasText(sourceKey) && slugify(sourceKey).equals(slug)) {
                return source;
            }
        }
        return null;
    }

    private String resolveSourceKey(InfraDataSource source, String fallback) {
        if (source == null) {
            return fallback;
        }
        Map<String, Object> props = parseProps(source.getProps());
        String key = firstText(props, "sourceSystem", "sourceName", "system", "app", "appCode", "name");
        if (StringUtils.hasText(key)) {
            return key;
        }
        if (StringUtils.hasText(source.getName())) {
            return source.getName();
        }
        return fallback;
    }

    private String resolveDagsDir(String layerGroup) {
        String dagsDir = airflowProperties.getDagsDir();
        if (StringUtils.hasText(dagsDir)) {
            return dagsDir.trim();
        }
        return "/opt/airflow/dags";
    }

    private void writeDagFile(String dagId, String selectorTag, String sourceKey, String layerGroup) {
        String dagsDir = resolveDagsDir(layerGroup);
        String subDir = normalizeLayerGroup(layerGroup);
        Path dir = StringUtils.hasText(subDir) ? Path.of(dagsDir, subDir) : Path.of(dagsDir);
        try {
            Files.createDirectories(dir);
        } catch (IOException ex) {
            LOG.warn("[dbt] failed to create dagsDir {}: {}", dagsDir, ex.getMessage());
            return;
        }
        Path dagFile = dir.resolve(dagId + ".py");
        String content = buildDagSource(dagId, selectorTag, sourceKey, subDir);
        try {
            if (Files.exists(dagFile)) {
                String existing = Files.readString(dagFile);
                if (existing.equals(content)) {
                    return;
                }
            }
            Files.writeString(dagFile, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            LOG.info("[dbt] ensured dag file {}", dagFile);
        } catch (IOException ex) {
            LOG.warn("[dbt] failed to write dag file {}: {}", dagFile, ex.getMessage());
        }
    }

    private String buildDagSource(String dagId, String selectorTag, String sourceKey, String layerGroup) {
        DbtConfigService.DbtConfigView view = dbtConfigService.loadConfig();
        String projectDir = view != null && view.config() != null ? view.config().projectDir() : null;
        String profilesDir = view != null && view.config() != null ? view.config().profilesDir() : null;
        String targetName = view != null && view.config() != null ? view.config().targetName() : null;
        String fallbackProject = StringUtils.hasText(projectDir) ? projectDir : "/opt/dts/dbt";
        String fallbackProfiles = StringUtils.hasText(profilesDir) ? profilesDir : "/opt/dts/dbt-profiles";
        String fallbackTarget = StringUtils.hasText(targetName) ? targetName : "dev";
        String tagValue = sanitizeTag(selectorTag, slugify(sourceKey));
        String selector = "tag:" + tagValue;
        String tagLabel = sanitizeTag(sourceKey, "dbt");
        String layerTag = sanitizeTag(layerGroup, "dwh");

        String dockerNetwork = airflowProperties.getDockerNetwork();
        boolean dockerPrivileged = airflowProperties.isDockerPrivileged();
        String networkMode = StringUtils.hasText(dockerNetwork) ? dockerNetwork : "dts-core";
        String privilegedStr = dockerPrivileged ? "true" : "false";

        return """
            from __future__ import annotations

            import os
            from datetime import datetime

            from airflow import DAG
            from airflow.operators.bash import BashOperator

            DBT_IMAGE = os.getenv("DBT_IMAGE", "dts-dbt:1.10.0")
            DBT_PROJECT_DIR = os.getenv("DBT_PROJECT_DIR", "%s")
            DBT_PROFILES_DIR = os.getenv("DBT_PROFILES_DIR", "%s")
            DBT_LOG_DIR = os.getenv("DBT_LOG_DIR", os.getenv("STACK_ROOT", "/opt/dts") + "/logs/dbt")
            DBT_PROJECT_MOUNT = os.getenv("DBT_PROJECT_MOUNT", "/opt/dbt")
            DBT_PROFILES_MOUNT = os.getenv("DBT_PROFILES_MOUNT", "/root/.dbt")
            DBT_LOG_MOUNT = "/opt/dbt-logs"
            DBT_THREADS = os.getenv("DBT_THREADS", "1")
            DBT_DOCKER_NETWORK = os.getenv("DBT_DOCKER_NETWORK", "%s")
            DBT_DOCKER_PRIVILEGED = os.getenv("DBT_DOCKER_PRIVILEGED", "%s")
            DTS_PLATFORM_BASE_URL = os.getenv("DTS_PLATFORM_BASE_URL", "http://dts-platform:8081")
            DTS_PLATFORM_SYNC_PATH = os.getenv("DTS_PLATFORM_SYNC_PATH", "/api/etl/dbt/models/sync")
            DTS_PLATFORM_SERVICE = os.getenv("DTS_PLATFORM_SERVICE", "dts-airflow")


            def build_command():
                return (
                    "set -euo pipefail\\n"
                    f"DBT_IMAGE=\\"{DBT_IMAGE}\\"\\n"
                    f"DBT_PROJECT_DIR=\\"{DBT_PROJECT_DIR}\\"\\n"
                    f"DBT_PROFILES_DIR=\\"{DBT_PROFILES_DIR}\\"\\n"
                    f"DBT_LOG_DIR=\\"{DBT_LOG_DIR}\\"\\n"
                    f"DBT_PROJECT_MOUNT=\\"{DBT_PROJECT_MOUNT}\\"\\n"
                    f"DBT_PROFILES_MOUNT=\\"{DBT_PROFILES_MOUNT}\\"\\n"
                    f"DBT_LOG_MOUNT=\\"{DBT_LOG_MOUNT}\\"\\n"
                    f"DBT_THREADS_DEFAULT=\\"{DBT_THREADS}\\"\\n"
                    f"DBT_DOCKER_NETWORK=\\"{DBT_DOCKER_NETWORK}\\"\\n"
                    f"DBT_DOCKER_PRIVILEGED=\\"{DBT_DOCKER_PRIVILEGED}\\"\\n"
                    "operation=\\"{{ dag_run.conf.get('operation', 'run') | lower }}\\"\\n"
                    "selector=\\"{{ dag_run.conf.get('models', '%s') }}\\"\\n"
                    "macro_name=\\"{{ dag_run.conf.get('macro_name', '') }}\\"\\n"
                    "project_dir=$(python - <<'PY'\\n"
                    "raw = {{ dag_run.conf.get('projectDir', '') | tojson }}\\n"
                    "if raw is None:\\n"
                    "    print('', end='')\\n"
                    "else:\\n"
                    "    print(raw, end='')\\n"
                    "PY\\n"
                    ")\\n"
                    "target=\\"{{ dag_run.conf.get('target', '%s') }}\\"\\n"
                    "threads=\\"{{ dag_run.conf.get('threads', '') }}\\"\\n"
                    "full_refresh=\\"{{ dag_run.conf.get('full_refresh', '') }}\\"\\n"
                    "vars_json=$(python - <<'PY'\\n"
                    "raw = {{ dag_run.conf.get('vars', '') | tojson }}\\n"
                    "if raw is None:\\n"
                    "    print('', end='')\\n"
                    "else:\\n"
                    "    print(raw, end='')\\n"
                    "PY\\n"
                    ")\\n"
                    "macro_args_json=$(python - <<'PY'\\n"
                    "raw = {{ dag_run.conf.get('macro_args', '') | tojson }}\\n"
                    "if raw is None:\\n"
                    "    print('', end='')\\n"
                    "else:\\n"
                    "    print(raw, end='')\\n"
                    "PY\\n"
                    ")\\n"
                    "selector_trim=$(echo \\"$selector\\" | tr -d '[:space:]')\\n"
                    "selector_norm=$(echo \\"$selector\\" | tr '[:upper:]' '[:lower:]')\\n"
                    "macro_name_trim=$(echo \\"$macro_name\\" | tr -d '[:space:]')\\n"
                    "project_dir_trim=$(echo \\"$project_dir\\" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')\\n"
                    "macro_args_trim=$(echo \\"$macro_args_json\\" | tr -d '[:space:]')\\n"
                    "threads_trim=$(echo \\"$threads\\" | tr -d '[:space:]')\\n"
                    "vars_trim=$(echo \\"$vars_json\\" | tr -d '[:space:]')\\n"
                    "if [ -z \\"$threads_trim\\" ]; then\\n"
                    "  threads_trim=\\"$DBT_THREADS_DEFAULT\\"\\n"
                    "fi\\n"
                    "if [ -n \\"$project_dir_trim\\" ]; then\\n"
                    "  DBT_PROJECT_DIR=\\"$project_dir_trim\\"\\n"
                    "fi\\n"
                    "docker_cmd=(docker run --rm --network \\"$DBT_DOCKER_NETWORK\\")\\n"
                    "if [ \\"$DBT_DOCKER_PRIVILEGED\\" = \\"true\\" ]; then\\n"
                    "  docker_cmd+=(--privileged)\\n"
                    "fi\\n"
                    "docker_cmd+=(\\n"
                    "  -v \\"$DBT_PROJECT_DIR:$DBT_PROJECT_MOUNT\\"\\n"
                    "  -v \\"$DBT_PROFILES_DIR:$DBT_PROFILES_MOUNT\\"\\n"
                    "  -v \\"$DBT_LOG_DIR:$DBT_LOG_MOUNT\\"\\n"
                    "  -e DBT_USE_EXPERIMENTAL_PARSER=false\\n"
                    "  -e DBT_LOG_PATH=$DBT_LOG_MOUNT\\n"
                    "  -e DBT_PROJECT_MOUNT=\\"$DBT_PROJECT_MOUNT\\"\\n"
                    "  -e DBT_PROFILES_MOUNT=\\"$DBT_PROFILES_MOUNT\\"\\n"
                    "  \\"$DBT_IMAGE\\"\\n"
                    ")\\n"
                    "if [ \\"$operation\\" = \\"docs\\" ]; then\\n"
                    "  docker_cmd+=(docs generate --project-dir \\"$DBT_PROJECT_MOUNT\\" --profiles-dir \\"$DBT_PROFILES_MOUNT\\" --log-path \\"$DBT_LOG_MOUNT\\" --target \\"$target\\")\\n"
                    "elif [ \\"$operation\\" = \\"run-operation\\" ]; then\\n"
                    "  if [ -z \\"$macro_name_trim\\" ]; then\\n"
                    "    echo \\"macro_name is required for run-operation\\" >&2\\n"
                    "    exit 1\\n"
                    "  fi\\n"
                    "  docker_cmd+=(run-operation \\"$macro_name\\" --project-dir \\"$DBT_PROJECT_MOUNT\\" --profiles-dir \\"$DBT_PROFILES_MOUNT\\" --log-path \\"$DBT_LOG_MOUNT\\" --target \\"$target\\")\\n"
                    "  if [ -n \\"$macro_args_trim\\" ]; then\\n"
                    "    docker_cmd+=(--args \\"$macro_args_json\\")\\n"
                    "  fi\\n"
                    "else\\n"
                    "  docker_cmd+=(\\"$operation\\" --project-dir \\"$DBT_PROJECT_MOUNT\\" --profiles-dir \\"$DBT_PROFILES_MOUNT\\" --log-path \\"$DBT_LOG_MOUNT\\" --target \\"$target\\")\\n"
                    "  if [ -n \\"$threads_trim\\" ]; then\\n"
                    "    docker_cmd+=(--threads \\"$threads_trim\\")\\n"
                    "  fi\\n"
                    "  if [ -n \\"$selector_trim\\" ] && [ \\"$selector_norm\\" != \\"all\\" ]; then\\n"
                    "    docker_cmd+=(--select \\"$selector\\")\\n"
                    "  fi\\n"
                    "  if [ \\"$full_refresh\\" = \\"True\\" ] || [ \\"$full_refresh\\" = \\"true\\" ]; then\\n"
                    "    docker_cmd+=(--full-refresh)\\n"
                    "  fi\\n"
                    "fi\\n"
                    "if [ -n \\"$vars_trim\\" ]; then\\n"
                    "  docker_cmd+=(--vars \\"$vars_json\\")\\n"
                    "fi\\n"
                    "echo Running: ${docker_cmd[*]}\\n"
                    "\\"${docker_cmd[@]}\\"\\n"
                )

            def build_sync_command():
                return (
                    "set -euo pipefail\\n"
                    f"DTS_PLATFORM_BASE_URL=\\"{DTS_PLATFORM_BASE_URL}\\"\\n"
                    f"DTS_PLATFORM_SYNC_PATH=\\"{DTS_PLATFORM_SYNC_PATH}\\"\\n"
                    f"DTS_PLATFORM_SERVICE=\\"{DTS_PLATFORM_SERVICE}\\"\\n"
                    "operation=\\"{{ dag_run.conf.get('operation', 'run') | lower }}\\"\\n"
                    "project_dir=$(python - <<'PY'\\n"
                    "raw = {{ dag_run.conf.get('projectDir', '') | tojson }}\\n"
                    "if raw is None:\\n"
                    "    print('', end='')\\n"
                    "else:\\n"
                    "    print(raw, end='')\\n"
                    "PY\\n"
                    ")\\n"
                    "sync_manifest=$(python - <<'PY'\\n"
                    "raw = {{ dag_run.conf.get('syncManifest', '') | string | tojson }}\\n"
                    "if raw is None:\\n"
                    "    print('', end='')\\n"
                    "else:\\n"
                    "    print(raw, end='')\\n"
                    "PY\\n"
                    ")\\n"
                    "if [ \\"$operation\\" = \\"run-operation\\" ]; then\\n"
                    "  exit 0\\n"
                    "fi\\n"
                    "payload=$(PROJECT_DIR=\\"$project_dir\\" SYNC_MANIFEST=\\"$sync_manifest\\" python - <<'PY'\\n"
                    "import json\\n"
                    "import os\\n"
                    "payload = {}\\n"
                    "project_dir = (os.environ.get('PROJECT_DIR') or '').strip()\\n"
                    "sync_manifest = (os.environ.get('SYNC_MANIFEST') or '').strip()\\n"
                    "if project_dir:\\n"
                    "    payload['projectDir'] = project_dir\\n"
                    "if sync_manifest:\\n"
                    "    payload['syncManifest'] = sync_manifest.lower() == 'true'\\n"
                    "print(json.dumps(payload), end='')\\n"
                    "PY\\n"
                    ")\\n"
                    "curl -sSf -X POST "
                    "-H \\"Content-Type: application/json\\" "
                    "-H \\"X-DTS-Service: $DTS_PLATFORM_SERVICE\\" "
                    "--data \\"$payload\\" "
                    "\\"$DTS_PLATFORM_BASE_URL$DTS_PLATFORM_SYNC_PATH\\" "
                    "|| true\\n"
                )


            with DAG(
                dag_id="%s",
                schedule=None,
                start_date=datetime(2024, 1, 1),
                catchup=False,
                tags=["dbt", "transform", "%s", "%s"],
            ) as dag:
                dbt_run = BashOperator(
                    task_id="dbt_run",
                    bash_command=build_command(),
                )

                sync_models = BashOperator(
                    task_id="sync_models",
                    bash_command=build_sync_command(),
                    trigger_rule="all_done",
                )

                dbt_run >> sync_models
            """.formatted(
            fallbackProject,
            fallbackProfiles,
            networkMode,
            privilegedStr,
            selector,
            fallbackTarget,
            dagId,
            layerTag,
            tagLabel
        );
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

    private Map<String, Object> parseProps(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Map.of();
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("{")) {
            try {
                return objectMapper.readValue(trimmed, new TypeReference<>() {});
            } catch (Exception ex) {
                return Map.of();
            }
        }
        return Map.of();
    }

    private String firstText(Map<String, Object> props, String... keys) {
        if (props == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            Object value = props.get(key);
            if (value != null) {
                String text = String.valueOf(value).trim();
                if (!text.isEmpty()) {
                    return text;
                }
            }
        }
        return null;
    }

    private String slugify(String value) {
        if (!StringUtils.hasText(value)) {
            return "dbt";
        }
        String slug = NON_SAFE.matcher(value.trim().toLowerCase(Locale.ROOT)).replaceAll("_");
        slug = slug.replaceAll("^_+", "").replaceAll("_+$", "");
        return StringUtils.hasText(slug) ? slug : "dbt";
    }

    private String normalizeTag(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String sanitizeTag(String value, String fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        String tag = NON_SAFE.matcher(value.trim().toLowerCase(Locale.ROOT)).replaceAll("-");
        tag = tag.replaceAll("^-+", "").replaceAll("-+$", "");
        return StringUtils.hasText(tag) ? tag : fallback;
    }

    private record SelectorContext(String tag, String layerGroup) {}

    public record ManagedDagDeployment(
        String dagId,
        String templateVersion,
        String deploymentChecksum,
        Path file
    ) {}
}
