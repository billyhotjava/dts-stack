package com.yuzhi.dts.admin.config;

import com.yuzhi.dts.admin.domain.AdminDataset;
import com.yuzhi.dts.admin.domain.OrganizationNode;
import com.yuzhi.dts.admin.domain.SystemConfig;
import com.yuzhi.dts.admin.repository.AdminDatasetRepository;
import com.yuzhi.dts.admin.repository.OrganizationRepository;
import com.yuzhi.dts.admin.repository.PortalMenuRepository;
import com.yuzhi.dts.admin.repository.SystemConfigRepository;
import com.yuzhi.dts.admin.service.OrganizationService;
import com.yuzhi.dts.admin.service.PortalMenuService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.core.env.Environment;
import org.springframework.transaction.annotation.Transactional;

@Configuration
public class DevDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    private final OrganizationService orgService;
    private final OrganizationRepository organizationRepository;
    private final AdminDatasetRepository datasetRepo;
    private final PortalMenuRepository menuRepo;
    private final PortalMenuService portalMenuService;
    private final SystemConfigRepository sysCfgRepo;
    private final Environment env;

    public DevDataSeeder(
        OrganizationService orgService,
        OrganizationRepository organizationRepository,
        AdminDatasetRepository datasetRepo,
        PortalMenuRepository menuRepo,
        PortalMenuService portalMenuService,
        SystemConfigRepository sysCfgRepo,
        Environment env
    ) {
        this.orgService = orgService;
        this.organizationRepository = organizationRepository;
        this.datasetRepo = datasetRepo;
        this.menuRepo = menuRepo;
        this.portalMenuService = portalMenuService;
        this.sysCfgRepo = sysCfgRepo;
        this.env = env;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedOnStartup() {
        seedAll();
    }

    @Transactional
    public void seedAll() {
        if (!isDevProfile()) {
            return;
        }
        safeSeed(this::seedOrganizations, "organizations");
        safeSeed(this::seedDatasets, "datasets");
        safeSeed(this::seedMenus, "menus");
        safeSeed(this::seedConfigs, "configs");
    }

    private void safeSeed(Runnable seeder, String label) {
        try {
            seeder.run();
        } catch (DataAccessException ex) {
            log.warn("Skip dev {} seeding: {}", label, ex.getMostSpecificCause().getMessage());
        }
    }

    private boolean isDevProfile() {
        return env != null && env.acceptsProfiles(org.springframework.core.env.Profiles.of("dev", "local"));
    }

    private void seedOrganizations() {
        // Only ensure a single root "S10" and its child "待分配"
        orgService.ensureUnassignedRoot();
        log.info("Ensured minimal organizations: S10 -> 待分配");
    }

    private void seedDatasets() {
        if (!datasetRepo.findAll().isEmpty()) {
            return;
        }
        addDataset("ds-001", "客户主数据", "部门共享客户主数据", "INTERNAL", 1L, true, 1200L);
        addDataset("ds-002", "销售主题指标", "销售指标日汇总", "SECRET", 1L, false, 2400L);
        addDataset("ds-003", "供应链事件", "供应链状态实时事件", "INTERNAL", 1L, false, 3600L);
        log.info("Seeded default datasets");
    }

    private void addDataset(
        String code,
        String name,
        String description,
        String level,
        Long ownerOrgId,
        boolean shared,
        Long rows
    ) {
        AdminDataset d = new AdminDataset();
        d.setBusinessCode(code);
        d.setName(name);
        d.setDescription(description);
        d.setDataLevel(level);
        d.setOwnerOrgId(ownerOrgId);
        d.setIsInstituteShared(shared);
        d.setRowCount(rows);
        d.setCreatedDate(Instant.now());
        d.setCreatedBy("system");
        datasetRepo.save(d);
    }

    private void seedMenus() {
        if (!menuRepo.findByDeletedFalseAndParentIsNullOrderBySortOrderAscIdAsc().isEmpty()) return;
        portalMenuService.resetMenusToSeed();
        log.info("Seeded portal menus from seed data");
    }

    private void seedConfigs() {
        if (sysCfgRepo.findAll().isEmpty()) {
            SystemConfig c1 = new SystemConfig();
            c1.setKey("cluster.mode");
            c1.setValue("production");
            c1.setDescription("Iceberg 集群运行模式");
            sysCfgRepo.save(c1);

            SystemConfig c2 = new SystemConfig();
            c2.setKey("minio.endpoint");
            c2.setValue("https://minio.internal");
            c2.setDescription("对象存储地址");
            sysCfgRepo.save(c2);

            SystemConfig c3 = new SystemConfig();
            c3.setKey("airflow.deployment");
            c3.setValue("v2.9.0");
            c3.setDescription("调度集群版本");
            sysCfgRepo.save(c3);

            log.info("Seeded system configs");
        }
    }
}
