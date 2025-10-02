package com.yuzhi.dts.admin.config;

import com.yuzhi.dts.admin.domain.AdminDataset;
import com.yuzhi.dts.admin.domain.OrganizationNode;
import com.yuzhi.dts.admin.domain.PortalMenu;
import com.yuzhi.dts.admin.domain.PortalMenuVisibility;
import com.yuzhi.dts.admin.domain.SystemConfig;
import com.yuzhi.dts.admin.repository.AdminDatasetRepository;
import com.yuzhi.dts.admin.repository.OrganizationRepository;
import com.yuzhi.dts.admin.repository.PortalMenuRepository;
import com.yuzhi.dts.admin.repository.SystemConfigRepository;
import com.yuzhi.dts.admin.service.OrganizationService;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.transaction.annotation.Transactional;

@Configuration
public class DevDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    private final OrganizationService orgService;
    private final OrganizationRepository organizationRepository;
    private final AdminDatasetRepository datasetRepo;
    private final PortalMenuRepository menuRepo;
    private final SystemConfigRepository sysCfgRepo;
    private final Environment env;

    public DevDataSeeder(
        OrganizationService orgService,
        OrganizationRepository organizationRepository,
        AdminDatasetRepository datasetRepo,
        PortalMenuRepository menuRepo,
        SystemConfigRepository sysCfgRepo,
        Environment env
    ) {
        this.orgService = orgService;
        this.organizationRepository = organizationRepository;
        this.datasetRepo = datasetRepo;
        this.menuRepo = menuRepo;
        this.sysCfgRepo = sysCfgRepo;
        this.env = env;
    }

    @PostConstruct
    @Transactional
    public void seed() {
        if (!isDevProfile()) {
            return;
        }
        seedOrganizations();
        seedDatasets();
        seedMenus();
        seedConfigs();
    }

    private boolean isDevProfile() {
        return env != null && env.acceptsProfiles(org.springframework.core.env.Profiles.of("dev", "local"));
    }

    private void seedOrganizations() {
        if (!organizationRepository.findAll().isEmpty()) {
            return;
        }
        OrganizationNode root = orgService.create("总部", "GENERAL", null, "王强", "13900000001", "集团总部");
        orgService.create("生产中心", "GENERAL", root.getId(), "刘洋", "13900000002", "生产业务条线");
        orgService.create("研究院", "GENERAL", root.getId(), "周星", "13900000003", "数据产品研发");
        log.info("Seeded default organizations");
    }

    private void seedDatasets() {
        if (!datasetRepo.findAll().isEmpty()) {
            return;
        }
        addDataset("ds-001", "客户主数据", "MDM", "部门共享客户主数据", "INTERNAL", 1L, true, 1200L);
        addDataset("ds-002", "销售主题指标", "SalesMart", "销售指标日汇总", "SECRET", 1L, false, 2400L);
        addDataset("ds-003", "供应链事件", "SupplyChain", "供应链状态实时事件", "INTERNAL", 1L, false, 3600L);
        log.info("Seeded default datasets");
    }

    private void addDataset(
        String code,
        String name,
        String owner,
        String description,
        String level,
        Long ownerOrgId,
        boolean shared,
        Long rows
    ) {
        AdminDataset d = new AdminDataset();
        d.setBusinessCode(code);
        d.setName(name);
        d.setOwner(owner);
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
        PortalMenu home = new PortalMenu();
        home.setName("首页");
        home.setPath("/home");
        home.setSortOrder(1);
        applyDefaultVisibility(home);
        menuRepo.save(home);

        PortalMenu assets = new PortalMenu();
        assets.setName("数据资产");
        assets.setPath("/assets");
        assets.setSortOrder(2);
        applyDefaultVisibility(assets);
        menuRepo.save(assets);

        PortalMenu governance = new PortalMenu();
        governance.setName("数据治理");
        governance.setPath("/governance");
        governance.setSortOrder(3);
        applyDefaultVisibility(governance);
        menuRepo.save(governance);

        PortalMenu sys = new PortalMenu();
        sys.setName("系统管理");
        sys.setPath("/system");
        sys.setSortOrder(9);
        applyDefaultVisibility(sys);
        menuRepo.save(sys);

        log.info("Seeded portal menus");
    }

    private void applyDefaultVisibility(PortalMenu menu) {
        menu.addVisibility(newVisibility(menu, "ROLE_OP_ADMIN"));
        menu.addVisibility(newVisibility(menu, "ROLE_USER"));
    }

    private PortalMenuVisibility newVisibility(PortalMenu menu, String role) {
        PortalMenuVisibility visibility = new PortalMenuVisibility();
        visibility.setMenu(menu);
        visibility.setRoleCode(role);
        visibility.setDataLevel("INTERNAL");
        return visibility;
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
