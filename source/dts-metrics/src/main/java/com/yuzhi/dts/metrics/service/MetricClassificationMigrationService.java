package com.yuzhi.dts.metrics.service;

import com.yuzhi.dts.metrics.domain.MetricModelState;
import com.yuzhi.dts.metrics.domain.repository.MetricModelStateRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class MetricClassificationMigrationService {

    private final MetricModelStateRepository stateRepository;
    private final MetricDownstreamRegistrar downstreamRegistrar;

    public MetricClassificationMigrationService(
        MetricModelStateRepository stateRepository,
        MetricDownstreamRegistrar downstreamRegistrar
    ) {
        this.stateRepository = stateRepository;
        this.downstreamRegistrar = downstreamRegistrar;
    }

    public DryRunReport dryRun() {
        List<ModelCandidate> candidates = candidates();
        long eligible = candidates.stream().filter(candidate -> "DERIVE".equals(candidate.decision())).count();
        long blocked = candidates.size() - eligible;
        return new DryRunReport(candidates.size(), eligible, blocked, candidates, false);
    }

    public ApplyReport apply(int offset, int requestedLimit) {
        int safeOffset = Math.max(0, offset);
        int safeLimit = Math.max(1, Math.min(requestedLimit <= 0 ? 100 : requestedLimit, 500));
        List<ModelCandidate> candidates = candidates();
        List<ModelCandidate> batch = candidates
            .stream()
            .skip(safeOffset)
            .limit(safeLimit)
            .toList();
        long applied = 0;
        java.util.ArrayList<ModelApplyResult> results = new java.util.ArrayList<>();
        for (ModelCandidate candidate : batch) {
            if (!"DERIVE".equals(candidate.decision())) {
                results.add(new ModelApplyResult(candidate.modelId(), "SKIPPED", candidate.reason()));
                continue;
            }
            try {
                MetricModelState state = stateRepository
                    .findById(candidate.modelId())
                    .orElseThrow(() -> new IllegalArgumentException("Metric model state does not exist"));
                downstreamRegistrar.backfillClassifications(
                    state.getModelId(),
                    state.getActiveVersion(),
                    graph(state)
                );
                applied++;
                results.add(new ModelApplyResult(candidate.modelId(), "APPLIED", null));
            } catch (RuntimeException failure) {
                results.add(new ModelApplyResult(candidate.modelId(), "FAILED", failure.getMessage()));
            }
        }
        int nextOffset = safeOffset + batch.size();
        return new ApplyReport(
            safeOffset,
            safeLimit,
            nextOffset,
            nextOffset >= candidates.size(),
            applied,
            results
        );
    }

    private List<ModelCandidate> candidates() {
        return stateRepository
            .findAll()
            .stream()
            .sorted(Comparator.comparing(MetricModelState::getModelId))
            .map(state -> {
                if (!"PUBLISHED".equalsIgnoreCase(state.getStatus())) {
                    return new ModelCandidate(
                        state.getModelId(),
                        state.getActiveVersion(),
                        state.getStatus(),
                        "BLOCKED_NOT_PUBLISHED",
                        "Only published metric models are eligible"
                    );
                }
                if (!StringUtils.hasText(state.getActiveVersion())) {
                    return new ModelCandidate(
                        state.getModelId(),
                        null,
                        state.getStatus(),
                        "BLOCKED_MISSING_VERSION",
                        "Published metric model has no active version"
                    );
                }
                if (graph(state).isEmpty()) {
                    return new ModelCandidate(
                        state.getModelId(),
                        state.getActiveVersion(),
                        state.getStatus(),
                        "BLOCKED_MISSING_GRAPH",
                        "Metric graph snapshot is required to derive upstream classification"
                    );
                }
                return new ModelCandidate(
                    state.getModelId(),
                    state.getActiveVersion(),
                    state.getStatus(),
                    "DERIVE",
                    null
                );
            })
            .toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> graph(MetricModelState state) {
        Object graph = state.getTransientState() == null
            ? null
            : state.getTransientState().get(MetricDownstreamRegistrar.GRAPH_SNAPSHOT_STATE_KEY);
        return graph instanceof Map<?, ?> ? (Map<String, Object>) graph : Map.of();
    }

    public record ModelCandidate(
        String modelId,
        String version,
        String lifecycleStatus,
        String decision,
        String reason
    ) {}

    public record DryRunReport(
        long total,
        long eligible,
        long blocked,
        List<ModelCandidate> candidates,
        boolean productionWrites
    ) {}

    public record ModelApplyResult(String modelId, String status, String error) {}

    public record ApplyReport(
        int offset,
        int limit,
        int nextOffset,
        boolean complete,
        long applied,
        List<ModelApplyResult> results
    ) {}
}
