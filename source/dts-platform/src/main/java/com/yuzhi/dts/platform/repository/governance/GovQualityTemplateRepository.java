package com.yuzhi.dts.platform.repository.governance;

import com.yuzhi.dts.platform.domain.governance.GovQualityTemplate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GovQualityTemplateRepository extends JpaRepository<GovQualityTemplate, UUID> {
    Optional<GovQualityTemplate> findByCode(String code);
    List<GovQualityTemplate> findByEnabledTrue();
    boolean existsByCodeIgnoreCase(String code);
}
