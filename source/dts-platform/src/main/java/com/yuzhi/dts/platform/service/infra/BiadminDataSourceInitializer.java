package com.yuzhi.dts.platform.service.infra;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 自动注册 biadmin 数据源（数仓专用）
 * 在系统启动时检查并创建 biadmin 数据源，使其可用于数据资产和即席查询
 */
@Component
public class BiadminDataSourceInitializer {

    private static final Logger LOG = LoggerFactory.getLogger(BiadminDataSourceInitializer.class);
    private static final String BIADMIN_DATASOURCE_NAME = "数仓 (biadmin)";
    private static final String BIADMIN_TYPE = "postgres";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final UUID LEGACY_BIADMIN_DATA_SOURCE_ID = UUID.fromString(
        "a0000000-0000-0000-0000-000000000001"
    );
    private static final String SOURCE_ADMIN_DEFAULT_DATA_LAKE = "admin-default-data-lake";
    private static final String SOURCE_ADMIN_DATA_LAKE = "admin-data-lake";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final InfraDataSourceRepository dataSourceRepository;
    private final InfraSecretService secretService;
    private final ObjectMapper objectMapper;

    @Value("${PG_HOST:dts-pg}")
    private String pgHost;

    @Value("${PG_PORT:5432}")
    private String pgPort;

    @Value("${PG_DB_BIADMIN:biadmin}")
    private String pgDbBiadmin;

    @Value("${PG_USER_BIADMIN:biadmin}")
    private String pgUserBiadmin;

    @Value("${PG_PWD_BIADMIN:}")
    private String pgPwdBiadmin;

    @Value("${dts.biadmin.datasource.auto-register:true}")
    private boolean autoRegister;

    public BiadminDataSourceInitializer(
        InfraDataSourceRepository dataSourceRepository,
        InfraSecretService secretService,
        ObjectMapper objectMapper
    ) {
        this.dataSourceRepository = dataSourceRepository;
        this.secretService = secretService;
        this.objectMapper = objectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public void initializeBiadminDataSource() {
        if (!autoRegister) {
            LOG.info("[biadmin-init] Auto-registration disabled, skipping biadmin datasource initialization");
            return;
        }

        if (!StringUtils.hasText(pgPwdBiadmin)) {
            LOG.warn("[biadmin-init] PG_PWD_BIADMIN not configured, skipping biadmin datasource initialization");
            return;
        }

        try {
            String jdbcUrl = String.format("jdbc:postgresql://%s:%s/%s", pgHost, pgPort, pgDbBiadmin);

            // 检查是否已存在 biadmin 数据源
            InfraDataSource existing = dataSourceRepository.findAll()
                .stream()
                .filter(this::isExistingBiadminSource)
                .findFirst()
                .orElse(null);

            if (existing != null) {
                // 记录已存在，但可能是 Liquibase seed 创建的（无密码），补写密码
                if (existing.getSecureProps() == null || existing.getSecureProps().length == 0) {
                    LOG.info("[biadmin-init] biadmin datasource exists but has no password, patching secrets");
                    secretService.applySecrets(existing, Map.of("password", pgPwdBiadmin));
                    dataSourceRepository.save(existing);
                    LOG.info("[biadmin-init] Successfully patched biadmin datasource password");
                } else {
                    LOG.info("[biadmin-init] biadmin datasource already exists with secrets, skipping");
                }
                return;
            }

            // 直接创建实体，绕过安全检查（系统启动时无安全上下文）
            InfraDataSource entity = new InfraDataSource();
            entity.setName(BIADMIN_DATASOURCE_NAME);
            entity.setType(BIADMIN_TYPE);
            entity.setJdbcUrl(jdbcUrl);
            entity.setUsername(pgUserBiadmin);
            entity.setDescription("数仓专用数据库，用于存储 dbt 模型输出和即席查询");
            entity.setStatus(STATUS_ACTIVE);
            entity.setCreatedBy("system");
            entity.setLastModifiedBy("system");
            // Mark as system-managed so platform UI disables edit/delete
            entity.setProps("{\"source\":\"admin-data-lake\",\"system\":true}");

            // 加密保存密码
            secretService.applySecrets(entity, Map.of("password", pgPwdBiadmin));

            dataSourceRepository.save(entity);
            LOG.info("[biadmin-init] Successfully registered biadmin datasource: {}", jdbcUrl);

        } catch (Exception ex) {
            LOG.warn("[biadmin-init] Failed to initialize biadmin datasource: {}", ex.getClass().getSimpleName());
            // 不抛出异常，避免阻止系统启动
        }
    }

    private boolean isExistingBiadminSource(InfraDataSource source) {
        if (source == null) {
            return false;
        }
        if (LEGACY_BIADMIN_DATA_SOURCE_ID.equals(source.getId())) {
            return true;
        }
        Map<String, Object> props = parseProps(source.getProps());
        String marker = text(props.get("source"));
        if (SOURCE_ADMIN_DEFAULT_DATA_LAKE.equalsIgnoreCase(marker) && isTrue(props.get("defaultLake"))) {
            return true;
        }
        return SOURCE_ADMIN_DATA_LAKE.equalsIgnoreCase(marker) && isTrue(props.get("system"));
    }

    private Map<String, Object> parseProps(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(raw, MAP_TYPE);
        } catch (Exception ex) {
            LOG.warn("[biadmin-init] Ignoring invalid managed data source props");
            return Map.of();
        }
    }

    private String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private boolean isTrue(Object value) {
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(text(value));
    }
}
