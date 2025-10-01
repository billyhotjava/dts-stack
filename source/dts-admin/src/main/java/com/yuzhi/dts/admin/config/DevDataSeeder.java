package com.yuzhi.dts.admin.config;

import com.yuzhi.dts.admin.domain.AdminDataset;
import com.yuzhi.dts.admin.domain.PortalMenu;
import com.yuzhi.dts.admin.domain.SystemConfig;
import com.yuzhi.dts.admin.repository.AdminDatasetRepository;
import com.yuzhi.dts.admin.repository.PortalMenuRepository;
import com.yuzhi.dts.admin.repository.SystemConfigRepository;
import com.yuzhi.dts.admin.service.OrganizationService;
import com.yuzhi.dts.admin.domain.OrganizationNode;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
public class DevDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    private final OrganizationService orgService;
    private final AdminDatasetRepository datasetRepo;
    private final PortalMenuRepository menuRepo;
    private final SystemConfigRepository sysCfgRepo;

    public DevDataSeeder(
        OrganizationService orgService,
        AdminDatasetRepository datasetRepo,
        PortalMenuRepository menuRepo,
        SystemConfigRepository sysCfgRepo
    ) {
        this.orgService = orgService;
        this.datasetRepo = datasetRepo;
        this.menuRepo = menuRepo;
        this.sysCfgRepo = sysCfgRepo;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedAll();
    }

    @Transactional
    public void seedAll() {
        seedOrgs();
        seedDatasets();
        seedMenus();
        seedConfigs();
    }

    private void seedOrgs() {
        if (!orgService.findTree().isEmpty()) return;
        OrganizationNode root = orgService.create("数据与智能中心", "DATA_TOP_SECRET", null, "李雷", "13800000001", "统筹企业数据治理、开发与运营能力");
        orgService.create("数据平台组", "DATA_SECRET", root.getId(), "韩梅", "13800000002", "负责数据平台建设与稳定性");
        orgService.create("数据治理组", "DATA_SECRET", root.getId(), "王雪", "13800000003", "制定数据标准与安全策略");
        log.info("Seeded organizations");
    }

    private void seedDatasets() {
        if (!datasetRepo.findAll().isEmpty()) return;
        Long platformOrgId = orgService.findTree().stream().findFirst().map(OrganizationNode::getChildren).filter(l -> !l.isEmpty()).map(l -> l.get(0).getId()).orElse(null);
        saveDataset("ODS 订单表", "ods_orders", "订单 ODS 原始表", "DATA_INTERNAL", platformOrgId, true, 1_000_000L);
        saveDataset("DWD 订单明细", "dwd_order_detail", "订单明细 DWD 表", "DATA_SECRET", platformOrgId, false, 520_000L);
        saveDataset("DIM 客户维表", "dim_customer", "客户维度表", "DATA_PUBLIC", platformOrgId, true, 50_000L);
        log.info("Seeded datasets");
    }

    private void saveDataset(String name, String code, String desc, String level, Long ownerOrgId, boolean shared, Long rows) {
        Optional<AdminDataset> existing = datasetRepo.findByBusinessCode(code);
        if (existing.isPresent()) return;
        AdminDataset d = new AdminDataset();
        d.setName(name);
        d.setBusinessCode(code);
        d.setDescription(desc);
        d.setDataLevel(level);
        d.setOwnerOrgId(ownerOrgId);
        d.setIsInstituteShared(shared);
        d.setRowCount(rows);
        datasetRepo.save(d);
    }

    private void seedMenus() {
        if (!menuRepo.findByDeletedFalseAndParentIsNullOrderBySortOrderAscIdAsc().isEmpty()) return;
        PortalMenu home = new PortalMenu();
        home.setName("首页");
        home.setPath("/home");
        home.setSortOrder(1);
        menuRepo.save(home);

        PortalMenu assets = new PortalMenu();
        assets.setName("数据资产");
        assets.setPath("/assets");
        assets.setSortOrder(2);
        menuRepo.save(assets);

        PortalMenu governance = new PortalMenu();
        governance.setName("数据治理");
        governance.setPath("/governance");
        governance.setSortOrder(3);
        menuRepo.save(governance);

        PortalMenu sys = new PortalMenu();
        sys.setName("系统管理");
        sys.setPath("/system");
        sys.setSortOrder(9);
        menuRepo.save(sys);

        log.info("Seeded portal menus");
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
