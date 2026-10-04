package com.yuzhi.dts.platform.repository.goldenchain;

import com.yuzhi.dts.platform.domain.goldenchain.GoldenChainStageSnapshotRecord;
import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GoldenChainStageSnapshotRecordRepository extends JpaRepository<GoldenChainStageSnapshotRecord, UUID> {
    List<GoldenChainStageSnapshotRecord> findByChainInstanceIdOrderByStageSequenceAsc(UUID chainInstanceId);

    Optional<GoldenChainStageSnapshotRecord> findFirstByChainInstanceIdAndStage(UUID chainInstanceId, GoldenChainStage stage);
}
