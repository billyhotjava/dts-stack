package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.service.catalog.CatalogAssetType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Read-only boundary for immutable governance quality evidence.
 *
 * <p>The modeling lifecycle consumes only this contract. Governance entities and repositories stay behind the
 * adapter so engineering verification can never be mistaken for a governance quality result.
 */
public interface QualityEvidencePort {

    List<QualityEvidence> read(List<QualityEvidenceRequest> requests);

    record QualityEvidenceRequest(
        CatalogAssetType assetType,
        String assetKey,
        List<UUID> ruleVersionIds,
        Instant asOf,
        long maxAgeSeconds
    ) {
        public QualityEvidenceRequest {
            if (assetType == null) throw new IllegalArgumentException("assetType is required");
            if (assetKey == null || assetKey.isBlank()) throw new IllegalArgumentException("assetKey is required");
            assetKey = assetKey.trim();
            ruleVersionIds = ruleVersionIds == null ? List.of() : List.copyOf(ruleVersionIds);
            if (ruleVersionIds.stream().anyMatch(id -> id == null) || ruleVersionIds.stream().distinct().count() != ruleVersionIds.size()) {
                throw new IllegalArgumentException("ruleVersionIds must be unique and non-null");
            }
            if (asOf == null) throw new IllegalArgumentException("asOf is required");
            if (maxAgeSeconds < 1) throw new IllegalArgumentException("maxAgeSeconds must be positive");
        }
    }

    record QualityEvidence(
        String assetKey,
        UUID ruleId,
        UUID ruleVersionId,
        UUID bindingId,
        UUID runId,
        String status,
        Instant finishedAt,
        String evidenceChecksum,
        List<String> violations
    ) {
        public QualityEvidence {
            if (assetKey == null || assetKey.isBlank()) throw new IllegalArgumentException("assetKey is required");
            assetKey = assetKey.trim();
            if (status == null || status.isBlank()) throw new IllegalArgumentException("status is required");
            status = status.trim().toUpperCase(java.util.Locale.ROOT);
            if (evidenceChecksum == null || !evidenceChecksum.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("evidenceChecksum must be a SHA-256 value");
            }
            violations = violations == null ? List.of() : List.copyOf(violations);
            if (violations.stream().anyMatch(value -> value == null || value.isBlank())) {
                throw new IllegalArgumentException("violations must contain stable non-blank codes");
            }
        }

        public boolean passed() {
            return "SUCCEEDED".equals(status) && violations.isEmpty();
        }
    }
}
