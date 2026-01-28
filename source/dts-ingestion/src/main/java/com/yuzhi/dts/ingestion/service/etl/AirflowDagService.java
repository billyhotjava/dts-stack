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

    public AirflowDagService(AirflowProperties properties, AddaxProperties addaxProperties, IngestionSettingsService settingsService) {
        this.properties = properties;
        this.addaxProperties = addaxProperties;
        this.settingsService = settingsService;
    }

    public String ensureDagForTask(IngestionTask task) {
        return ensureDagForTask(task, false);
    }

    public String rebuildDagForTask(IngestionTask task) {
        return ensureDagForTask(task, true);
    }

    private String ensureDagForTask(IngestionTask task, boolean force) {
        if (task == null) {
            return null;
        }
        String dagId = resolveDagId(task);
        String dagsDir = resolveDagsDir();
        if (!StringUtils.hasText(dagsDir)) {
            LOG.warn("[airflow] dagsDir not configured, skip DAG file generation for dagId={}", dagId);
            return dagId;
        }
        Path dir = Path.of(dagsDir);
        try {
            Files.createDirectories(dir);
        } catch (IOException ex) {
            LOG.warn("[airflow] failed to create dagsDir {}: {}", dagsDir, ex.getMessage());
            return dagId;
        }
        Path dagFile = dir.resolve(dagId + ".py");
        String content = buildDagSource(dagId, task);
        try {
            if (!force && Files.exists(dagFile)) {
                String existing = Files.readString(dagFile);
                if (existing.equals(content)) {
                    return dagId;
                }
            }
            Files.writeString(dagFile, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            LOG.info("[airflow] {} dag file {}", force ? "rebuilt" : "ensured", dagFile);
        } catch (IOException ex) {
            LOG.warn("[airflow] failed to write dag file {}: {}", dagFile, ex.getMessage());
        }
        return dagId;
    }

    private String resolveDagsDir() {
        IngestionSettingsService.SettingsSnapshot settings = settingsService.getSettings(IngestionSettingsService.SERVICE_AIRFLOW);
        String configured = settings.getString("dagsDir", null);
        String fallback = properties.getDagsDir();
        String normalized = normalizePath(configured);
        if (StringUtils.hasText(normalized) && Files.exists(Path.of(normalized))) {
            return normalized;
        }
        if (StringUtils.hasText(fallback)) {
            if (StringUtils.hasText(configured)) {
                LOG.warn("[airflow] dagsDir {} not found, fallback to {}", configured, fallback);
            }
            return fallback;
        }
        return normalized;
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
        String sourceKey = resolveSourceKey(task);
        if (StringUtils.hasText(sourceKey)) {
            return "ingestion_" + slugify(sourceKey);
        }
        String base = StringUtils.hasText(task.getName()) ? task.getName() : "task";
        String slug = slugify(base);
        String suffix = task.getId() == null ? "new" : String.valueOf(task.getId());
        return "ingestion_" + slug + "_" + suffix;
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

    private String buildDagSource(String dagId, IngestionTask task) {
        String sourceTag = sanitizeTag(task == null ? null : task.getSourceType(), "source");
        String nameTag = sanitizeTag(task == null ? null : task.getName(), "ingestion");
        String addaxImage = escapePythonString(resolveAddaxImage());
        String addaxJobDir = escapePythonString(resolveAddaxJobDir());
        return """
            from __future__ import annotations

            import os
            from datetime import datetime

            from airflow import DAG
            from airflow.providers.docker.operators.docker import DockerOperator
            from docker.types import Mount

            ADDAX_IMAGE = os.getenv("ADDAX_IMAGE", "%s")
            ADDAX_JOB_DIR = os.getenv("ADDAX_JOB_DIR", "%s")
            ADDAX_DRIVER_DIR = os.getenv("ADDAX_DRIVER_DIR", "")
            ADDAX_DRIVER_JARS = os.getenv("ADDAX_DRIVER_JARS", "")


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
                schedule=None,
                start_date=datetime(2024, 1, 1),
                catchup=False,
                tags=["addax", "etl", "ingestion", "%s", "%s"],
            ) as dag:
                run_cmd = [
                    "sh",
                    "-lc",
                    "/opt/addax/bin/addax.sh {{ dag_run.conf.get('job_path', '/opt/addax/jobs/job.json') }}",
                ]

                addax_run = DockerOperator(
                    task_id="addax_run",
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
            """.formatted(addaxImage, addaxJobDir, dagId, sourceTag, nameTag);
    }

    private String resolveAddaxJobDir() {
        IngestionSettingsService.SettingsSnapshot settings = settingsService.getSettings(IngestionSettingsService.SERVICE_ADDAX);
        String configured = settings.getString("jobDir", null);
        String fallback = addaxProperties.getJobDir();
        return normalizePath(configured, fallback != null ? fallback : "/opt/airflow/dags");
    }

    private String resolveAddaxImage() {
        IngestionSettingsService.SettingsSnapshot settings = settingsService.getSettings(IngestionSettingsService.SERVICE_ADDAX);
        String configured = settings.getString("image", null);
        String fallback = addaxProperties.getImage();
        return StringUtils.hasText(configured) ? configured.trim() : (StringUtils.hasText(fallback) ? fallback.trim() : "quay.io/wgzhao/addax:6.0.8");
    }

    private String escapePythonString(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
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
