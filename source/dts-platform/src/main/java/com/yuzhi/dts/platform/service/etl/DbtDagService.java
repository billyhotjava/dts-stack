package com.yuzhi.dts.platform.service.etl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.config.AirflowProperties;
import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class DbtDagService {

    private static final Logger LOG = LoggerFactory.getLogger(DbtDagService.class);
    private static final Pattern NON_SAFE = Pattern.compile("[^a-z0-9_]+");

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

    public String extractTag(String selector) {
        if (!StringUtils.hasText(selector)) {
            return null;
        }
        String trimmed = selector.trim();
        String normalized = trimmed.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("tag:")) {
            return trimmed.substring(4).trim();
        }
        // 不再支持 tab: 这个 typo，只支持标准的 tag:
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

        // 从配置读取 Docker 网络和权限设置
        String dockerNetwork = airflowProperties.getDockerNetwork();
        boolean dockerPrivileged = airflowProperties.isDockerPrivileged();
        String networkMode = StringUtils.hasText(dockerNetwork) ? dockerNetwork : "dts-core";
        String privilegedStr = dockerPrivileged ? "True" : "False";

        return """
            from __future__ import annotations

            import os
            from datetime import datetime

            from airflow import DAG
            from airflow.operators.bash import BashOperator
            from airflow.providers.docker.operators.docker import DockerOperator
            from docker.types import Mount

            DBT_IMAGE = os.getenv("DBT_IMAGE", "dts-dbt:1.10.0")
            DBT_PROJECT_DIR = os.getenv("DBT_PROJECT_DIR", "%s")
            DBT_PROFILES_DIR = os.getenv("DBT_PROFILES_DIR", "%s")
            DBT_PROJECT_MOUNT = os.getenv("DBT_PROJECT_MOUNT", "/opt/dbt")
            DBT_PROFILES_MOUNT = os.getenv("DBT_PROFILES_MOUNT", "/root/.dbt")
            DTS_PLATFORM_BASE_URL = os.getenv("DTS_PLATFORM_BASE_URL", "http://dts-platform:8081")
            DTS_PLATFORM_SYNC_PATH = os.getenv("DTS_PLATFORM_SYNC_PATH", "/api/etl/dbt/models/sync")
            DTS_PLATFORM_SERVICE = os.getenv("DTS_PLATFORM_SERVICE", "dts-airflow")


            def build_command():
                selector = "{{ dag_run.conf.get('models', '%s') }}"
                target = "{{ dag_run.conf.get('target', '%s') }}"
                return ["run", "--project-dir", DBT_PROJECT_MOUNT, "--profiles-dir", DBT_PROFILES_MOUNT, "--select", selector, "--target", target]


            with DAG(
                dag_id="%s",
                schedule=None,
                start_date=datetime(2024, 1, 1),
                catchup=False,
                tags=["dbt", "transform", "%s", "%s"],
            ) as dag:
                dbt_run = DockerOperator(
                    task_id="dbt_run",
                    image=DBT_IMAGE,
                    api_version="auto",
                    auto_remove=True,
                    docker_url="unix://var/run/docker.sock",
                    network_mode="%s",
                    command=build_command(),
                    mount_tmp_dir=False,
                    mounts=[
                        Mount(source=DBT_PROJECT_DIR, target=DBT_PROJECT_MOUNT, type="bind"),
                        Mount(source=DBT_PROFILES_DIR, target=DBT_PROFILES_MOUNT, type="bind"),
                    ],
                    environment={"DBT_USE_EXPERIMENTAL_PARSER": "false"},
                    tty=True,
                    privileged=%s,
                )

                sync_models = BashOperator(
                    task_id="sync_models",
                    bash_command=(
                        f"curl -sSf -X POST "
                        f"-H \\"Content-Type: application/json\\" "
                        f"-H \\"X-DTS-Service: {DTS_PLATFORM_SERVICE}\\" "
                        f"\\"{DTS_PLATFORM_BASE_URL}{DTS_PLATFORM_SYNC_PATH}\\" "
                        f"|| true"
                    ),
                    trigger_rule="all_done",
                )

                dbt_run >> sync_models
            """.formatted(fallbackProject, fallbackProfiles, selector, fallbackTarget, dagId, layerTag, tagLabel, networkMode, privilegedStr);
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
}
