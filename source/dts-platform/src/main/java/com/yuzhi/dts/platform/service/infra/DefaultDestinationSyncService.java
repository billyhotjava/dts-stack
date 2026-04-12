package com.yuzhi.dts.platform.service.infra;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
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
public class DefaultDestinationSyncService {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultDestinationSyncService.class);
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String TYPE_INCEPTOR = "INCEPTOR";
    private static final String BIADMIN_NAME = "数仓 (biadmin)";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final AdminInfraClient adminInfraClient;
    private final InfraDataSourceRepository dataSourceRepository;
    private final InfraSecretService secretService;
    private final ObjectMapper objectMapper;

    public DefaultDestinationSyncService(
        AdminInfraClient adminInfraClient,
        InfraDataSourceRepository dataSourceRepository,
        InfraSecretService secretService,
        ObjectMapper objectMapper
    ) {
        this.adminInfraClient = adminInfraClient;
        this.dataSourceRepository = dataSourceRepository;
        this.secretService = secretService;
        this.objectMapper = objectMapper;
    }

    public DefaultDestinationSnapshot ensureDefaultDestination() {
        LakeSnapshot lake = resolveDefaultLake().orElse(null);
        if (lake == null) {
            return null;
        }
        Map<String, Object> destinationConfig = resolveDestinationConfig(lake);
        String writerType = resolveWriterType(lake, destinationConfig);
        if (!StringUtils.hasText(writerType)) {
            LOG.debug("Default data lake missing Addax writer type; skip default destination");
            return null;
        }
        String destinationName = firstNonEmpty(lake.getDestinationName(), lake.getName(), "dts-addax-destination");
        return new DefaultDestinationSnapshot(writerType, destinationName, destinationConfig);
    }

    public DefaultDestinationStatus checkDefaultDestinationStatus() {
        LakeSnapshot lake = resolveDefaultLake().orElse(null);
        if (lake == null) {
            return DefaultDestinationStatus.missing("未配置默认数据湖");
        }
        Map<String, Object> destinationConfig = resolveDestinationConfig(lake);
        String writerType = resolveWriterType(lake, destinationConfig);
        boolean hasWriterType = StringUtils.hasText(writerType);
        boolean hasConfig = destinationConfig != null && !destinationConfig.isEmpty();
        String message = null;
        if (!hasWriterType) {
            message = "默认数据湖未配置写入器类型";
        } else if (!hasConfig) {
            message = "默认数据湖未配置写入器参数";
        }
        String destinationName = firstNonEmpty(lake.getDestinationName(), lake.getName());
        String dataSourceId = resolveLocalDataSourceId(lake);
        return new DefaultDestinationStatus(true, hasWriterType, hasConfig, destinationName, writerType, message, dataSourceId);
    }

    private String resolveLocalDataSourceId(LakeSnapshot lake) {
        if (lake == null) {
            return null;
        }
        if (StringUtils.hasText(lake.getDataSourceId())) {
            return lake.getDataSourceId();
        }
        String lakeName = normalize(lake.getName());
        String lakeDestName = normalize(lake.getDestinationName());
        String lakeJdbc = normalize(lake.getJdbcUrl());
        if (!StringUtils.hasText(lakeName) && !StringUtils.hasText(lakeDestName) && !StringUtils.hasText(lakeJdbc)) {
            return null;
        }
        try {
            List<InfraDataSource> candidates = dataSourceRepository.findByStatusIgnoreCase(STATUS_ACTIVE);
            if (candidates.isEmpty()) {
                candidates = dataSourceRepository.findAll();
            }
            InfraDataSource matched = null;
            for (InfraDataSource source : candidates) {
                String name = normalize(source.getName());
                if (StringUtils.hasText(lakeName) && lakeName.equalsIgnoreCase(name)) {
                    matched = source;
                    break;
                }
                if (StringUtils.hasText(lakeDestName) && lakeDestName.equalsIgnoreCase(name)) {
                    matched = source;
                    break;
                }
            }
            if (matched == null && StringUtils.hasText(lakeJdbc)) {
                for (InfraDataSource source : candidates) {
                    String jdbc = normalize(source.getJdbcUrl());
                    if (StringUtils.hasText(jdbc) && lakeJdbc.equalsIgnoreCase(jdbc)) {
                        matched = source;
                        break;
                    }
                }
            }
            if (matched != null && matched.getId() != null) {
                return matched.getId().toString();
            }
        } catch (RuntimeException ex) {
            LOG.debug("Failed to resolve local data source id for default lake: {}", ex.getMessage());
        }
        return null;
    }

    private Optional<LakeSnapshot> resolveDefaultLake() {
        AdminInfraClient.AdminDataLakeConfig adminLake = adminInfraClient.fetchDefaultDataLake().orElse(null);
        if (adminLake != null) {
            return Optional.of(LakeSnapshot.fromAdmin(adminLake));
        }
        return resolveLocalFallbackLake();
    }

    private Optional<LakeSnapshot> resolveLocalFallbackLake() {
        try {
            List<InfraDataSource> localSources = dataSourceRepository.findByStatusIgnoreCase(STATUS_ACTIVE);
            if (localSources.isEmpty()) {
                localSources = dataSourceRepository.findAll();
            }
            return localSources
                .stream()
                .filter(this::isLocalLakeCandidate)
                .max((a, b) -> Integer.compare(scoreLocalLake(a), scoreLocalLake(b)))
                .map(this::toLocalLakeSnapshot);
        } catch (RuntimeException ex) {
            LOG.debug("Failed to load local default data lake fallback: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private boolean isLocalLakeCandidate(InfraDataSource source) {
        if (source == null) {
            return false;
        }
        String type = normalize(source.getType());
        if (TYPE_INCEPTOR.equalsIgnoreCase(type)) {
            return false;
        }
        if (StringUtils.hasText(normalize(source.getJdbcUrl()))) {
            return true;
        }
        Map<String, Object> props = parseMap(source.getProps());
        if (props.isEmpty()) {
            return false;
        }
        return props.containsKey("destinationConfig")
            || StringUtils.hasText(normalize(props.get("destinationDefinitionId")))
            || StringUtils.hasText(normalize(props.get("writerType")))
            || StringUtils.hasText(normalize(props.get("type")));
    }

    private int scoreLocalLake(InfraDataSource source) {
        if (source == null) {
            return Integer.MIN_VALUE;
        }
        int score = 0;
        String name = normalize(source.getName());
        String type = normalize(source.getType());
        String jdbcUrl = normalize(source.getJdbcUrl());
        String status = normalize(source.getStatus());

        if (BIADMIN_NAME.equalsIgnoreCase(name)) {
            score += 100;
        }
        if (StringUtils.hasText(name) && name.toLowerCase().contains("biadmin")) {
            score += 80;
        }
        if (StringUtils.hasText(jdbcUrl) && jdbcUrl.toLowerCase().contains("/biadmin")) {
            score += 70;
        }
        if ("postgres".equalsIgnoreCase(type) || "postgresql".equalsIgnoreCase(type)) {
            score += 50;
        } else if (StringUtils.hasText(type)) {
            score += 20;
        }
        if (STATUS_ACTIVE.equalsIgnoreCase(status)) {
            score += 5;
        }
        if (StringUtils.hasText(jdbcUrl)) {
            score += 5;
        }
        return score;
    }

    private LakeSnapshot toLocalLakeSnapshot(InfraDataSource source) {
        Map<String, Object> props = parseMap(source.getProps());
        Map<String, Object> secrets = secretService.readSecrets(source);

        Map<String, Object> destinationConfig = new LinkedHashMap<>();
        mergeMap(destinationConfig, props.get("destinationConfig"));
        mergeMap(destinationConfig, secrets.get("destinationConfig"));

        String writerType = firstNonEmpty(
            normalize(props.get("destinationDefinitionId")),
            normalize(props.get("writerType")),
            normalize(props.get("writer")),
            normalize(props.get("type")),
            normalize(destinationConfig.get("writerType")),
            normalize(destinationConfig.get("writer")),
            normalize(destinationConfig.get("type"))
        );
        if (StringUtils.hasText(writerType)) {
            destinationConfig.putIfAbsent("writerType", writerType);
        }

        String jdbcUrl = firstNonEmpty(normalize(source.getJdbcUrl()), normalize(destinationConfig.get("jdbcUrl")));
        String username = firstNonEmpty(normalize(source.getUsername()), normalize(destinationConfig.get("username")));
        String password = firstNonEmpty(normalize(secrets.get("password")), normalize(destinationConfig.get("password")));
        if (StringUtils.hasText(jdbcUrl)) {
            destinationConfig.putIfAbsent("jdbcUrl", jdbcUrl);
        }
        if (StringUtils.hasText(username)) {
            destinationConfig.putIfAbsent("username", username);
        }
        if (StringUtils.hasText(password)) {
            destinationConfig.putIfAbsent("password", password);
        }

        String destinationName = firstNonEmpty(normalize(props.get("destinationName")), normalize(source.getName()));
        String destinationDefinitionId = firstNonEmpty(normalize(props.get("destinationDefinitionId")), writerType);

        UUID sourceId = source.getId();
        return new LakeSnapshot(
            normalize(source.getName()),
            normalize(source.getType()),
            jdbcUrl,
            username,
            password,
            destinationName,
            destinationDefinitionId,
            destinationConfig,
            sourceId == null ? null : sourceId.toString()
        );
    }

    private Map<String, Object> resolveDestinationConfig(LakeSnapshot lake) {
        if (lake == null || lake.getDestinationConfig() == null) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> config = new LinkedHashMap<>(lake.getDestinationConfig());
        String jdbcUrl = normalize(config.get("jdbcUrl"));
        if (!StringUtils.hasText(jdbcUrl)) {
            Object legacy = firstNonEmptyValue(config.get("jdbc_url"), config.get("url"), config.get("jdbc"), config.get("jdbcURL"));
            jdbcUrl = normalize(legacy);
            if (StringUtils.hasText(jdbcUrl)) {
                config.put("jdbcUrl", jdbcUrl);
            }
        }
        if (!StringUtils.hasText(jdbcUrl) && StringUtils.hasText(lake.getJdbcUrl())) {
            jdbcUrl = lake.getJdbcUrl();
            config.put("jdbcUrl", jdbcUrl);
        }
        if (!StringUtils.hasText(jdbcUrl)) {
            String host = normalize(config.get("host"));
            String database = normalize(config.get("database"));
            String port = normalize(config.get("port"));
            String writerType = normalize(
                firstNonEmpty(
                    normalize(config.get("writerType")),
                    normalize(config.get("type")),
                    lake.getDestinationDefinitionId()
                )
            );
            String built = buildJdbcUrl(writerType, host, port, database);
            if (StringUtils.hasText(built)) {
                config.put("jdbcUrl", built);
            }
        }
        if (!StringUtils.hasText(normalize(config.get("username"))) && StringUtils.hasText(lake.getUsername())) {
            config.put("username", lake.getUsername());
        }
        if (!StringUtils.hasText(normalize(config.get("password"))) && StringUtils.hasText(lake.getPassword())) {
            config.put("password", lake.getPassword());
        }
        return config;
    }

    private String resolveWriterType(LakeSnapshot lake, Map<String, Object> config) {
        String writerType = normalize(lake == null ? null : lake.getDestinationDefinitionId());
        if (!StringUtils.hasText(writerType) && config != null) {
            writerType = normalize(config.get("writerType"));
            if (!StringUtils.hasText(writerType)) {
                writerType = normalize(config.get("writer"));
            }
            if (!StringUtils.hasText(writerType)) {
                writerType = normalize(config.get("type"));
            }
        }
        if (!StringUtils.hasText(writerType) && lake != null) {
            writerType = inferWriterTypeFromLake(lake);
        }
        if (StringUtils.hasText(writerType) && config != null && !StringUtils.hasText(normalize(config.get("writerType")))) {
            config.put("writerType", writerType);
        }
        return writerType;
    }

    private String inferWriterTypeFromLake(LakeSnapshot lake) {
        String type = normalize(lake.getType());
        if (StringUtils.hasText(type)) {
            String inferred = inferWriterFromTypeString(type.toLowerCase());
            if (inferred != null) {
                return inferred;
            }
        }
        String jdbcUrl = normalize(lake.getJdbcUrl());
        if (StringUtils.hasText(jdbcUrl)) {
            String lower = jdbcUrl.toLowerCase();
            if (lower.startsWith("jdbc:postgresql:")) {
                return "postgresqlwriter";
            }
            if (lower.startsWith("jdbc:mysql:") || lower.startsWith("jdbc:mariadb:")) {
                return "mysqlwriter";
            }
            if (lower.startsWith("jdbc:oracle:")) {
                return "oraclewriter";
            }
            if (lower.startsWith("jdbc:sqlserver:")) {
                return "sqlserverwriter";
            }
            if (lower.startsWith("jdbc:dm:")) {
                return "rdbmswriter";
            }
            if (lower.startsWith("jdbc:clickhouse:")) {
                return "clickhousewriter";
            }
            if (lower.startsWith("jdbc:hive2:")) {
                return "hivewriter";
            }
        }
        return null;
    }

    private String inferWriterFromTypeString(String type) {
        if (type.contains("postgres") || type.contains("pg")) {
            return "postgresqlwriter";
        }
        if (type.contains("mysql") || type.contains("mariadb")) {
            return "mysqlwriter";
        }
        if (type.contains("oracle")) {
            return "oraclewriter";
        }
        if (type.contains("sqlserver") || type.contains("mssql")) {
            return "sqlserverwriter";
        }
        if (type.contains("dm") || type.contains("dameng")) {
            return "rdbmswriter";
        }
        if (type.contains("clickhouse")) {
            return "clickhousewriter";
        }
        if (type.contains("hive")) {
            return "hivewriter";
        }
        return null;
    }

    private void mergeMap(Map<String, Object> target, Object raw) {
        if (raw == null || target == null) {
            return;
        }
        if (raw instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    target.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return;
        }
        if (raw instanceof String text && StringUtils.hasText(text)) {
            target.putAll(parseMap(text));
        }
    }

    private Map<String, Object> parseMap(String raw) {
        if (!StringUtils.hasText(raw)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(raw, MAP_TYPE);
        } catch (Exception ex) {
            LOG.debug("Failed to parse json map: {}", ex.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private String normalize(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private String firstNonEmpty(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private Object firstNonEmptyValue(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            String text = normalize(value);
            if (StringUtils.hasText(text)) {
                return value;
            }
        }
        return null;
    }

    private String buildJdbcUrl(String writerType, String host, String port, String database) {
        if (!StringUtils.hasText(host) || !StringUtils.hasText(database)) {
            return null;
        }
        String type = normalize(writerType);
        if (!StringUtils.hasText(type)) {
            return null;
        }
        String lower = type.toLowerCase();
        String resolvedPort = StringUtils.hasText(port) ? port.trim() : null;
        if (lower.contains("postgres")) {
            return "jdbc:postgresql://" + host + (resolvedPort == null ? "" : ":" + resolvedPort) + "/" + database;
        }
        if (lower.contains("mysql") || lower.contains("mariadb")) {
            return "jdbc:mysql://" + host + (resolvedPort == null ? "" : ":" + resolvedPort) + "/" + database;
        }
        if (lower.contains("oracle")) {
            return "jdbc:oracle:thin:@" + host + (resolvedPort == null ? "" : ":" + resolvedPort) + ":" + database;
        }
        if (lower.contains("sqlserver") || lower.contains("mssql")) {
            return "jdbc:sqlserver://" + host + (resolvedPort == null ? "" : ":" + resolvedPort) + ";databaseName=" + database;
        }
        if (lower.contains("dm")) {
            return "jdbc:dm://" + host + (resolvedPort == null ? "" : ":" + resolvedPort);
        }
        if (lower.contains("rdbms")) {
            return "jdbc:postgresql://" + host + (resolvedPort == null ? "" : ":" + resolvedPort) + "/" + database;
        }
        return null;
    }

    private static final class LakeSnapshot {

        private final String name;
        private final String type;
        private final String jdbcUrl;
        private final String username;
        private final String password;
        private final String destinationName;
        private final String destinationDefinitionId;
        private final Map<String, Object> destinationConfig;
        private final String dataSourceId;

        private LakeSnapshot(
            String name,
            String type,
            String jdbcUrl,
            String username,
            String password,
            String destinationName,
            String destinationDefinitionId,
            Map<String, Object> destinationConfig,
            String dataSourceId
        ) {
            this.name = name;
            this.type = type;
            this.jdbcUrl = jdbcUrl;
            this.username = username;
            this.password = password;
            this.destinationName = destinationName;
            this.destinationDefinitionId = destinationDefinitionId;
            this.destinationConfig = destinationConfig == null ? Map.of() : new LinkedHashMap<>(destinationConfig);
            this.dataSourceId = dataSourceId;
        }

        private static LakeSnapshot fromAdmin(AdminInfraClient.AdminDataLakeConfig lake) {
            return new LakeSnapshot(
                lake.getName(),
                lake.getType(),
                lake.getJdbcUrl(),
                lake.getUsername(),
                lake.getPassword(),
                lake.getDestinationName(),
                lake.getDestinationDefinitionId(),
                lake.getDestinationConfig(),
                null
            );
        }

        private String getDataSourceId() {
            return dataSourceId;
        }

        private String getName() {
            return name;
        }

        private String getType() {
            return type;
        }

        private String getJdbcUrl() {
            return jdbcUrl;
        }

        private String getUsername() {
            return username;
        }

        private String getPassword() {
            return password;
        }

        private String getDestinationName() {
            return destinationName;
        }

        private String getDestinationDefinitionId() {
            return destinationDefinitionId;
        }

        private Map<String, Object> getDestinationConfig() {
            return destinationConfig;
        }
    }

    public record DefaultDestinationSnapshot(
        String destinationDefinitionId,
        String destinationName,
        Map<String, Object> destinationConfig
    ) {
        public DefaultDestinationSnapshot {
            destinationConfig = destinationConfig == null ? Map.of() : new LinkedHashMap<>(destinationConfig);
        }

        public boolean isEmpty() {
            return !StringUtils.hasText(destinationDefinitionId)
                && !StringUtils.hasText(destinationName)
                && (destinationConfig == null || destinationConfig.isEmpty());
        }
    }

    public record DefaultDestinationStatus(
        boolean available,
        boolean writerTypeReady,
        boolean writerConfigReady,
        String destinationName,
        String writerType,
        String message,
        String dataSourceId
    ) {
        public static DefaultDestinationStatus missing(String message) {
            return new DefaultDestinationStatus(false, false, false, null, null, message, null);
        }
    }
}
