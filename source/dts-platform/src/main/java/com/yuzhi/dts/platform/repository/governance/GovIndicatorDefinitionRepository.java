package com.yuzhi.dts.platform.repository.governance;

import com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GovIndicatorDefinitionRepository extends JpaRepository<GovIndicatorDefinition, UUID> {
    Optional<GovIndicatorDefinition> findFirstByCodeIgnoreCase(String code);
}

