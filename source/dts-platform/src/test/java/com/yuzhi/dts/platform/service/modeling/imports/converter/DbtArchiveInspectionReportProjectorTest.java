package com.yuzhi.dts.platform.service.modeling.imports.converter;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.imports.ModelPackageFixtures;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.ConversionMode;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.ConversionResult;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.ModelPackage;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.PackageModel;
import com.yuzhi.dts.platform.service.modeling.imports.converter.DbtArchiveInspectionContract.CandidateEligibility;
import com.yuzhi.dts.platform.service.modeling.imports.converter.DbtArchiveInspectionContract.PackageProfile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class DbtArchiveInspectionReportProjectorTest {

    private final DbtModelArchiveInspectService inspector = new DbtModelArchiveInspectService(
        new ObjectMapper(),
        new SafeZipExtractor()
    );
    private final DbtArchiveInspectionReportProjector projector = new DbtArchiveInspectionReportProjector();

    @Test
    void reportsTheRealPjmSourcePackageAsStructureOnlyWithActionableBlockers() throws Exception {
        Path module = Path.of("").toAbsolutePath().normalize();
        Path repository = module.endsWith(Path.of("source", "dts-platform")) ? module.getParent().getParent() : module;
        Path fixture = repository.resolve("worklog/v2.2.3/s10/v4/pjm/pjm-dbt-model.zip");
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isRegularFile(fixture), "PJM repository fixture is unavailable");

        ModelPackage modelPackage = inspector.inspect(
            new MockMultipartFile("archive", fixture.getFileName().toString(), "application/zip", Files.readAllBytes(fixture))
        );
        var compatibility = new DbtCompatibilityEvaluator().evaluate(modelPackage);
        var report = projector.project(modelPackage, compatibility);

        assertThat(report.packageProfile()).isEqualTo(PackageProfile.SOURCE_ONLY);
        assertThat(report.summary().discovered()).isEqualTo(38);
        assertThat(report.summary().technicalOnly()).isEqualTo(10);
        assertThat(report.summary().eligible()).isZero();
        assertThat(report.summary().requiresMapping()).isZero();
        assertThat(report.summary().blocked()).isEqualTo(38);
        assertThat(report.candidates()).allSatisfy(candidate ->
            assertThat(candidate.eligibility()).isEqualTo(CandidateEligibility.BLOCKED)
        );
        assertThat(report.diagnostics()).extracting("code").contains("SOURCE_FIELDS_UNVERIFIED");
    }

    @Test
    void distinguishesEligibleAndBusinessMappingCandidatesWithoutWeakeningTechnicalBlocks() {
        ModelPackage fixture = ModelPackageFixtures.validPackage();
        PackageModel original = fixture.models().getFirst();
        PackageModel mappingCandidate = new PackageModel(
            original.dbtUniqueId(),
            original.name(),
            original.description(),
            original.resourcePath(),
            original.sql(),
            original.materialization(),
            original.config(),
            original.tags(),
            original.columns(),
            original.tests(),
            original.dependencies(),
            original.semantics(),
            new ConversionResult(ConversionMode.BLOCKED, List.of("SOURCE_SEMANTICS_INCOMPLETE"))
        );
        ModelPackage mappingPackage = new ModelPackage(
            fixture.schemaVersion(),
            fixture.packageId(),
            fixture.packageChecksum(),
            fixture.dbt(),
            fixture.defaults(),
            fixture.sources(),
            fixture.technicalNodes(),
            List.of(mappingCandidate),
            fixture.issues()
        );

        var artifactReport = projector.project(fixture, new DbtCompatibilityEvaluator().evaluate(fixture));
        var mappingReport = projector.project(mappingPackage, new DbtCompatibilityEvaluator().evaluate(mappingPackage));

        assertThat(artifactReport.packageProfile()).isEqualTo(PackageProfile.ARTIFACT_RICH);
        assertThat(artifactReport.candidates()).singleElement().extracting("eligibility").isEqualTo(CandidateEligibility.ELIGIBLE);
        assertThat(mappingReport.candidates())
            .singleElement()
            .extracting("eligibility")
            .isEqualTo(CandidateEligibility.REQUIRES_MAPPING);
        assertThat(mappingReport.summary().requiresMapping()).isEqualTo(1);
    }
}
