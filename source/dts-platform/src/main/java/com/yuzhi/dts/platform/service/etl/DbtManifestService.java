package com.yuzhi.dts.platform.service.etl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.config.DbtProperties;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DbtManifestService {

    private static final Logger LOG = LoggerFactory.getLogger(DbtManifestService.class);

    private final ObjectMapper objectMapper;
    private final DbtProperties properties;
    private final DbtConfigService configService;

    public DbtManifestService(ObjectMapper objectMapper, DbtProperties properties, DbtConfigService configService) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.configService = configService;
    }

    public DbtModelResult listModels() {
        if (!properties.isEnabled()) {
            return DbtModelResult.disabled("dbt 未启用");
        }
        String projectDir = configService.loadConfig().config() != null
            ? configService.loadConfig().config().projectDir()
            : properties.getProjectDir();
        String manifestPath = StringUtils.hasText(projectDir) ? projectDir + "/target/manifest.json" : null;
        if (!StringUtils.hasText(manifestPath)) {
            return DbtModelResult.empty("dbt 项目目录未配置");
        }
        File file = new File(manifestPath);
        if (!file.exists()) {
            return DbtModelResult.empty("manifest.json 不存在，请先执行 dbt run/docs");
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(file, new TypeReference<>() {});
            Object nodesObj = raw.get("nodes");
            if (!(nodesObj instanceof Map<?, ?> nodes)) {
                return DbtModelResult.empty("manifest.json 缺少 nodes");
            }
            List<DbtModelSummary> models = new ArrayList<>();
            for (Map.Entry<?, ?> entry : nodes.entrySet()) {
                if (!(entry.getValue() instanceof Map<?, ?> node)) continue;
                String resourceType = stringVal(node.get("resource_type"));
                if (!"model".equalsIgnoreCase(resourceType)) continue;
                String name = stringVal(node.get("name"));
                String uniqueId = stringVal(node.get("unique_id"));
                String database = stringVal(node.get("database"));
                String schema = stringVal(node.get("schema"));
                String alias = stringVal(node.get("alias"));
                String path = stringVal(node.get("path"));
                if (!StringUtils.hasText(name)) continue;
                models.add(new DbtModelSummary(uniqueId, name, alias, database, schema, path));
            }
            models.sort(Comparator.comparing(DbtModelSummary::name));
            return DbtModelResult.of(models);
        } catch (Exception ex) {
            LOG.warn("Failed to parse dbt manifest: {}", ex.getMessage());
            return DbtModelResult.empty("解析 manifest.json 失败");
        }
    }

    private String stringVal(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    public record DbtModelSummary(
        String uniqueId,
        String name,
        String alias,
        String database,
        String schema,
        String path
    ) {}

    public record DbtModelResult(boolean enabled, List<DbtModelSummary> models, String message) {
        public static DbtModelResult disabled(String message) {
            return new DbtModelResult(false, List.of(), message);
        }

        public static DbtModelResult empty(String message) {
            return new DbtModelResult(true, List.of(), message);
        }

        public static DbtModelResult of(List<DbtModelSummary> models) {
            return new DbtModelResult(true, models, null);
        }
    }
}
