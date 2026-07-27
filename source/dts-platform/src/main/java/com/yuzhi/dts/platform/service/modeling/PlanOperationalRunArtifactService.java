package com.yuzhi.dts.platform.service.modeling;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.config.ModelMaterializationProperties;
import com.yuzhi.dts.platform.repository.modeling.PhysicalRelationObservationRepository;
import com.yuzhi.dts.platform.repository.modeling.PhysicalRelationObservationRepository.ObservationWrite;
import com.yuzhi.dts.platform.repository.modeling.PlanOperationalRunRepository;
import com.yuzhi.dts.platform.repository.modeling.PlanOperationalRunRepository.EvidenceEntry;
import com.yuzhi.dts.platform.repository.modeling.PlanOperationalRunRepository.EvidenceScope;
import com.yuzhi.dts.platform.service.etl.DbtRuntimeProfileLeaseService;
import com.yuzhi.dts.platform.service.etl.DbtScopedProjectService;
import com.yuzhi.dts.platform.service.modeling.ModelMaterializationRunArtifactService.FinalizeCommand;
import com.yuzhi.dts.platform.service.modeling.ModelMaterializationRunArtifactService.RunArtifactView;
import com.yuzhi.dts.platform.service.modeling.ModelMaterializationRunArtifactService.SyncProbeCommand;
import com.yuzhi.dts.platform.service.modeling.PhysicalRelationInspector.ExpectedRelationType;
import com.yuzhi.dts.platform.service.modeling.PhysicalRelationInspector.PhysicalColumn;
import com.yuzhi.dts.platform.service.modeling.PhysicalRelationInspector.PhysicalRelationObservation;
import com.yuzhi.dts.platform.service.modeling.PhysicalRelationInspector.RelationLocator;
import com.yuzhi.dts.platform.service.modeling.PhysicalRelationInspector.TargetContext;
import com.yuzhi.dts.platform.service.modeling.PlanExecutionException.Kind;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Validates dbt artifacts and appends fresh relation evidence for OPERATIONAL_RUN. */
@Service
public class PlanOperationalRunArtifactService {

    private static final long MAX_ARTIFACT_BYTES =
        25L * 1024L * 1024L;

    private final PlanOperationalRunRepository runs;
    private final DbtScopedProjectService scopedProjects;
    private final PhysicalRelationInspectorRegistry inspectors;
    private final PhysicalRelationObservationRepository observations;
    private final DbtRuntimeProfileLeaseService leases;
    private final ModelMaterializationProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public PlanOperationalRunArtifactService(
        PlanOperationalRunRepository runs,
        DbtScopedProjectService scopedProjects,
        PhysicalRelationInspectorRegistry inspectors,
        PhysicalRelationObservationRepository observations,
        DbtRuntimeProfileLeaseService leases,
        ModelMaterializationProperties properties,
        ObjectMapper objectMapper
    ) {
        this(
            runs,
            scopedProjects,
            inspectors,
            observations,
            leases,
            properties,
            objectMapper,
            Clock.systemUTC()
        );
    }

    PlanOperationalRunArtifactService(
        PlanOperationalRunRepository runs,
        DbtScopedProjectService scopedProjects,
        PhysicalRelationInspectorRegistry inspectors,
        PhysicalRelationObservationRepository observations,
        DbtRuntimeProfileLeaseService leases,
        ModelMaterializationProperties properties,
        ObjectMapper objectMapper,
        Clock clock
    ) {
        this.runs = Objects.requireNonNull(runs, "runs is required");
        this.scopedProjects = Objects.requireNonNull(
            scopedProjects,
            "scopedProjects is required"
        );
        this.inspectors = Objects.requireNonNull(
            inspectors,
            "inspectors is required"
        );
        this.observations = Objects.requireNonNull(
            observations,
            "observations is required"
        );
        this.leases = Objects.requireNonNull(leases, "leases is required");
        this.properties = Objects.requireNonNull(
            properties,
            "properties is required"
        );
        this.objectMapper = Objects.requireNonNull(
            objectMapper,
            "objectMapper is required"
        );
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    public RunArtifactView syncAndProbe(
        UUID groupId,
        SyncProbeCommand command
    ) {
        if (
            groupId == null ||
            command == null ||
            !"OPERATIONAL_RUN".equals(command.runPurpose())
        ) {
            throw failure(
                "MODEL_DBT_RUN_REQUEST_INVALID",
                "Operational sync identity is invalid",
                Kind.INVALID
            );
        }
        EvidenceScope scope = runs.loadEvidenceScope(groupId);
        if (
            !Objects.equals(
                scope.projectBundleChecksum(),
                command.projectBundleChecksum()
            )
        ) {
            throw failure(
                "MODEL_DBT_RUN_IDENTITY_MISMATCH",
                "Runtime artifacts do not match the durable plan run",
                Kind.CONFLICT
            );
        }
        try {
            Path project = scopedProjects.verifyCandidateProject(
                scope.projectBundleChecksum()
            );
            JsonNode manifest = readArtifact(project, "manifest.json");
            JsonNode results = readArtifact(project, "run_results.json");
            UUID invocationId = invocation(manifest, results);
            Map<UUID, RelationLocator> locators =
                validateManifest(scope, manifest);
            validateResults(scope, results);
            Instant now = clock.instant();
            int dbtSucceeded = runs.markDbtSucceeded(
                groupId,
                invocationId,
                now
            );
            if (dbtSucceeded != scope.entries().size()) {
                throw failure(
                    "MODEL_DBT_RUN_IDENTITY_MISMATCH",
                    "Operational pipeline scope changed during sync",
                    Kind.CONFLICT
                );
            }
            List<ObservationWrite> evidence = observe(
                scope,
                invocationId,
                locators,
                now
            );
            observations.appendAll(evidence);
            ObservationWrite failed = evidence
                .stream()
                .filter(item -> !item.verified())
                .findFirst()
                .orElse(null);
            if (failed != null) {
                throw failure(
                    failed.errorCode(),
                    "Physical relation verification failed",
                    Kind.CONFLICT
                );
            }
            if (
                runs.markRelationsVerified(groupId, now) !=
                scope.entries().size()
            ) {
                throw failure(
                    "MODEL_DBT_RUN_IDENTITY_MISMATCH",
                    "Operational relation evidence is incomplete",
                    Kind.CONFLICT
                );
            }
            return new RunArtifactView(
                groupId,
                "BUILT",
                invocationId,
                scope.entries().size()
            );
        } catch (RuntimeException failed) {
            runs.finalizeFailed(
                groupId,
                stableCode(failed),
                clock.instant()
            );
            throw failed;
        }
    }

    public RunArtifactView finalizeRun(
        UUID groupId,
        FinalizeCommand command
    ) {
        if (groupId == null || command == null) {
            throw failure(
                "MODEL_DBT_RUN_REQUEST_INVALID",
                "Operational finalize identity is invalid",
                Kind.INVALID
            );
        }
        EvidenceScope scope = runs.loadEvidenceScope(groupId);
        Instant now = clock.instant();
        int count;
        if ("SUCCEEDED".equals(command.outcome())) {
            count = runs.finalizeSucceeded(groupId, now);
        } else if ("FAILED".equals(command.outcome())) {
            count = runs.finalizeFailed(
                groupId,
                "MODEL_DBT_AIRFLOW_UPSTREAM_FAILED",
                now
            );
        } else {
            throw failure(
                "MODEL_DBT_RUN_REQUEST_INVALID",
                "Finalize outcome is invalid",
                Kind.INVALID
            );
        }
        scopedProjects.releaseCandidateProject(
            scope.projectBundleChecksum()
        );
        leases.release(scope.profileLeaseId());
        return new RunArtifactView(
            groupId,
            "SUCCEEDED".equals(command.outcome())
                ? "SUCCEEDED"
                : "FAILED",
            null,
            count
        );
    }

    private JsonNode readArtifact(Path project, String fileName) {
        Path root = project.normalize();
        Path target = root.resolve("target").normalize();
        Path artifact = target.resolve(fileName).normalize();
        if (
            !target.startsWith(root) ||
            !artifact.startsWith(target) ||
            Files.isSymbolicLink(target) ||
            Files.isSymbolicLink(artifact) ||
            !Files.isRegularFile(
                artifact,
                LinkOption.NOFOLLOW_LINKS
            )
        ) {
            throw failure(
                "MODEL_DBT_ARTIFACT_INVALID",
                "dbt runtime artifact is unavailable",
                Kind.CONFLICT
            );
        }
        try {
            long size = Files.size(artifact);
            if (size < 2 || size > MAX_ARTIFACT_BYTES) {
                throw new IOException("artifact size is invalid");
            }
            JsonNode parsed = objectMapper.readTree(
                Files.readAllBytes(artifact)
            );
            if (parsed == null || !parsed.isObject()) {
                throw new IOException("artifact is not an object");
            }
            return parsed;
        } catch (IOException invalid) {
            throw failure(
                "MODEL_DBT_ARTIFACT_INVALID",
                "dbt runtime artifact cannot be read",
                Kind.CONFLICT
            );
        }
    }

    private static UUID invocation(
        JsonNode manifest,
        JsonNode results
    ) {
        String manifestId = manifest
            .path("metadata")
            .path("invocation_id")
            .asText("");
        String resultsId = results
            .path("metadata")
            .path("invocation_id")
            .asText("");
        try {
            if (!manifestId.equals(resultsId)) {
                throw new IllegalArgumentException();
            }
            return UUID.fromString(manifestId);
        } catch (IllegalArgumentException invalid) {
            throw failure(
                "MODEL_DBT_INVOCATION_MISMATCH",
                "dbt runtime artifacts disagree on invocation identity",
                Kind.CONFLICT
            );
        }
    }

    private static Map<UUID, RelationLocator> validateManifest(
        EvidenceScope scope,
        JsonNode manifest
    ) {
        JsonNode nodes = manifest.path("nodes");
        if (!nodes.isObject()) {
            throw failure(
                "MODEL_DBT_ARTIFACT_INVALID",
                "dbt manifest nodes are unavailable",
                Kind.CONFLICT
            );
        }
        Map<UUID, RelationLocator> locators =
            new LinkedHashMap<>();
        for (EvidenceEntry entry : scope.entries()) {
            JsonNode node = nodes.path(entry.dbtUniqueId());
            JsonNode meta = node.path("config").path("meta");
            if (
                node.isMissingNode() ||
                !entry
                    .modelSpecId()
                    .toString()
                    .equals(meta.path("modelSpecId").asText()) ||
                entry.modelRevision() !=
                meta.path("modelRevision").asInt(-1) ||
                !entry
                    .modelChecksum()
                    .equals(meta.path("modelChecksum").asText()) ||
                entry.implementationRevision() !=
                meta
                    .path("implementationRevision")
                    .asInt(-1) ||
                !entry
                    .implementationChecksum()
                    .equals(
                        meta
                            .path("implementationChecksum")
                            .asText()
                    ) ||
                !entry
                    .targetIdentifier()
                    .equals(node.path("alias").asText())
            ) {
                throw failure(
                    "MODEL_DBT_MANIFEST_IDENTITY_MISMATCH",
                    "dbt manifest does not match the published implementation",
                    Kind.CONFLICT
                );
            }
            JsonNode columns = node.path("columns");
            try {
                locators.put(
                    entry.modelSpecId(),
                    new RelationLocator(
                        node.path("database").asText(""),
                        node.path("schema").asText(""),
                        entry.targetIdentifier(),
                        expectedType(
                            node
                                .path("config")
                                .path("materialized")
                                .asText("")
                        ),
                        expectedColumns(columns),
                        expectedColumnTypes(
                            columns,
                            entry.implementationMode()
                        )
                    )
                );
            } catch (IllegalArgumentException invalid) {
                throw failure(
                    "MODEL_DBT_MANIFEST_LOCATOR_INVALID",
                    "dbt manifest relation locator is invalid",
                    Kind.CONFLICT
                );
            }
        }
        return Map.copyOf(locators);
    }

    private static void validateResults(
        EvidenceScope scope,
        JsonNode results
    ) {
        Map<String, String> statuses = new LinkedHashMap<>();
        for (JsonNode result : results.path("results")) {
            statuses.put(
                result.path("unique_id").asText(""),
                result.path("status").asText("").toLowerCase()
            );
        }
        for (EvidenceEntry entry : scope.entries()) {
            if (
                !"success".equals(statuses.get(entry.dbtUniqueId()))
            ) {
                throw failure(
                    "MODEL_DBT_RUN_RESULT_FAILED",
                    "dbt did not successfully build every published model",
                    Kind.CONFLICT
                );
            }
        }
    }

    private List<ObservationWrite> observe(
        EvidenceScope scope,
        UUID invocationId,
        Map<UUID, RelationLocator> locators,
        Instant createdAt
    ) {
        PhysicalRelationInspector inspector = inspectors.require(
            properties.getAdapter()
        );
        List<ObservationWrite> result =
            new ArrayList<>(scope.entries().size());
        for (EvidenceEntry entry : scope.entries()) {
            RelationLocator locator = locators.get(entry.modelSpecId());
            PhysicalRelationObservation observed;
            try {
                observed = inspector.observe(
                    new TargetContext(
                        scope.executionTargetKey(),
                        inspector.adapter(),
                        locator.databaseName(),
                        locator.schemaName(),
                        scope.credentialVersionRef()
                    ),
                    locator
                );
            } catch (
                PhysicalRelationInspectionException inspectionFailure
            ) {
                observed = new PhysicalRelationObservation(
                    false,
                    null,
                    List.of(),
                    null,
                    clock.instant(),
                    inspectionFailure.code()
                );
            }
            String error = verificationError(
                inspector,
                locator,
                observed
            );
            boolean verified = error == null;
            String expectedColumnsChecksum = digest(
                expectedColumnContract(locator)
            );
            String metadataChecksum = observed.exists()
                ? digest(
                    List.of(
                        scope.tenantId(),
                        scope.bindingId().toString(),
                        Integer.toString(scope.bindingVersion()),
                        entry.modelSpecId().toString(),
                        entry.modelChecksum(),
                        entry.implementationChecksum(),
                        invocationId.toString(),
                        scope.projectBundleChecksum(),
                        locator.databaseName(),
                        locator.schemaName(),
                        locator.identifier(),
                        observed.columnsChecksum()
                    )
                )
                : null;
            result.add(
                new ObservationWrite(
                    scope.tenantId(),
                    null,
                    0,
                    scope.pipelineRunGroupId(),
                    entry.pipelineRunId(),
                    entry.modelSpecId(),
                    entry.modelRevision(),
                    entry.modelChecksum(),
                    entry.implementationRevision(),
                    entry.implementationChecksum(),
                    invocationId,
                    scope.projectBundleChecksum(),
                    inspector.adapter(),
                    scope.credentialVersionRef(),
                    locator.databaseName(),
                    locator.schemaName(),
                    locator.identifier(),
                    locator.expectedType(),
                    observed.actualType(),
                    observed.exists(),
                    verified,
                    observed.columns(),
                    expectedColumnsChecksum,
                    observed.columnsChecksum(),
                    metadataChecksum,
                    error,
                    observed.observedAt(),
                    createdAt,
                    "OPERATIONAL_RUN",
                    scope.bindingId(),
                    scope.bindingVersion()
                )
            );
        }
        return List.copyOf(result);
    }

    private static String verificationError(
        PhysicalRelationInspector inspector,
        RelationLocator locator,
        PhysicalRelationObservation observed
    ) {
        if (!observed.exists()) {
            return observed.errorCode() == null
                ? "MODEL_PHYSICAL_RELATION_MISSING"
                : observed.errorCode();
        }
        if (locator.expectedType() != observed.actualType()) {
            return "MODEL_PHYSICAL_RELATION_TYPE_MISMATCH";
        }
        List<PhysicalColumn> actual = observed
            .columns()
            .stream()
            .sorted(
                Comparator.comparingInt(
                    PhysicalColumn::ordinalPosition
                )
            )
            .toList();
        if (
            !locator
                .expectedColumns()
                .equals(
                    actual
                        .stream()
                        .map(PhysicalColumn::name)
                        .toList()
                )
        ) {
            return "MODEL_PHYSICAL_RELATION_COLUMNS_MISMATCH";
        }
        for (PhysicalColumn column : actual) {
            String expected = locator
                .expectedColumnTypes()
                .get(column.name());
            if (
                expected != null &&
                !inspector.dataTypeMatches(
                    expected,
                    column.dataType()
                )
            ) {
                return "MODEL_PHYSICAL_RELATION_COLUMN_TYPE_MISMATCH";
            }
        }
        return null;
    }

    private static ExpectedRelationType expectedType(
        String materialized
    ) {
        return switch (
            materialized == null
                ? ""
                : materialized.trim().toLowerCase()
        ) {
            case "view" -> ExpectedRelationType.VIEW;
            case "materialized_view" ->
                ExpectedRelationType.MATERIALIZED_VIEW;
            case "table", "incremental" ->
                ExpectedRelationType.TABLE;
            default -> throw failure(
                "MODEL_DBT_MANIFEST_LOCATOR_INVALID",
                "dbt materialization is unsupported",
                Kind.CONFLICT
            );
        };
    }

    private static List<String> expectedColumns(JsonNode columns) {
        if (!columns.isObject()) return List.of();
        List<String> names = new ArrayList<>();
        columns
            .fields()
            .forEachRemaining(entry ->
                names.add(
                    entry
                        .getValue()
                        .path("name")
                        .asText(entry.getKey())
                )
            );
        return List.copyOf(names);
    }

    private static Map<String, String> expectedColumnTypes(
        JsonNode columns,
        String implementationMode
    ) {
        if (!columns.isObject()) return Map.of();
        boolean generated = "DESIGNER_GENERATED".equals(
            implementationMode
        );
        boolean managed = "DBT_MANAGED".equals(implementationMode);
        if (!generated && !managed) {
            throw failure(
                "MODEL_DBT_MANIFEST_IMPLEMENTATION_MODE_INVALID",
                "Operational implementation mode is invalid",
                Kind.CONFLICT
            );
        }
        Map<String, String> types = new LinkedHashMap<>();
        int columnCount = 0;
        var fields = columns.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            columnCount++;
            String name = entry
                .getValue()
                .path("name")
                .asText(entry.getKey());
            String declared = entry
                .getValue()
                .path("data_type")
                .asText("");
            if (declared.isBlank()) {
                if (generated) {
                    throw failure(
                        "MODEL_DBT_MANIFEST_COLUMN_TYPE_REQUIRED",
                        "Generated model manifest must declare every column type",
                        Kind.CONFLICT
                    );
                }
                continue;
            }
            try {
                types.put(
                    name,
                    ModelFieldPhysicalTypeContract.canonicalPostgresType(
                        declared
                    )
                );
            } catch (IllegalArgumentException unsupported) {
                throw failure(
                    "MODEL_DBT_MANIFEST_COLUMN_TYPE_UNSUPPORTED",
                    "dbt manifest declares an unsupported column type",
                    Kind.CONFLICT
                );
            }
        }
        if (
            managed &&
            !types.isEmpty() &&
            types.size() != columnCount
        ) {
            throw failure(
                "MODEL_DBT_MANIFEST_COLUMN_TYPE_PARTIAL",
                "dbt-managed manifest column types must be complete or omitted",
                Kind.CONFLICT
            );
        }
        return Map.copyOf(types);
    }

    private static List<String> expectedColumnContract(
        RelationLocator locator
    ) {
        return locator
            .expectedColumns()
            .stream()
            .map(column ->
                column +
                "\u0000" +
                locator
                    .expectedColumnTypes()
                    .getOrDefault(column, "")
            )
            .toList();
    }

    private static String digest(List<String> values) {
        try {
            MessageDigest digest = MessageDigest.getInstance(
                "SHA-256"
            );
            for (String value : values) {
                byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
                digest.update(
                    java.nio.ByteBuffer
                        .allocate(Integer.BYTES)
                        .putInt(bytes.length)
                        .array()
                );
                digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception impossible) {
            throw new IllegalStateException(
                "SHA-256 is unavailable",
                impossible
            );
        }
    }

    private static String stableCode(RuntimeException failure) {
        if (failure instanceof PlanExecutionException stable) {
            return stable.code();
        }
        if (
            failure instanceof ModelMaterializationRuntimeException stable
        ) {
            return stable.code();
        }
        return "MODEL_OPERATIONAL_RUN_ARTIFACT_FAILED";
    }

    private static PlanExecutionException failure(
        String code,
        String message,
        Kind kind
    ) {
        return new PlanExecutionException(code, message, kind);
    }
}
