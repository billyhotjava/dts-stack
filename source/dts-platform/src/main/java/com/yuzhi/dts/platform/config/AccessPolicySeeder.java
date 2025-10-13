package com.yuzhi.dts.platform.config;

import com.yuzhi.dts.platform.domain.catalog.CatalogAccessPolicy;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.repository.catalog.CatalogAccessPolicyRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.security.RoleUtils;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * One-time alignment and default seeding for catalog access policies.
 * - Normalize existing allowRoles (ROLE_ prefix, upper-case, dedupe) and align to dataset scope.
 * - For datasets without a policy, create a default policy with six standard roles for its scope.
 * The runner is idempotent and safe to run multiple times.
 */
@Component
public class AccessPolicySeeder implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(AccessPolicySeeder.class);

  private final CatalogDatasetRepository datasetRepo;
  private final CatalogAccessPolicyRepository policyRepo;

  public AccessPolicySeeder(
      CatalogDatasetRepository datasetRepo,
      CatalogAccessPolicyRepository policyRepo
  ) {
    this.datasetRepo = datasetRepo;
    this.policyRepo = policyRepo;
  }

  @Override
  public void run(ApplicationArguments args) {
    List<CatalogDataset> all = datasetRepo.findAll();
    int created = 0, normalized = 0;
    for (CatalogDataset ds : all) {
      Optional<CatalogAccessPolicy> opt = policyRepo.findByDataset(ds);
      String scope = optionalUpper(ds.getScope());
      if (opt.isEmpty()) {
        // Do NOT auto-create restrictive policies; leave datasets open by role until admin configures them.
        continue;
      }
      CatalogAccessPolicy p = opt.orElseThrow();
      String before = p.getAllowRoles();
      String after = RoleUtils.normalizeAndAlignToScope(before, scope);
      if (!Objects.equals(before, after)) {
        p.setAllowRoles(after);
        policyRepo.save(p);
        normalized++;
      }
    }
    if (created > 0 || normalized > 0) {
      log.info("AccessPolicySeeder: created={}, normalized={}", created, normalized);
    } else {
      log.debug("AccessPolicySeeder: no changes");
    }
  }

  private String optionalUpper(String v) {
    return v == null ? null : v.trim().toUpperCase(Locale.ROOT);
  }

  private String defaultRolesForScope(String scope) {
    if ("DEPT".equals(scope)) {
      return "ROLE_DEPT_DATA_VIEWER,ROLE_DEPT_DATA_DEV,ROLE_DEPT_DATA_OWNER";
    }
    if ("INST".equals(scope)) {
      return "ROLE_INST_DATA_VIEWER,ROLE_INST_DATA_DEV,ROLE_INST_DATA_OWNER";
    }
    return null;
  }
}

