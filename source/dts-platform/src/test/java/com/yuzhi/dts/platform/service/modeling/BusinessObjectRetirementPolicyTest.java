package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.BusinessObjectRetirementPolicy.CandidateTarget;
import com.yuzhi.dts.platform.service.modeling.BusinessObjectRetirementPolicy.Decision;
import com.yuzhi.dts.platform.service.modeling.BusinessObjectRetirementPolicy.Disposition;
import com.yuzhi.dts.platform.service.modeling.BusinessObjectRetirementPolicy.FieldOwner;
import com.yuzhi.dts.platform.service.modeling.BusinessObjectRetirementPolicy.FieldSignal;
import com.yuzhi.dts.platform.service.modeling.BusinessObjectRetirementPolicy.Identification;
import com.yuzhi.dts.platform.service.modeling.BusinessObjectRetirementPolicy.JoinType;
import com.yuzhi.dts.platform.service.modeling.BusinessObjectRetirementPolicy.LegacyBusinessObject;
import com.yuzhi.dts.platform.service.modeling.BusinessObjectRetirementPolicy.LegacyField;
import com.yuzhi.dts.platform.service.modeling.BusinessObjectRetirementPolicy.LegacyRef;
import com.yuzhi.dts.platform.service.modeling.BusinessObjectRetirementPolicy.LegacySourceRef;
import com.yuzhi.dts.platform.service.modeling.BusinessObjectRetirementPolicy.Readiness;
import com.yuzhi.dts.platform.service.modeling.BusinessObjectRetirementPolicy.SourceRole;
import com.yuzhi.dts.platform.service.modeling.BusinessObjectRetirementPolicy.TargetWriteMetadata;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BusinessObjectRetirementPolicyTest {

    @Test
    void stableEntityBecomesAnExecutableDimensionMigration() {
        LegacyBusinessObject legacy = input()
            .legacyKind("ENTITY")
            .stableKeys("department_id")
            .fields(field("department_id", FieldSignal.KEY), field("department_name", FieldSignal.ATTRIBUTE))
            .proposedModelSpecId("dim-department")
            .build();

        Decision decision = BusinessObjectRetirementPolicy.decide(legacy);

        assertThat(decision.identification()).isEqualTo(Identification.DIMENSION_LIKE);
        assertThat(decision.readiness()).isEqualTo(Readiness.READY);
        assertThat(decision.recommendedDisposition()).contains(Disposition.AUTO_DIMENSION);
        assertThat(decision.executableAction()).contains(Disposition.AUTO_DIMENSION);
        assertThat(decision.targetWriteMetadata()).get().extracting(TargetWriteMetadata::modelSpecId).isEqualTo("dim-department");
    }

    @Test
    void factMergesOnlyWhenExactlyOneCompatibleTargetExists() {
        LegacyBusinessObject legacy = input()
            .legacyKind("FACT")
            .factSignal(true)
            .grainKeys("order_id")
            .candidateTargets(factTarget("fact-order-detail"))
            .build();

        Decision decision = BusinessObjectRetirementPolicy.decide(legacy);

        assertThat(decision.identification()).isEqualTo(Identification.FACT_LIKE);
        assertThat(decision.readiness()).isEqualTo(Readiness.READY);
        assertThat(decision.executableAction()).contains(Disposition.AUTO_FACT_MERGE);
        assertThat(decision.targetModelSpecId()).contains("fact-order-detail");
    }

    @Test
    void factNeedsTargetUnlessExactlyOneTypeAndDomainCompatibleTargetExists() {
        List<Decision> unresolved = List.of(
            BusinessObjectRetirementPolicy.decide(input().legacyKind("FACT").factSignal(true).build()),
            BusinessObjectRetirementPolicy.decide(
                input()
                    .legacyKind("FACT")
                    .factSignal(true)
                    .candidateTargets(factTarget("fact-a"), factTarget("fact-b"))
                    .build()
            ),
            BusinessObjectRetirementPolicy.decide(
                input()
                    .legacyKind("FACT")
                    .factSignal(true)
                    .candidateTargets(new CandidateTarget("dim-order", "DIMENSION", "operations"))
                    .build()
            ),
            BusinessObjectRetirementPolicy.decide(
                input()
                    .legacyKind("FACT")
                    .factSignal(true)
                    .candidateTargets(new CandidateTarget("fact-finance", "FACT", "finance"))
                    .build()
            )
        );

        assertThat(unresolved).allSatisfy(decision -> {
            assertThat(decision.readiness()).isEqualTo(Readiness.NEEDS_TARGET);
            assertThat(decision.targetModelSpecId()).isEmpty();
            assertThat(decision.executableAction()).isEmpty();
        });
    }

    @Test
    void conflictingMetadataForTheSameTargetIdIsNotHiddenByDeduplication() {
        Decision decision = BusinessObjectRetirementPolicy.decide(
            input()
                .legacyKind("FACT")
                .factSignal(true)
                .candidateTargets(
                    new CandidateTarget("fact-order", "FACT", "operations"),
                    new CandidateTarget("fact-order", "DIMENSION", "operations")
                )
                .build()
        );

        assertThat(decision.readiness()).isEqualTo(Readiness.NEEDS_CLASSIFICATION);
        assertThat(decision.targetModelSpecId()).isEmpty();
        assertThat(decision.conflicts()).containsExactly("TARGET_METADATA_CONFLICT:fact-order");
        assertThat(decision.executableAction()).isEmpty();
    }

    @Test
    void mixedSignalsRequireManualSplitAndNeverExposeAnExecutableAction() {
        LegacyBusinessObject legacy = input()
            .legacyKind("ENTITY")
            .stableKeys("project_id")
            .factSignal(true)
            .grainKeys("snapshot_date")
            .build();

        Decision decision = BusinessObjectRetirementPolicy.decide(legacy);

        assertThat(decision.identification()).isEqualTo(Identification.MIXED);
        assertThat(decision.readiness()).isEqualTo(Readiness.NEEDS_SPLIT);
        assertThat(decision.recommendedDisposition()).contains(Disposition.MANUAL_SPLIT);
        assertThat(decision.executableAction()).isEmpty();
    }

    @Test
    void everyLegacyFieldGetsExactlyOneOwnerIncludingAmbiguousFields() {
        LegacyBusinessObject legacy = input()
            .legacyKind("ENTITY")
            .stableKeys("id")
            .fields(
                field("id", FieldSignal.KEY),
                field("name", FieldSignal.ATTRIBUTE),
                field("grain_note", FieldSignal.GRAIN),
                field("status_code", FieldSignal.STANDARD),
                field("amount_formula", FieldSignal.METRIC),
                new LegacyField("ambiguous", Set.of(FieldSignal.ATTRIBUTE, FieldSignal.METRIC))
            )
            .proposedModelSpecId("dim-example")
            .build();

        Decision decision = BusinessObjectRetirementPolicy.decide(legacy);

        assertThat(decision.fieldDispositions()).hasSameSizeAs(legacy.fields());
        assertThat(decision.fieldDispositions()).extracting(disposition -> disposition.sourceField()).doesNotHaveDuplicates();
        assertThat(decision.fieldDispositions())
            .filteredOn(disposition -> disposition.sourceField().equals("ambiguous"))
            .singleElement()
            .extracting(disposition -> disposition.owner())
            .isEqualTo(FieldOwner.MANUAL_REVIEW);
    }

    @Test
    void duplicateFieldsWithTheSameSignalCollapseToOneManualReviewOwner() {
        Decision decision = BusinessObjectRetirementPolicy.decide(
            input()
                .legacyKind("ENTITY")
                .stableKeys("id")
                .fields(field(" Status_Code ", FieldSignal.STANDARD), field("STATUS_CODE", FieldSignal.STANDARD))
                .proposedModelSpecId("dim-example")
                .build()
        );

        assertThat(decision.fieldDispositions()).singleElement().satisfies(disposition -> {
            assertThat(disposition.sourceField()).isEqualTo("status_code");
            assertThat(disposition.owner()).isEqualTo(FieldOwner.MANUAL_REVIEW);
        });
        assertThat(decision.conflicts()).containsExactly("FIELD_DUPLICATE:status_code");
        assertThat(decision.readiness()).isEqualTo(Readiness.NEEDS_CLASSIFICATION);
    }

    @Test
    void duplicateFieldsWithConflictingSignalsStillHaveOnlyOneManualReviewOwner() {
        Decision decision = BusinessObjectRetirementPolicy.decide(
            input()
                .legacyKind("ENTITY")
                .stableKeys("id")
                .fields(field("Amount", FieldSignal.ATTRIBUTE), field(" amount ", FieldSignal.METRIC))
                .proposedModelSpecId("dim-example")
                .build()
        );

        assertThat(decision.fieldDispositions()).singleElement().satisfies(disposition -> {
            assertThat(disposition.sourceField()).isEqualTo("amount");
            assertThat(disposition.owner()).isEqualTo(FieldOwner.MANUAL_REVIEW);
        });
        assertThat(decision.conflicts()).contains("FIELD_DUPLICATE:amount");
        assertThat(decision.readiness()).isEqualTo(Readiness.NEEDS_CLASSIFICATION);
    }

    @Test
    void fieldMappingIsDeterministicAcrossInputOrderAndNameCasing() {
        Decision first = BusinessObjectRetirementPolicy.decide(
            input()
                .legacyKind("ENTITY")
                .stableKeys("id")
                .fields(
                    field("Zeta", FieldSignal.ATTRIBUTE),
                    field("alpha", FieldSignal.KEY),
                    field("BETA", FieldSignal.STANDARD),
                    field("ALPHA", FieldSignal.KEY)
                )
                .proposedModelSpecId("dim-example")
                .build()
        );
        Decision second = BusinessObjectRetirementPolicy.decide(
            input()
                .legacyKind("ENTITY")
                .stableKeys("id")
                .fields(
                    field("ALPHA", FieldSignal.KEY),
                    field("beta", FieldSignal.STANDARD),
                    field("Alpha", FieldSignal.KEY),
                    field("zeta", FieldSignal.ATTRIBUTE)
                )
                .proposedModelSpecId("dim-example")
                .build()
        );

        assertThat(first.fieldDispositions()).isEqualTo(second.fieldDispositions());
        assertThat(first.conflicts()).isEqualTo(second.conflicts());
        assertThat(first.fieldDispositions()).extracting(disposition -> disposition.sourceField()).containsExactly("alpha", "beta", "zeta");
        assertThat(first.conflicts()).containsExactly("FIELD_DUPLICATE:alpha");
    }

    @Test
    void legacySourceValuesArePreservedAndMissingJoinDetailsAreNeverInvented() {
        LegacySourceRef source = new LegacySourceRef(
            "TABLE",
            "ods_order",
            "ODS",
            "JOINED",
            null,
            "orders.customer_id = customer.id",
            7
        );
        LegacyBusinessObject legacy = input()
            .legacyKind("FACT")
            .factSignal(true)
            .grainKeys("order_id")
            .candidateTargets(factTarget("fact-order-detail"))
            .sourceRefs(source)
            .build();

        Decision decision = BusinessObjectRetirementPolicy.decide(legacy);

        assertThat(decision.mappedSourceRefs()).singleElement().satisfies(mapped -> {
            assertThat(mapped.role()).isEqualTo(SourceRole.JOINED);
            assertThat(mapped.alias()).isNull();
            assertThat(mapped.joinType()).isNull();
            assertThat(mapped.joinExpression()).isEqualTo(source.joinExpression());
            assertThat(mapped.sortOrder()).isEqualTo(source.sortOrder());
            assertThat(mapped.legacyRawRole()).isEqualTo(source.tableRole());
        });
        assertThat(decision.conflicts()).contains("SOURCE_ALIAS_MISSING:ods_order", "SOURCE_JOIN_TYPE_AMBIGUOUS:ods_order:JOINED");
        assertThat(decision.readiness()).isEqualTo(Readiness.NEEDS_CLASSIFICATION);
        assertThat(decision.executableAction()).isEmpty();
    }

    @Test
    void unambiguousLegacyJoinRoleMayBeMappedWithoutChangingTheRawValue() {
        LegacySourceRef source = new LegacySourceRef("TABLE", "dim_customer", "DWD", "LEFT_JOIN", "customer", "f.customer_id = customer.id", 2);

        Decision decision = BusinessObjectRetirementPolicy.decide(
            input()
                .legacyKind("FACT")
                .factSignal(true)
                .grainKeys("order_id")
                .candidateTargets(factTarget("fact-order-detail"))
                .sourceRefs(source)
                .build()
        );

        assertThat(decision.mappedSourceRefs()).singleElement().satisfies(mapped -> {
            assertThat(mapped.role()).isEqualTo(SourceRole.JOINED);
            assertThat(mapped.joinType()).isEqualTo(JoinType.LEFT);
            assertThat(mapped.legacyRawRole()).isEqualTo("LEFT_JOIN");
        });
    }

    @Test
    void legacyReferenceIdentityIncludesBothSourceSystemAndSourceId() {
        LegacyRef semanticRef = BusinessObjectRetirementPolicy.legacyRef("semantic", "object-7");
        LegacyRef modelingRef = BusinessObjectRetirementPolicy.legacyRef("modeling", "object-7");

        assertThat(semanticRef).isNotEqualTo(modelingRef);
        assertThat(semanticRef.externalForm()).isEqualTo("semantic:object-7");
        assertThat(modelingRef.externalForm()).isEqualTo("modeling:object-7");
    }

    @Test
    void domainMismatchNeedsClassificationAndCannotExecuteSuggestedDisposition() {
        LegacyBusinessObject legacy = input()
            .legacyKind("ENTITY")
            .stableKeys("id")
            .targetDomainId("finance")
            .proposedModelSpecId("dim-department")
            .build();

        Decision decision = BusinessObjectRetirementPolicy.decide(legacy);

        assertThat(decision.identification()).isEqualTo(Identification.DIMENSION_LIKE);
        assertThat(decision.readiness()).isEqualTo(Readiness.NEEDS_CLASSIFICATION);
        assertThat(decision.recommendedDisposition()).contains(Disposition.AUTO_DIMENSION);
        assertThat(decision.executableAction()).isEmpty();
    }

    @Test
    void targetWriteMetadataUsesModelIdentityAndHasNoObjectIdComponent() {
        assertThat(Arrays.stream(TargetWriteMetadata.class.getRecordComponents()).map(component -> component.getName()))
            .containsExactly("planId", "domainId", "modelSpecId", "revision", "legacyRef")
            .noneMatch(name -> name.equalsIgnoreCase("objectId"));

        TargetWriteMetadata metadata = new TargetWriteMetadata(
            "plan-1",
            "operations",
            "fact-order-detail",
            3,
            BusinessObjectRetirementPolicy.legacyRef("semantic", "object-7")
        );
        assertThat(metadata.modelSpecId()).isEqualTo("fact-order-detail");
        assertThat(metadata.legacyRef().externalForm()).isEqualTo("semantic:object-7");
    }

    @Test
    void archiveBecomesExecutableOnlyAfterAllConsumersAreGone() {
        Decision blocked = BusinessObjectRetirementPolicy.decide(
            input().legacyKind(null).archiveRequested(true).consumerRefs("metric:revenue").build()
        );
        Decision ready = BusinessObjectRetirementPolicy.decide(input().legacyKind(null).archiveRequested(true).build());

        assertThat(blocked.identification()).isEqualTo(Identification.ARCHIVE_CANDIDATE);
        assertThat(blocked.readiness()).isEqualTo(Readiness.BLOCKED_BY_CONSUMERS);
        assertThat(blocked.recommendedDisposition()).contains(Disposition.ARCHIVE_ONLY);
        assertThat(blocked.executableAction()).isEmpty();

        assertThat(ready.readiness()).isEqualTo(Readiness.READY);
        assertThat(ready.executableAction()).contains(Disposition.ARCHIVE_ONLY);
    }

    @Test
    void archiveWithDomainMismatchNeedsClassificationEvenWithNoConsumers() {
        Decision decision = BusinessObjectRetirementPolicy.decide(
            input().legacyKind(null).targetDomainId("finance").archiveRequested(true).build()
        );

        assertThat(decision.identification()).isEqualTo(Identification.ARCHIVE_CANDIDATE);
        assertThat(decision.readiness()).isEqualTo(Readiness.NEEDS_CLASSIFICATION);
        assertThat(decision.executableAction()).isEmpty();
    }

    @Test
    void archiveWithFieldConflictNeedsClassificationEvenWithNoConsumers() {
        Decision decision = BusinessObjectRetirementPolicy.decide(
            input()
                .legacyKind(null)
                .fields(field("status", FieldSignal.ATTRIBUTE), field("STATUS", FieldSignal.STANDARD))
                .archiveRequested(true)
                .build()
        );

        assertThat(decision.readiness()).isEqualTo(Readiness.NEEDS_CLASSIFICATION);
        assertThat(decision.conflicts()).contains("FIELD_DUPLICATE:status");
        assertThat(decision.executableAction()).isEmpty();
    }

    @Test
    void archiveWithSourceConflictNeedsClassificationEvenWithNoConsumers() {
        Decision decision = BusinessObjectRetirementPolicy.decide(
            input()
                .legacyKind(null)
                .sourceRefs(new LegacySourceRef("TABLE", "ods_order", "ODS", "JOINED", null, null, 1))
                .archiveRequested(true)
                .build()
        );

        assertThat(decision.readiness()).isEqualTo(Readiness.NEEDS_CLASSIFICATION);
        assertThat(decision.conflicts()).contains("SOURCE_ALIAS_MISSING:ods_order", "SOURCE_JOIN_TYPE_AMBIGUOUS:ods_order:JOINED");
        assertThat(decision.executableAction()).isEmpty();
    }

    @Test
    void canonicalFixtureDrivesAllFourIdentificationAndDispositionOutcomes() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        try (
            InputStream stream = BusinessObjectRetirementPolicyTest.class.getResourceAsStream(
                "/fixtures/modeling-retirement/business-object-dispositions.json"
            )
        ) {
            assertThat(stream).as("retirement classification fixture").isNotNull();
            JsonNode cases = objectMapper.readTree(stream).path("cases");
            assertThat(cases).hasSize(4);

            for (JsonNode fixtureCase : cases) {
                JsonNode fixtureInput = fixtureCase.path("input");
                LegacyInputBuilder builder = input()
                    .legacyKind(fixtureInput.path("legacyKind").isNull() ? null : fixtureInput.path("legacyKind").asText())
                    .stableKeys(strings(fixtureInput.path("stableKeys")))
                    .grainKeys(strings(fixtureInput.path("grainKeys")))
                    .factSignal(fixtureInput.path("factSignal").asBoolean(false))
                    .archiveRequested(fixtureInput.path("archiveRequested").asBoolean(false))
                    .proposedModelSpecId(textOrNull(fixtureInput.path("proposedModelSpecId")));
                List<CandidateTarget> targets = new ArrayList<>();
                fixtureInput
                    .path("candidateTargets")
                    .forEach(target ->
                        targets.add(
                            new CandidateTarget(
                                target.path("modelSpecId").asText(),
                                target.path("modelType").asText(),
                                target.path("domainId").asText()
                            )
                        )
                    );
                builder.candidateTargets(targets.toArray(CandidateTarget[]::new));

                Decision decision = BusinessObjectRetirementPolicy.decide(builder.build());
                JsonNode expected = fixtureCase.path("expected");
                assertThat(decision.identification())
                    .as(fixtureCase.path("name").asText())
                    .isEqualTo(Identification.valueOf(expected.path("identification").asText()));
                assertThat(decision.readiness()).isEqualTo(Readiness.valueOf(expected.path("readiness").asText()));
                assertThat(decision.recommendedDisposition())
                    .contains(Disposition.valueOf(expected.path("recommendedDisposition").asText()));
                assertThat(decision.executableAction().map(Enum::name).orElse(null))
                    .isEqualTo(textOrNull(expected.path("executableAction")));
            }
        }
    }

    private static LegacyField field(String name, FieldSignal signal) {
        return new LegacyField(name, Set.of(signal));
    }

    private static CandidateTarget factTarget(String modelSpecId) {
        return new CandidateTarget(modelSpecId, "FACT", "operations");
    }

    private static String[] strings(JsonNode node) {
        List<String> values = new ArrayList<>();
        node.forEach(value -> values.add(value.asText()));
        return values.toArray(String[]::new);
    }

    private static String textOrNull(JsonNode node) {
        return node.isMissingNode() || node.isNull() ? null : node.asText();
    }

    private static LegacyInputBuilder input() {
        return new LegacyInputBuilder();
    }

    private static final class LegacyInputBuilder {

        private String sourceSystem = "semantic";
        private String sourceId = "object-1";
        private String legacyKind = "ENTITY";
        private String domainId = "operations";
        private String targetDomainId = "operations";
        private List<String> stableKeys = List.of();
        private boolean factSignal;
        private List<String> grainKeys = List.of();
        private List<String> timeFields = List.of();
        private List<LegacyField> fields = List.of();
        private List<LegacySourceRef> sourceRefs = List.of();
        private List<CandidateTarget> candidateTargets = List.of();
        private List<String> consumerRefs = List.of();
        private boolean archiveRequested;
        private String planId = "plan-1";
        private String proposedModelSpecId;
        private int targetRevision = 1;

        LegacyInputBuilder legacyKind(String value) {
            legacyKind = value;
            return this;
        }

        LegacyInputBuilder targetDomainId(String value) {
            targetDomainId = value;
            return this;
        }

        LegacyInputBuilder stableKeys(String... values) {
            stableKeys = List.of(values);
            return this;
        }

        LegacyInputBuilder factSignal(boolean value) {
            factSignal = value;
            return this;
        }

        LegacyInputBuilder grainKeys(String... values) {
            grainKeys = List.of(values);
            return this;
        }

        LegacyInputBuilder fields(LegacyField... values) {
            fields = List.of(values);
            return this;
        }

        LegacyInputBuilder sourceRefs(LegacySourceRef... values) {
            sourceRefs = List.of(values);
            return this;
        }

        LegacyInputBuilder candidateTargets(CandidateTarget... values) {
            candidateTargets = List.of(values);
            return this;
        }

        LegacyInputBuilder consumerRefs(String... values) {
            consumerRefs = List.of(values);
            return this;
        }

        LegacyInputBuilder archiveRequested(boolean value) {
            archiveRequested = value;
            return this;
        }

        LegacyInputBuilder proposedModelSpecId(String value) {
            proposedModelSpecId = value;
            return this;
        }

        LegacyBusinessObject build() {
            return new LegacyBusinessObject(
                sourceSystem,
                sourceId,
                legacyKind,
                domainId,
                targetDomainId,
                stableKeys,
                factSignal,
                grainKeys,
                timeFields,
                fields,
                sourceRefs,
                candidateTargets,
                consumerRefs,
                archiveRequested,
                planId,
                proposedModelSpecId,
                targetRevision
            );
        }
    }
}
