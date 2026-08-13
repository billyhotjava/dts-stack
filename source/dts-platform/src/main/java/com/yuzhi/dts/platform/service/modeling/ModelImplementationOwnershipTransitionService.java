package com.yuzhi.dts.platform.service.modeling;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.repository.modeling.ModelLifecycleRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelLifecycleCommandReceiptRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ArtifactWrite;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.GeneratedInput;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.InputMode;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.SaveImplementationCommand;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService.ExpectedVersion;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelStatus;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.CanonicalDbtProjectBundleAssembler;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.DraftException;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtProjectBundleManifest;
import com.yuzhi.dts.platform.service.modeling.imports.checksum.ModelPackageChecksum;
import com.yuzhi.dts.platform.service.modeling.imports.converter.AdvancedDbtDraftStaticValidator;
import com.yuzhi.dts.platform.service.modeling.imports.converter.AdvancedDbtDraftStaticValidator.StaticValidationException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

/** The sole forward-only DESIGNER_GENERATED to DBT_MANAGED hand-over seam. */
@Service
public class ModelImplementationOwnershipTransitionService {

    private final ModelSpecApplicationService modelSpecs;
    private final ModelLifecycleRepository lifecycle;
    private final ModelLifecycleCompilerPort compiler;
    private final ModelingDbtArtifactImportService artifacts;
    private final AdvancedDbtDraftStaticValidator validator;
    private final ObjectMapper objectMapper;
    private final AuditService audit;
    private final ModelLifecycleCommandReceiptRepository receipts;

    public ModelImplementationOwnershipTransitionService(
        ModelSpecApplicationService modelSpecs, ModelLifecycleRepository lifecycle, ModelLifecycleCompilerPort compiler,
        ModelingDbtArtifactImportService artifacts, AdvancedDbtDraftStaticValidator validator,
        ObjectMapper objectMapper, AuditService audit, ModelLifecycleCommandReceiptRepository receipts
    ) {
        this.modelSpecs = modelSpecs;
        this.lifecycle = lifecycle;
        this.compiler = compiler;
        this.artifacts = artifacts;
        this.validator = validator;
        this.objectMapper = objectMapper;
        this.audit = audit;
        this.receipts = receipts;
    }

    @Transactional(readOnly = true)
    public Preview preview(String tenantId, UUID id, int modelRevision, int implementationRevision) {
        ModelSpecView model = modelSpecs.get(tenantId, id);
        ImplementationView implementation = lifecycle.findImplementation(tenantId, id).orElseThrow(() -> conflict("MODEL_IMPLEMENTATION_REQUIRED", "Implementation is required"));
        if (model.revision() != modelRevision || implementation.implementationRevision() != implementationRevision) throw conflict("MODEL_DBT_PREVIEW_STALE", "The requested preview revision is stale");
        return preview(tenantId, id, new ExpectedVersion(id, model.revision(), model.checksum()), new ModelLifecycleService.ExpectedImplementationVersion(id, implementation.implementationRevision(), implementation.implementationChecksum()));
    }

    @Transactional(readOnly = true)
    public Preview preview(String tenantId, UUID id, ExpectedVersion modelPin, ModelLifecycleService.ExpectedImplementationVersion implementationPin) {
        return prepare(tenantId, id, modelPin, implementationPin).preview();
    }

    @Transactional(readOnly = true)
    public Validation validate(String tenantId, UUID id, ExpectedVersion modelPin, ModelLifecycleService.ExpectedImplementationVersion implementationPin) {
        Prepared prepared = prepare(tenantId, id, modelPin, implementationPin);
        Preview preview = prepared.preview();
        return new Validation(true, List.of(), preview.previewChecksum(), "DESIGNER_GENERATED", "DBT_MANAGED", preview.modelRevision(), preview.implementationRevision(), 3, 4, false);
    }

    @Transactional
    public Transition transition(
        String tenantId, String actorId, UUID id, ExpectedVersion modelPin,
        ModelLifecycleService.ExpectedImplementationVersion implementationPin, String previewChecksum, String idempotencyKey
    ) {
        if (actorId == null || actorId.isBlank()) {
            throw new ModelSpecException(
                "MODEL_SPEC_ACTOR_REQUIRED",
                "Authenticated actor is required",
                ModelSpecException.Kind.FORBIDDEN
            );
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) throw conflict("MODEL_IMPLEMENTATION_IDEMPOTENCY_REQUIRED", "idempotencyKey is required");
        if (previewChecksum == null || previewChecksum.isBlank()) throw conflict("MODEL_DBT_PREVIEW_STALE", "The generated dbt preview is stale");
        actorId = actorId.trim();
        String commandKey = idempotencyKey.trim();
        String payloadHash = receipts.payloadHash(Map.of("modelRevision", modelPin.revision(), "modelChecksum", modelPin.checksum(), "implementationRevision", implementationPin.revision(), "implementationChecksum", implementationPin.checksum(), "previewChecksum", previewChecksum));
        receipts.lockCommandKey(tenantId, id, "OWNERSHIP_TRANSITION", commandKey);
        var replay = receipts.find(tenantId, id, "OWNERSHIP_TRANSITION", commandKey);
        if (replay.isPresent()) {
            if (!payloadHash.equals(replay.get().payloadHash())) throw conflict("MODEL_LIFECYCLE_IDEMPOTENCY_CONFLICT", "The idempotency key belongs to another request payload");
            ModelSpecView replayModel = modelSpecs.revision(tenantId, new ModelSpecContract.ModelRevisionRef(id, replay.get().result().revision()));
            return new Transition(transitionId(id, commandKey), replayModel, replay.get().result(), List.of("SQL", "SCHEMA", "CONFIG"), 3, 4, "DESIGNER_GENERATED", "DBT_MANAGED");
        }
        Prepared prepared = prepare(tenantId, id, modelPin, implementationPin);
        State before = prepared.state();
        Preview preview = prepared.preview();
        if (!preview.previewChecksum().equals(previewChecksum)) throw conflict("MODEL_DBT_PREVIEW_STALE", "The generated dbt preview is stale");

        String projectKey = prepared.projectKey();
        String uniqueId = prepared.uniqueId();
        var bundle = prepared.bundle();

        ModelSpecView model = modelSpecs.transitionToDbtManaged(tenantId, actorId, id, modelPin);
        SaveImplementationCommand command = new SaveImplementationCommand(
            InputMode.GENERATED, List.of(new GeneratedInput("DBT", Map.of("projectKey", projectKey, "dbtUniqueId", uniqueId))),
            before.implementation().fieldMappings(), before.implementation().settings(), ImplementationMode.DBT_MANAGED,
            before.implementation().materialization(), commandKey
        );
        try {
            if (lifecycle.transitionDesignerImplementationToDbtManaged(tenantId, actorId, model, projectKey, uniqueId, command,
                before.model().revision(), before.model().checksum(),
                before.implementation().implementationRevision(), before.implementation().implementationChecksum(), Instant.now()) == 0) {
                throw conflict("MODEL_IMPLEMENTATION_REVISION_CONFLICT", "Implementation changed during ownership transition");
            }
        } catch (DataIntegrityViolationException exception) {
            throw conflict("MODEL_IMPLEMENTATION_DBT_CONFLICT", "The dbt node is already owned by another ModelSpec");
        }
        ImplementationView implementation = lifecycle.findImplementation(tenantId, id).orElseThrow();
        artifacts.importArtifacts(new ModelingDbtArtifactImportService.ImportCommand(
            tenantId, model.id(), model.planId(), model.revision(), model.checksum(), ModelStatus.DRAFT,
            implementation.id(), implementation.implementationRevision(), implementation.implementationChecksum(), projectKey, uniqueId,
            commandKey, List.of(
                imported(uniqueId, ModelingDbtArtifactImportService.ArtifactType.SQL, main(preview, ".sql"), model.materialization()),
                imported(uniqueId, ModelingDbtArtifactImportService.ArtifactType.SCHEMA, main(preview, ".yml"), model.materialization()),
                new ModelingDbtArtifactImportService.ImportedArtifact(uniqueId, ModelingDbtArtifactImportService.NodeKind.MODEL,
                    ModelingDbtArtifactImportService.ArtifactType.CONFIG, ".dts/dbt-project-bundle.json", bundle.bundleChecksum(), bundle.manifest(), model.materialization())
            )
        ));
        String transitionId = transitionId(id, commandKey);
        String eventIdentity = "MODEL_IMPLEMENTATION_OWNERSHIP_TRANSITION:" + id + ":" + implementation.implementationRevision();
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("eventIdentity", eventIdentity); auditPayload.put("transitionId", transitionId); auditPayload.put("actor", actorId); auditPayload.put("tenantId", tenantId);
        auditPayload.put("planId", model.planId().toString()); auditPayload.put("modelSpecId", id.toString());
        auditPayload.put("modelBeforeRevision", before.model().revision()); auditPayload.put("modelBeforeChecksum", before.model().checksum());
        auditPayload.put("modelAfterRevision", model.revision()); auditPayload.put("modelAfterChecksum", model.checksum());
        auditPayload.put("implementationId", implementation.id().toString());
        auditPayload.put("implementationBeforeRevision", before.implementation().implementationRevision());
        auditPayload.put("implementationBeforeChecksum", before.implementation().implementationChecksum());
        auditPayload.put("implementationAfterRevision", implementation.implementationRevision());
        auditPayload.put("implementationAfterChecksum", implementation.implementationChecksum());
        auditPayload.put("sourceOwnership", "DESIGNER_GENERATED"); auditPayload.put("targetOwnership", "DBT_MANAGED"); auditPayload.put("outcome", "SUCCESS");
        audit.auditActionStrict("MODEL_IMPLEMENTATION_OWNERSHIP_TRANSITION", AuditStage.SUCCESS, eventIdentity, auditPayload);
        receipts.append(tenantId, id, "OWNERSHIP_TRANSITION", commandKey, payloadHash, implementation, actorId, Instant.now());
        return new Transition(transitionId, model, implementation, List.of("SQL", "SCHEMA", "CONFIG"), 3, 4, "DESIGNER_GENERATED", "DBT_MANAGED");
    }

    private State pinnedDesigner(String tenantId, UUID id, ExpectedVersion modelPin, ModelLifecycleService.ExpectedImplementationVersion implementationPin) {
        ModelSpecView model = modelSpecs.get(tenantId, id);
        if (model.revision() != modelPin.revision() || !model.checksum().equals(modelPin.checksum())) throw conflict("MODEL_DBT_PREVIEW_STALE", "Model revision is stale");
        ImplementationView implementation = lifecycle.findImplementation(tenantId, id).orElseThrow(() -> conflict("MODEL_IMPLEMENTATION_REQUIRED", "Implementation is required"));
        if (implementation.implementationRevision() != implementationPin.revision() || !implementation.implementationChecksum().equals(implementationPin.checksum())) throw conflict("MODEL_DBT_PREVIEW_STALE", "Implementation revision is stale");
        if (model.status() != ModelStatus.DRAFT || model.implementationMode() != ImplementationMode.DESIGNER_GENERATED || implementation.ownership() != ImplementationMode.DESIGNER_GENERATED || !"ACTIVE".equals(implementation.status())) throw conflict("MODEL_DBT_PREVIEW_DESIGNER_REQUIRED", "An active designer-generated implementation is required");
        return new State(model, implementation);
    }
    private Prepared prepare(String tenantId, UUID id, ExpectedVersion modelPin, ModelLifecycleService.ExpectedImplementationVersion implementationPin) {
        State state = pinnedDesigner(tenantId, id, modelPin, implementationPin);
        Preview preview = Preview.from(state.model(), state.implementation(), compiler.compile(tenantId, state.model(), state.implementation()));
        String projectKey = ModelImplementationExecutionPlanner.systemManagedDbtProjectKey(state.model());
        String uniqueId = ModelImplementationExecutionPlanner.systemManagedDbtUniqueId(state.model());
        try {
            Map<String, String> files = CanonicalDbtProjectBundleAssembler.assemble(projectKey, state.model().materialization(), preview.filesByPath());
            var validated = validator.validate(files);
            if (
                !projectKey.equals(validated.projectKey()) ||
                validated.nodes().stream().noneMatch(node -> uniqueId.equals(node.dbtUniqueId()))
            ) {
                throw conflict("MODEL_DBT_PREVIEW_INVALID", "Generated dbt project identity does not match the ModelSpec");
            }
            List<DbtProjectBundleManifest.BundleFile> frozen = files.entrySet().stream().map(entry -> new DbtProjectBundleManifest.BundleFile(entry.getKey(), entry.getValue(), ModelPackageChecksum.sha256Text(entry.getValue()), entry.getValue().getBytes(StandardCharsets.UTF_8).length)).toList();
            return new Prepared(state, preview, projectKey, uniqueId, DbtProjectBundleManifest.freeze(objectMapper, frozen, validated));
        } catch (StaticValidationException exception) {
            throw new ModelSpecException(
                exception.code(),
                exception.getMessage(),
                ModelSpecException.Kind.UNPROCESSABLE
            );
        } catch (DraftException exception) {
            throw new ModelSpecException(
                exception.code(),
                exception.getMessage(),
                ModelSpecException.Kind.UNPROCESSABLE,
                exception.details()
            );
        }
    }
    private static ModelingDbtArtifactImportService.ImportedArtifact imported(String id, ModelingDbtArtifactImportService.ArtifactType type, File file, String materialization) {
        return new ModelingDbtArtifactImportService.ImportedArtifact(id, ModelingDbtArtifactImportService.NodeKind.MODEL, type, file.path(), file.checksum(), file.content(), materialization);
    }
    private static File main(Preview preview, String suffix) { return preview.files().stream().filter(file -> file.path().endsWith(suffix) && "MODEL".equals(file.nodeKind())).findFirst().orElseThrow(() -> conflict("MODEL_DBT_PREVIEW_INVALID", "Generated preview is incomplete")); }
    private static ModelSpecException conflict(String code, String message) { return new ModelSpecException(code, message, ModelSpecException.Kind.CONFLICT); }
    private static String transitionId(UUID id, String idempotencyKey) { return UUID.nameUUIDFromBytes((id + ":" + idempotencyKey).getBytes(StandardCharsets.UTF_8)).toString(); }
    private record State(ModelSpecView model, ImplementationView implementation) {}
    private record Prepared(State state, Preview preview, String projectKey, String uniqueId, DbtProjectBundleManifest.BundleSnapshot bundle) {}
    public record File(String path, String content, String checksum, String nodeKind, List<String> artifactTypes) {}
    public record Preview(int modelRevision, String modelChecksum, int implementationRevision, String implementationChecksum, String ownership, List<File> files, String previewChecksum, boolean readOnly) {
        static Preview from(ModelSpecView model, ImplementationView implementation, List<ArtifactWrite> writes) {
            Map<String, List<ArtifactWrite>> grouped = new java.util.TreeMap<>();
            writes.forEach(write -> grouped.computeIfAbsent(write.path(), ignored -> new java.util.ArrayList<>()).add(write));
            List<File> files = grouped.entrySet().stream().filter(entry -> !entry.getKey().endsWith(".tests.yml") && !entry.getKey().endsWith(".md")).map(entry -> {
                ArtifactWrite primary = entry.getValue().get(0);
                if (entry.getValue().stream().anyMatch(write -> !primary.content().equals(write.content()) || !primary.checksum().equals(write.checksum()))) {
                    throw conflict("MODEL_DBT_PREVIEW_INVALID", "Generated artifacts for the same path disagree");
                }
                String nodeKind = entry.getValue().stream().anyMatch(write -> "STG".equals(write.nodeKind()) || "STG_SQL".equals(write.artifactType())) ? "STG" : "MODEL";
                return new File(primary.path(), primary.content(), ModelPackageChecksum.sha256Text(primary.content()), nodeKind, entry.getValue().stream().map(ArtifactWrite::artifactType).distinct().sorted().toList());
            }).toList();
            if (files.size() != 3) throw conflict("MODEL_DBT_PREVIEW_INVALID", "Generated preview must contain exactly three files");
            String checksum = ModelPackageChecksum.sha256Text(files.stream().map(file -> file.path() + "\\n" + file.checksum()).sorted().reduce("", (a, b) -> a + b));
            return new Preview(model.revision(), model.checksum(), implementation.implementationRevision(), implementation.implementationChecksum(), "DESIGNER_GENERATED", files, checksum, true);
        }
        Map<String, String> filesByPath() { LinkedHashMap<String, String> values = new LinkedHashMap<>(); files.forEach(file -> values.put(file.path(), file.content())); return values; }
    }
    public record Validation(boolean allowed, List<String> reasons, String previewChecksum, String sourceOwnership, String targetOwnership, int baseModelRevision, int baseImplementationRevision, int previewFileCount, int bundleFileCount, boolean reversible) {}
    public record Transition(String transitionId, ModelSpecView model, ImplementationView implementation, List<String> artifactTypes, int previewFileCount, int bundleFileCount, String sourceOwnership, String targetOwnership) {}
}
