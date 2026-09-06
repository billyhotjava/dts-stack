package com.yuzhi.dts.analytics.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.analytics.domain.AnalyticsDatabase;
import com.yuzhi.dts.analytics.domain.AnalyticsTable;
import com.yuzhi.dts.analytics.repository.AnalyticsDatabaseRepository;
import java.sql.SQLException;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Registers a trusted platform source once per tenant and verifies one published target. */
@Service
public class PlatformAnalyticsDatabaseRegistrationService {

    private final AnalyticsDatabaseRepository databaseRepository;
    private final PlatformInfraClient platformInfraClient;
    private final JdbcDetailsResolver jdbcDetailsResolver;
    private final MetadataSyncService metadataSyncService;
    private final PlatformAnalyticsDatabaseBindingWriter bindingWriter;
    private final ObjectMapper objectMapper;
    private final String defaultTenantId;

    public PlatformAnalyticsDatabaseRegistrationService(
        AnalyticsDatabaseRepository databaseRepository,
        PlatformInfraClient platformInfraClient,
        JdbcDetailsResolver jdbcDetailsResolver,
        MetadataSyncService metadataSyncService,
        PlatformAnalyticsDatabaseBindingWriter bindingWriter,
        ObjectMapper objectMapper,
        @Value("${dts.analytics.registration.default-tenant:default}") String defaultTenantId
    ) {
        this.databaseRepository = databaseRepository;
        this.platformInfraClient = platformInfraClient;
        this.jdbcDetailsResolver = jdbcDetailsResolver;
        this.metadataSyncService = metadataSyncService;
        this.bindingWriter = bindingWriter;
        this.objectMapper = objectMapper;
        this.defaultTenantId = defaultTenantId;
    }

    public String defaultTenantId() {
        return defaultTenantId;
    }

    @Transactional(noRollbackFor = AnalysisRegistrationException.class)
    public AnalyticsDatabase ensureDataLakeDatabase(UUID platformDataSourceId) {
        AnalyticsDatabase database = adoptLegacyDataLake(platformDataSourceId);
        if (database == null) {
            database = ensureDatabase(defaultTenantId, platformDataSourceId);
        }
        try {
            ObjectNode details = (ObjectNode) objectMapper.readTree(database.getDetailsJson());
            details.put("source", DataLakeDatabaseInitializer.DATA_LAKE_SOURCE_KEY);
            details.put("system", true);
            database.setDetailsJson(details.toString());
        } catch (Exception invalid) {
            throw new AnalysisRegistrationException("ANALYSIS_REGISTRATION_CONFLICT", invalid);
        }
        database.setName(DataLakeDatabaseInitializer.DATA_LAKE_NAME);
        database.setDescription("内置数据湖，用于存储建模输出与上传数据");
        return databaseRepository.save(database);
    }

    public AnalyticsDatabase ensureDatabase(String tenantId, UUID platformDataSourceId) {
        String tenant = requireTenant(tenantId);
        if (platformDataSourceId == null) throw new AnalysisRegistrationException("ANALYSIS_PLATFORM_SOURCE_INVALID");
        AnalyticsDatabase existing = databaseRepository.findByTenantIdAndPlatformDataSourceId(tenant, platformDataSourceId).orElse(null);
        if (existing != null) return existing;
        if (hasUnresolvedLegacyBinding(platformDataSourceId)) {
            throw new AnalysisRegistrationException("ANALYSIS_LEGACY_SOURCE_UNRESOLVED");
        }
        PlatformInfraClient.DataSourceDetail detail;
        try {
            detail = platformInfraClient.fetchDataSourceDetail(platformDataSourceId);
            ObjectNode jdbcReference = objectMapper.createObjectNode();
            jdbcReference.put("platformDataSourceId", platformDataSourceId.toString());
            // Resolve through the shared credential boundary; credentials remain transient.
            jdbcDetailsResolver.resolve("platform", jdbcReference);
        } catch (IllegalArgumentException invalid) {
            throw new AnalysisRegistrationException("ANALYSIS_PLATFORM_SOURCE_INVALID", invalid);
        } catch (RuntimeException unavailable) {
            throw new AnalysisRegistrationException("ANALYSIS_PLATFORM_UNAVAILABLE", unavailable);
        }
        if (!StringUtils.hasText(detail.jdbcUrl())) throw new AnalysisRegistrationException("ANALYSIS_PLATFORM_SOURCE_INVALID");
        AnalyticsDatabase database = new AnalyticsDatabase();
        database.setTenantId(tenant);
        database.setPlatformDataSourceId(platformDataSourceId);
        database.setName(StringUtils.hasText(detail.name()) ? detail.name().trim() : "platform-" + platformDataSourceId);
        database.setEngine(resolveEngine(detail));
        database.setDetailsJson(platformDetails(platformDataSourceId));
        database.setDescription(detail.description());
        database.setSample(false);
        database.setTimezone(ZoneId.systemDefault().getId());
        database.setMetadataSyncSchedule("0 50 * * * ? *");
        database.setCacheFieldValuesSchedule("0 50 0 * * ? *");
        database.setAutoRunQueries(true);
        database.setFullSync(true);
        database.setOnDemand(false);
        try {
            return bindingWriter.insert(database);
        } catch (DataIntegrityViolationException collision) {
            return databaseRepository.findByTenantIdAndPlatformDataSourceId(tenant, platformDataSourceId)
                .orElseThrow(() -> new AnalysisRegistrationException("ANALYSIS_REGISTRATION_CONFLICT", collision));
        }
    }

    @Transactional(noRollbackFor = AnalysisRegistrationException.class)
    public TargetRegistration ensureTarget(
        String tenantId,
        UUID platformDataSourceId,
        String schemaName,
        String tableName,
        Set<String> requiredFields
    ) {
        AnalyticsDatabase database = ensureDatabase(tenantId, platformDataSourceId);
        Set<String> required = normalizeFields(requiredFields);
        try {
            MetadataSyncService.TargetSyncResult target = metadataSyncService.syncTarget(database.getId(), schemaName, tableName, required);
            return new TargetRegistration(database, target.table(), target.fieldNames());
        } catch (MetadataSyncService.TargetNotFoundException missing) {
            throw new AnalysisRegistrationException(missing.code(), missing);
        } catch (SQLException unavailable) {
            throw new AnalysisRegistrationException("ANALYSIS_METADATA_TRANSIENT_FAILURE", unavailable);
        } catch (RuntimeException unavailable) {
            throw new AnalysisRegistrationException("ANALYSIS_METADATA_TRANSIENT_FAILURE", unavailable);
        }
    }

    private boolean hasUnresolvedLegacyBinding(UUID platformDataSourceId) {
        return databaseRepository.findAll().stream().anyMatch(database ->
            database.getTenantId() == null && platformDataSourceId.toString().equals(extractLegacyPlatformId(database))
        );
    }

    private String extractLegacyPlatformId(AnalyticsDatabase database) {
        try {
            var details = objectMapper.readTree(database.getDetailsJson());
            for (String key : new String[] {"platformDataSourceId", "platform_data_source_id", "platformDataSourceID"}) {
                if (details.hasNonNull(key) && StringUtils.hasText(details.path(key).asText())) {
                    return details.path(key).asText().trim();
                }
            }
            var platform = details.path("platform");
            for (String key : new String[] {"dataSourceId", "datasourceId", "id"}) {
                if (platform.hasNonNull(key) && StringUtils.hasText(platform.path(key).asText())) {
                    return platform.path(key).asText().trim();
                }
            }
        } catch (Exception ignored) {
            // Legacy rows with malformed details are intentionally not adopted.
        }
        return null;
    }

    /** Only the explicit system marker proves that a pre-T13 row is the built-in data lake. */
    private AnalyticsDatabase adoptLegacyDataLake(UUID platformDataSourceId) {
        for (AnalyticsDatabase candidate : databaseRepository.findAll()) {
            if (candidate.getTenantId() != null || !DataLakeDatabaseInitializer.isDataLakeDatabase(candidate)) {
                continue;
            }
            if (!platformDataSourceId.toString().equals(extractLegacyPlatformId(candidate))) {
                continue;
            }
            candidate.setTenantId(defaultTenantId);
            candidate.setPlatformDataSourceId(platformDataSourceId);
            try {
                return bindingWriter.insert(candidate);
            } catch (DataIntegrityViolationException collision) {
                return databaseRepository.findByTenantIdAndPlatformDataSourceId(defaultTenantId, platformDataSourceId)
                    .orElseThrow(() -> new AnalysisRegistrationException("ANALYSIS_REGISTRATION_CONFLICT", collision));
            }
        }
        return null;
    }

    private String platformDetails(UUID platformDataSourceId) {
        ObjectNode details = objectMapper.createObjectNode();
        details.put("platformDataSourceId", platformDataSourceId.toString());
        return details.toString();
    }

    private static String requireTenant(String tenantId) {
        if (!StringUtils.hasText(tenantId)) throw new AnalysisRegistrationException("ANALYSIS_TENANT_REQUIRED");
        return tenantId.trim();
    }

    private static Set<String> normalizeFields(Set<String> fields) {
        if (fields == null || fields.isEmpty()) throw new AnalysisRegistrationException("ANALYSIS_REQUIRED_FIELDS_REQUIRED");
        Set<String> normalized = new LinkedHashSet<>();
        for (String field : fields) {
            if (!StringUtils.hasText(field) || !normalized.add(field.trim().toLowerCase(java.util.Locale.ROOT))) {
                throw new AnalysisRegistrationException("ANALYSIS_REQUIRED_FIELDS_INVALID");
            }
        }
        return normalized;
    }

    private static String resolveEngine(PlatformInfraClient.DataSourceDetail detail) {
        if (StringUtils.hasText(detail.type())) return detail.type().trim().toLowerCase(java.util.Locale.ROOT);
        String jdbcUrl = detail.jdbcUrl() == null ? "" : detail.jdbcUrl().toLowerCase(java.util.Locale.ROOT);
        if (jdbcUrl.startsWith("jdbc:mysql:")) return "mysql";
        return "postgres";
    }

    public record TargetRegistration(AnalyticsDatabase database, AnalyticsTable table, Set<String> verifiedFields) {}

    public static class AnalysisRegistrationException extends RuntimeException {
        private final String code;

        public AnalysisRegistrationException(String code) { super(code); this.code = code; }
        public AnalysisRegistrationException(String code, Throwable cause) { super(code, cause); this.code = code; }
        public String code() { return code; }
    }
}
