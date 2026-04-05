package com.yuzhi.dts.platform.repository.governance;

import com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GovIndicatorDefinitionRepository extends JpaRepository<GovIndicatorDefinition, UUID> {
    Optional<GovIndicatorDefinition> findFirstByCodeIgnoreCase(String code);

    List<GovIndicatorDefinition> findByDomainAndStatusNot(String domain, String excludedStatus);

    List<GovIndicatorDefinition> findByTemplateId(UUID templateId);

    List<GovIndicatorDefinition> findByIsDerivedTrue();

    List<GovIndicatorDefinition> findByStatusIn(List<String> statuses);
}

