package com.yuzhi.dts.platform.service.modeling.warehouse;

import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService.CreateResult;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.CreateModelSpecCommand;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.Layer;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelType;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.SourceKind;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.SourceRef;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.SourceRole;
import com.yuzhi.dts.platform.service.modeling.ModelSpecException;
import com.yuzhi.dts.platform.service.modeling.warehouse.SourceReferenceResolver.AccessContext;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.ConfirmationStatus;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceBindingView;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceFreshness;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceInventoryView;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Produces deterministic, non-persistent source candidates and confirms them through canonical ModelSpec writes. */
@Service
public class WarehousePlanModelCandidateService {

    public static final String INFERENCE_VERSION = "source-candidate-v1";

    private final WarehousePlanApplicationService plans;
    private final ModelSpecApplicationService modelSpecs;

    public WarehousePlanModelCandidateService(
        WarehousePlanApplicationService plans,
        ModelSpecApplicationService modelSpecs
    ) {
        this.plans = plans;
        this.modelSpecs = modelSpecs;
    }

    @Transactional(readOnly = true)
    public CandidatePreview preview(String tenantId, UUID planId, AccessContext accessContext) {
        plans.get(tenantId, planId);
        SourceInventoryView inventory = plans.getSources(tenantId, planId, accessContext);
        List<ModelCandidate> candidates = inventory
            .bindings()
            .stream()
            .filter(Objects::nonNull)
            .filter(binding -> binding.confirmationStatus() == ConfirmationStatus.CONFIRMED)
            .filter(binding -> binding.freshness() == SourceFreshness.CURRENT)
            .filter(binding -> binding.resolutionStatus() == SourceReferenceResolver.ResolutionStatus.AVAILABLE)
            .map(binding -> candidate(tenantId, planId, inventory.version(), binding))
            .toList();
        return new CandidatePreview(planId, inventory.version(), INFERENCE_VERSION, candidates);
    }

    @Transactional
    public CreateResult confirm(
        String tenantId,
        String actorId,
        UUID planId,
        AccessContext accessContext,
        ConfirmCandidateCommand command
    ) {
        if (command == null || command.candidateId() == null || command.candidateId().isBlank() || command.modelSpec() == null) {
            throw invalid("MODEL_CANDIDATE_REQUEST_INVALID", "Candidate id and ModelSpec are required");
        }
        CandidatePreview current = preview(tenantId, planId, accessContext);
        ModelCandidate candidate = current
            .candidates()
            .stream()
            .filter(item -> item.candidateId().equals(command.candidateId()))
            .findFirst()
            .orElseThrow(() ->
                new ModelSpecException(
                    "MODEL_CANDIDATE_STALE",
                    "Source versions changed after candidate preview",
                    ModelSpecException.Kind.CONFLICT
                )
            );
        CreateModelSpecCommand requested = command.modelSpec();
        if (!planId.equals(requested.planId())) {
            throw invalid("MODEL_CANDIDATE_PLAN_MISMATCH", "Candidate and ModelSpec must belong to the same plan");
        }
        if (requested.modelType() != ModelType.FACT && requested.modelType() != ModelType.DIMENSION) {
            throw invalid("MODEL_CANDIDATE_TYPE_INVALID", "Source candidates can only confirm FACT or DIMENSION models");
        }
        if (!requested.sourceRefs().isEmpty()) {
            throw invalid("MODEL_CANDIDATE_SOURCE_NOT_ALLOWED", "Candidate confirmation resolves its source on the server");
        }
        return modelSpecs.create(tenantId, actorId, withSource(requested, candidate));
    }

    private static ModelCandidate candidate(String tenantId, UUID planId, int sourceVersion, SourceBindingView binding) {
        String seed = String.join(
            "|",
            INFERENCE_VERSION,
            tenantId,
            planId.toString(),
            Integer.toString(sourceVersion),
            binding.bindingId().toString(),
            binding.sourceType().name(),
            binding.sourceId(),
            binding.resolvedVersion()
        );
        String name = binding.displayName() == null || binding.displayName().isBlank()
            ? binding.sourceId()
            : binding.displayName();
        return new ModelCandidate(
            sha256(seed),
            binding.bindingId(),
            binding.sourceType(),
            binding.sourceId(),
            binding.resolvedVersion(),
            ModelType.FACT,
            name
        );
    }

    private static CreateModelSpecCommand withSource(CreateModelSpecCommand command, ModelCandidate candidate) {
        SourceRef source = new SourceRef(
            sourceKind(candidate.sourceType()),
            candidate.sourceId(),
            Layer.ODS,
            SourceRole.PRIMARY,
            null,
            null,
            null,
            0,
            candidate.sourceBindingId(),
            candidate.resolvedVersion()
        );
        return new CreateModelSpecCommand(
            command.planId(),
            command.domainId(),
            command.modelType(),
            command.layer(),
            command.name(),
            command.description(),
            command.implementationMode(),
            command.materialization(),
            command.businessActivityRef(),
            command.consumptionScenario(),
            command.grain(),
            command.factShape(),
            command.timeSemantics(),
            command.fields(),
            List.of(source),
            command.dependsOn(),
            command.dimensionRefs(),
            command.metricRefs(),
            command.standardBindings(),
            command.generationStrategy(),
            command.dimensionProfile(),
            command.idempotencyKey()
        );
    }

    private static SourceKind sourceKind(SourceType type) {
        return switch (type) {
            case CONNECTION_TABLE, CATALOG_TABLE -> SourceKind.TABLE;
            case EXCEL_FILE -> SourceKind.DATASET;
            case DBT_NODE -> SourceKind.DBT_MODEL;
        };
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static ModelSpecException invalid(String code, String message) {
        return new ModelSpecException(code, message, ModelSpecException.Kind.UNPROCESSABLE);
    }

    public record CandidatePreview(UUID planId, int sourcesVersion, String inferenceVersion, List<ModelCandidate> candidates) {
        public CandidatePreview {
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
        }
    }

    public record ModelCandidate(
        String candidateId,
        UUID sourceBindingId,
        SourceType sourceType,
        String sourceId,
        String resolvedVersion,
        ModelType suggestedModelType,
        String suggestedName
    ) {}

    public record ConfirmCandidateCommand(String candidateId, CreateModelSpecCommand modelSpec) {}
}
