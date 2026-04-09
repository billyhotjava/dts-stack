package com.yuzhi.dts.platform.service.etl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.config.DbtProperties;
import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import com.yuzhi.dts.platform.service.infra.DataSourceScorer;
import com.yuzhi.dts.platform.service.infra.InfraSecretService;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
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
            DbtWorkspaceConfig config;
            if (!Files.exists(path)) {
                Files.createDirectories(path.getParent());
                config = hydrateResolvedConfig(
                    new DbtWorkspaceConfig(
                    true,
                    properties.getProjectDir(),
                    properties.getProfilesDir(),
                    "dts",
                    "dev",
                    null,
                    null,
                    null,
                    Collections.emptyMap()
                    )
                );
                writeConfig(config);
            } else {
                config = hydrateResolvedConfig(readConfig());
            }
            ensureWorkspaceBootstrap(config);
        } catch (IOException ex) {
            LOG.warn("Failed to initialize dbt config file: {}", ex.getMessage());
        }
    }

    public DbtConfigView loadConfig() {
        if (!properties.isEnabled()) {
            return DbtConfigView.disabled("dbt 配置未启用");
        }
        DbtWorkspaceConfig config = hydrateResolvedConfig(readConfig());
        DbtWorkspaceBootstrapResult bootstrap = ensureWorkspaceBootstrap(config);
        DbtProfileStatus profileStatus = buildProfile(config);
        InfraDataSourceDto target = resolveTarget(config);
        DbtWorkspaceStatus workspaceStatus = withBootstrapDetail(validateWorkspace(config, false), bootstrap);
        return new DbtConfigView(true, config, profileStatus, target, workspaceStatus);
    }

    /**
     * Lightweight method that only reads the config file to resolve the project directory.
     * Does NOT validate workspace, probe file system, or bootstrap directories.
     * Use this when you only need the project path (e.g., for file deletion).
     */
    public String resolveProjectDir() {
        if (!properties.isEnabled()) {
            return null;
        }
        DbtWorkspaceConfig config = readConfig();
        return config != null ? config.projectDir() : null;
    }

    public DbtConfigView saveConfig(DbtWorkspaceConfigRequest request) {
        if (!properties.isEnabled()) {
            return DbtConfigView.disabled("dbt 配置未启用");
        }
        DbtWorkspaceConfig config = hydrateResolvedConfig(
            new DbtWorkspaceConfig(
            true,
            safeText(request.projectDir(), properties.getProjectDir()),
            safeText(request.profilesDir(), properties.getProfilesDir()),
            safeText(request.profileName(), "dts"),
            safeText(request.targetName(), "dev"),
            request.targetDataSourceId(),
            safeText(request.database(), null),
            safeText(request.schema(), null),
            request.vars() == null ? Collections.emptyMap() : request.vars()
            )
        );
        DbtWorkspaceBootstrapResult bootstrap = ensureWorkspaceBootstrap(config);
        DbtWorkspaceStatus workspaceStatus = withBootstrapDetail(validateWorkspace(config, true), bootstrap);
        if (!workspaceStatus.ok()) {
            throw new IllegalArgumentException(workspaceStatus.message());
        }
        writeConfig(config);
        DbtProfileStatus profileStatus = buildProfile(config);
        InfraDataSourceDto target = resolveTarget(config);
        return new DbtConfigView(true, config, profileStatus, target, workspaceStatus);
    }

    private DbtWorkspaceBootstrapResult ensureWorkspaceBootstrap(DbtWorkspaceConfig config) {
        if (config == null || !StringUtils.hasText(config.projectDir())) {
            return DbtWorkspaceBootstrapResult.empty();
        }
        List<String> created = new ArrayList<>();
        try {
            Path projectDir = Path.of(config.projectDir());
            Files.createDirectories(projectDir);
            createDirectoryIfMissing(projectDir.resolve("models"), created, "models/");
            createDirectoryIfMissing(projectDir.resolve("models/ods"), created, "models/ods/");
            createDirectoryIfMissing(projectDir.resolve("models/dwd"), created, "models/dwd/");
            createDirectoryIfMissing(projectDir.resolve("models/dws"), created, "models/dws/");
            createDirectoryIfMissing(projectDir.resolve("models/ads"), created, "models/ads/");
            createDirectoryIfMissing(projectDir.resolve("macros"), created, "macros/");
            createDirectoryIfMissing(projectDir.resolve("seeds"), created, "seeds/");
            createDirectoryIfMissing(projectDir.resolve("tests"), created, "tests/");
            createDirectoryIfMissing(projectDir.resolve("analyses"), created, "analyses/");
            createDirectoryIfMissing(projectDir.resolve("snapshots"), created, "snapshots/");
            createDirectoryIfMissing(projectDir.resolve("target"), created, "target/");
            createDirectoryIfMissing(projectDir.resolve("logs"), created, "logs/");

            writeManagedFileIfMissing(projectDir.resolve("dbt_project.yml"), buildDbtProjectYaml(config), created, "dbt_project.yml");
            writeManagedFileIfMissing(
                projectDir.resolve("models/ods_sources.yml"),
                """
                version: 2
                sources: []
                """,
                created,
                "models/ods_sources.yml"
            );
            writeManagedFileIfMissing(
                projectDir.resolve(".gitignore"),
                """
                target/
                logs/
                dbt_packages/
                """,
                created,
                ".gitignore"
            );
            writeManagedFileIfMissing(
                projectDir.resolve("README.md"),
                """
                # DTS dbt workspace

                This workspace is auto-initialized by dts-platform.
                """,
                created,
                "README.md"
            );
            writeManagedFileIfMissing(
                projectDir.resolve("macros/get_custom_schema.sql"),
                """
                {% macro generate_schema_name(custom_schema_name, node) -%}
                    {%- if custom_schema_name is none -%}
                        {{ target.schema }}
                    {%- else -%}
                        {{ custom_schema_name | trim }}
                    {%- endif -%}
                {%- endmacro %}
                """,
                created,
                "macros/get_custom_schema.sql"
            );
            writeManagedFileIfMissing(
                projectDir.resolve("macros/nullif_placeholder.sql"),
                """
                {% macro nullif_placeholder(expr) -%}
                (
                  case
                    when {{ expr }} is null then null
                    when upper(btrim(cast({{ expr }} as text))) in ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
                      then null
                    else nullif(btrim(cast({{ expr }} as text)), '')
                  end
                )
                {%- endmacro %}
                """,
                created,
                "macros/nullif_placeholder.sql"
            );
            writeManagedFileIfMissing(
                projectDir.resolve("macros/parse_numeric_safe.sql"),
                """
                {% macro parse_numeric_safe(expr) -%}
                (
                  case
                    when {{ nullif_placeholder(expr) }} is null then null
                    when regexp_replace({{ nullif_placeholder(expr) }}, ',', '', 'g') ~ '^-?\\d+(\\.\\d+)?$'
                      then regexp_replace({{ nullif_placeholder(expr) }}, ',', '', 'g')::numeric
                    else null
                  end
                )
                {%- endmacro %}
                """,
                created,
                "macros/parse_numeric_safe.sql"
            );
            writeManagedFileIfMissing(
                projectDir.resolve("macros/parse_date_safe.sql"),
                """
                {% macro parse_date_safe(expr) -%}
                (
                  case
                    when {{ nullif_placeholder(expr) }} is null then null
                    when {{ nullif_placeholder(expr) }} ~ '^\\d{4}-\\d{2}-\\d{2}$'
                      then to_date({{ nullif_placeholder(expr) }}, 'YYYY-MM-DD')
                    when {{ nullif_placeholder(expr) }} ~ '^\\d{4}-\\d{1,2}-\\d{1,2}$'
                      then make_date(
                        split_part({{ nullif_placeholder(expr) }}, '-', 1)::int,
                        split_part({{ nullif_placeholder(expr) }}, '-', 2)::int,
                        split_part({{ nullif_placeholder(expr) }}, '-', 3)::int
                      )
                    when {{ nullif_placeholder(expr) }} ~ '^\\d{4}-\\d{2}-\\d{2}\\s+.*$'
                      then to_date(substr({{ nullif_placeholder(expr) }}, 1, 10), 'YYYY-MM-DD')
                    when {{ nullif_placeholder(expr) }} ~ '^\\d{4}-\\d{1,2}-\\d{1,2}\\s+.*$'
                      then make_date(
                        split_part(split_part({{ nullif_placeholder(expr) }}, ' ', 1), '-', 1)::int,
                        split_part(split_part({{ nullif_placeholder(expr) }}, ' ', 1), '-', 2)::int,
                        split_part(split_part({{ nullif_placeholder(expr) }}, ' ', 1), '-', 3)::int
                      )
                    when {{ nullif_placeholder(expr) }} ~ '^\\d{4}/\\d{2}/\\d{2}$'
                      then to_date(replace({{ nullif_placeholder(expr) }}, '/', '-'), 'YYYY-MM-DD')
                    when {{ nullif_placeholder(expr) }} ~ '^\\d{4}/\\d{1,2}/\\d{1,2}$'
                      then make_date(
                        split_part(replace({{ nullif_placeholder(expr) }}, '/', '-'), '-', 1)::int,
                        split_part(replace({{ nullif_placeholder(expr) }}, '/', '-'), '-', 2)::int,
                        split_part(replace({{ nullif_placeholder(expr) }}, '/', '-'), '-', 3)::int
                      )
                    when {{ nullif_placeholder(expr) }} ~ '^\\d{4}/\\d{2}/\\d{2}\\s+.*$'
                      then to_date(replace(substr({{ nullif_placeholder(expr) }}, 1, 10), '/', '-'), 'YYYY-MM-DD')
                    when {{ nullif_placeholder(expr) }} ~ '^\\d{4}/\\d{1,2}/\\d{1,2}\\s+.*$'
                      then make_date(
                        split_part(replace(split_part({{ nullif_placeholder(expr) }}, ' ', 1), '/', '-'), '-', 1)::int,
                        split_part(replace(split_part({{ nullif_placeholder(expr) }}, ' ', 1), '/', '-'), '-', 2)::int,
                        split_part(replace(split_part({{ nullif_placeholder(expr) }}, ' ', 1), '/', '-'), '-', 3)::int
                      )
                    when {{ nullif_placeholder(expr) }} ~ '^\\d{4}\\.\\d{2}\\.\\d{2}$'
                      then to_date(replace({{ nullif_placeholder(expr) }}, '.', '-'), 'YYYY-MM-DD')
                    when {{ nullif_placeholder(expr) }} ~ '^\\d{4}\\.\\d{1,2}\\.\\d{1,2}$'
                      then make_date(
                        split_part(replace({{ nullif_placeholder(expr) }}, '.', '-'), '-', 1)::int,
                        split_part(replace({{ nullif_placeholder(expr) }}, '.', '-'), '-', 2)::int,
                        split_part(replace({{ nullif_placeholder(expr) }}, '.', '-'), '-', 3)::int
                      )
                    when {{ nullif_placeholder(expr) }} ~ '^\\d{4}\\.\\d{2}\\.\\d{2}\\s+.*$'
                      then to_date(replace(substr({{ nullif_placeholder(expr) }}, 1, 10), '.', '-'), 'YYYY-MM-DD')
                    when {{ nullif_placeholder(expr) }} ~ '^\\d{4}\\.\\d{1,2}\\.\\d{1,2}\\s+.*$'
                      then make_date(
                        split_part(replace(split_part({{ nullif_placeholder(expr) }}, ' ', 1), '.', '-'), '-', 1)::int,
                        split_part(replace(split_part({{ nullif_placeholder(expr) }}, ' ', 1), '.', '-'), '-', 2)::int,
                        split_part(replace(split_part({{ nullif_placeholder(expr) }}, ' ', 1), '.', '-'), '-', 3)::int
                      )
                    when {{ nullif_placeholder(expr) }} ~ '^\\d{8}$'
                      then to_date({{ nullif_placeholder(expr) }}, 'YYYYMMDD')
                    else null
                  end
                )
                {%- endmacro %}
                """,
                created,
                "macros/parse_date_safe.sql"
            );
            writeManagedFileIfMissing(
                projectDir.resolve("macros/truncate_relation.sql"),
                """
                {% macro truncate_relation(schema_name, identifier, database_name=None) -%}
                    {% set database_value = database_name if database_name is not none and database_name | trim else target.database %}
                    {% set relation = adapter.get_relation(
                        database=database_value,
                        schema=schema_name,
                        identifier=identifier
                    ) %}

                    {% if relation is none %}
                        {{ log("truncate_relation skipped: relation not found for " ~ schema_name ~ "." ~ identifier, info=True) }}
                        {{ return("SKIPPED") }}
                    {% endif %}

                    {% if relation.type is not none and relation.type | upper == 'VIEW' %}
                        {{ exceptions.raise_compiler_error("当前产出 relation 为视图，不支持清空。请改用「重建产出表」。") }}
                    {% endif %}

                    {% set sql -%}
                        truncate table {{ relation }}
                    {%- endset %}
                    {% do run_query(sql) %}
                    {{ log("truncate_relation executed for " ~ relation, info=True) }}
                    {{ return("OK") }}
                {%- endmacro %}
                """,
                created,
                "macros/truncate_relation.sql"
            );
            return new DbtWorkspaceBootstrapResult(!created.isEmpty(), created);
        } catch (IOException ex) {
            LOG.warn("[dbt] failed to bootstrap workspace: {}", ex.getMessage());
            return new DbtWorkspaceBootstrapResult(false, List.of("bootstrap_failed:" + ex.getMessage()));
        }
    }

    private void createDirectoryIfMissing(Path dir, List<String> created, String label) throws IOException {
        if (Files.isDirectory(dir)) {
            return;
        }
        Files.createDirectories(dir);
        created.add(label);
    }

    private void writeManagedFileIfMissing(Path file, String content, List<String> created, String label) throws IOException {
        if (Files.exists(file) && Files.size(file) > 0) {
            return;
        }
        Files.createDirectories(file.getParent() == null ? file.toAbsolutePath().getParent() : file.getParent());
        Files.writeString(file, normalizeManagedContent(content), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        created.add(label);
        LOG.info("[dbt] created managed file: {}", file);
    }

    private String buildDbtProjectYaml(DbtWorkspaceConfig config) {
        return """
            name: "dts_workspace"
            version: "1.0.0"
            config-version: 2
            profile: "%s"

            model-paths: ["models"]
            analysis-paths: ["analyses"]
            test-paths: ["tests"]
            seed-paths: ["seeds"]
            macro-paths: ["macros"]
            snapshot-paths: ["snapshots"]
            target-path: "target"
            clean-targets: ["target", "dbt_packages", "logs"]
            """.formatted(safeText(config.profileName(), "dts"));
    }

    private String normalizeManagedContent(String content) {
        return (content == null ? "" : content.strip()) + "\n";
    }

    private DbtWorkspaceStatus withBootstrapDetail(DbtWorkspaceStatus status, DbtWorkspaceBootstrapResult bootstrap) {
        if (status == null) {
            return null;
        }
        Map<String, Object> detail = new LinkedHashMap<>(status.detail() == null ? Map.of() : status.detail());
        if (bootstrap != null) {
            detail.put("bootstrapped", bootstrap.changed());
            detail.put("bootstrapFiles", bootstrap.files());
        }
        return new DbtWorkspaceStatus(status.ok(), status.message(), detail);
    }

    private DbtProfileStatus buildProfile(DbtWorkspaceConfig config) {
        if (config == null || !StringUtils.hasText(config.profilesDir())) {
            return DbtProfileStatus.skipped("未选择目标数仓或 profiles 目录");
        }
        InfraDataSource source = resolveTargetSource(config);
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

    private InfraDataSourceDto resolveTarget(DbtWorkspaceConfig config) {
        InfraDataSource target = resolveTargetSource(config);
        if (target == null) {
            return null;
        }
        return Optional.of(target)
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

    private DbtWorkspaceConfig hydrateResolvedConfig(DbtWorkspaceConfig config) {
        if (config == null) {
            return config;
        }
        InfraDataSource configuredTarget = resolveConfiguredTargetSource(config);
        if (configuredTarget != null && config.targetDataSourceId() != null) {
            return config;
        }
        UUID resolvedTargetId = resolveDefaultTargetDataSourceId(config);
        if (resolvedTargetId == null) {
            return config;
        }
        return new DbtWorkspaceConfig(
            config.enabled(),
            config.projectDir(),
            config.profilesDir(),
            config.profileName(),
            config.targetName(),
            resolvedTargetId,
            config.database(),
            config.schema(),
            config.vars()
        );
    }

    private InfraDataSource resolveTargetSource(DbtWorkspaceConfig config) {
        if (config == null) {
            return null;
        }
        InfraDataSource configured = resolveConfiguredTargetSource(config);
        if (configured != null) {
            return configured;
        }
        UUID targetId = resolveDefaultTargetDataSourceId(config);
        if (targetId == null) {
            return null;
        }
        return dataSourceRepository.findById(targetId).orElse(null);
    }

    private InfraDataSource resolveConfiguredTargetSource(DbtWorkspaceConfig config) {
        if (config == null || config.targetDataSourceId() == null) {
            return null;
        }
        return dataSourceRepository.findById(config.targetDataSourceId()).orElse(null);
    }

    private UUID resolveDefaultTargetDataSourceId(DbtWorkspaceConfig config) {
        List<InfraDataSource> candidates = new ArrayList<>(dataSourceRepository.findByStatusIgnoreCase("ACTIVE"));
        if (candidates.isEmpty()) {
            candidates.addAll(dataSourceRepository.findAll());
        }
        if (candidates.isEmpty()) {
            return null;
        }
        String preferredDatabase = safeText(config != null ? config.database() : null, null);
        return candidates
            .stream()
            .filter(source -> source != null && source.getId() != null)
            .max(Comparator.comparingInt(source -> scoreTargetSource(source, preferredDatabase)))
            .map(InfraDataSource::getId)
            .orElse(null);
    }

    private int scoreTargetSource(InfraDataSource source, String preferredDatabase) {
        int score = DataSourceScorer.scoreDataSource(source.getName(), source.getJdbcUrl(), source.getType(), source.getStatus());
        // Context-specific bonus: preferred database match in JDBC URL
        String normalizedUrl = source.getJdbcUrl() == null ? "" : source.getJdbcUrl().toLowerCase();
        String normalizedDatabase = preferredDatabase == null ? "" : preferredDatabase.toLowerCase();
        if (StringUtils.hasText(normalizedDatabase) && normalizedUrl.contains("/" + normalizedDatabase)) {
            score += 120;
        }
        return score;
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
            detail.put("dbtProjectExists", Files.isRegularFile(projectPath.resolve("dbt_project.yml")));
            detail.put("modelsDirExists", Files.isDirectory(projectPath.resolve("models")));
            boolean projectWritable = probeWritable(projectPath);
            detail.put("projectWritable", projectWritable);
            Path targetDir = projectPath.resolve("target");
            boolean targetExists = Files.isDirectory(targetDir);
            if (createIfMissing) {
                Files.createDirectories(targetDir);
                targetExists = true;
            }
            detail.put("targetDir", targetDir.toString());
            detail.put("targetExists", targetExists);
            boolean targetWritable = targetExists ? probeWritable(targetDir) : projectWritable;
            detail.put("targetWritable", targetWritable);
            if (!projectWritable || !targetWritable) {
                return DbtWorkspaceStatus.failed("dbt 项目目录或 target 目录不可写");
            }
        } catch (Exception ex) {
            return DbtWorkspaceStatus.failed("dbt 项目目录检查失败: " + ex.getMessage());
        }

        try {
            Path profilesPath = Path.of(profilesDir);
            boolean profilesExists = Files.isDirectory(profilesPath);
            if (createIfMissing) {
                Files.createDirectories(profilesPath);
                profilesExists = true;
            }
            if (createIfMissing && !Files.isDirectory(profilesPath)) {
                return DbtWorkspaceStatus.failed("profiles 目录不可用: " + profilesDir);
            }
            detail.put("profilesDir", profilesPath.toString());
            detail.put("profilesExists", profilesExists);
            boolean profilesWritable = profilesExists ? probeWritable(profilesPath) : probeCreatable(profilesPath);
            detail.put("profilesWritable", profilesWritable);
            if (!profilesWritable) {
                return DbtWorkspaceStatus.failed("profiles 目录不可写");
            }
        } catch (Exception ex) {
            return DbtWorkspaceStatus.failed("profiles 目录检查失败: " + ex.getMessage());
        }
        return DbtWorkspaceStatus.success("dbt 工作区可用", detail);
    }

    /** Probe writability by creating and deleting a temp file instead of relying on access() which is unreliable on some ARM bind mounts. */
    private static boolean probeWritable(Path dir) {
        try {
            Path probe = Files.createTempFile(dir, ".dts_probe_", ".tmp");
            Files.deleteIfExists(probe);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private static boolean probeCreatable(Path dir) {
        try {
            Path parent = dir.getParent();
            if (parent == null) {
                return false;
            }
            if (!Files.exists(parent)) {
                return false;
            }
            return probeWritable(parent);
        } catch (Exception ex) {
            return false;
        }
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

    private record DbtWorkspaceBootstrapResult(boolean changed, List<String> files) {
        private static DbtWorkspaceBootstrapResult empty() {
            return new DbtWorkspaceBootstrapResult(false, List.of());
        }
    }
}
