package com.yuzhi.dts.platform.service.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogClassificationPropagationJob;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogDatasetLineage;
import com.yuzhi.dts.platform.repository.catalog.CatalogClassificationPropagationJobRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetLineageRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogClassificationPropagationJobService {

    private static final List<String> RETRYABLE = List.of("PENDING", "RETRY");

    private final CatalogClassificationPropagationJobRepository jobRepository;
    private final CatalogDatasetLineageRepository lineageRepository;
    private final CatalogDatasetRepository datasetRepository;
    private final CatalogClassificationPropagationService propagationService;

    public CatalogClassificationPropagationJobService(
        CatalogClassificationPropagationJobRepository jobRepository,
        CatalogDatasetLineageRepository lineageRepository,
        CatalogDatasetRepository datasetRepository,
        CatalogClassificationPropagationService propagationService
    ) {
        this.jobRepository = jobRepository;
        this.lineageRepository = lineageRepository;
        this.datasetRepository = datasetRepository;
        this.propagationService = propagationService;
    }

    @Transactional
    public int enqueueForUpstream(UUID upstreamDatasetId, String triggerType, String triggerRef) {
        int enqueued = 0;
        for (CatalogDatasetLineage edge : lineageRepository.findByUpstreamDatasetId(upstreamDatasetId)) {
            if (edge.getValidTo() == null && enqueue(edge.getDownstreamDatasetId(), triggerType, triggerRef)) {
                enqueued++;
            }
        }
        return enqueued;
    }

    @Transactional
    public boolean enqueue(UUID downstreamDatasetId, String triggerType, String triggerRef) {
        CatalogDataset target = datasetRepository.findById(downstreamDatasetId).orElse(null);
        if (target == null) {
            return false;
        }
        String assetKey = CatalogAssetKey.dataset(target);
        String idempotencyKey = sha256(downstreamDatasetId + ":" + triggerType + ":" + triggerRef);
        Instant now = Instant.now();
        return (
            jobRepository.insertPendingIfAbsent(
                UUID.randomUUID(),
                idempotencyKey,
                downstreamDatasetId,
                assetKey,
                triggerType,
                triggerRef,
                "system",
                now
            ) ==
            1
        );
    }

    @Scheduled(fixedDelayString = "${dts.catalog.classification.propagation-delay-ms:5000}")
    public void processPending() {
        List<CatalogClassificationPropagationJob> jobs =
            jobRepository.findTop50ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedDateAsc(
                RETRYABLE,
                Instant.now()
            );
        for (CatalogClassificationPropagationJob job : jobs) {
            process(job);
        }
    }

    @Transactional
    public CatalogClassificationPropagationJob replay(UUID jobId) {
        CatalogClassificationPropagationJob job = jobRepository
            .findById(jobId)
            .orElseThrow(() -> new IllegalArgumentException("Propagation job not found: " + jobId));
        job.setStatus("PENDING");
        job.setNextAttemptAt(Instant.now());
        job.setLastError(null);
        return jobRepository.save(job);
    }

    private void process(CatalogClassificationPropagationJob job) {
        job.setStatus("RUNNING");
        job.setAttempts(job.getAttempts() + 1);
        jobRepository.save(job);
        try {
            CatalogClassificationPropagationService.PropagationResult result = propagationService.recompute(
                job.getTargetDatasetId(),
                job.getTriggerRef()
            );
            job.setAffectedSubjects(result.affectedFields() + 1);
            job.setStatus("DONE");
            job.setLastError(null);
            job.setNextAttemptAt(null);
        } catch (CatalogClassificationException ex) {
            job.setLastError(truncate(ex.getCode() + ": " + ex.getMessage()));
            if ("PENDING_LINEAGE".equals(ex.getCode()) || "CLASSIFICATION_LINEAGE_CYCLE".equals(ex.getCode())) {
                job.setStatus("BLOCKED");
                job.setNextAttemptAt(null);
            } else if (job.getAttempts() >= 5) {
                job.setStatus("FAILED");
                job.setNextAttemptAt(null);
            } else {
                job.setStatus("RETRY");
                job.setNextAttemptAt(Instant.now().plus(Duration.ofSeconds(1L << job.getAttempts())));
            }
        } catch (RuntimeException ex) {
            job.setLastError(truncate(ex.getMessage()));
            job.setStatus(job.getAttempts() >= 5 ? "FAILED" : "RETRY");
            job.setNextAttemptAt(
                job.getAttempts() >= 5 ? null : Instant.now().plus(Duration.ofSeconds(1L << job.getAttempts()))
            );
        }
        jobRepository.save(job);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to calculate propagation idempotency key", ex);
        }
    }

    private String truncate(String value) {
        if (value == null || value.length() <= 2048) {
            return value;
        }
        return value.substring(0, 2048);
    }
}
