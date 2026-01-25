package com.yuzhi.dts.ingestion.service.etl;

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
    private final IngestionSettingsService settingsService;

    public AirflowDagService(AirflowProperties properties, IngestionSettingsService settingsService) {
        this.properties = properties;
        this.settingsService = settingsService;
    }

    public String ensureDagForTask(IngestionTask task) {
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
            if (Files.exists(dagFile)) {
                String existing = Files.readString(dagFile);
                if (existing.equals(content)) {
                    return dagId;
                }
            }
            Files.writeString(dagFile, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            LOG.info("[airflow] ensured dag file {}", dagFile);
        } catch (IOException ex) {
            LOG.warn("[airflow] failed to write dag file {}: {}", dagFile, ex.getMessage());
        }
        return dagId;
    }

    private String resolveDagsDir() {
        IngestionSettingsService.SettingsSnapshot settings = settingsService.getSettings(IngestionSettingsService.SERVICE_AIRFLOW);
        return settings.getString("dagsDir", properties.getDagsDir());
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
        String explicit = firstText(config, "sourceSystem", "sourceApp", "appCode", "system", "app", "name");
        if (StringUtils.hasText(explicit)) {
            return explicit;
        }
        String host = firstText(config, "host");
        String database = firstText(config, "database");
        if (StringUtils.hasText(host)) {
            return StringUtils.hasText(database) ? host + "_" + database : host;
        }
        String jdbcUrl = extractJdbcUrl(config);
        if (StringUtils.hasText(jdbcUrl)) {
            return jdbcUrl;
        }
        return null;
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

    private String extractJdbcUrl(JsonNode config) {
        String direct = textValue(config.get("jdbcUrl"));
        if (StringUtils.hasText(direct)) {
            return direct;
        }
        JsonNode connection = config.get("connection");
        if (connection != null && connection.isArray()) {
            for (JsonNode item : connection) {
                if (item == null || item.isNull()) {
                    continue;
                }
                String candidate = textValue(item.get("jdbcUrl"));
                if (StringUtils.hasText(candidate)) {
                    return candidate;
                }
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
        return """
            from __future__ import annotations

            import os
            from datetime import datetime

            from airflow import DAG
            from airflow.providers.docker.operators.docker import DockerOperator
            from docker.types import Mount

            ADDAX_IMAGE = os.getenv("ADDAX_IMAGE", "wgzhao/addax:0.59.1")
            ADDAX_JOB_DIR = os.getenv("ADDAX_JOB_DIR", "/opt/prod/s10/dts-stack/services/dts-addax/jobs")


            with DAG(
                dag_id="%s",
                schedule=None,
                start_date=datetime(2024, 1, 1),
                catchup=False,
                tags=["addax", "etl", "ingestion", "%s", "%s"],
            ) as dag:
                run_cmd = [
                    "python",
                    "/opt/addax/bin/addax.py",
                    "{{ dag_run.conf.get('job_path', '/opt/addax/jobs/job.json') }}",
                ]

                addax_run = DockerOperator(
                    task_id="addax_run",
                    image=ADDAX_IMAGE,
                    api_version="auto",
                    auto_remove=True,
                    docker_url="unix://var/run/docker.sock",
                    command=run_cmd,
                    mounts=[
                        Mount(source=ADDAX_JOB_DIR, target="/opt/addax/jobs", type="bind"),
                    ],
                    environment={},
                    tty=True,
                )
            """.formatted(dagId, sourceTag, nameTag);
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
