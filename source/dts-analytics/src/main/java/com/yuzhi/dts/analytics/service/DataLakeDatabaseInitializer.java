package com.yuzhi.dts.analytics.service;

import com.yuzhi.dts.analytics.domain.AnalyticsDatabase;
import java.util.UUID;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 自动注册内置数据湖 (biadmin) 为 AnalyticsDatabase。
 * 启动时从平台获取 biadmin 数据源 ID，若本地尚未导入则自动创建，
 * 标记为系统内置（不可删除、不可修改）。
 */
@Component
public class DataLakeDatabaseInitializer {

    private static final Logger LOG = LoggerFactory.getLogger(DataLakeDatabaseInitializer.class);
    static final String DATA_LAKE_SOURCE_KEY = "data-lake";
    static final String DATA_LAKE_NAME = "数据湖 (数仓)";

    private final PlatformInfraClient platformInfraClient;
    private final MetadataSyncService metadataSyncService;
    private final PlatformAnalyticsDatabaseRegistrationService registrationService;

    public DataLakeDatabaseInitializer(
        PlatformInfraClient platformInfraClient,
        MetadataSyncService metadataSyncService,
        PlatformAnalyticsDatabaseRegistrationService registrationService
    ) {
        this.platformInfraClient = platformInfraClient;
        this.metadataSyncService = metadataSyncService;
        this.registrationService = registrationService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean initializeDataLake() {
        try {
            // Find biadmin data source from platform
            List<PlatformInfraClient.DataSourceSummary> sources = platformInfraClient.listDataSources();
            PlatformInfraClient.DataSourceSummary biadmin = sources.stream()
                .filter(ds -> ds.jdbcUrl() != null && ds.jdbcUrl().contains("/biadmin"))
                .findFirst()
                .orElse(null);

            if (biadmin == null || !StringUtils.hasText(biadmin.id())) {
                LOG.warn("[data-lake-init] biadmin data source not found in platform, skipping");
                return false;
            }

            AnalyticsDatabase db = registrationService.ensureDataLakeDatabase(UUID.fromString(biadmin.id()));
            LOG.info("[data-lake-init] Created data lake database id={} platformDataSourceId={}", db.getId(), biadmin.id());

            // Sync metadata so tables appear immediately
            try {
                metadataSyncService.syncDatabaseSchema(db.getId());
                LOG.info("[data-lake-init] Metadata sync completed for data lake database id={}", db.getId());
            } catch (Exception ex) {
                LOG.warn("[data-lake-init] Metadata sync failed (will retry later): {}", ex.getMessage());
            }
            return true;
        } catch (Exception ex) {
            LOG.warn("[data-lake-init] Failed to initialize data lake database: {}", ex.getMessage());
            return false;
        }
    }

    /**
     * Check if an AnalyticsDatabase is the system data lake.
     */
    public static boolean isDataLakeDatabase(AnalyticsDatabase db) {
        if (db == null || db.getDetailsJson() == null) {
            return false;
        }
        String json = db.getDetailsJson();
        return json.contains("\"source\":\"" + DATA_LAKE_SOURCE_KEY + "\"")
            && json.contains("\"system\":true");
    }
}
