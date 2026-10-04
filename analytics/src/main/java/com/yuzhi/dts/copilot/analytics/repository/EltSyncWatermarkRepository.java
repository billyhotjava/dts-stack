package com.yuzhi.dts.copilot.analytics.repository;

import com.yuzhi.dts.copilot.analytics.domain.EltSyncWatermark;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EltSyncWatermarkRepository extends JpaRepository<EltSyncWatermark, Long> {

    Optional<EltSyncWatermark> findByTargetTable(String targetTable);
}
