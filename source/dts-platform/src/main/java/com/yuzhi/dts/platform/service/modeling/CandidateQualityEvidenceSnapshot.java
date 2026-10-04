package com.yuzhi.dts.platform.service.modeling;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.EvidenceState;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.GovernanceQualitySummaryView;
import com.yuzhi.dts.platform.service.modeling.QualityEvidencePort.QualityEvidence;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Immutable quality references embedded in the existing append-only candidate command snapshot. */
public record CandidateQualityEvidenceSnapshot(
    UUID candidateId,
    int sourceCandidateVersion,
    EngineeringEvidence engineeringEvidence,
    boolean governanceQualityRequired,
    EvidenceState governanceQualityState,
    String governanceQualityCode,
    String governanceQualityMessage,
    long governanceQualityMaxAgeSeconds,
    List<GovernanceEvidenceRef> governanceQualityEvidence,
    String combinedEvidenceChecksum
) {

    private static final String ENGINEERING_FIELD = "engineeringEvidence";
    private static final String GOVERNANCE_FIELD = "governanceQualityEvidence";
    private static final String COMBINED_CHECKSUM_FIELD = "combinedEvidenceChecksum";

    public CandidateQualityEvidenceSnapshot {
        if (candidateId == null) throw new IllegalArgumentException("candidateId is required");
        if (sourceCandidateVersion < 1) throw new IllegalArgumentException("sourceCandidateVersion must be positive");
        if (engineeringEvidence == null) throw new IllegalArgumentException("engineeringEvidence is required");
        if (governanceQualityState == null) throw new IllegalArgumentException("governanceQualityState is required");
        governanceQualityCode = optionalText(governanceQualityCode);
        governanceQualityMessage = optionalText(governanceQualityMessage);
        if ((governanceQualityCode == null) != (governanceQualityMessage == null)) {
            throw new IllegalArgumentException("governance quality code and message must be provided together");
        }
        if (governanceQualityMaxAgeSeconds < 1) {
            throw new IllegalArgumentException("governanceQualityMaxAgeSeconds must be positive");
        }
        governanceQualityEvidence = List.copyOf(
            governanceQualityEvidence == null ? List.of() : governanceQualityEvidence
        );
        if (governanceQualityEvidence.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("governanceQualityEvidence must not contain null items");
        }
        String expected = combinedChecksum(
            candidateId,
            sourceCandidateVersion,
            engineeringEvidence,
            governanceQualityRequired,
            governanceQualityState,
            governanceQualityCode,
            governanceQualityMessage,
            governanceQualityMaxAgeSeconds,
            governanceQualityEvidence
        );
        if (!expected.equals(combinedEvidenceChecksum)) {
            throw new IllegalArgumentException("combinedEvidenceChecksum does not match the pinned evidence");
        }
    }

    public static CandidateQualityEvidenceSnapshot capture(
        CandidateView candidate,
        UUID pipelineRunGroupId,
        int candidateEntryCount,
        int runCount,
        int verifiedCount,
        GovernanceQualitySummaryView governance
    ) {
        if (candidate == null) throw new IllegalArgumentException("candidate is required");
        EngineeringEvidence engineering = EngineeringEvidence.verified(
            pipelineRunGroupId,
            candidateEntryCount,
            runCount,
            verifiedCount
        );
        return create(candidate, engineering, governance);
    }

    public static CandidateQualityEvidenceSnapshot captureLegacy(
        CandidateView candidate,
        GovernanceQualitySummaryView governance
    ) {
        if (candidate == null) throw new IllegalArgumentException("candidate is required");
        String seed = candidate.id() + "\n" + candidate.version() + "\n" + candidate.status() + "\n" +
            candidate.entries().stream().map(entry -> entry.modelSpecId() + ":" + entry.revision() + ":" + entry.checksum()).sorted().toList();
        EngineeringEvidence engineering = new EngineeringEvidence(
            "LEGACY_QUALITY_PASSED",
            null,
            candidate.entries().size(),
            candidate.entries().size(),
            candidate.entries().size(),
            sha256(seed)
        );
        return create(candidate, engineering, governance);
    }

    private static CandidateQualityEvidenceSnapshot create(
        CandidateView candidate,
        EngineeringEvidence engineering,
        GovernanceQualitySummaryView governance
    ) {
        if (governance == null) throw new IllegalArgumentException("governance quality summary is required");
        List<GovernanceEvidenceRef> references = governance
            .evidence()
            .stream()
            .filter(QualityEvidence::passed)
            .map(GovernanceEvidenceRef::from)
            .sorted(
                Comparator.comparing(GovernanceEvidenceRef::assetKey)
                    .thenComparing(item -> item.ruleVersionId().toString())
                    .thenComparing(item -> item.bindingId().toString())
            )
            .toList();
        String checksum = combinedChecksum(
            candidate.id(),
            candidate.version(),
            engineering,
            governance.required(),
            governance.state(),
            governance.code(),
            governance.message(),
            governance.maxAgeSeconds(),
            references
        );
        return new CandidateQualityEvidenceSnapshot(
            candidate.id(),
            candidate.version(),
            engineering,
            governance.required(),
            governance.state(),
            governance.code(),
            governance.message(),
            governance.maxAgeSeconds(),
            references,
            checksum
        );
    }

    public GovernanceQualitySummaryView governanceSummary() {
        List<QualityEvidence> evidence = governanceQualityEvidence
            .stream()
            .map(GovernanceEvidenceRef::toEvidence)
            .toList();
        return new GovernanceQualitySummaryView(
            governanceQualityRequired,
            governanceQualityState,
            governanceQualityCode,
            governanceQualityMessage,
            governanceQualityMaxAgeSeconds,
            evidence
        );
    }

    public void appendTo(ObjectNode response, ObjectMapper mapper) {
        if (response == null || mapper == null) {
            throw new IllegalArgumentException("response and mapper are required");
        }
        response.put("qualityEvidenceCandidateId", candidateId.toString());
        response.put("qualityEvidenceCandidateVersion", sourceCandidateVersion);
        response.set(ENGINEERING_FIELD, mapper.valueToTree(engineeringEvidence));
        response.put("governanceQualityRequired", governanceQualityRequired);
        response.put("governanceQualityState", governanceQualityState.name());
        putNullable(response, "governanceQualityCode", governanceQualityCode);
        putNullable(response, "governanceQualityMessage", governanceQualityMessage);
        response.put("governanceQualityMaxAgeSeconds", governanceQualityMaxAgeSeconds);
        response.set(GOVERNANCE_FIELD, mapper.valueToTree(governanceQualityEvidence));
        response.put(COMBINED_CHECKSUM_FIELD, combinedEvidenceChecksum);
    }

    public static Optional<CandidateQualityEvidenceSnapshot> read(String responseSnapshot, ObjectMapper mapper) {
        if (responseSnapshot == null || responseSnapshot.isBlank()) return Optional.empty();
        if (mapper == null) throw new IllegalArgumentException("mapper is required");
        try {
            JsonNode root = mapper.readTree(responseSnapshot);
            if (root == null || !root.hasNonNull(COMBINED_CHECKSUM_FIELD)) return Optional.empty();
            UUID candidateId = UUID.fromString(requiredText(root, "qualityEvidenceCandidateId"));
            int version = root.path("qualityEvidenceCandidateVersion").asInt(0);
            EngineeringEvidence engineering = mapper.treeToValue(
                requiredNode(root, ENGINEERING_FIELD),
                EngineeringEvidence.class
            );
            EvidenceState state = EvidenceState.valueOf(requiredText(root, "governanceQualityState"));
            List<GovernanceEvidenceRef> governance = mapper
                .readerForListOf(GovernanceEvidenceRef.class)
                .readValue(requiredNode(root, GOVERNANCE_FIELD));
            return Optional.of(
                new CandidateQualityEvidenceSnapshot(
                    candidateId,
                    version,
                    engineering,
                    root.path("governanceQualityRequired").asBoolean(false),
                    state,
                    optionalText(root.path("governanceQualityCode").asText(null)),
                    optionalText(root.path("governanceQualityMessage").asText(null)),
                    root.path("governanceQualityMaxAgeSeconds").asLong(0),
                    governance,
                    requiredText(root, COMBINED_CHECKSUM_FIELD)
                )
            );
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("Candidate quality evidence snapshot is invalid", exception);
        }
    }

    private static String combinedChecksum(
        UUID candidateId,
        int sourceCandidateVersion,
        EngineeringEvidence engineering,
        boolean required,
        EvidenceState state,
        String code,
        String message,
        long maxAgeSeconds,
        List<GovernanceEvidenceRef> references
    ) {
        String governance = references
            .stream()
            .map(GovernanceEvidenceRef::canonical)
            .sorted()
            .reduce((left, right) -> left + "\n" + right)
            .orElse("");
        return sha256(
            String.join(
                "\n",
                candidateId.toString(),
                Integer.toString(sourceCandidateVersion),
                engineering.canonical(),
                Boolean.toString(required),
                state.name(),
                Objects.toString(code, ""),
                Objects.toString(message, ""),
                Long.toString(maxAgeSeconds),
                governance
            )
        );
    }

    private static JsonNode requiredNode(JsonNode root, String field) {
        JsonNode value = root.path(field);
        if (value.isMissingNode() || value.isNull()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private static String requiredText(JsonNode root, String field) {
        String value = requiredNode(root, field).asText(null);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }

    private static void putNullable(ObjectNode node, String field, String value) {
        if (value == null) node.putNull(field); else node.put(field, value);
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record EngineeringEvidence(
        String status,
        UUID pipelineRunGroupId,
        int candidateEntryCount,
        int runCount,
        int verifiedCount,
        String evidenceChecksum
    ) {
        public EngineeringEvidence {
            if (status == null || status.isBlank()) throw new IllegalArgumentException("engineering status is required");
            status = status.trim();
            if (candidateEntryCount < 1 || runCount < 1 || verifiedCount < 1) {
                throw new IllegalArgumentException("engineering evidence counts must be positive");
            }
            if (runCount != candidateEntryCount || verifiedCount != runCount) {
                throw new IllegalArgumentException("engineering evidence must cover every candidate entry");
            }
            requireChecksum(evidenceChecksum, "engineering evidenceChecksum");
        }

        static EngineeringEvidence verified(
            UUID pipelineRunGroupId,
            int candidateEntryCount,
            int runCount,
            int verifiedCount
        ) {
            if (pipelineRunGroupId == null) throw new IllegalArgumentException("pipelineRunGroupId is required");
            String seed = String.join(
                "\n",
                "PASSED",
                pipelineRunGroupId.toString(),
                Integer.toString(candidateEntryCount),
                Integer.toString(runCount),
                Integer.toString(verifiedCount)
            );
            return new EngineeringEvidence(
                "PASSED",
                pipelineRunGroupId,
                candidateEntryCount,
                runCount,
                verifiedCount,
                sha256(seed)
            );
        }

        String canonical() {
            return String.join(
                "|",
                status,
                Objects.toString(pipelineRunGroupId, ""),
                Integer.toString(candidateEntryCount),
                Integer.toString(runCount),
                Integer.toString(verifiedCount),
                evidenceChecksum
            );
        }
    }

    public record GovernanceEvidenceRef(
        String assetKey,
        UUID ruleId,
        UUID ruleVersionId,
        UUID bindingId,
        UUID runId,
        Instant finishedAt,
        String evidenceChecksum
    ) {
        public GovernanceEvidenceRef {
            if (assetKey == null || assetKey.isBlank()) throw new IllegalArgumentException("assetKey is required");
            assetKey = assetKey.trim();
            if (ruleId == null || ruleVersionId == null || bindingId == null || runId == null) {
                throw new IllegalArgumentException("governance evidence references are required");
            }
            if (finishedAt == null) throw new IllegalArgumentException("finishedAt is required");
            requireChecksum(evidenceChecksum, "governance evidenceChecksum");
        }

        static GovernanceEvidenceRef from(QualityEvidence evidence) {
            return new GovernanceEvidenceRef(
                evidence.assetKey(),
                evidence.ruleId(),
                evidence.ruleVersionId(),
                evidence.bindingId(),
                evidence.runId(),
                evidence.finishedAt(),
                evidence.evidenceChecksum()
            );
        }

        QualityEvidence toEvidence() {
            return new QualityEvidence(
                assetKey,
                ruleId,
                ruleVersionId,
                bindingId,
                runId,
                "SUCCEEDED",
                finishedAt,
                evidenceChecksum,
                List.of()
            );
        }

        String canonical() {
            return String.join(
                "|",
                assetKey,
                ruleId.toString(),
                ruleVersionId.toString(),
                bindingId.toString(),
                runId.toString(),
                finishedAt.toString(),
                evidenceChecksum
            );
        }
    }

    private static void requireChecksum(String value, String name) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(name + " must be a SHA-256 value");
        }
    }
}
