package com.yuzhi.dts.platform.service.modeling.imports.validator;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.platform.service.modeling.imports.ModelPackageFixtures;
import com.yuzhi.dts.platform.service.modeling.imports.checksum.ModelPackageChecksum;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.ModelPackage;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.PackageModel;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.SemanticMetadata;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModelPackageValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ModelPackageValidator validator = new ModelPackageValidator(objectMapper);

    @Test
    void acceptsMinimalValidPackage() throws Exception {
        byte[] json = objectMapper.writeValueAsBytes(ModelPackageFixtures.validPackage());

        ModelPackage parsed = validator.parseAndValidate(json);

        assertThat(parsed.schemaVersion()).isEqualTo("dts.model-package/v1");
    }

    @Test
    void rejectsUnknownFieldUnsupportedVersionChecksumAndOversizeWithStableCodes() throws Exception {
        ObjectNode unknown = objectMapper.valueToTree(ModelPackageFixtures.validPackage());
        unknown.put("unexpected", true);
        assertCode(unknown.toString().getBytes(), "MODEL_PACKAGE_SCHEMA_INVALID");

        ObjectNode version = objectMapper.valueToTree(ModelPackageFixtures.validPackage());
        version.put("schemaVersion", "dts.model-package/v2");
        assertCode(version.toString().getBytes(), "MODEL_PACKAGE_SCHEMA_VERSION_UNSUPPORTED");

        ObjectNode checksum = objectMapper.valueToTree(ModelPackageFixtures.validPackage());
        checksum.put("packageChecksum", "0".repeat(64));
        assertCode(checksum.toString().getBytes(), "MODEL_PACKAGE_CHECKSUM_MISMATCH");

        assertThat(validator.validateBytes(new byte[ModelPackageValidator.MAX_PACKAGE_BYTES + 1]))
            .extracting(ModelPackageValidator.ValidationIssue::code)
            .containsExactly("MODEL_PACKAGE_TOO_LARGE");
    }

    @Test
    void capsValidationIssuesAtTwoHundredWithOneStableTruncationMarker() {
        ObjectNode invalid = packageJson();
        for (int index = 0; index < ModelPackageValidator.MAX_VALIDATION_ISSUES + 50; index++) {
            invalid.put("unexpected_" + index, true);
        }

        List<ModelPackageValidator.ValidationIssue> issues = validator.validateBytes(invalid.toString().getBytes());

        assertThat(issues).hasSize(ModelPackageValidator.MAX_VALIDATION_ISSUES);
        assertThat(issues.getLast().code()).isEqualTo(ModelPackageValidator.VALIDATION_ISSUES_TRUNCATED_CODE);
        assertThat(issues)
            .filteredOn(issue -> issue.code().equals(ModelPackageValidator.VALIDATION_ISSUES_TRUNCATED_CODE))
            .hasSize(1);
    }

    @Test
    void rejectsDuplicateUniqueIdAndUnsafeResourcePath() {
        ModelPackage valid = ModelPackageFixtures.validPackage();
        PackageModel original = valid.models().getFirst();
        PackageModel unsafe = new PackageModel(
            original.dbtUniqueId(),
            original.name(),
            original.description(),
            "../outside.sql",
            original.sql(),
            original.materialization(),
            original.config(),
            original.tags(),
            original.columns(),
            original.tests(),
            original.dependencies(),
            original.semantics(),
            original.conversion()
        );
        ModelPackage invalid = ModelPackageChecksum.withChecksum(new ModelPackage(
            valid.schemaVersion(),
            valid.packageId(),
            null,
            valid.dbt(),
            valid.defaults(),
            valid.sources(),
            valid.technicalNodes(),
            List.of(original, unsafe),
            valid.issues()
        ));

        assertThat(validator.validate(invalid))
            .extracting(ModelPackageValidator.ValidationIssue::code)
            .contains("MODEL_PACKAGE_DUPLICATE_UNIQUE_ID", "MODEL_PACKAGE_PATH_INVALID");
    }

    @Test
    void rejectsMissingTopLevelMembersNullableMembersAndNullArraysBeforeRecordBinding() {
        for (String field : List.of("defaults", "sources", "technicalNodes", "models", "issues")) {
            ObjectNode missing = packageJson();
            missing.remove(field);
            assertSchemaPath(missing, "$." + field);
        }

        ObjectNode missingNullableDefault = packageJson();
        ((ObjectNode) missingNullableDefault.path("defaults")).remove("planRef");
        assertSchemaPath(missingNullableDefault, "$.defaults.planRef");

        for (String field : List.of("sources", "technicalNodes", "models", "issues")) {
            ObjectNode nullArray = packageJson();
            nullArray.putNull(field);
            assertSchemaPath(nullArray, "$." + field);
        }
    }

    @Test
    void rejectsMissingSourceAndTechnicalMembersIncludingNestedSqlAndConversion() {
        ObjectNode sourceMemberMissing = packageJson();
        ((ObjectNode) sourceMemberMissing.withArray("sources").get(0)).remove("columns");
        assertSchemaPath(sourceMemberMissing, "$.sources[0].columns");

        ObjectNode technicalConversionMissing = packageWithTechnicalNode();
        ((ObjectNode) technicalConversionMissing.withArray("technicalNodes").get(0)).remove("conversion");
        assertSchemaPath(technicalConversionMissing, "$.technicalNodes[0].conversion");

        ObjectNode technicalSqlMemberMissing = packageWithTechnicalNode();
        ((ObjectNode) technicalSqlMemberMissing.at("/technicalNodes/0/sql")).remove("effectiveSource");
        assertSchemaPath(technicalSqlMemberMissing, "$.technicalNodes[0].sql.effectiveSource");

        ObjectNode technicalNullDependencies = packageWithTechnicalNode();
        ((ObjectNode) technicalNullDependencies.withArray("technicalNodes").get(0)).putNull("dependencies");
        assertSchemaPath(technicalNullDependencies, "$.technicalNodes[0].dependencies");
    }

    @Test
    void rejectsMalformedNestedModelColumnSqlConversionAndSemanticContracts() {
        ObjectNode nullConversionReasons = packageJson();
        ((ObjectNode) nullConversionReasons.at("/models/0/conversion")).putNull("reasonCodes");
        assertSchemaPath(nullConversionReasons, "$.models[0].conversion.reasonCodes");

        ObjectNode missingSemanticSourceRefs = packageJson();
        ((ObjectNode) missingSemanticSourceRefs.at("/models/0/semantics")).remove("sourceRefs");
        assertSchemaPath(missingSemanticSourceRefs, "$.models[0].semantics.sourceRefs");

        ObjectNode wrongSqlChecksumType = packageJson();
        ((ObjectNode) wrongSqlChecksumType.at("/models/0/sql")).put("rawSqlChecksum", 42);
        assertSchemaPath(wrongSqlChecksumType, "$.models[0].sql.rawSqlChecksum");

        ObjectNode unknownColumnMember = packageJson();
        ((ObjectNode) unknownColumnMember.at("/models/0/columns/0")).put("unexpected", true);
        assertSchemaPath(unknownColumnMember, "$.models[0].columns[0].unexpected");
    }

    @Test
    void requiresStableDimensionDefinitionCodeForCanonicalDimensionModels() {
        ModelPackage valid = ModelPackageFixtures.validPackage();
        SemanticMetadata dimension = new SemanticMetadata(
            "DIMENSION",
            "DWD",
            valid.models().getFirst().semantics().grain(),
            null,
            null,
            "PROJECT_MANAGEMENT",
            List.of(),
            List.of(),
            valid.models().getFirst().semantics().fieldRoles(),
            "STATIC_DBT_SEED_SQL",
            null,
            "test",
            false
        );
        PackageModel dimensionModel = ModelPackageFixtures.model(
            "model.pjm.dimension",
            "source.pjm.budget",
            "select budget_id from source_budget",
            dimension
        );
        ModelPackage invalid = ModelPackageChecksum.withChecksum(new ModelPackage(
            valid.schemaVersion(),
            valid.packageId(),
            null,
            valid.dbt(),
            valid.defaults(),
            valid.sources(),
            valid.technicalNodes(),
            List.of(dimensionModel),
            valid.issues()
        ));

        assertThat(validator.validate(invalid))
            .extracting(ModelPackageValidator.ValidationIssue::code)
            .contains("MODEL_PACKAGE_DIMENSION_DEFINITION_CODE_REQUIRED");
    }

    private ObjectNode packageJson() {
        return objectMapper.valueToTree(ModelPackageFixtures.validPackage());
    }

    private ObjectNode packageWithTechnicalNode() {
        ObjectNode root = packageJson();
        ObjectNode model = (ObjectNode) root.withArray("models").get(0);
        ObjectNode technical = objectMapper.createObjectNode();
        technical.put("dbtUniqueId", "test.pjm.not_null_budget");
        technical.put("name", "not_null_budget");
        technical.put("resourceType", "test");
        technical.put("resourcePath", "models/schema.yml");
        technical.set("sql", model.path("sql").deepCopy());
        technical.set("config", objectMapper.createObjectNode());
        technical.set("dependencies", objectMapper.createArrayNode().add("model.pjm.budget"));
        technical.set("tags", objectMapper.createArrayNode());
        ObjectNode conversion = objectMapper.createObjectNode();
        conversion.put("mode", "TECHNICAL_ONLY");
        conversion.set("reasonCodes", objectMapper.createArrayNode().add("TECHNICAL_RESOURCE"));
        technical.set("conversion", conversion);
        root.withArray("technicalNodes").add(technical);
        return root;
    }

    private void assertSchemaPath(JsonNode json, String path) {
        assertThat(validator.validateBytes(json.toString().getBytes()))
            .filteredOn(issue -> issue.code().equals("MODEL_PACKAGE_SCHEMA_INVALID"))
            .extracting(ModelPackageValidator.ValidationIssue::fieldPath)
            .contains(path);
    }

    private void assertCode(byte[] json, String code) {
        assertThat(validator.validateBytes(json))
            .extracting(ModelPackageValidator.ValidationIssue::code)
            .contains(code);
    }
}
