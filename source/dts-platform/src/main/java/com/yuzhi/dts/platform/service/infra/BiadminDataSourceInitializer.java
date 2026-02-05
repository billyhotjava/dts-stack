package com.yuzhi.dts.platform.service.infra;

import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
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

    private final InfraDataSourceRepository dataSourceRepository;
    private final InfraSecretService secretService;

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
        InfraSecretService secretService
    ) {
        this.dataSourceRepository = dataSourceRepository;
        this.secretService = secretService;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
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
            // 检查是否已存在 biadmin 数据源
            boolean exists = dataSourceRepository.findAll()
                .stream()
                .anyMatch(ds -> ds != null && (
                    BIADMIN_DATASOURCE_NAME.equalsIgnoreCase(ds.getName()) ||
                    (ds.getJdbcUrl() != null && ds.getJdbcUrl().contains("/" + pgDbBiadmin))
                ));

            if (exists) {
                LOG.info("[biadmin-init] biadmin datasource already exists, skipping initialization");
                return;
            }

            // 直接创建实体，绕过安全检查（系统启动时无安全上下文）
            String jdbcUrl = String.format("jdbc:postgresql://%s:%s/%s", pgHost, pgPort, pgDbBiadmin);

            InfraDataSource entity = new InfraDataSource();
            entity.setName(BIADMIN_DATASOURCE_NAME);
            entity.setType(BIADMIN_TYPE);
            entity.setJdbcUrl(jdbcUrl);
            entity.setUsername(pgUserBiadmin);
            entity.setDescription("数仓专用数据库，用于存储 dbt 模型输出和即席查询");
            entity.setStatus(STATUS_ACTIVE);
            entity.setCreatedBy("system");
            entity.setLastModifiedBy("system");

            // 加密保存密码
            secretService.applySecrets(entity, Map.of("password", pgPwdBiadmin));

            dataSourceRepository.save(entity);
            LOG.info("[biadmin-init] Successfully registered biadmin datasource: {}", jdbcUrl);

        } catch (Exception ex) {
            LOG.warn("[biadmin-init] Failed to initialize biadmin datasource: {}", ex.getMessage());
            // 不抛出异常，避免阻止系统启动
        }
    }
}
