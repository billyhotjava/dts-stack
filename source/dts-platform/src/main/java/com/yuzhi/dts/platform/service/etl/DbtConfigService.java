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
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DbtConfigService {

    /** Written by DefaultDestinationSyncService for the mirror of the dts-admin default data lake. */
    private static final String MANAGED_DEFAULT_LAKE_SOURCE = "admin-default-data-lake";

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
     * Reads the explicitly configured runtime target without bootstrapping the workspace,
     * generating a shared profile or silently selecting a replacement data source.
     */
    public DbtWorkspaceConfig loadRuntimeConfig() {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("dbt 配置未启用");
        }
        DbtWorkspaceConfig config = readConfig();
        if (config == null || config.targetDataSourceId() == null) {
            throw new DbtRuntimeTargetException("DBT_TARGET_NOT_CONFIGURED", "未配置目标数仓");
        }
        return config;
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
            writeManagedFileIfMissing(
                projectDir.resolve("macros/dts_unique_combination.sql"),
                """
                {% test dts_unique_combination(model, combination_of_columns) %}
                    select
                        {% for column in combination_of_columns %}
                        {{ adapter.quote(column) }}{% if not loop.last %}, {% endif %}
                        {% endfor %}
                    from {{ model }}
                    group by
                        {% for column in combination_of_columns %}
                        {{ adapter.quote(column) }}{% if not loop.last %}, {% endif %}
                        {% endfor %}
                    having count(*) > 1
                {% endtest %}
                """,
                created,
                "macros/dts_unique_combination.sql"
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
        return DbtProfileStatus.skipped(
            "运行凭据由物化任务的一次性 tmpfs profile lease 提供"
        );
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

    /**
     * The config file outlives database resets and migrations, while the runtime deliberately
     * never substitutes a target on its own. After the managed default-lake mirror has been
     * synchronized from dts-admin, a missing or unset target is pointed at that mirror; its
     * identity comes from the admin default data lake, never from a database-name match. A
     * target that still exists is never switched, even if the default lake changed.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.LOWEST_PRECEDENCE)
    public void reconcileTargetWithManagedDefaultLake() {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            reconcileMissingTarget(readConfig());
        } catch (RuntimeException ex) {
            LOG.warn("event=dbt_config_target_reconcile_failed failureType={}", ex.getClass().getSimpleName());
        }
    }

    private void reconcileMissingTarget(DbtWorkspaceConfig config) {
        if (config == null || (config.targetDataSourceId() != null && resolveConfiguredTargetSource(config) != null)) {
            return;
        }
        List<InfraDataSource> mirrors = dataSourceRepository
            .findByStatusIgnoreCase("ACTIVE")
            .stream()
            .filter(source -> source != null && source.getId() != null && isManagedDefaultLakeMirror(source))
            .toList();
        if (mirrors.size() != 1) {
            LOG.error(
                "event=dbt_config_target_invalid configuredTarget={} managedDefaultLakeMirrors={}",
                config.targetDataSourceId(),
                mirrors.size()
            );
            return;
        }
        InfraDataSource mirror = mirrors.getFirst();
        writeConfig(
            new DbtWorkspaceConfig(
                config.enabled(),
                config.projectDir(),
                config.profilesDir(),
                config.profileName(),
                config.targetName(),
                mirror.getId(),
                config.database(),
                config.schema(),
                config.vars()
            )
        );
        LOG.warn(
            "event=dbt_config_target_reconciled previousTarget={} target={} adminDataLakeId={}",
            config.targetDataSourceId(),
            mirror.getId(),
            managedProps(mirror).get("adminDataLakeId")
        );
    }

    private boolean isManagedDefaultLakeMirror(InfraDataSource source) {
        Map<String, Object> props = managedProps(source);
        return MANAGED_DEFAULT_LAKE_SOURCE.equals(props.get("source")) &&
            Boolean.TRUE.equals(props.get("defaultLake")) &&
            props.get("adminDataLakeId") instanceof String adminDataLakeId &&
            !adminDataLakeId.isBlank();
    }

    private Map<String, Object> managedProps(InfraDataSource source) {
        if (source.getProps() == null || source.getProps().isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(source.getProps(), new TypeReference<>() {});
        } catch (Exception ex) {
            return Map.of();
        }
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

    private String safeText(String value, String fallback) {
        if (!StringUtils.hasText(value)) return fallback;
        return value.trim();
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

    private record DbtWorkspaceBootstrapResult(boolean changed, List<String> files) {
        private static DbtWorkspaceBootstrapResult empty() {
            return new DbtWorkspaceBootstrapResult(false, List.of());
        }
    }
}
