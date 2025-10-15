package com.yuzhi.dts.platform.config;

import com.yuzhi.dts.platform.domain.catalog.CatalogAccessPolicy;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.repository.catalog.CatalogAccessPolicyRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.security.RoleUtils;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.stereotype.Component;

/**
 * One-time alignment and default seeding for catalog access policies.
 * - Normalize existing allowRoles (ROLE_ prefix, upper-case, dedupe).
 * - For datasets without a policy, leave untouched (explicit policies are admin-managed).
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
    List<CatalogDataset> all;
    try {
      all = datasetRepo.findAll();
    } catch (InvalidDataAccessResourceUsageException ex) {
      log.info(
          "AccessPolicySeeder: catalog dataset table unavailable; skipping normalization until Liquibase completes ({})",
          ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage()
      );
      return;
    }
    int normalized = 0;
    for (CatalogDataset ds : all) {
      Optional<CatalogAccessPolicy> opt = policyRepo.findByDataset(ds);
      if (opt.isEmpty()) {
        // Do NOT auto-create restrictive policies; leave datasets open by role until admin configures them.
        continue;
      }
      CatalogAccessPolicy p = opt.orElseThrow();
      String before = p.getAllowRoles();
      String after = RoleUtils.normalizeCsv(before);
      if (!Objects.equals(before, after)) {
        p.setAllowRoles(after);
        policyRepo.save(p);
        normalized++;
      }
    }
    if (normalized > 0) {
      log.info("AccessPolicySeeder: normalized={}", normalized);
    } else {
      log.debug("AccessPolicySeeder: no changes");
    }
  }
}
