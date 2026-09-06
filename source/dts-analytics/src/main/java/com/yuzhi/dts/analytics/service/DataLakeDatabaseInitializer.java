package com.yuzhi.dts.analytics.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.analytics.domain.AnalyticsDatabase;
import com.yuzhi.dts.analytics.repository.AnalyticsDatabaseRepository;
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

    private final AnalyticsDatabaseRepository databaseRepository;
    private final PlatformInfraClient platformInfraClient;
    private final MetadataSyncService metadataSyncService;
    private final ObjectMapper objectMapper;

    public DataLakeDatabaseInitializer(
        AnalyticsDatabaseRepository databaseRepository,
        PlatformInfraClient platformInfraClient,
        MetadataSyncService metadataSyncService,
        ObjectMapper objectMapper
    ) {
        this.databaseRepository = databaseRepository;
        this.platformInfraClient = platformInfraClient;
        this.metadataSyncService = metadataSyncService;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean initializeDataLake() {
        try {
            // Check if data lake already exists locally
            boolean exists = databaseRepository.findAll().stream()
                .anyMatch(DataLakeDatabaseInitializer::isDataLakeDatabase);
            if (exists) {
                LOG.info("[data-lake-init] Data lake database already exists, skipping");
                return true;
            }

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

            // Create AnalyticsDatabase referencing the platform data source
            ObjectNode details = objectMapper.createObjectNode();
            details.put("platformDataSourceId", biadmin.id());
            details.put("source", DATA_LAKE_SOURCE_KEY);
            details.put("system", true);

            AnalyticsDatabase db = new AnalyticsDatabase();
            db.setName(DATA_LAKE_NAME);
            db.setEngine("postgres");
            db.setDetailsJson(details.toString());
            db.setDescription("内置数据湖，用于存储建模输出与上传数据");
            db.setSample(false);
            db.setTimezone(java.time.ZoneId.systemDefault().getId());
            db.setMetadataSyncSchedule("0 50 * * * ? *");
            db.setCacheFieldValuesSchedule("0 50 0 * * ? *");
            db.setAutoRunQueries(true);
            db.setFullSync(true);
            db.setOnDemand(false);

            db = databaseRepository.save(db);
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
