package com.yuzhi.dts.platform.service.goldenchain;

import com.yuzhi.dts.platform.domain.goldenchain.GoldenChainInstance;
import com.yuzhi.dts.platform.domain.goldenchain.GoldenChainStageSnapshotRecord;
import com.yuzhi.dts.platform.repository.goldenchain.GoldenChainInstanceRepository;
import com.yuzhi.dts.platform.repository.goldenchain.GoldenChainStageSnapshotRecordRepository;
import com.yuzhi.dts.platform.service.goldenchain.dto.GoldenChainDetailResponse;
import com.yuzhi.dts.platform.service.goldenchain.dto.GoldenChainStageResponse;
import com.yuzhi.dts.platform.service.goldenchain.dto.GoldenChainSummaryResponse;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GoldenChainQueryService {

    private final GoldenChainInstanceRepository instanceRepository;
    private final GoldenChainStageSnapshotRecordRepository snapshotRepository;

    public GoldenChainQueryService(
        GoldenChainInstanceRepository instanceRepository,
        GoldenChainStageSnapshotRecordRepository snapshotRepository
    ) {
        this.instanceRepository = instanceRepository;
        this.snapshotRepository = snapshotRepository;
    }

    public List<GoldenChainSummaryResponse> listChains() {
        return instanceRepository.findByEnabledTrueOrderByLastModifiedDateDesc().stream().map(this::toSummary).toList();
    }

    public Optional<GoldenChainDetailResponse> findDetailByChainKey(String chainKey) {
        return instanceRepository.findFirstByChainKeyAndEnabledTrue(chainKey).map(this::toDetail);
    }

    private GoldenChainSummaryResponse toSummary(GoldenChainInstance instance) {
        return new GoldenChainSummaryResponse(
            instance.getChainKey(),
            instance.getDisplayName(),
            instance.getSourceKind(),
            instance.getCurrentStage(),
            instance.getCurrentStage().label(),
            instance.getLifecycleStatus(),
            instance.getOwner()
        );
    }

    private GoldenChainDetailResponse toDetail(GoldenChainInstance instance) {
        List<GoldenChainStageResponse> stages = snapshotRepository
            .findByChainInstanceIdOrderByStageSequenceAsc(instance.getId())
            .stream()
            .map(this::toStageResponse)
            .toList();
        return new GoldenChainDetailResponse(
            instance.getChainKey(),
            instance.getDisplayName(),
            instance.getSourceKind(),
            instance.getCurrentStage(),
            instance.getCurrentStage().label(),
            instance.getLifecycleStatus(),
            instance.getOwner(),
            stages
        );
    }

    private GoldenChainStageResponse toStageResponse(GoldenChainStageSnapshotRecord snapshot) {
        return new GoldenChainStageResponse(
            snapshot.getStage(),
            snapshot.getStage().label(),
            snapshot.getStatus(),
            snapshot.getOwner(),
            snapshot.getEvidenceRef(),
            snapshot.getBlockerReason(),
            nextAction(snapshot)
        );
    }

    private String nextAction(GoldenChainStageSnapshotRecord snapshot) {
        if (snapshot.getStatus() == GoldenChainStageStatus.BLOCKED && snapshot.getBlockerCode() != null) {
            return snapshot.getBlockerCode().remediation();
        }
        if (snapshot.getStatus() == GoldenChainStageStatus.READY) {
            return "已完成";
        }
        if (snapshot.getStatus() == GoldenChainStageStatus.SKIPPED) {
            return "已跳过";
        }
        return "等待上游阶段完成";
    }
}
