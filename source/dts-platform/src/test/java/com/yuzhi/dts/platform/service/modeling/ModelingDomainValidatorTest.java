package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ModelingDomainValidatorTest {

    @Test
    void rejectsBusinessObjectWithoutProcessOrGrain() {
        ModelingVNextContract.BusinessObject object = new ModelingVNextContract.BusinessObject(
            "object-1",
            "project_node",
            "项目节点",
            null,
            ModelingVNextContract.ObjectKind.FACT,
            " ",
            List.of("project_no"),
            new ModelingVNextContract.Grain(" ", List.of()),
            List.of(),
            "DRAFT",
            ModelingVNextContract.ImplementationMode.DESIGNER_GENERATED
        );

        List<ModelingDomainValidator.Issue> issues = ModelingDomainValidator.validateBusinessObject(object, "tenant-a");

        assertThat(issues).extracting(ModelingDomainValidator.Issue::code).containsExactlyInAnyOrder("PROCESS_REQUIRED", "GRAIN_REQUIRED");
    }

    @Test
    void rejectsDwdModelWithoutGrainOrBusinessKey() {
        ModelingVNextContract.BusinessObject fixtureObject = ModelingVNextContract.pjmProjectNodeFixture().businessObject();
        ModelingVNextContract.BusinessObject object = new ModelingVNextContract.BusinessObject(
            fixtureObject.id(),
            fixtureObject.code(),
            fixtureObject.name(),
            fixtureObject.description(),
            fixtureObject.objectKind(),
            fixtureObject.processId(),
            List.of(),
            fixtureObject.grain(),
            fixtureObject.sourceRefs(),
            fixtureObject.status(),
            fixtureObject.implementationMode()
        );
        ModelingVNextContract.ModelSpec model = new ModelingVNextContract.ModelSpec(
            "model-1",
            object.id(),
            object.processId(),
            ModelingVNextContract.Layer.DWD,
            ModelingVNextContract.ModelType.FACT,
            ModelingVNextContract.ImplementationMode.DESIGNER_GENERATED,
            "project_node_detail",
            new ModelingVNextContract.Grain(" ", List.of()),
            List.of(),
            object.sourceRefs(),
            1
        );

        List<ModelingDomainValidator.Issue> issues = ModelingDomainValidator.validateModelSpec(model, object);

        assertThat(issues).extracting(ModelingDomainValidator.Issue::code).containsExactlyInAnyOrder("GRAIN_REQUIRED", "BUSINESS_KEY_REQUIRED", "STANDARD_BINDING_REQUIRED");
    }

    @Test
    void rejectsModelWhenProcessDoesNotMatchBusinessObject() {
        ModelingVNextContract.PjmFixture fixture = ModelingVNextContract.pjmProjectNodeFixture();
        ModelingVNextContract.ModelSpec model = new ModelingVNextContract.ModelSpec(
            fixture.modelSpec().id(),
            fixture.modelSpec().objectId(),
            "another-process",
            fixture.modelSpec().layer(),
            fixture.modelSpec().modelType(),
            fixture.modelSpec().implementationMode(),
            fixture.modelSpec().name(),
            fixture.modelSpec().grain(),
            fixture.modelSpec().standardBindings(),
            fixture.modelSpec().sourceRefs(),
            fixture.modelSpec().revision()
        );

        assertThat(ModelingDomainValidator.validateModelSpec(model, fixture.businessObject()))
            .extracting(ModelingDomainValidator.Issue::code)
            .contains("PROCESS_MISMATCH");
    }

    @Test
    void rejectsDuplicateBusinessObjectCodeWithinTenant() {
        assertThat(ModelingDomainValidator.validateBusinessObjectCode("project_node", List.of("project_node", "order"), "tenant-a"))
            .extracting(ModelingDomainValidator.Issue::code)
            .containsExactly("DUPLICATE_OBJECT_CODE");
    }

    @Test
    void projectSpaceIsOptionalForNormalModeling() {
        ModelingVNextContract.PjmFixture fixture = ModelingVNextContract.pjmProjectNodeFixture();

        assertThat(ModelingDomainValidator.validateBusinessObject(fixture.businessObject(), "tenant-a")).isEmpty();
        assertThat(ModelingDomainValidator.validateModelSpec(fixture.modelSpec(), fixture.businessObject())).isEmpty();
    }
}
