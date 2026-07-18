package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;

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
            .candidateTargetModelIds("fact-order-detail")
            .build();

        Decision decision = BusinessObjectRetirementPolicy.decide(legacy);

        assertThat(decision.identification()).isEqualTo(Identification.FACT_LIKE);
        assertThat(decision.readiness()).isEqualTo(Readiness.READY);
        assertThat(decision.executableAction()).contains(Disposition.AUTO_FACT_MERGE);
        assertThat(decision.targetModelSpecId()).contains("fact-order-detail");
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
            .candidateTargetModelIds("fact-order-detail")
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
                .candidateTargetModelIds("fact-order-detail")
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

    private static LegacyField field(String name, FieldSignal signal) {
        return new LegacyField(name, Set.of(signal));
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
        private List<String> candidateTargetModelIds = List.of();
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

        LegacyInputBuilder candidateTargetModelIds(String... values) {
            candidateTargetModelIds = List.of(values);
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
                candidateTargetModelIds,
                consumerRefs,
                archiveRequested,
                planId,
                proposedModelSpecId,
                targetRevision
            );
        }
    }
}
