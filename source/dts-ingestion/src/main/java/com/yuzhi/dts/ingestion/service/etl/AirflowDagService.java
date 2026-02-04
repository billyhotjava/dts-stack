package com.yuzhi.dts.ingestion.service.etl;

import com.yuzhi.dts.ingestion.config.AddaxProperties;
import com.yuzhi.dts.ingestion.config.AirflowProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.yuzhi.dts.ingestion.domain.IngestionTask;
import com.yuzhi.dts.ingestion.service.infra.IngestionSettingsService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AirflowDagService {

    private static final Logger LOG = LoggerFactory.getLogger(AirflowDagService.class);
    private static final Pattern NON_SAFE = Pattern.compile("[^a-z0-9_]+");

    private final AirflowProperties properties;
    private final AddaxProperties addaxProperties;
    private final IngestionSettingsService settingsService;
    private final AirflowClient airflowClient;

    public AirflowDagService(
        AirflowProperties properties,
        AddaxProperties addaxProperties,
        IngestionSettingsService settingsService,
        AirflowClient airflowClient
    ) {
        this.properties = properties;
        this.addaxProperties = addaxProperties;
        this.settingsService = settingsService;
        this.airflowClient = airflowClient;
    }

    public String ensureDagForTask(IngestionTask task) {
        return ensureDagForTask(task, null, false);
    }

    public String ensureDagForTask(IngestionTask task, List<AddaxJobService.PerTableJob> perTableJobs) {
        return ensureDagForTask(task, perTableJobs, false);
    }

    public String rebuildDagForTask(IngestionTask task) {
        return ensureDagForTask(task, null, true);
    }

    public boolean deleteDagForTask(IngestionTask task) {
        if (task == null) {
            return false;
        }
        String dagId = resolveDagId(task);
        if (StringUtils.hasText(dagId)) {
            try {
                boolean deletedRemote = airflowClient.deleteDag(dagId);
                if (deletedRemote) {
                    LOG.info("[airflow] deleted dag {} via API", dagId);
                }
            } catch (Exception ex) {
                LOG.warn("[airflow] failed to delete dag {} via API: {}", dagId, ex.getMessage());
            }
        }
        Path dagDir = resolveDagDir(task);
        if (dagDir == null) {
            LOG.warn("[airflow] dagsDir not configured, skip delete dag file for dagId={}", dagId);
            return false;
        }
        Path dagFile = dagDir.resolve(dagId + ".py");
        try {
            boolean deleted = Files.deleteIfExists(dagFile);
            if (deleted) {
                LOG.info("[airflow] deleted dag file {}", dagFile);
            }
            return deleted;
        } catch (IOException ex) {
            LOG.warn("[airflow] failed to delete dag file {}: {}", dagFile, ex.getMessage());
            return false;
        }
    }

    public String resolveDagIdForTask(IngestionTask task) {
        if (task == null) {
            return null;
        }
        return resolveDagId(task);
    }

    public String resolveTaskIdForTask(IngestionTask task) {
        if (task == null) {
            return null;
        }
        return resolveTaskId(task);
    }

    private String ensureDagForTask(IngestionTask task, List<AddaxJobService.PerTableJob> perTableJobs, boolean force) {
        if (task == null) {
            return null;
        }
        String dagId = resolveDagId(task);
        String previousDagId = task.getAirflowDagId();
        Path dagDir = resolveDagDir(task);
        Path baseDir = resolveDagsBasePath();
        if (dagDir == null) {
            LOG.warn("[airflow] dagsDir not configured, skip DAG file generation for dagId={}", dagId);
            return null;
        }
        try {
            if (Files.exists(dagDir) && !Files.isDirectory(dagDir)) {
                throw new IOException("dagsDir is not a directory");
            }
            Files.createDirectories(dagDir);
        } catch (IOException ex) {
            LOG.warn("[airflow] failed to create dagsDir {}: {}", dagDir, ex.getMessage());
            if (baseDir != null && !baseDir.equals(dagDir)) {
                try {
                    if (Files.exists(baseDir) && !Files.isDirectory(baseDir)) {
                        throw new IOException("fallback dagsDir is not a directory");
                    }
                    Files.createDirectories(baseDir);
                    dagDir = baseDir;
                } catch (IOException inner) {
                    LOG.warn("[airflow] failed to create fallback dagsDir {}: {}", baseDir, inner.getMessage());
                    return null;
                }
            } else {
                return null;
            }
        }
        Path dagFile = dagDir.resolve(dagId + ".py");
        String content = buildDagSource(dagId, task, perTableJobs);
        boolean written = false;
        try {
            if (StringUtils.hasText(previousDagId) && !previousDagId.equals(dagId)) {
                Path previousFile = dagDir.resolve(previousDagId + ".py");
                try {
                    if (Files.deleteIfExists(previousFile)) {
                        LOG.info("[airflow] removed old dag file {}", previousFile);
                    }
                } catch (IOException ex) {
                    LOG.warn("[airflow] failed to delete old dag file {}: {}", previousFile, ex.getMessage());
                }
            }
            if (baseDir != null && !baseDir.equals(dagDir)) {
                Path legacyFile = baseDir.resolve(dagId + ".py");
                try {
                    if (Files.deleteIfExists(legacyFile)) {
                        LOG.info("[airflow] removed legacy dag file {}", legacyFile);
                    }
                } catch (IOException ex) {
                    LOG.warn("[airflow] failed to delete legacy dag file {}: {}", legacyFile, ex.getMessage());
                }
            }
            if (!force && Files.exists(dagFile)) {
                String existing = Files.readString(dagFile);
                if (existing.equals(content)) {
                    return dagId;
                }
            }
            Files.writeString(dagFile, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            LOG.info("[airflow] {} dag file {}", force ? "rebuilt" : "ensured", dagFile);
            written = true;
        } catch (IOException ex) {
            LOG.warn("[airflow] failed to write dag file {}: {}", dagFile, ex.getMessage());
        }
        return written ? dagId : null;
    }

    private Path resolveDagDir(IngestionTask task) {
        String dagsDir = resolveDagsBaseDir();
        if (!StringUtils.hasText(dagsDir)) {
            return null;
        }
        String layerDir = resolveLayerDir(task);
        if (!StringUtils.hasText(layerDir)) {
            return Path.of(dagsDir);
        }
        Path base = Path.of(dagsDir);
        if (base.endsWith(layerDir)) {
            return base;
        }
        return base.resolve(layerDir);
    }

    private Path resolveDagsBasePath() {
        String dagsDir = resolveDagsBaseDir();
        return StringUtils.hasText(dagsDir) ? Path.of(dagsDir) : null;
    }

    private String resolveDagsBaseDir() {
        IngestionSettingsService.SettingsSnapshot settings = settingsService.getSettings(IngestionSettingsService.SERVICE_AIRFLOW);
        String configured = settings.getString("dagsDir", null);
        String fallback = properties.getDagsDir();
        String normalized = normalizePath(configured);
        if (StringUtils.hasText(normalized)) {
            return normalized;
        }
        if (StringUtils.hasText(fallback)) {
            if (StringUtils.hasText(configured)) {
                LOG.warn("[airflow] dagsDir {} not found, fallback to {}", configured, fallback);
            }
            return fallback;
        }
        return null;
    }

    private String resolveLayerDir(IngestionTask task) {
        IngestionSettingsService.SettingsSnapshot settings = settingsService.getSettings(IngestionSettingsService.SERVICE_AIRFLOW);
        String configured = settings.getString("layerDir", null);
        if (StringUtils.hasText(configured)) {
            return configured.trim();
        }
        return null;
    }

    private String normalizePath(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String normalizePath(String value, String fallback) {
        String normalized = normalizePath(value);
        if (StringUtils.hasText(normalized)) {
            return normalized;
        }
        return StringUtils.hasText(fallback) ? fallback.trim() : "";
    }

    private String resolveDagId(IngestionTask task) {
        if (StringUtils.hasText(task.getAirflowDagId())) {
            return task.getAirflowDagId().trim();
        }
        String layer = normalizeSegment(resolveLayerDir(task), "ods");
        String sourceKey = normalizeSegment(resolveSourceKey(task), "source");
        String desc = normalizeSegment(task != null ? task.getName() : null, "ingestion");
        String freq = normalizeSegment(resolveFrequency(task != null ? task.getSyncSchedule() : null), "manual");
        String dagId = String.format("%s_%s_%s_%s", layer, sourceKey, desc, freq);
        return limitLength(dagId, 200);
    }

    private String resolveSourceKey(IngestionTask task) {
        if (task == null) {
            return null;
        }
        JsonNode config = task.getSourceConfig();
        if (config == null || config.isNull()) {
            return null;
        }
        return firstText(config, "sourceName", "sourceSystem", "sourceApp", "appCode", "system", "app", "name");
    }

    private String firstText(JsonNode node, String... keys) {
        if (node == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            JsonNode value = node.get(key);
            String text = textValue(value);
            if (StringUtils.hasText(text)) {
                return text;
            }
        }
        return null;
    }

    private String textValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isArray() && node.size() > 0) {
            JsonNode first = node.get(0);
            return first != null && first.isTextual() ? first.asText() : null;
        }
        return null;
    }

    private String slugify(String value) {
        if (!StringUtils.hasText(value)) {
            return "task";
        }
        String slug = NON_SAFE.matcher(value.trim().toLowerCase()).replaceAll("_");
        slug = slug.replaceAll("^_+", "").replaceAll("_+$", "");
        return StringUtils.hasText(slug) ? slug : "task";
    }

    private String normalizeSegment(String value, String fallback) {
        String slug = slugify(value);
        return StringUtils.hasText(slug) ? slug : fallback;
    }

    private String limitLength(String value, int max) {
        if (!StringUtils.hasText(value) || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    private String resolveFrequency(String schedule) {
        if (!StringUtils.hasText(schedule)) {
            return "manual";
        }
        String cron = schedule.trim().toLowerCase();
        if ("@hourly".equals(cron) || cron.startsWith("0 *") || cron.startsWith("*/1 ")) {
            return "hourly";
        }
        if ("@daily".equals(cron) || cron.startsWith("0 0 *") || cron.startsWith("0 0 ?")) {
            return "daily";
        }
        if ("@weekly".equals(cron) || cron.matches("0\\s+0\\s+\\*\\s+\\*\\s+\\d+")) {
            return "weekly";
        }
        if ("@monthly".equals(cron) || cron.matches("0\\s+0\\s+1\\s+\\*\\s+\\*")) {
            return "monthly";
        }
        return "custom";
    }

    private String resolveTaskId(IngestionTask task) {
        String target = resolveFirstTargetTable(task);
        if (!StringUtils.hasText(target)) {
            return "addax_run";
        }
        String slug = slugify(target);
        return StringUtils.hasText(slug) ? "addax_" + slug : "addax_run";
    }

    private String resolveFirstTargetTable(IngestionTask task) {
        if (task == null) return null;
        JsonNode mapping = task.getTableMapping();
        if (mapping == null || !mapping.isArray() || mapping.size() == 0) {
            return null;
        }
        JsonNode first = mapping.get(0);
        if (first == null || first.isNull()) {
            return null;
        }
        return firstText(first, "target", "targetTable", "ods", "dest");
    }

    private String buildDagSource(String dagId, IngestionTask task, List<AddaxJobService.PerTableJob> perTableJobs) {
        if (perTableJobs != null && perTableJobs.size() > 1) {
            return buildMultiTaskDagSource(dagId, task, perTableJobs);
        }
        String sourceTag = sanitizeTag(task == null ? null : task.getSourceType(), "source");
        String nameTag = sanitizeTag(task == null ? null : task.getName(), "ingestion");
        String addaxImage = escapePythonString(resolveAddaxImage());
        String addaxJobDir = escapePythonString(resolveAddaxJobDir());
        String defaultJobPath = escapePythonString(resolveDefaultJobPath(task));
        String schedule = normalizeSchedule(task == null ? null : task.getSyncSchedule());
        String scheduleLiteral = schedule == null ? "None" : "\"" + escapePythonString(schedule) + "\"";
        String taskId = resolveTaskId(task);

        // For file source tasks, add a PythonOperator pre-task to create the target table.
        // Addax 6.0.8 validates table metadata during init (before preSql), so the table
        // must exist before the Addax DockerOperator starts.
        boolean fileSource = isFileSourceType(task);
        String initTableBlock = "";
        String dependencyBlock = "";
        String extraImports = "";
        if (fileSource) {
            String createTableDdl = buildCreateTableDdl(task);
            if (StringUtils.hasText(createTableDdl)) {
                FileSourceDbInfo dbInfo = extractFileSourceDbInfo(task);
                extraImports = "\nfrom airflow.operators.python import PythonOperator\n";
                // Indentation must be 4 spaces to align with other statements inside "with DAG" block
                // (the outer text block strips 12 chars of leading whitespace)
                initTableBlock = """

    def _init_target_table():
        import psycopg2
        conn = psycopg2.connect(host="%s", port=%d, dbname="%s", user="%s", password="%s")
        try:
            cur = conn.cursor()
            cur.execute(\"\"\"%s\"\"\")
            conn.commit()
        finally:
            conn.close()

    init_table = PythonOperator(
        task_id="init_target_table",
        python_callable=_init_target_table,
    )

""".formatted(
                    escapePythonString(dbInfo.host),
                    dbInfo.port,
                    escapePythonString(dbInfo.dbname),
                    escapePythonString(dbInfo.username),
                    escapePythonString(dbInfo.password),
                    escapePythonString(createTableDdl)
                );
                dependencyBlock = "\n    init_table >> addax_run\n";
            }
        }

        return """
            from __future__ import annotations

            import os
            from datetime import datetime

            from airflow import DAG
            from airflow.providers.docker.operators.docker import DockerOperator
            from docker.types import Mount
            %s
            ADDAX_IMAGE = os.getenv("ADDAX_IMAGE", "%s")
            ADDAX_JOB_DIR = os.getenv("ADDAX_JOB_DIR", "%s")
            ADDAX_DRIVER_DIR = os.getenv("ADDAX_DRIVER_DIR", "")
            ADDAX_DRIVER_JARS = os.getenv("ADDAX_DRIVER_JARS", "")
            DEFAULT_JOB_PATH = os.getenv("ADDAX_JOB_DEFAULT", "%s")


            def build_driver_mounts():
                mounts = []
                if not ADDAX_DRIVER_DIR or not ADDAX_DRIVER_JARS:
                    return mounts
                target_dirs = [
                    "/opt/addax/plugin/reader/rdbmsreader/lib",
                    "/opt/addax/plugin/reader/rdbmsreader/libs",
                    "/opt/addax/plugin/writer/rdbmswriter/lib",
                    "/opt/addax/plugin/writer/rdbmswriter/libs",
                ]
                for jar_name in [jar.strip() for jar in ADDAX_DRIVER_JARS.split(",") if jar.strip()]:
                    host_path = os.path.join(ADDAX_DRIVER_DIR, jar_name)
                    if not os.path.exists(host_path):
                        continue
                    for target_dir in target_dirs:
                        mounts.append(
                            Mount(
                                source=host_path,
                                target=f"{target_dir}/{jar_name}",
                                type="bind",
                                read_only=True,
                            )
                        )
                return mounts


            with DAG(
                dag_id="%s",
                schedule=%s,
                start_date=datetime(2024, 1, 1),
                catchup=False,
                is_paused_upon_creation=False,
                tags=["addax", "etl", "ods", "%s", "%s"],
            ) as dag:
                run_cmd = [
                    "sh",
                    "-lc",
                    "/opt/addax/bin/addax.sh {{ dag_run.conf.get('job_path', DEFAULT_JOB_PATH) }}",
                ]
            %s
                addax_run = DockerOperator(
                    task_id="%s",
                    image=ADDAX_IMAGE,
                    api_version="auto",
                    auto_remove=True,
                    docker_url="unix://var/run/docker.sock",
                    command=run_cmd,
                    mount_tmp_dir=False,
                    mounts=[
                        Mount(source=ADDAX_JOB_DIR, target="/opt/addax/jobs", type="bind"),
                        *build_driver_mounts(),
                    ],
                    environment={},
                    tty=True,
                )
            %s
            """.formatted(extraImports, addaxImage, addaxJobDir, defaultJobPath,
                dagId, scheduleLiteral, sourceTag, nameTag,
                initTableBlock, taskId, dependencyBlock);
    }

    /**
     * Generate a DAG with one DockerOperator per table.
     * Each operator runs Addax with its own per-table job JSON file.
     */
    private String buildMultiTaskDagSource(String dagId, IngestionTask task, List<AddaxJobService.PerTableJob> perTableJobs) {
        String sourceTag = sanitizeTag(task == null ? null : task.getSourceType(), "source");
        String nameTag = sanitizeTag(task == null ? null : task.getName(), "ingestion");
        String addaxImage = escapePythonString(resolveAddaxImage());
        String addaxJobDir = escapePythonString(resolveAddaxJobDir());
        String schedule = normalizeSchedule(task == null ? null : task.getSyncSchedule());
        String scheduleLiteral = schedule == null ? "None" : "\"" + escapePythonString(schedule) + "\"";

        StringBuilder sb = new StringBuilder();
        sb.append("from __future__ import annotations\n\n");
        sb.append("import os\n");
        sb.append("from datetime import datetime\n\n");
        sb.append("from airflow import DAG\n");
        sb.append("from airflow.providers.docker.operators.docker import DockerOperator\n");
        sb.append("from docker.types import Mount\n\n");
        sb.append(String.format("ADDAX_IMAGE = os.getenv(\"ADDAX_IMAGE\", \"%s\")\n", addaxImage));
        sb.append(String.format("ADDAX_JOB_DIR = os.getenv(\"ADDAX_JOB_DIR\", \"%s\")\n", addaxJobDir));
        sb.append("ADDAX_DRIVER_DIR = os.getenv(\"ADDAX_DRIVER_DIR\", \"\")\n");
        sb.append("ADDAX_DRIVER_JARS = os.getenv(\"ADDAX_DRIVER_JARS\", \"\")\n\n\n");
        sb.append("def build_driver_mounts():\n");
        sb.append("    mounts = []\n");
        sb.append("    if not ADDAX_DRIVER_DIR or not ADDAX_DRIVER_JARS:\n");
        sb.append("        return mounts\n");
        sb.append("    target_dirs = [\n");
        sb.append("        \"/opt/addax/plugin/reader/rdbmsreader/lib\",\n");
        sb.append("        \"/opt/addax/plugin/reader/rdbmsreader/libs\",\n");
        sb.append("        \"/opt/addax/plugin/writer/rdbmswriter/lib\",\n");
        sb.append("        \"/opt/addax/plugin/writer/rdbmswriter/libs\",\n");
        sb.append("    ]\n");
        sb.append("    for jar_name in [jar.strip() for jar in ADDAX_DRIVER_JARS.split(\",\") if jar.strip()]:\n");
        sb.append("        host_path = os.path.join(ADDAX_DRIVER_DIR, jar_name)\n");
        sb.append("        if not os.path.exists(host_path):\n");
        sb.append("            continue\n");
        sb.append("        for target_dir in target_dirs:\n");
        sb.append("            mounts.append(\n");
        sb.append("                Mount(\n");
        sb.append("                    source=host_path,\n");
        sb.append("                    target=f\"{target_dir}/{jar_name}\",\n");
        sb.append("                    type=\"bind\",\n");
        sb.append("                    read_only=True,\n");
        sb.append("                )\n");
        sb.append("            )\n");
        sb.append("    return mounts\n\n\n");

        sb.append(String.format("with DAG(\n"));
        sb.append(String.format("    dag_id=\"%s\",\n", escapePythonString(dagId)));
        sb.append(String.format("    schedule=%s,\n", scheduleLiteral));
        sb.append("    start_date=datetime(2024, 1, 1),\n");
        sb.append("    catchup=False,\n");
        sb.append("    is_paused_upon_creation=False,\n");
        sb.append(String.format("    tags=[\"addax\", \"etl\", \"ods\", \"%s\", \"%s\"],\n", sourceTag, nameTag));
        sb.append(") as dag:\n");

        // Generate one DockerOperator per table, deduplicating task IDs
        Set<String> usedIds = new LinkedHashSet<>();
        for (AddaxJobService.PerTableJob job : perTableJobs) {
            String baseId = "addax_" + slugify(job.tableName());
            String opTaskId = baseId;
            int suffix = 2;
            while (usedIds.contains(opTaskId)) {
                opTaskId = baseId + "_" + suffix++;
            }
            usedIds.add(opTaskId);
            String jobPath = escapePythonString(job.containerJobPath());

            sb.append(String.format("    %s = DockerOperator(\n", opTaskId));
            sb.append(String.format("        task_id=\"%s\",\n", opTaskId));
            sb.append("        image=ADDAX_IMAGE,\n");
            sb.append("        api_version=\"auto\",\n");
            sb.append("        auto_remove=True,\n");
            sb.append("        docker_url=\"unix://var/run/docker.sock\",\n");
            sb.append(String.format("        command=\"/opt/addax/bin/addax.sh %s\",\n", jobPath));
            sb.append("        mount_tmp_dir=False,\n");
            sb.append("        mounts=[\n");
            sb.append("            Mount(source=ADDAX_JOB_DIR, target=\"/opt/addax/jobs\", type=\"bind\"),\n");
            sb.append("            *build_driver_mounts(),\n");
            sb.append("        ],\n");
            sb.append("        environment={},\n");
            sb.append("        tty=True,\n");
            sb.append("    )\n\n");
        }

        return sb.toString();
    }

    private String normalizeSchedule(String schedule) {
        if (!StringUtils.hasText(schedule)) {
            return null;
        }
        return schedule.trim();
    }

    private String resolveAddaxJobDir() {
        IngestionSettingsService.SettingsSnapshot settings = settingsService.getSettings(IngestionSettingsService.SERVICE_ADDAX);
        String configured = settings.getString("jobDir", null);
        String fallback = addaxProperties.getJobDir();
        return normalizePath(configured, fallback != null ? fallback : "/opt/airflow/dags");
    }

    private String resolveDefaultJobPath(IngestionTask task) {
        String jobPath = task == null ? null : task.getAddaxJobPath();
        if (StringUtils.hasText(jobPath)) {
            Path path = Path.of(jobPath.trim());
            Path file = path.getFileName();
            if (file != null) {
                return "/opt/addax/jobs/" + file.toString();
            }
        }
        return "/opt/addax/jobs/job.json";
    }

    private String resolveAddaxImage() {
        IngestionSettingsService.SettingsSnapshot settings = settingsService.getSettings(IngestionSettingsService.SERVICE_ADDAX);
        String configured = settings.getString("image", null);
        String fallback = addaxProperties.getImage();
        return StringUtils.hasText(configured) ? configured.trim() : (StringUtils.hasText(fallback) ? fallback.trim() : "dts-addax:6.0.8");
    }

    private String escapePythonString(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // ---- File source helpers for DAG pre-task table creation ----

    private boolean isFileSourceType(IngestionTask task) {
        if (task == null) return false;
        String st = task.getSourceType();
        if (!StringUtils.hasText(st)) return false;
        String lower = st.trim().toLowerCase();
        return "excel".equals(lower) || "csv".equals(lower)
            || "excelreader".equals(lower) || "txtfilereader".equals(lower);
    }

    private record FileSourceDbInfo(String host, int port, String dbname, String username, String password) {}

    /**
     * Extract PostgreSQL connection info from the task's destination config.
     * Parses JDBC URL like jdbc:postgresql://host:port/dbname
     */
    private FileSourceDbInfo extractFileSourceDbInfo(IngestionTask task) {
        JsonNode destConfig = task.getDestinationConfig();
        if (destConfig == null || destConfig.isNull()) {
            return new FileSourceDbInfo("localhost", 5432, "postgres", "postgres", "");
        }
        String jdbcUrl = firstText(destConfig, "jdbcUrl");
        if (!StringUtils.hasText(jdbcUrl)) {
            // Try connection[0].jdbcUrl
            JsonNode conn = destConfig.get("connection");
            if (conn != null && conn.isArray() && conn.size() > 0) {
                jdbcUrl = firstText(conn.get(0), "jdbcUrl");
            } else if (conn != null && conn.isObject()) {
                jdbcUrl = firstText(conn, "jdbcUrl");
            }
        }
        String username = firstText(destConfig, "username");
        String password = firstText(destConfig, "password");
        if (!StringUtils.hasText(username)) username = "postgres";
        if (!StringUtils.hasText(password)) password = "";

        // Parse JDBC URL: jdbc:postgresql://host:port/dbname?params
        String host = "localhost";
        int port = 5432;
        String dbname = "postgres";
        if (StringUtils.hasText(jdbcUrl)) {
            String remainder = jdbcUrl;
            if (remainder.startsWith("jdbc:postgresql://")) {
                remainder = remainder.substring("jdbc:postgresql://".length());
            } else if (remainder.startsWith("jdbc:")) {
                remainder = remainder.substring(5);
                int slashIdx = remainder.indexOf("//");
                if (slashIdx >= 0) remainder = remainder.substring(slashIdx + 2);
            }
            // Remove query params
            int qIdx = remainder.indexOf('?');
            if (qIdx >= 0) remainder = remainder.substring(0, qIdx);
            // Split host:port/dbname
            int slashIdx = remainder.indexOf('/');
            String hostPort = slashIdx >= 0 ? remainder.substring(0, slashIdx) : remainder;
            if (slashIdx >= 0 && slashIdx < remainder.length() - 1) {
                dbname = remainder.substring(slashIdx + 1);
            }
            int colonIdx = hostPort.indexOf(':');
            if (colonIdx >= 0) {
                host = hostPort.substring(0, colonIdx);
                try { port = Integer.parseInt(hostPort.substring(colonIdx + 1)); } catch (NumberFormatException ignored) {}
            } else {
                host = hostPort;
            }
        }
        return new FileSourceDbInfo(host, port, dbname, username, password);
    }

    /**
     * Build CREATE TABLE IF NOT EXISTS DDL from file columns in the task's source config.
     */
    private String buildCreateTableDdl(IngestionTask task) {
        if (task == null) return null;
        // Get target table name from table mapping
        String targetTable = resolveFirstTargetTable(task);
        if (!StringUtils.hasText(targetTable)) {
            // Fallback: try to get from destination config connection.table
            JsonNode destConfig = task.getDestinationConfig();
            if (destConfig != null) {
                JsonNode conn = destConfig.get("connection");
                if (conn != null && conn.isArray() && conn.size() > 0) {
                    targetTable = firstText(conn.get(0), "table");
                } else if (conn != null && conn.isObject()) {
                    targetTable = firstText(conn, "table");
                }
                if (!StringUtils.hasText(targetTable)) {
                    targetTable = firstText(destConfig, "table");
                }
            }
        }
        if (!StringUtils.hasText(targetTable)) return null;

        // Parse schema.table if present
        String schema = null;
        String tableName = targetTable.trim().toLowerCase();
        int dotIdx = tableName.indexOf('.');
        if (dotIdx > 0 && dotIdx < tableName.length() - 1) {
            schema = tableName.substring(0, dotIdx);
            tableName = tableName.substring(dotIdx + 1);
        }

        // Get file columns from source config
        JsonNode sourceConfig = task.getSourceConfig();
        if (sourceConfig == null || sourceConfig.isNull()) return null;
        JsonNode fileColumnsNode = sourceConfig.get("_fileColumns");
        if (fileColumnsNode == null || !fileColumnsNode.isArray() || fileColumnsNode.size() == 0) return null;
        boolean autoId = sourceConfig.has("_autoId") && sourceConfig.get("_autoId").asBoolean(false);

        StringBuilder ddl = new StringBuilder("CREATE TABLE IF NOT EXISTS ");
        if (StringUtils.hasText(schema) && !"public".equalsIgnoreCase(schema)) {
            ddl.append(pgQuote(schema)).append(".");
        }
        ddl.append(pgQuote(tableName)).append(" (");
        boolean first = true;
        if (autoId) {
            ddl.append("\"id\" bigserial primary key");
            first = false;
        }
        for (JsonNode col : fileColumnsNode) {
            String colName = col.has("safeName") ? col.get("safeName").asText("") : "";
            if (!StringUtils.hasText(colName)) {
                colName = col.has("name") ? col.get("name").asText("") : "";
            }
            if (!StringUtils.hasText(colName)) {
                colName = col.has("label") ? col.get("label").asText("") : "";
            }
            String colType = col.has("type") ? col.get("type").asText("string") : "string";
            if (!StringUtils.hasText(colName)) continue;
            if (!first) ddl.append(", ");
            first = false;
            ddl.append(pgQuote(colName.trim().toLowerCase()))
               .append(" ").append(mapFileTypeToPg(colType));
        }
        ddl.append(")");
        return ddl.toString();
    }

    private String pgQuote(String identifier) {
        if (!StringUtils.hasText(identifier)) return identifier;
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private String mapFileTypeToPg(String fileType) {
        if (!StringUtils.hasText(fileType)) return "text";
        return switch (fileType.trim().toLowerCase()) {
            case "long" -> "bigint";
            case "double" -> "double precision";
            case "date" -> "timestamp";
            case "boolean" -> "boolean";
            default -> "varchar(500)";
        };
    }

    private String sanitizeTag(String value, String fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        String tag = NON_SAFE.matcher(value.trim().toLowerCase()).replaceAll("-");
        tag = tag.replaceAll("^-+", "").replaceAll("-+$", "");
        return StringUtils.hasText(tag) ? tag : fallback;
    }
}
