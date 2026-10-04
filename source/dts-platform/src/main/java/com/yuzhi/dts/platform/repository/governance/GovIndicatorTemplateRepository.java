package com.yuzhi.dts.platform.repository.governance;

import com.yuzhi.dts.platform.domain.governance.GovIndicatorTemplate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GovIndicatorTemplateRepository extends JpaRepository<GovIndicatorTemplate, UUID> {
    List<GovIndicatorTemplate> findByDomainAndEnabledTrue(String domain);
    List<GovIndicatorTemplate> findByEnabledTrue();
    Optional<GovIndicatorTemplate> findByCode(String code);
    Optional<GovIndicatorTemplate> findFirstByCodeIgnoreCase(String code);
}
