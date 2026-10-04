package com.yuzhi.dts.platform.repository.governance;

import com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition;
import com.yuzhi.dts.platform.domain.governance.GovIndicatorVersion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface GovIndicatorVersionRepository extends JpaRepository<GovIndicatorVersion, UUID> {
    List<GovIndicatorVersion> findByIndicatorOrderByCreatedDateDesc(GovIndicatorDefinition indicator);
    Optional<GovIndicatorVersion> findByIndicatorAndVersion(GovIndicatorDefinition indicator, String version);
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from GovIndicatorVersion v where v.indicator = :indicator")
    int deleteByIndicator(@Param("indicator") GovIndicatorDefinition indicator);

    Optional<GovIndicatorVersion> findFirstByIndicatorAndStatusIgnoreCaseOrderByReleasedAtDescCreatedDateDesc(
        GovIndicatorDefinition indicator,
        String status
    );
}
