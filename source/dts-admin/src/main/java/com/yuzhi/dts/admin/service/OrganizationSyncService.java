package com.yuzhi.dts.admin.service;

import com.yuzhi.dts.admin.config.MdmGatewayProperties;
import com.yuzhi.dts.admin.service.mdm.MdmGatewayService;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.boot.context.event.ApplicationReadyEvent;

@Service
public class OrganizationSyncService {

    private static final Logger LOG = LoggerFactory.getLogger(OrganizationSyncService.class);
    private static final Duration RETRY_DELAY = Duration.ofSeconds(5);
    private static final int MAX_ATTEMPTS = 12;

    private final OrganizationService organizationService;
    private final MdmGatewayService mdmGatewayService;
    private final MdmGatewayProperties mdmGatewayProperties;
    private final JdbcTemplate jdbcTemplate;
    private final ScheduledExecutorService retryExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "organization-sync-init");
        t.setDaemon(true);
        return t;
    });
    private volatile boolean bootstrapComplete = false;

    public OrganizationSyncService(
        OrganizationService organizationService,
        MdmGatewayService mdmGatewayService,
        MdmGatewayProperties mdmGatewayProperties,
        JdbcTemplate jdbcTemplate
    ) {
        this.organizationService = organizationService;
        this.mdmGatewayService = mdmGatewayService;
        this.mdmGatewayProperties = mdmGatewayProperties;
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initializeDefaults() {
        scheduleEnsure(0, "initial trigger");
    }

    public void ensureUnassignedRoot() {
        organizationService.ensureUnassignedRoot();
    }

    public Optional<MdmGatewayService.PullResult> syncAll() {
        organizationService.ensureUnassignedRoot();
        Optional<MdmGatewayService.PullResult> pullResult = triggerUpstreamPull();
        organizationService.pushTreeToKeycloak();
        return pullResult;
    }

    private void scheduleEnsure(int attempt, String reason) {
        if (bootstrapComplete) {
            return;
        }
        if (attempt == 0) {
            retryExecutor.execute(() -> tryEnsure(attempt));
            return;
        }
        long delaySeconds = Math.min(30L, RETRY_DELAY.getSeconds() * (attempt + 1));
        LOG.debug("Deferring organization bootstrap (attempt {}, reason: {}) for {}s", attempt, reason, delaySeconds);
        retryExecutor.schedule(() -> tryEnsure(attempt), delaySeconds, TimeUnit.SECONDS);
    }

    private void tryEnsure(int attempt) {
        if (bootstrapComplete) {
            return;
        }
        if (!organizationTableExists()) {
            if (attempt >= MAX_ATTEMPTS) {
                LOG.error("organization_node table still missing after {} attempts; stop retrying", attempt);
                return;
            }
            scheduleEnsure(attempt + 1, "organization_node table not ready");
            return;
        }
        try {
            organizationService.ensureUnassignedRoot();
            bootstrapComplete = true;
        } catch (RuntimeException ex) {
            if (attempt >= MAX_ATTEMPTS) {
                LOG.error("Failed to ensure default unassigned organization after {} attempts", attempt + 1, ex);
            } else {
                LOG.warn("Failed to ensure default unassigned organization (attempt {}): {}", attempt + 1, ex.getMessage());
                scheduleEnsure(attempt + 1, ex.getClass().getSimpleName());
            }
        }
    }

    private boolean organizationTableExists() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = current_schema() AND LOWER(table_name) = ?",
                Integer.class,
                "organization_node"
            );
            return count != null && count > 0;
        } catch (DataAccessException ex) {
            LOG.debug("Failed to query information_schema for organization_node: {}", ex.getMessage());
            return false;
        }
    }

    @PreDestroy
    public void shutdown() {
        retryExecutor.shutdownNow();
    }

    private Optional<MdmGatewayService.PullResult> triggerUpstreamPull() {
        if (mdmGatewayProperties == null || !mdmGatewayProperties.isEnabled()) {
            return Optional.empty();
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("dataRange", mdmGatewayProperties.getRegistry().getDataRange());
            String dataType = mdmGatewayProperties.getRegistry().getDataType();
            // 手工同步直接请求全量数据；若配置为 sync-demand 则改为 full
            if ("sync-demand".equalsIgnoreCase(dataType) || "sync_demand".equalsIgnoreCase(dataType)) {
                dataType = "full";
            }
            payload.put("dataType", dataType);
            payload.put("sendTime", System.currentTimeMillis() / 1000);
            return Optional.of(mdmGatewayService.triggerUpstreamPull(payload));
        } catch (RuntimeException ex) {
            LOG.warn("mdm upstream pull failed: {}", ex.getMessage());
            return Optional.empty();
        }
    }
}
