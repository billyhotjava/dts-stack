package com.yuzhi.dts.ingestion.service.etl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.common.security.SecurityLevelCatalog;
import com.yuzhi.dts.ingestion.config.AddaxProperties;
import com.yuzhi.dts.ingestion.config.AirflowProperties;
import com.yuzhi.dts.ingestion.config.ApiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.yuzhi.dts.ingestion.domain.IngestionTask;
import com.yuzhi.dts.ingestion.service.infra.IngestionSettingsService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AirflowDagService {

    private static final Logger LOG = LoggerFactory.getLogger(AirflowDagService.class);
    private static final Pattern NON_SAFE = Pattern.compile("[^a-z0-9_]+");
    private static final Pattern SAFE_DAG_ID = Pattern.compile("[A-Za-z0-9_][A-Za-z0-9_.-]{0,199}");
    private static final ObjectMapper JSON = new ObjectMapper();

    private final AirflowProperties properties;
    private final AddaxProperties addaxProperties;
    private final IngestionSettingsService settingsService;
    private final AirflowClient airflowClient;
    private final org.springframework.core.env.Environment springEnv;
    private final ApiProperties apiProperties;

    /** A DAG rendered outside Airflow's *.py discovery surface until admission commits. */
    public record StagedDag(String dagId, Path stagedPath, Path finalPath) {}

    public AirflowDagService(
        AirflowProperties properties,
        AddaxProperties addaxProperties,
        IngestionSettingsService settingsService,
        AirflowClient airflowClient,
        org.springframework.core.env.Environment springEnv
    ) {
        this(properties, addaxProperties, settingsService, airflowClient, springEnv, new ApiProperties());
    }

    @Autowired
    public AirflowDagService(
        AirflowProperties properties,
        AddaxProperties addaxProperties,
        IngestionSettingsService settingsService,
        AirflowClient airflowClient,
        org.springframework.core.env.Environment springEnv,
        ApiProperties apiProperties
    ) {
        this.properties = properties;
        this.addaxProperties = addaxProperties;
        this.settingsService = settingsService;
        this.airflowClient = airflowClient;
        this.springEnv = springEnv;
        this.apiProperties = apiProperties == null ? new ApiProperties() : apiProperties;
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

    public String rebuildDagForTask(IngestionTask task, List<AddaxJobService.PerTableJob> perTableJobs) {
        return ensureDagForTask(task, perTableJobs, true);
    }

    public StagedDag stageDagForTask(IngestionTask task, List<AddaxJobService.PerTableJob> perTableJobs) {
        return stageDagForTask(task, perTableJobs, null, null);
    }

    public StagedDag stageDagForTask(
        IngestionTask task,
        List<AddaxJobService.PerTableJob> perTableJobs,
        Long revisionId,
        String effectiveConfigChecksum
    ) {
        if (task == null) {
            throw new IllegalArgumentException("Ingestion task is required");
        }
        String dagId = resolveDagId(task);
        if (revisionId != null) {
            dagId = bindDagIdToRevision(dagId, revisionId);
        }
        Path dagDir = resolveDagDir(task);
        if (dagDir == null) {
            throw new IllegalStateException("Airflow DAG directory is not configured");
        }
        try {
            Files.createDirectories(dagDir);
            Path finalPath = resolveDagFile(dagDir, dagId);
            Path stagedPath = dagDir.toAbsolutePath().normalize()
                .resolve("." + dagId + "." + UUID.randomUUID() + ".staged")
                .normalize();
            if (!stagedPath.getParent().equals(dagDir.toAbsolutePath().normalize())) {
                throw new IllegalArgumentException("Staged DAG must remain inside the configured DAG directory");
            }
            Files.writeString(
                stagedPath,
                buildStagedDagSource(dagId, task, perTableJobs, revisionId, effectiveConfigChecksum),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW
            );
            return new StagedDag(dagId, stagedPath, finalPath);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to stage Airflow DAG: " + ex.getMessage(), ex);
        }
    }

    public String publishStagedDag(StagedDag stagedDag) {
        if (stagedDag == null) {
            return null;
        }
        try {
            try {
                Files.move(
                    stagedDag.stagedPath(),
                    stagedDag.finalPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(stagedDag.stagedPath(), stagedDag.finalPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.setPosixFilePermissions(
                    stagedDag.finalPath(),
                    java.nio.file.attribute.PosixFilePermissions.fromString("rw-r--r--")
                );
            } catch (Exception ignored) {}
            return stagedDag.dagId();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to publish staged Airflow DAG: " + ex.getMessage(), ex);
        }
    }

    public void discardStagedDag(StagedDag stagedDag, boolean includePublishedFile) {
        if (stagedDag == null) {
            return;
        }
        try {
            Files.deleteIfExists(stagedDag.stagedPath());
            if (includePublishedFile) {
                Files.deleteIfExists(stagedDag.finalPath());
            }
        } catch (IOException ex) {
            LOG.warn("[airflow] failed to discard staged dag {}: {}", stagedDag.dagId(), ex.getMessage());
        }
    }

    public void discardStagedDagStrict(StagedDag stagedDag, boolean includePublishedFile) {
        if (stagedDag == null) {
            return;
        }
        try {
            Files.deleteIfExists(stagedDag.stagedPath());
            if (includePublishedFile) {
                Files.deleteIfExists(stagedDag.finalPath());
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to compensate staged DAG " + stagedDag.dagId() + ": " + ex.getMessage(), ex);
        }
    }

    public void setDagPausedStrict(String dagId, boolean paused) {
        airflowClient.setDagPausedStrict(dagId, paused);
    }

    public void retireDagStrict(String dagId, IngestionTask pathContext) {
        if (!StringUtils.hasText(dagId)) {
            return;
        }
        // Retirement is deliberately a strict pause, not deletion. Airflow may
        // still have queued/running DagRuns whose immutable revision contract is
        // embedded in this file. Keeping the paused definition lets those runs
        // finish while preventing any new schedule from being created.
        airflowClient.setDagPausedStrict(dagId, true);
    }

    private String buildStagedDagSource(
        String dagId,
        IngestionTask task,
        List<AddaxJobService.PerTableJob> perTableJobs,
        Long revisionId,
        String effectiveConfigChecksum
    ) {
        String source = buildDagSource(dagId, task, perTableJobs);
        source = source.replace("is_paused_upon_creation=False", "is_paused_upon_creation=True");
        if (!source.contains("is_paused_upon_creation=")) {
            source = source.replace("    catchup=False,", "    catchup=False,\n    is_paused_upon_creation=True,");
        }
        if (revisionId == null || !StringUtils.hasText(effectiveConfigChecksum)) {
            return source;
        }
        if (com.yuzhi.dts.ingestion.service.etl.api.ApiConnectorTypes.isApiSourceType(task.getSourceType())) {
            source = source.replace(
                "INGESTION_BASE_URL =",
                "INGESTION_REVISION_ID = " + revisionId + "\n" +
                "INGESTION_CONFIG_CHECKSUM = \"" + escapePythonString(effectiveConfigChecksum) + "\"\n" +
                "INGESTION_DAG_ID = \"" + escapePythonString(dagId) + "\"\n" +
                "INGESTION_BASE_URL ="
            );
            return source.replace(
                "\"taskId\": INGESTION_TASK_ID,",
                "\"taskId\": INGESTION_TASK_ID,\n" +
                "                    \"revisionId\": INGESTION_REVISION_ID,\n" +
                "                    \"configChecksum\": INGESTION_CONFIG_CHECKSUM,\n" +
                "                    \"airflowDagId\": INGESTION_DAG_ID,"
            );
        }

        String registration = """

            DTS_INGESTION_BASE_URL = os.getenv("DTS_INGESTION_INTERNAL_BASE_URL", "http://dts-ingestion:8083").rstrip("/")
            DTS_SERVICE_NAME = "dts-airflow"
            DTS_SERVICE_TOKEN = os.getenv("DTS_AIRFLOW_TO_INGESTION_TOKEN", "")
            DTS_REVISION_ID = %d
            DTS_CONFIG_CHECKSUM = "%s"
            DTS_DAG_ID = "%s"
            DTS_TASK_ID = %d


            def _register_dts_execution(context):
                payload = json.dumps({
                    "taskId": DTS_TASK_ID,
                    "revisionId": DTS_REVISION_ID,
                    "configChecksum": DTS_CONFIG_CHECKSUM,
                    "airflowDagId": DTS_DAG_ID,
                    "airflowRunId": context.get("run_id"),
                }).encode("utf-8")
                headers = {"Content-Type": "application/json", "X-DTS-Service": DTS_SERVICE_NAME}
                if DTS_SERVICE_TOKEN:
                    headers["X-DTS-Service-Token"] = DTS_SERVICE_TOKEN
                request = urllib.request.Request(
                    DTS_INGESTION_BASE_URL + "/internal/api-ingestion/scheduled-executions",
                    data=payload,
                    method="POST",
                    headers=headers,
                )
                with urllib.request.build_opener(urllib.request.ProxyHandler({})).open(request, timeout=30) as response:
                    if response.status < 200 or response.status >= 300:
                        raise RuntimeError(f"DTS_SCHEDULED_EXECUTION_REGISTER_FAILED: HTTP {response.status}")

            """.formatted(
                revisionId,
                escapePythonString(effectiveConfigChecksum),
                escapePythonString(dagId),
                task.getId() == null ? 0L : task.getId()
            );
        int dagBlock = source.indexOf("\nwith DAG(");
        if (dagBlock < 0) {
            throw new IllegalStateException("Generated DAG source has no DAG declaration");
        }
        source = source.substring(0, dagBlock) + registration + source.substring(dagBlock);
        return source.replace(
            "        tty=False,",
            "        on_execute_callback=_register_dts_execution,\n        tty=False,"
        );
    }

    public boolean deleteDagForTask(IngestionTask task) {
        if (task == null) {
            return false;
        }
        String dagId = resolveDagId(task);
        boolean deletedRemote = false;
        if (StringUtils.hasText(dagId)) {
            deletedRemote = airflowClient.deleteDag(dagId);
            if (deletedRemote) {
                LOG.info("[airflow] deleted dag {} via API", dagId);
            }
        }
        Path dagDir = resolveDagDir(task);
        if (dagDir == null) {
            return deletedRemote;
        }
        Path dagFile = resolveDagFile(dagDir, dagId);
        try {
            boolean deletedLocal = Files.deleteIfExists(dagFile);
            if (deletedLocal) {
                LOG.info("[airflow] deleted dag file {}", dagFile);
            }
            return deletedRemote || deletedLocal;
        } catch (IOException ex) {
            throw new IllegalStateException("AIRFLOW_DAG_FILE_DELETE_FAILED: " + dagId, ex);
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
        String previousDagId = resolveOwnedPreviousDagId(task);
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
        Path dagFile = resolveDagFile(dagDir, dagId);
        String content = buildDagSource(dagId, task, perTableJobs);
        boolean written = false;
        try {
            if (StringUtils.hasText(previousDagId) && !previousDagId.equals(dagId)) {
                Path previousFile = resolveDagFile(dagDir, previousDagId);
                try {
                    if (Files.deleteIfExists(previousFile)) {
                        LOG.info("[airflow] removed old dag file {}", previousFile);
                    }
                } catch (IOException ex) {
                    LOG.warn("[airflow] failed to delete old dag file {}: {}", previousFile, ex.getMessage());
                }
            }
            if (baseDir != null && !baseDir.equals(dagDir)) {
                Path legacyFile = resolveDagFile(baseDir, dagId);
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
            try { java.nio.file.Files.setPosixFilePermissions(dagFile, java.nio.file.attribute.PosixFilePermissions.fromString("rw-r--r--")); } catch (Exception ignored) {}
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
        String baseDagId;
        if (StringUtils.hasText(task.getAirflowDagId())) {
            baseDagId = requireSafeDagId(task.getAirflowDagId());
        } else {
            String layer = normalizeSegment(resolveLayerDir(task), "ods");
            String sourceKey = normalizeSegment(resolveSourceKey(task), "source");
            String desc = normalizeSegment(task != null ? task.getName() : null, "ingestion");
            String freq = normalizeSegment(resolveFrequency(task != null ? task.getSyncSchedule() : null), "manual");
            baseDagId = requireSafeDagId(limitLength(String.format("%s_%s_%s_%s", layer, sourceKey, desc, freq), 200));
        }
        return bindDagIdToTask(baseDagId, task == null ? null : task.getId());
    }

    private String bindDagIdToTask(String baseDagId, Long taskId) {
        String safeBase = requireSafeDagId(baseDagId);
        if (taskId == null) {
            return safeBase;
        }
        String suffix = "_task_" + taskId;
        if (safeBase.endsWith(suffix)) {
            return safeBase;
        }
        return requireSafeDagId(limitLength(safeBase, 200 - suffix.length()) + suffix);
    }

    private String bindDagIdToRevision(String baseDagId, Long revisionId) {
        String safeBase = requireSafeDagId(baseDagId);
        if (revisionId == null) {
            return safeBase;
        }
        String suffix = "_revision_" + revisionId;
        if (safeBase.endsWith(suffix)) {
            return safeBase;
        }
        String withoutPriorRevision = safeBase.replaceFirst("_revision_[0-9]+$", "");
        return requireSafeDagId(limitLength(withoutPriorRevision, 200 - suffix.length()) + suffix);
    }

    private String resolveOwnedPreviousDagId(IngestionTask task) {
        if (task == null || !StringUtils.hasText(task.getAirflowDagId())) {
            return null;
        }
        String previousDagId = requireSafeDagId(task.getAirflowDagId());
        if (task.getId() == null) {
            return previousDagId;
        }
        return previousDagId.endsWith("_task_" + task.getId()) ? previousDagId : null;
    }

    private String requireSafeDagId(String value) {
        String dagId = value == null ? "" : value.trim();
        if (!SAFE_DAG_ID.matcher(dagId).matches()) {
            throw new IllegalArgumentException("Invalid Airflow DAG ID");
        }
        return dagId;
    }

    private Path resolveDagFile(Path dagDir, String dagId) {
        Path normalizedDir = dagDir.toAbsolutePath().normalize();
        Path candidate = normalizedDir.resolve(requireSafeDagId(dagId) + ".py").normalize();
        if (!candidate.startsWith(normalizedDir) || !normalizedDir.equals(candidate.getParent())) {
            throw new IllegalArgumentException("Airflow DAG file must remain inside the configured DAG directory");
        }
        return candidate;
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
        String cron = schedule.trim().toLowerCase(Locale.ROOT);
        if ("manual".equals(cron)) {
            return "manual";
        }
        if (cron.startsWith("interval:")) {
            return "interval";
        }
        if (cron.startsWith("cron:")) {
            cron = cron.substring("cron:".length()).trim();
        }
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
        if (task != null && com.yuzhi.dts.ingestion.service.etl.api.ApiConnectorTypes.isApiSourceType(task.getSourceType())) {
            return buildApiDagSource(dagId, task);
        }
        String sourceTag = sanitizeTag(task == null ? null : task.getSourceType(), "source");
        String nameTag = sanitizeTag(task == null ? null : task.getName(), "ingestion");
        String addaxImage = escapePythonString(resolveAddaxImage());
        String addaxJobDir = escapePythonString(resolveAddaxJobDir());
        String defaultJobPath = escapePythonString(resolveDefaultJobPath(task));
        String scheduleLiteral = buildScheduleExpression(task == null ? null : task.getSyncSchedule());
        String taskId = resolveTaskId(task);
        String lineageSupport = buildOpenLineageSupportBlock(task, null);
        String lineageCallbacks = StringUtils.hasText(lineageSupport)
            ? "        on_success_callback=make_openlineage_callback(\"COMPLETE\", LINEAGE_DATASETS),\n" +
            "        on_failure_callback=make_openlineage_callback(\"FAIL\", LINEAGE_DATASETS),\n"
            : "";

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
                extraImports = "\nfrom airflow.operators.python import PythonOperator\nimport os\n";
                // Security: password MUST be supplied via env var DTS_TARGET_DB_PASSWORD at runtime.
                // No fallback string is embedded — KeyError fails the task fast if the env var is missing.
                // Provision the env var via docker-compose service definition or Airflow Connections.
                // host/port/dbname/user retain non-sensitive fallbacks for local debugging convenience.
                initTableBlock = """

    def _init_target_table():
        import psycopg2
        conn = psycopg2.connect(
            host=os.getenv("DTS_TARGET_DB_HOST", "%s"),
            port=int(os.getenv("DTS_TARGET_DB_PORT", "%d")),
            dbname=os.getenv("DTS_TARGET_DB_NAME", "%s"),
            user=os.getenv("DTS_TARGET_DB_USER", "%s"),
            password=os.environ["DTS_TARGET_DB_PASSWORD"],
        )
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
                    escapePythonString(createTableDdl)
                );
                dependencyBlock = "\n    init_table >> addax_run\n";
            }
        }

        return """
            from __future__ import annotations

            import os
            import json
            import urllib.request
            from datetime import datetime, timedelta

            from airflow import DAG
            from airflow.models import Variable
            from airflow.utils.template import literal
            from airflow.providers.docker.operators.docker import DockerOperator
            from docker.types import Mount
            %s
            ADDAX_IMAGE = os.getenv("ADDAX_IMAGE", "%s")
            ADDAX_JOB_DIR = os.getenv("ADDAX_JOB_DIR", "%s")
            ADDAX_LOG_DIR = os.getenv("ADDAX_LOG_DIR", "/opt/dts/logs/addax")
            ADDAX_DOCKER_NETWORK = os.getenv("ADDAX_DOCKER_NETWORK", "dts-core")
            ADDAX_DRIVER_DIR = os.getenv("ADDAX_DRIVER_DIR", "")
            ADDAX_DRIVER_JARS = os.getenv("ADDAX_DRIVER_JARS", "")
            DEFAULT_JOB_PATH = os.getenv("ADDAX_JOB_DEFAULT", "%s")
            %s
            %s


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
                run_cmd = "{{ dag_run.conf.get('job_path', '%s') }}"
            %s
                addax_run = DockerOperator(
                    task_id="%s",
                    image=ADDAX_IMAGE,
                    api_version="auto",
                    auto_remove=True,
                    docker_url="unix://var/run/docker.sock",
                    entrypoint="java",
                    command=[
                        "-cp",
                        literal(ADDAX_RUNNER_JAR),
                        ADDAX_RUNNER_CLASS,
                        run_cmd,
                    ],
                    network_mode=ADDAX_DOCKER_NETWORK,
                    mount_tmp_dir=False,
                    mounts=[
                        Mount(source=ADDAX_JOB_DIR, target="/opt/addax/jobs", type="bind"),
                        Mount(source=ADDAX_LOG_DIR, target="/opt/addax/log", type="bind"),
                        Mount(target="/decrypted", source=None, type="tmpfs", read_only=False),
                        *build_driver_mounts(),
                    ],
                    environment=build_addax_environment(),
            %s
                    tty=False,
                )
            %s
            """.formatted(extraImports, addaxImage, addaxJobDir, defaultJobPath, buildAddaxCredentialSupportBlock(), lineageSupport,
                dagId, scheduleLiteral, sourceTag, nameTag,
                defaultJobPath, initTableBlock, taskId, lineageCallbacks, dependencyBlock);
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
        String scheduleLiteral = buildScheduleExpression(task == null ? null : task.getSyncSchedule());

        StringBuilder sb = new StringBuilder();
        sb.append("from __future__ import annotations\n\n");
        sb.append("import os\n");
        sb.append("import json\n");
        sb.append("import urllib.request\n");
        sb.append("from datetime import datetime, timedelta\n\n");
        sb.append("from airflow import DAG\n");
        sb.append("from airflow.models import Variable\n");
        sb.append("from airflow.utils.template import literal\n");
        sb.append("from airflow.providers.docker.operators.docker import DockerOperator\n");
        sb.append("from docker.types import Mount\n\n");
        sb.append(String.format("ADDAX_IMAGE = os.getenv(\"ADDAX_IMAGE\", \"%s\")\n", addaxImage));
        sb.append(String.format("ADDAX_JOB_DIR = os.getenv(\"ADDAX_JOB_DIR\", \"%s\")\n", addaxJobDir));
        sb.append("ADDAX_LOG_DIR = os.getenv(\"ADDAX_LOG_DIR\", \"/opt/dts/logs/addax\")\n");
        sb.append("ADDAX_DOCKER_NETWORK = os.getenv(\"ADDAX_DOCKER_NETWORK\", \"dts-core\")\n");
        sb.append("ADDAX_DRIVER_DIR = os.getenv(\"ADDAX_DRIVER_DIR\", \"\")\n");
        sb.append("ADDAX_DRIVER_JARS = os.getenv(\"ADDAX_DRIVER_JARS\", \"\")\n\n\n");
        sb.append(buildAddaxCredentialSupportBlock());
        sb.append("\n");
        sb.append(buildOpenLineageSupportBlock(task, null));
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
            String lineageDatasets = pythonJsonLoads(lineageDatasetsJson(task, job.tableName()));

            sb.append(String.format("    %s = DockerOperator(\n", opTaskId));
            sb.append(String.format("        task_id=\"%s\",\n", opTaskId));
            sb.append("        image=ADDAX_IMAGE,\n");
            sb.append("        api_version=\"auto\",\n");
            sb.append("        auto_remove=True,\n");
            sb.append("        docker_url=\"unix://var/run/docker.sock\",\n");
            sb.append("        entrypoint=\"java\",\n");
            sb.append("        command=[\n");
            sb.append("            \"-cp\",\n");
            sb.append("            literal(ADDAX_RUNNER_JAR),\n");
            sb.append("            ADDAX_RUNNER_CLASS,\n");
            sb.append(String.format("            \"%s\",\n", jobPath));
            sb.append("        ],\n");
            sb.append("        network_mode=ADDAX_DOCKER_NETWORK,\n");
            sb.append("        mount_tmp_dir=False,\n");
            sb.append("        mounts=[\n");
        sb.append("            Mount(source=ADDAX_JOB_DIR, target=\"/opt/addax/jobs\", type=\"bind\"),\n");
        sb.append("            Mount(source=ADDAX_LOG_DIR, target=\"/opt/addax/log\", type=\"bind\"),\n");
            sb.append("            Mount(target=\"/decrypted\", source=None, type=\"tmpfs\", read_only=False),\n");
            sb.append("            *build_driver_mounts(),\n");
            sb.append("        ],\n");
            sb.append("        environment=build_addax_environment(),\n");
            if (StringUtils.hasText(lineageDatasets)) {
                sb.append(String.format("        on_success_callback=make_openlineage_callback(\"COMPLETE\", %s),\n", lineageDatasets));
                sb.append(String.format("        on_failure_callback=make_openlineage_callback(\"FAIL\", %s),\n", lineageDatasets));
            }
            sb.append("        tty=False,\n");
            sb.append("    )\n\n");
        }

        return sb.toString();
    }

    private String buildAddaxCredentialSupportBlock() {
        return """
            ADDAX_RUNNER_JAR = os.getenv("ADDAX_RUNNER_JAR", "/opt/addax/addax-env-runner.jar")
            ADDAX_RUNNER_CLASS = os.getenv("ADDAX_RUNNER_CLASS", "com.yuzhi.dts.addax.AddaxEnvRunner")


            def resolve_secret(primary_name, *fallback_names):
                for name in (primary_name, *fallback_names):
                    value = os.getenv(name)
                    if value:
                        return value
                    value = Variable.get(name, default_var="")
                    if value:
                        return value
                return ""


            def build_addax_environment():
                return {
                    # Sprint-37: 上传密文解密到 tmpfs(/decrypted) 供 Addax 读取；密钥经 Airflow worker 透传，明文不落盘
                    "TMPDIR": "/decrypted",
                    "DTS_INFRA_ENCRYPTION_KEY": os.environ.get("DTS_INFRA_ENCRYPTION_KEY", ""),
                    "DTS_INFRA_KEY_VERSION": os.environ.get("DTS_INFRA_KEY_VERSION", "v1"),
                    "DTS_ADDAX_READER_PASSWORD": resolve_secret(
                        "DTS_ADDAX_READER_PASSWORD",
                        "DTS_SOURCE_DB_PASSWORD",
                        "DTS_PTR_MYSQL_PASSWORD",
                    ),
                    "DTS_TARGET_DB_PASSWORD": resolve_secret(
                        "DTS_TARGET_DB_PASSWORD",
                        "DTS_ADDAX_WRITER_PASSWORD",
                    ),
                }

            """;
    }

    private String buildOpenLineageSupportBlock(IngestionTask task, String tableName) {
        String datasetsJson = lineageDatasetsJson(task, tableName);
        if (!StringUtils.hasText(datasetsJson)) {
            return "";
        }
        String openLineageUrl = escapePythonString(resolveOpenLineageUrl());
        return """

            OPENLINEAGE_URL = os.getenv("DTS_OPENLINEAGE_URL", "%s")
            OPENLINEAGE_SERVICE = os.getenv("DTS_OPENLINEAGE_SERVICE", "dts-airflow")
            OPENLINEAGE_ENABLED = os.getenv("DTS_OPENLINEAGE_ENABLED", "true").lower() not in ("0", "false", "no")
            LINEAGE_DATASETS = %s
            _DTS_INTERNAL_HTTP_OPENER = globals().get("_DTS_INTERNAL_HTTP_OPENER") or urllib.request.build_opener(urllib.request.ProxyHandler({}))


            def emit_openlineage_event(event_type, context, datasets):
                if not OPENLINEAGE_ENABLED or not OPENLINEAGE_URL:
                    return
                inputs = datasets.get("inputs") or []
                outputs = datasets.get("outputs") or []
                if not inputs or not outputs:
                    return
                dag_run = context.get("dag_run")
                run_id = getattr(dag_run, "run_id", None) or context.get("run_id") or context.get("ts_nodash")
                payload = {
                    "eventType": event_type,
                    "eventTime": context.get("ts"),
                    "run": {"runId": run_id},
                    "job": {"namespace": "airflow", "name": context["dag"].dag_id},
                    "inputs": inputs,
                    "outputs": outputs,
                }
                data = json.dumps(payload).encode("utf-8")
                request = urllib.request.Request(
                    OPENLINEAGE_URL,
                    data=data,
                    method="POST",
                    headers={
                        "Content-Type": "application/json",
                        "X-DTS-Service": OPENLINEAGE_SERVICE,
                    },
                )
                try:
                    _DTS_INTERNAL_HTTP_OPENER.open(request, timeout=5).read()
                except Exception as exc:
                    print(f"OpenLineage emit failed: {exc}")


            def make_openlineage_callback(event_type, datasets):
                def _callback(context):
                    emit_openlineage_event(event_type, context, datasets)
                return _callback

            """.formatted(openLineageUrl, pythonJsonLoads(datasetsJson));
    }

    private String lineageDatasetsJson(IngestionTask task, String tableName) {
        JsonNode tableMapping = task == null ? null : task.getTableMapping();
        if (tableMapping == null || !tableMapping.isArray() || tableMapping.isEmpty()) {
            return "";
        }
        List<Map<String, Object>> inputs = new ArrayList<>();
        List<Map<String, Object>> outputs = new ArrayList<>();
        String tableFilter = stripQualifier(tableName);
        Map<String, Object> classificationFacet = lineageClassificationFacet(task);
        for (JsonNode mapping : tableMapping) {
            if (mapping == null || !mapping.isObject()) {
                continue;
            }
            String source = firstText(mapping, "source", "sourceTable", "stream", "table");
            String target = firstText(mapping, "target", "targetTable", "ods", "dest");
            if (StringUtils.hasText(tableFilter) && !tableFilter.equalsIgnoreCase(stripQualifier(target)) && !tableFilter.equalsIgnoreCase(stripQualifier(source))) {
                continue;
            }
            Map<String, Object> input = lineageDataset(source, "source", classificationFacet);
            Map<String, Object> output = lineageDataset(target, "ods", classificationFacet);
            if (!input.isEmpty()) {
                inputs.add(input);
            }
            if (!output.isEmpty()) {
                outputs.add(output);
            }
        }
        if (inputs.isEmpty() || outputs.isEmpty()) {
            return "";
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("inputs", inputs);
        data.put("outputs", outputs);
        try {
            return JSON.writeValueAsString(data);
        } catch (Exception ex) {
            LOG.warn("[airflow] failed to serialize OpenLineage dataset mapping for task {}: {}", task != null ? task.getId() : null, ex.getMessage());
            return "";
        }
    }

    private Map<String, Object> lineageDataset(
        String qualifiedName,
        String fallbackNamespace,
        Map<String, Object> classificationFacet
    ) {
        String value = StringUtils.hasText(qualifiedName) ? qualifiedName.trim() : null;
        if (!StringUtils.hasText(value)) {
            return Map.of();
        }
        String namespace = fallbackNamespace;
        String name = value;
        int idx = value.lastIndexOf('.');
        if (idx > 0 && idx < value.length() - 1) {
            namespace = value.substring(0, idx);
            name = value.substring(idx + 1);
        }
        Map<String, Object> dataset = new LinkedHashMap<>();
        dataset.put("namespace", namespace);
        dataset.put("name", name);
        if (classificationFacet != null && !classificationFacet.isEmpty()) {
            dataset.put("facets", Map.of("dtsGovernance", classificationFacet));
        }
        return dataset;
    }

    private Map<String, Object> lineageClassificationFacet(IngestionTask task) {
        JsonNode seal = task == null ? null : task.getClassificationSeal();
        if (seal == null || !seal.isObject()) {
            return Map.of();
        }
        String effectiveLevel;
        try {
            effectiveLevel = SecurityLevelCatalog
                .requireDataLevel(seal.path("effectiveLevel").asText(null))
                .code();
        } catch (IllegalArgumentException ex) {
            return Map.of();
        }
        Map<String, Object> facet = new LinkedHashMap<>();
        facet.put("classification", effectiveLevel);
        copySealFacet(seal, facet, "sealId");
        copySealFacet(seal, facet, "subjectKey");
        copySealFacet(seal, facet, "snapshotVersion");
        copySealFacet(seal, facet, "checksum");
        return Map.copyOf(facet);
    }

    private void copySealFacet(JsonNode seal, Map<String, Object> facet, String field) {
        JsonNode value = seal.get(field);
        if (value == null || value.isNull()) {
            return;
        }
        if (value.isIntegralNumber()) {
            facet.put(field, value.longValue());
            return;
        }
        if (StringUtils.hasText(value.asText())) {
            facet.put(field, value.asText());
        }
    }

    private String pythonJsonLoads(String json) {
        if (!StringUtils.hasText(json)) {
            return "";
        }
        return "json.loads(r'''" + json.replace("'''", "\\'\\'\\'") + "''')";
    }

    private String resolveOpenLineageUrl() {
        IngestionSettingsService.SettingsSnapshot settings = settingsService.getSettings(IngestionSettingsService.SERVICE_PLATFORM);
        String baseUrl = settings.getString("baseUrl", "http://dts-platform:8081");
        String apiPath = settings.getString("apiPath", "/api");
        return joinUrl(joinUrl(baseUrl, apiPath), "/internal/lineage/openlineage");
    }

    private String joinUrl(String left, String right) {
        String normalizedLeft = StringUtils.hasText(left) ? left.trim() : "";
        String normalizedRight = StringUtils.hasText(right) ? right.trim() : "";
        if (!StringUtils.hasText(normalizedLeft)) {
            return normalizedRight;
        }
        if (!StringUtils.hasText(normalizedRight)) {
            return normalizedLeft;
        }
        return normalizedLeft.replaceAll("/+$", "") + "/" + normalizedRight.replaceAll("^/+", "");
    }

    private String stripQualifier(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value.trim();
        int idx = text.lastIndexOf('.');
        return idx >= 0 && idx < text.length() - 1 ? text.substring(idx + 1) : text;
    }

    private String buildScheduleExpression(String schedule) {
        if (!StringUtils.hasText(schedule)) {
            return "None";
        }
        String normalized = schedule.trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        if ("manual".equals(lower) || "none".equals(lower)) {
            return "None";
        }
        if (lower.startsWith("interval:")) {
            String intervalText = normalized.substring("interval:".length()).trim();
            try {
                int minutes = Integer.parseInt(intervalText);
                if (minutes > 0) {
                    return "timedelta(minutes=" + minutes + ")";
                }
            } catch (NumberFormatException ignored) {}
            LOG.warn("[airflow] invalid interval schedule {}, fallback to manual trigger", normalized);
            return "None";
        }
        if (lower.startsWith("cron:")) {
            String cron = normalized.substring("cron:".length()).trim();
            if (!StringUtils.hasText(cron)) {
                LOG.warn("[airflow] empty cron schedule {}, fallback to manual trigger", normalized);
                return "None";
            }
            return "\"" + escapePythonString(cron) + "\"";
        }
        return "\"" + escapePythonString(normalized) + "\"";
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

    private record FileSourceDbInfo(String host, int port, String dbname, String username) {}

    /**
     * Extract non-sensitive PostgreSQL connection info from the task's destination config.
     * Parses JDBC URL like jdbc:postgresql://host:port/dbname.
     * Password is intentionally NOT returned — credentials must be provisioned via env var
     * DTS_TARGET_DB_PASSWORD at runtime to prevent plaintext leakage into DAG files.
     */
    private FileSourceDbInfo extractFileSourceDbInfo(IngestionTask task) {
        JsonNode destConfig = task.getDestinationConfig();
        if (destConfig == null || destConfig.isNull()) {
            return new FileSourceDbInfo("localhost", 5432, "postgres", "postgres");
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
        // Fallback: try to read username from connection[0] (Addax writer config
        // stores it there when the top-level fields are missing). Password is intentionally
        // never extracted — it must be supplied via DTS_TARGET_DB_PASSWORD env var at runtime.
        if (!StringUtils.hasText(username)) {
            JsonNode conn = destConfig.get("connection");
            JsonNode connEntry = null;
            if (conn != null && conn.isArray() && conn.size() > 0) {
                connEntry = conn.get(0);
            } else if (conn != null && conn.isObject()) {
                connEntry = conn;
            }
            if (connEntry != null) {
                username = firstText(connEntry, "username");
            }
        }
        // Last resort: read username/jdbcUrl from the Addax job JSON file (writer.parameter)
        if (!StringUtils.hasText(jdbcUrl) || !StringUtils.hasText(username)) {
            JsonNode writerParam = readWriterParamFromJob(task.getAddaxJobPath());
            if (writerParam != null) {
                if (!StringUtils.hasText(jdbcUrl)) jdbcUrl = firstText(writerParam, "jdbcUrl");
                if (!StringUtils.hasText(username)) username = firstText(writerParam, "username");
                // Also check nested connection[0]
                JsonNode wpConn = writerParam.get("connection");
                JsonNode wpEntry = null;
                if (wpConn != null && wpConn.isArray() && !wpConn.isEmpty()) wpEntry = wpConn.get(0);
                else if (wpConn != null && wpConn.isObject()) wpEntry = wpConn;
                if (wpEntry != null) {
                    if (!StringUtils.hasText(jdbcUrl)) jdbcUrl = firstText(wpEntry, "jdbcUrl");
                    if (!StringUtils.hasText(username)) username = firstText(wpEntry, "username");
                }
            }
        }
        // Fallback: use spring.datasource username (data lake is typically the platform's own PG)
        if (!StringUtils.hasText(username)) {
            username = springEnv.getProperty("spring.datasource.username", "postgres");
        }

        // Fallback: use spring.datasource URL if no JDBC URL found
        if (!StringUtils.hasText(jdbcUrl)) {
            jdbcUrl = springEnv.getProperty("spring.datasource.url", "");
        }

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
        return new FileSourceDbInfo(host, port, dbname, username);
    }

    private static final ObjectMapper JOB_MAPPER = new ObjectMapper();

    /** Read writer.parameter from an Addax job JSON file. Returns null on any failure. */
    private JsonNode readWriterParamFromJob(String jobPath) {
        if (!StringUtils.hasText(jobPath)) return null;
        try {
            Path path = Path.of(jobPath);
            if (!Files.isReadable(path)) return null;
            JsonNode root = JOB_MAPPER.readTree(path.toFile());
            JsonNode content = root.at("/job/content");
            if (content.isMissingNode() || !content.isArray() || content.isEmpty()) return null;
            JsonNode writer = content.get(0).get("writer");
            if (writer == null) return null;
            JsonNode param = writer.get("parameter");
            return (param != null && !param.isNull()) ? param : null;
        } catch (Exception e) {
            LOG.debug("Could not read writer params from job {}: {}", jobPath, e.getMessage());
            return null;
        }
    }

    /**
     * Build non-destructive CREATE/ALTER DDL from file columns in the task's source config.
     * Addax validates columns during init() (before preSql), so missing columns must be
     * provisioned before the container starts without deleting the previous successful batch.
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

        // Build qualified table reference for non-destructive CREATE + ALTER.
        String qualifiedTable = (StringUtils.hasText(schema) && !"public".equalsIgnoreCase(schema))
            ? pgQuote(schema) + "." + pgQuote(tableName)
            : pgQuote(tableName);
        StringBuilder ddl = new StringBuilder();
        ddl.append("CREATE TABLE IF NOT EXISTS ").append(qualifiedTable).append(" (");
        List<String> ensureColumns = new java.util.ArrayList<>();
        boolean first = true;
        if (autoId) {
            ddl.append("\"id\" bigserial primary key");
            ensureColumns.add("ALTER TABLE " + qualifiedTable + " ADD COLUMN IF NOT EXISTS \"id\" bigserial");
            first = false;
        }
        Set<String> usedColumnNames = FileSourceColumnNames.reservedTechnicalNames();
        if (autoId) {
            usedColumnNames.add("id");
        }
        for (JsonNode col : fileColumnsNode) {
            String colName = FileSourceColumnNames.uniqueColumnName(
                FileSourceColumnNames.resolveName(col),
                usedColumnNames
            );
            String colType = col.has("type") ? col.get("type").asText("string") : "string";
            if (!StringUtils.hasText(colName)) continue;
            if (!first) ddl.append(", ");
            first = false;
            ddl.append(pgQuote(colName))
               .append(" ").append(mapFileTypeToPg(colType, col));
            ensureColumns.add(
                "ALTER TABLE " + qualifiedTable + " ADD COLUMN IF NOT EXISTS "
                    + pgQuote(colName) + " " + mapFileTypeToPg(colType, col)
            );
        }
        ddl.append(")");
        for (String ensureColumn : ensureColumns) {
            ddl.append(";\n").append(ensureColumn);
        }
        return ddl.toString();
    }

    private String pgQuote(String identifier) {
        if (!StringUtils.hasText(identifier)) return identifier;
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private String mapFileTypeToPg(String fileType, JsonNode col) {
        if (!StringUtils.hasText(fileType)) return "text";
        return switch (fileType.trim().toLowerCase()) {
            case "long", "bigint" -> "bigint";
            case "integer", "int" -> "integer";
            case "double" -> "double precision";
            case "numeric", "decimal" -> {
                int precision = col.has("precision") ? col.get("precision").asInt(18) : 18;
                int scale = col.has("scale") ? col.get("scale").asInt(2) : 2;
                yield "numeric(" + precision + "," + scale + ")";
            }
            case "date" -> "date";
            case "timestamp" -> "timestamp";
            case "boolean" -> "boolean";
            case "text" -> "text";
            case "jsonb" -> "jsonb";
            default -> {
                int length = col.has("length") ? col.get("length").asInt(500) : 500;
                yield "varchar(" + length + ")";
            }
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

    /**
     * Build a thin Airflow DAG for API ingestion.
     * Business runtime stays in dts-ingestion; Airflow only triggers and polls it.
     */
    private String buildApiDagSource(String dagId, IngestionTask task) {
        String taskName = task.getName() == null ? "" : task.getName();
        String scheduleLiteral = buildScheduleExpression(task.getSyncSchedule());
        String taskIdLiteral = resolveTaskId(task);
        long ingestionTaskId = task.getId() == null ? 0L : task.getId();
        long executionTimeoutSeconds = Math.max(1L, apiProperties.getExecutionTimeout().toSeconds());
        String lineageSupport = buildOpenLineageSupportBlock(task, null);
        String lineageCallbacks = StringUtils.hasText(lineageSupport)
            ? "                    on_success_callback=make_openlineage_callback(\"COMPLETE\", LINEAGE_DATASETS),\n" +
            "                    on_failure_callback=make_openlineage_callback(\"FAIL\", LINEAGE_DATASETS),\n"
            : "";

        return """
            from __future__ import annotations

            import json
            import os
            import time
            import urllib.error
            import urllib.request
            import uuid
            from datetime import datetime, timedelta

            from airflow import DAG
            from airflow.operators.python import PythonOperator

            INGESTION_TASK_ID = %d
            INGESTION_TASK_NAME = "%s"
            INGESTION_BASE_URL = os.getenv("DTS_INGESTION_INTERNAL_BASE_URL", "http://dts-ingestion:8083").rstrip("/")
            SERVICE_NAME = "dts-airflow"
            SERVICE_TOKEN = os.getenv("DTS_AIRFLOW_TO_INGESTION_TOKEN", "")
            POLL_INTERVAL_SECONDS = int(os.getenv("DTS_API_INGESTION_POLL_INTERVAL_SECONDS", "5"))
            POLL_TIMEOUT_SECONDS = int(os.getenv("DTS_API_INGESTION_POLL_TIMEOUT_SECONDS", "%d"))
            _DTS_INTERNAL_HTTP_OPENER = urllib.request.build_opener(urllib.request.ProxyHandler({}))
            %s


            def _dag_conf(context, key):
                run = context.get("dag_run") if isinstance(context, dict) else None
                conf = getattr(run, "conf", None) or {}
                return conf.get(key)


            def _call_ingestion_api(method, path, payload=None):
                headers = {
                    "Content-Type": "application/json",
                    "X-DTS-Service": SERVICE_NAME,
                }
                if SERVICE_TOKEN:
                    headers["X-DTS-Service-Token"] = SERVICE_TOKEN
                body = json.dumps(payload).encode("utf-8") if payload is not None else None
                request = urllib.request.Request(
                    INGESTION_BASE_URL + path,
                    data=body,
                    method=method,
                    headers=headers,
                )
                try:
                    with _DTS_INTERNAL_HTTP_OPENER.open(request, timeout=30) as response:
                        raw = response.read().decode("utf-8")
                        return json.loads(raw) if raw else {}
                except urllib.error.HTTPError as ex:
                    raw = ex.read().decode("utf-8", "ignore")
                    raise RuntimeError(f"DTS_API_INGESTION_HTTP_{ex.code}: {raw}") from ex


            def _copy_conf(payload, context, field, *aliases):
                for key in (field, *aliases):
                    value = _dag_conf(context, key)
                    if value is not None:
                        payload[field] = value
                        return


            def _trigger_api_ingestion(**context):
                batch_id = _dag_conf(context, "batchId") or _dag_conf(context, "batch_id") or str(uuid.uuid4())
                payload = {
                    "taskId": INGESTION_TASK_ID,
                    "batchId": batch_id,
                    "airflowRunId": context.get("run_id"),
                    "logicalDate": str(context.get("logical_date") or ""),
                }
                _copy_conf(payload, context, "mode")
                _copy_conf(payload, context, "backfillWindowStart", "backfill_window_start")
                _copy_conf(payload, context, "backfillWindowEnd", "backfill_window_end")
                _copy_conf(payload, context, "backfillCursorColumn", "backfill_cursor_column")

                response = _call_ingestion_api("POST", "/internal/api-ingestion/executions", payload)
                execution_id = response.get("id") or response.get("executionId")
                if not execution_id:
                    raise RuntimeError("DTS_API_INGESTION_EXECUTION_ID_MISSING")

                deadline = time.time() + POLL_TIMEOUT_SECONDS
                last_status = "UNKNOWN"
                while time.time() < deadline:
                    state = _call_ingestion_api("GET", f"/internal/api-ingestion/executions/{execution_id}")
                    last_status = str(state.get("status") or "").upper()
                    if last_status in ("SUCCEEDED", "SUCCESS", "COMPLETED", "DONE"):
                        return state
                    if last_status in ("FAILED", "ERROR", "CANCELED", "CANCELLED"):
                        message = state.get("errorMessage") or state.get("message") or last_status
                        raise RuntimeError(f"DTS_API_INGESTION_FAILED: {message}")
                    time.sleep(POLL_INTERVAL_SECONDS)
                raise TimeoutError(f"DTS_API_INGESTION_TIMEOUT: execution={execution_id} status={last_status}")


            with DAG(
                dag_id="%s",
                description="API ingestion runtime: " + INGESTION_TASK_NAME,
                start_date=datetime(2024, 1, 1),
                schedule=%s,
                catchup=False,
                max_active_runs=1,
                default_args={
                    "owner": "dts-ingestion",
                    "retries": 0,
                    "execution_timeout": timedelta(seconds=%d),
                },
                tags=["dts", "ingestion", "api"],
            ) as dag:
                api_run = PythonOperator(
                    task_id="%s",
                    python_callable=_trigger_api_ingestion,
            %s
                )
            """.formatted(
                ingestionTaskId,
                escapePythonString(taskName),
                executionTimeoutSeconds,
                lineageSupport,
                escapePythonString(dagId),
                scheduleLiteral,
                executionTimeoutSeconds,
                escapePythonString(taskIdLiteral),
                lineageCallbacks
            );
    }

}
