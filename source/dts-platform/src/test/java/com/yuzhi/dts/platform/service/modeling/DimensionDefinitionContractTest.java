package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.CreateCommand;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.FieldIssue;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.HierarchyLevelSemantic;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.HierarchySemantic;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.ReuseScope;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.UpdateCommand;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class DimensionDefinitionContractTest {

    private static final UUID DOMAIN_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Test
    void acceptsValidCreateAndUpdateCommands() {
        assertThat(DimensionDefinitionContract.validateCreate(validCreate())).isEmpty();
        assertThat(DimensionDefinitionContract.validateUpdate(validUpdate())).isEmpty();
    }

    @Test
    void exposesExactInputAllowlistsWithoutImplementationProperties() {
        assertThat(DimensionDefinitionContract.CREATE_FIELDS).containsExactlyInAnyOrderElementsOf(recordFields(CreateCommand.class));
        assertThat(DimensionDefinitionContract.UPDATE_FIELDS).containsExactlyInAnyOrderElementsOf(recordFields(UpdateCommand.class));
        assertThat(DimensionDefinitionContract.CREATE_FIELDS)
            .doesNotContain("systemCode", "source", "targetLayer", "scd", "materialization", "sql", "dbt");
        assertThat(DimensionDefinitionContract.UPDATE_FIELDS)
            .doesNotContain("systemCode", "source", "targetLayer", "scd", "materialization", "sql", "dbt", "idempotencyKey");
    }

    @Test
    void hierarchySemanticsContainBusinessLabelsOnly() {
        assertThat(recordFields(HierarchySemantic.class)).containsExactlyInAnyOrder("code", "name", "levels");
        assertThat(recordFields(HierarchyLevelSemantic.class)).containsExactlyInAnyOrder("code", "name", "order");
    }

    @Test
    void rejectsMissingAndBlankRequiredCreatePropertiesInStableOrder() {
        CreateCommand command = new CreateCommand(null, " ", " ", " ", null, List.of(), " ");

        assertThat(DimensionDefinitionContract.validateCreate(command))
            .extracting(FieldIssue::field, FieldIssue::code)
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple("domainId", "DIMENSION_DEFINITION_DOMAIN_REQUIRED"),
                org.assertj.core.groups.Tuple.tuple("name", "DIMENSION_DEFINITION_NAME_REQUIRED"),
                org.assertj.core.groups.Tuple.tuple("definition", "DIMENSION_DEFINITION_DEFINITION_REQUIRED"),
                org.assertj.core.groups.Tuple.tuple("ownerId", "DIMENSION_DEFINITION_OWNER_REQUIRED"),
                org.assertj.core.groups.Tuple.tuple("reuseScope", "DIMENSION_DEFINITION_REUSE_SCOPE_REQUIRED"),
                org.assertj.core.groups.Tuple.tuple("idempotencyKey", "DIMENSION_DEFINITION_IDEMPOTENCY_KEY_REQUIRED")
            );
    }

    @Test
    void rejectsBlankRequiredUpdatePropertiesInStableOrder() {
        UpdateCommand command = new UpdateCommand(" ", " ", " ", null, List.of());

        assertThat(DimensionDefinitionContract.validateUpdate(command))
            .extracting(FieldIssue::field, FieldIssue::code)
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple("name", "DIMENSION_DEFINITION_NAME_REQUIRED"),
                org.assertj.core.groups.Tuple.tuple("definition", "DIMENSION_DEFINITION_DEFINITION_REQUIRED"),
                org.assertj.core.groups.Tuple.tuple("ownerId", "DIMENSION_DEFINITION_OWNER_REQUIRED"),
                org.assertj.core.groups.Tuple.tuple("reuseScope", "DIMENSION_DEFINITION_REUSE_SCOPE_REQUIRED")
            );
    }

    @Test
    void rejectsInvalidHierarchyCodesNamesAndLevels() {
        assertHierarchyInvalid(new HierarchySemantic("customer", "Customer location", validLevels()));
        assertHierarchyInvalid(new HierarchySemantic("CUSTOMER_LOCATION", " ", validLevels()));
        assertHierarchyInvalid(new HierarchySemantic("CUSTOMER_LOCATION", "Customer location", List.of()));
        assertHierarchyInvalid(new HierarchySemantic("CUSTOMER_LOCATION", "Customer location", null));
        assertHierarchyInvalid(
            new HierarchySemantic(
                "CUSTOMER_LOCATION",
                "Customer location",
                List.of(new HierarchyLevelSemantic("country", "Country", 1))
            )
        );
        assertHierarchyInvalid(
            new HierarchySemantic(
                "CUSTOMER_LOCATION",
                "Customer location",
                List.of(new HierarchyLevelSemantic("COUNTRY", " ", 1))
            )
        );
    }

    @Test
    void rejectsDuplicateHierarchyCodes() {
        assertHierarchiesInvalid(
            List.of(
                new HierarchySemantic("CUSTOMER_LOCATION", "Customer location", validLevels()),
                new HierarchySemantic("CUSTOMER_LOCATION", "Alternate location", validLevels())
            )
        );
    }

    @Test
    void rejectsDuplicateLevelCodesAndOrdersAndNonContiguousOrders() {
        assertHierarchyInvalid(
            new HierarchySemantic(
                "CUSTOMER_LOCATION",
                "Customer location",
                List.of(
                    new HierarchyLevelSemantic("COUNTRY", "Country", 1),
                    new HierarchyLevelSemantic("COUNTRY", "Duplicate country", 2)
                )
            )
        );
        assertHierarchyInvalid(
            new HierarchySemantic(
                "CUSTOMER_LOCATION",
                "Customer location",
                List.of(
                    new HierarchyLevelSemantic("COUNTRY", "Country", 1),
                    new HierarchyLevelSemantic("CITY", "City", 1)
                )
            )
        );
        assertHierarchyInvalid(
            new HierarchySemantic(
                "CUSTOMER_LOCATION",
                "Customer location",
                List.of(
                    new HierarchyLevelSemantic("COUNTRY", "Country", 1),
                    new HierarchyLevelSemantic("CITY", "City", 3)
                )
            )
        );
    }

    private static CreateCommand validCreate() {
        return new CreateCommand(
            DOMAIN_ID,
            "Customer",
            "Customer business dimension",
            "owner-1",
            ReuseScope.DOMAIN,
            validHierarchies(),
            "create-customer"
        );
    }

    private static UpdateCommand validUpdate() {
        return new UpdateCommand("Customer", "Customer business dimension", "owner-1", ReuseScope.DOMAIN, validHierarchies());
    }

    private static List<HierarchySemantic> validHierarchies() {
        return List.of(new HierarchySemantic("CUSTOMER_LOCATION", "Customer location", validLevels()));
    }

    private static List<HierarchyLevelSemantic> validLevels() {
        return List.of(
            new HierarchyLevelSemantic("COUNTRY", "Country", 1),
            new HierarchyLevelSemantic("CITY", "City", 2)
        );
    }

    private static void assertHierarchyInvalid(HierarchySemantic hierarchy) {
        assertHierarchiesInvalid(List.of(hierarchy));
    }

    private static void assertHierarchiesInvalid(List<HierarchySemantic> hierarchies) {
        CreateCommand command = new CreateCommand(
            DOMAIN_ID,
            "Customer",
            "Customer business dimension",
            "owner-1",
            ReuseScope.DOMAIN,
            hierarchies,
            "create-customer"
        );

        assertThat(DimensionDefinitionContract.validateCreate(command))
            .extracting(FieldIssue::field, FieldIssue::code)
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple("hierarchies", "DIMENSION_DEFINITION_HIERARCHY_INVALID")
            );
    }

    private static Set<String> recordFields(Class<?> type) {
        return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).collect(Collectors.toSet());
    }
}
