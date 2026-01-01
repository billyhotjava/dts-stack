package com.yuzhi.dts.platform.repository.governance;

import com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition;
import com.yuzhi.dts.platform.domain.governance.GovIndicatorReference;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GovIndicatorReferenceRepository extends JpaRepository<GovIndicatorReference, UUID> {
    List<GovIndicatorReference> findByIndicatorOrderByCreatedDateAsc(GovIndicatorDefinition indicator);
    Optional<GovIndicatorReference> findFirstByIndicatorAndRefTypeIgnoreCaseAndRefTargetIgnoreCase(
        GovIndicatorDefinition indicator,
        String refType,
        String refTarget
    );
}

