package com.yuzhi.dts.platform.service.etl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.config.DbtProperties;
import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import com.yuzhi.dts.platform.service.infra.InfraSecretService;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DbtConfigService {

    private static final Logger LOG = LoggerFactory.getLogger(DbtConfigService.class);

    private final ObjectMapper objectMapper;
    private final InfraDataSourceRepository dataSourceRepository;
    private final InfraSecretService secretService;
    private final DbtProperties properties;

    public DbtConfigService(
        ObjectMapper objectMapper,
        InfraDataSourceRepository dataSourceRepository,
        InfraSecretService secretService,
        DbtProperties properties
    ) {
        this.objectMapper = objectMapper;
        this.dataSourceRepository = dataSourceRepository;
        this.secretService = secretService;
        this.properties = properties;
    }

    @PostConstruct
    public void ensureConfigFile() {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            Path path = configPath();
            if (!Files.exists(path)) {
                Files.createDirectories(path.getParent());
                DbtWorkspaceConfig config = new DbtWorkspaceConfig(
                    true,
                    properties.getProjectDir(),
                    properties.getProfilesDir(),
                    "dts",
                    "dev",
                    null,
                    null,
                    null,
                    Collections.emptyMap()
                );
                writeConfig(config);
            }
        } catch (IOException ex) {
            LOG.warn("Failed to initialize dbt config file: {}", ex.getMessage());
        }
    }

    public DbtConfigView loadConfig() {
        if (!properties.isEnabled()) {
            return DbtConfigView.disabled("dbt 配置未启用");
        }
        DbtWorkspaceConfig config = readConfig();
        DbtProfileStatus profileStatus = buildProfile(config);
        InfraDataSourceDto target = resolveTarget(config.targetDataSourceId());
        DbtWorkspaceStatus workspaceStatus = validateWorkspace(config, false);
        return new DbtConfigView(true, config, profileStatus, target, workspaceStatus);
    }

    public DbtConfigView saveConfig(DbtWorkspaceConfigRequest request) {
        if (!properties.isEnabled()) {
            return DbtConfigView.disabled("dbt 配置未启用");
        }
        DbtWorkspaceConfig config = new DbtWorkspaceConfig(
            true,
            safeText(request.projectDir(), properties.getProjectDir()),
            safeText(request.profilesDir(), properties.getProfilesDir()),
            safeText(request.profileName(), "dts"),
            safeText(request.targetName(), "dev"),
            request.targetDataSourceId(),
            safeText(request.database(), null),
            safeText(request.schema(), null),
            request.vars() == null ? Collections.emptyMap() : request.vars()
        );
        DbtWorkspaceStatus workspaceStatus = validateWorkspace(config, true);
        if (!workspaceStatus.ok()) {
            throw new IllegalArgumentException(workspaceStatus.message());
        }
        writeConfig(config);
        DbtProfileStatus profileStatus = buildProfile(config);
        InfraDataSourceDto target = resolveTarget(config.targetDataSourceId());
        return new DbtConfigView(true, config, profileStatus, target, workspaceStatus);
    }

    private DbtProfileStatus buildProfile(DbtWorkspaceConfig config) {
        if (config == null || !StringUtils.hasText(config.profilesDir()) || config.targetDataSourceId() == null) {
            return DbtProfileStatus.skipped("未选择目标数仓或 profiles 目录");
        }
        InfraDataSource source = dataSourceRepository.findById(config.targetDataSourceId()).orElse(null);
        if (source == null) {
            return DbtProfileStatus.skipped("目标数仓不存在");
        }
        Map<String, Object> secrets = secretService.readSecrets(source);
        String password = secrets == null ? null : stringValue(secrets.get("password"));
        JdbcEndpoint endpoint = JdbcEndpoint.parse(source.getJdbcUrl());
        if (endpoint == null) {
            return DbtProfileStatus.skipped("暂不支持的 JDBC 地址格式");
        }
        String adapter = resolveAdapter(source.getType());
        if (!StringUtils.hasText(adapter)) {
            return DbtProfileStatus.skipped("暂不支持的数据库类型");
        }
        endpoint = endpoint.withDefaultPort(defaultPort(adapter));

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("type", adapter);
        output.put("host", endpoint.host());
        output.put("port", endpoint.port());
        output.put("user", safeText(source.getUsername(), ""));
        output.put("password", safeText(password, ""));
        output.put("dbname", safeText(config.database(), endpoint.database()));
        output.put("schema", safeText(config.schema(), endpoint.database() == null ? "public" : endpoint.database()));
        output.put("threads", 4);

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("target", config.targetName());
        profile.put("outputs", Map.of(config.targetName(), output));

        Map<String, Object> root = Map.of(config.profileName(), profile);
        try {
            Path profilesDir = Path.of(config.profilesDir());
            Files.createDirectories(profilesDir);
            Path profileFile = profilesDir.resolve("profiles.yml");
            String yaml = YamlWriter.toYaml(root);
            Files.writeString(profileFile, yaml, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return DbtProfileStatus.success(profileFile.toString());
        } catch (IOException ex) {
            return DbtProfileStatus.skipped("写入 profiles.yml 失败: " + ex.getMessage());
        }
    }

    private InfraDataSourceDto resolveTarget(UUID targetId) {
        if (targetId == null) return null;
        return dataSourceRepository
            .findById(targetId)
            .map(
                source ->
                    new InfraDataSourceDto(
                        source.getId(),
                        source.getName(),
                        source.getType(),
                        source.getJdbcUrl(),
                        source.getUsername()
                    )
            )
            .orElse(null);
    }

    private DbtWorkspaceConfig readConfig() {
        Path path = configPath();
        if (!Files.exists(path)) {
            return new DbtWorkspaceConfig(
                true,
                properties.getProjectDir(),
                properties.getProfilesDir(),
                "dts",
                "dev",
                null,
                null,
                null,
                Collections.emptyMap()
            );
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(path.toFile(), new TypeReference<>() {});
            return DbtWorkspaceConfig.from(raw, properties);
        } catch (Exception ex) {
            LOG.warn("Failed to read dbt config: {}", ex.getMessage());
            return new DbtWorkspaceConfig(
                true,
                properties.getProjectDir(),
                properties.getProfilesDir(),
                "dts",
                "dev",
                null,
                null,
                null,
                Collections.emptyMap()
            );
        }
    }

    private void writeConfig(DbtWorkspaceConfig config) {
        try {
            Path path = configPath();
            Files.createDirectories(path.getParent());
            Map<String, Object> payload = config.toMap();
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), payload);
        } catch (Exception ex) {
            LOG.warn("Failed to write dbt config: {}", ex.getMessage());
        }
    }

    private Path configPath() {
        String path = properties.getConfigPath();
        return Path.of(StringUtils.hasText(path) ? path : "/opt/dts/upload/dbt-config.json");
    }

    private String resolveAdapter(String type) {
        String normalized = type == null ? "" : type.trim().toLowerCase();
        if (normalized.contains("postgres")) return "postgres";
        if (normalized.contains("mysql")) return "mysql";
        return null;
    }

    private String safeText(String value, String fallback) {
        if (!StringUtils.hasText(value)) return fallback;
        return value.trim();
    }

    private String stringValue(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    public record DbtWorkspaceConfig(
        boolean enabled,
        String projectDir,
        String profilesDir,
        String profileName,
        String targetName,
        UUID targetDataSourceId,
        String database,
        String schema,
        Map<String, Object> vars
    ) {
        public static DbtWorkspaceConfig from(Map<String, Object> raw, DbtProperties properties) {
            if (raw == null) {
                return new DbtWorkspaceConfig(
                    true,
                    properties.getProjectDir(),
                    properties.getProfilesDir(),
                    "dts",
                    "dev",
                    null,
                    null,
                    null,
                    Collections.emptyMap()
                );
            }
            UUID targetId = null;
            Object targetObj = raw.get("targetDataSourceId");
            if (targetObj != null) {
                try {
                    targetId = UUID.fromString(String.valueOf(targetObj));
                } catch (Exception ignored) {}
            }
            Map<String, Object> vars;
            if (raw.get("vars") instanceof Map<?, ?> map) {
                vars = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getKey() != null) {
                        vars.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                }
            } else {
                vars = Collections.emptyMap();
            }
            return new DbtWorkspaceConfig(
                true,
                stringVal(raw.get("projectDir"), properties.getProjectDir()),
                stringVal(raw.get("profilesDir"), properties.getProfilesDir()),
                stringVal(raw.get("profileName"), "dts"),
                stringVal(raw.get("targetName"), "dev"),
                targetId,
                stringVal(raw.get("database"), null),
                stringVal(raw.get("schema"), null),
                vars
            );
        }

        public Map<String, Object> toMap() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("enabled", enabled);
            payload.put("projectDir", projectDir);
            payload.put("profilesDir", profilesDir);
            payload.put("profileName", profileName);
            payload.put("targetName", targetName);
            payload.put("targetDataSourceId", targetDataSourceId != null ? targetDataSourceId.toString() : null);
            if (StringUtils.hasText(database)) payload.put("database", database);
            if (StringUtils.hasText(schema)) payload.put("schema", schema);
            if (vars != null && !vars.isEmpty()) payload.put("vars", vars);
            return payload;
        }

        private static String stringVal(Object value, String fallback) {
            if (value == null) return fallback;
            String text = String.valueOf(value).trim();
            return text.isEmpty() ? fallback : text;
        }
    }

    public record DbtWorkspaceConfigRequest(
        String projectDir,
        String profilesDir,
        String profileName,
        String targetName,
        UUID targetDataSourceId,
        String database,
        String schema,
        Map<String, Object> vars
    ) {}

    public record DbtProfileStatus(boolean generated, String message, String profilePath) {
        public static DbtProfileStatus skipped(String message) {
            return new DbtProfileStatus(false, message, null);
        }

        public static DbtProfileStatus success(String path) {
            return new DbtProfileStatus(true, "profiles.yml 已更新", path);
        }
    }

    public record DbtConfigView(
        boolean enabled,
        DbtWorkspaceConfig config,
        DbtProfileStatus profileStatus,
        InfraDataSourceDto target,
        DbtWorkspaceStatus workspaceStatus
    ) {
        public static DbtConfigView disabled(String message) {
            return new DbtConfigView(false, null, DbtProfileStatus.skipped(message), null, DbtWorkspaceStatus.failed(message));
        }
    }

    public record InfraDataSourceDto(UUID id, String name, String type, String jdbcUrl, String username) {}

    public record DbtWorkspaceStatus(boolean ok, String message, Map<String, Object> detail) {
        static DbtWorkspaceStatus success(String message, Map<String, Object> detail) {
            return new DbtWorkspaceStatus(true, message, detail == null ? Map.of() : detail);
        }

        static DbtWorkspaceStatus failed(String message) {
            return new DbtWorkspaceStatus(false, message, Map.of());
        }
    }

    private record JdbcEndpoint(String host, int port, String database) {
        static JdbcEndpoint parse(String jdbcUrl) {
            if (!StringUtils.hasText(jdbcUrl)) return null;
            String url = jdbcUrl.trim();
            if (!url.startsWith("jdbc:")) return null;
            String withoutPrefix = url.substring(5);
            int schemeIdx = withoutPrefix.indexOf("://");
            if (schemeIdx < 0) return null;
            String remainder = withoutPrefix.substring(schemeIdx + 3);
            String[] parts = remainder.split("/", 2);
            String hostPart = parts[0];
            String database = parts.length > 1 ? parts[1].split("\\?")[0] : null;
            String host = hostPart;
            int port = 0;
            int colonIdx = hostPart.indexOf(":");
            if (colonIdx >= 0) {
                host = hostPart.substring(0, colonIdx);
                try {
                    port = Integer.parseInt(hostPart.substring(colonIdx + 1));
                } catch (Exception ignored) {
                    port = 0;
                }
            }
            return new JdbcEndpoint(host, port == 0 ? 0 : port, database);
        }

        JdbcEndpoint withDefaultPort(int fallbackPort) {
            if (port > 0) return this;
            return new JdbcEndpoint(host, fallbackPort, database);
        }
    }

    private int defaultPort(String adapter) {
        if ("mysql".equalsIgnoreCase(adapter)) return 3306;
        if ("postgres".equalsIgnoreCase(adapter)) return 5432;
        return 0;
    }

    private DbtWorkspaceStatus validateWorkspace(DbtWorkspaceConfig config, boolean createIfMissing) {
        if (config == null) {
            return DbtWorkspaceStatus.failed("dbt 配置为空");
        }
        String projectDir = safeText(config.projectDir(), null);
        String profilesDir = safeText(config.profilesDir(), null);
        if (!StringUtils.hasText(projectDir)) {
            return DbtWorkspaceStatus.failed("dbt 项目目录不能为空");
        }
        if (!StringUtils.hasText(profilesDir)) {
            return DbtWorkspaceStatus.failed("profiles 目录不能为空");
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        try {
            Path projectPath = Path.of(projectDir);
            if (createIfMissing) {
                Files.createDirectories(projectPath);
            }
            if (!Files.isDirectory(projectPath)) {
                return DbtWorkspaceStatus.failed("dbt 项目目录不可用: " + projectDir);
            }
            detail.put("projectDir", projectPath.toString());
            detail.put("projectWritable", Files.isWritable(projectPath));
            Path targetDir = projectPath.resolve("target");
            if (createIfMissing) {
                Files.createDirectories(targetDir);
            }
            detail.put("targetDir", targetDir.toString());
            detail.put("targetWritable", Files.isWritable(targetDir));
            if (!Files.isWritable(projectPath) || !Files.isWritable(targetDir)) {
                return DbtWorkspaceStatus.failed("dbt 项目目录或 target 目录不可写");
            }
        } catch (Exception ex) {
            return DbtWorkspaceStatus.failed("dbt 项目目录检查失败: " + ex.getMessage());
        }

        try {
            Path profilesPath = Path.of(profilesDir);
            if (createIfMissing) {
                Files.createDirectories(profilesPath);
            }
            if (!Files.isDirectory(profilesPath)) {
                return DbtWorkspaceStatus.failed("profiles 目录不可用: " + profilesDir);
            }
            detail.put("profilesDir", profilesPath.toString());
            detail.put("profilesWritable", Files.isWritable(profilesPath));
            if (!Files.isWritable(profilesPath)) {
                return DbtWorkspaceStatus.failed("profiles 目录不可写");
            }
        } catch (Exception ex) {
            return DbtWorkspaceStatus.failed("profiles 目录检查失败: " + ex.getMessage());
        }
        return DbtWorkspaceStatus.success("dbt 工作区可用", detail);
    }

    private static final class YamlWriter {
        private static String toYaml(Map<String, Object> root) throws IOException {
            StringBuilder sb = new StringBuilder();
            writeMap(sb, root, 0);
            return sb.toString();
        }

        private static void writeMap(StringBuilder sb, Map<String, Object> map, int indent) {
            String prefix = " ".repeat(Math.max(0, indent));
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                sb.append(prefix).append(entry.getKey()).append(":");
                Object value = entry.getValue();
                if (value instanceof Map<?, ?> nested) {
                    sb.append("\n");
                    writeMap(sb, (Map<String, Object>) nested, indent + 2);
                } else {
                    sb.append(" ").append(value == null ? "\"\"" : value).append("\n");
                }
            }
        }
    }
}
