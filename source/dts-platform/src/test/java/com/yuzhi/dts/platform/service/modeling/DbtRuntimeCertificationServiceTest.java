package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuzhi.dts.platform.config.DbtRuntimeCertificationProperties;
import org.junit.jupiter.api.Test;

class DbtRuntimeCertificationServiceTest {

    @Test
    void defaultConfigurationFailsClosed() {
        var service = new DbtRuntimeCertificationService(
            new DbtRuntimeCertificationProperties()
        );

        assertNotCertified(service);
    }

    @Test
    void returnsOnlyTheExactCertifiedEvidencePins() {
        DbtRuntimeCertificationProperties properties = certified();

        var runtime = new DbtRuntimeCertificationService(properties)
            .requireCertified();

        assertThat(runtime.profileId()).isEqualTo(
            DbtRuntimeCertificationService.EXPECTED_CERTIFICATION_PROFILE_ID
        );
        assertThat(runtime.candidateProfileId()).isEqualTo(
            DbtRuntimeCertificationService.EXPECTED_CANDIDATE_PROFILE_ID
        );
        assertThat(runtime.imageRef()).isEqualTo(
            "registry.example/dts-dbt@" +
            DbtRuntimeCertificationService.EXPECTED_CERTIFIED_IMAGE_DIGEST
        );
        assertThat(runtime.candidateImageDigest()).isEqualTo(
            DbtRuntimeCertificationService.EXPECTED_CANDIDATE_IMAGE_DIGEST
        );
        assertThat(runtime.evidenceManifestSha256())
            .isEqualTo(
                DbtRuntimeCertificationService.EXPECTED_EVIDENCE_MANIFEST_SHA256
            );
    }

    @Test
    void dependencyAdapterDigestAndEvidenceDriftFailClosed() {
        DbtRuntimeCertificationProperties properties = certified();
        properties.setDbtCoreVersion("1.10.21");
        assertNotCertified(new DbtRuntimeCertificationService(properties));

        properties = certified();
        properties.setAdapter("duckdb");
        assertNotCertified(new DbtRuntimeCertificationService(properties));

        properties = certified();
        properties.setProfileId("H83-CERT-UNRELATED");
        assertNotCertified(new DbtRuntimeCertificationService(properties));

        properties = certified();
        properties.setCandidateImageDigest("sha256:" + "b".repeat(64));
        assertNotCertified(new DbtRuntimeCertificationService(properties));

        properties = certified();
        properties.setImageRef(
            "registry.example/dts-dbt@sha256:" + "b".repeat(64)
        );
        assertNotCertified(new DbtRuntimeCertificationService(properties));

        properties = certified();
        properties.setEvidenceManifestSha256("pending");
        assertNotCertified(new DbtRuntimeCertificationService(properties));
    }

    @Test
    void mutableImageTagNeverQualifies() {
        DbtRuntimeCertificationProperties properties = certified();
        properties.setImageRef("registry.example/dts-dbt:1.10.0");

        assertNotCertified(new DbtRuntimeCertificationService(properties));
    }

    @Test
    void revokedR1DerivativeNeverQualifies() {
        DbtRuntimeCertificationProperties properties = certified();
        properties.setImageRef(
            "registry.example/dts-dbt@sha256:" +
            "423926d8ce77a9bdce23db23501910843e9c7b17476b1c025320a3098e2d33f8"
        );

        assertNotCertified(new DbtRuntimeCertificationService(properties));
    }

    static DbtRuntimeCertificationService.CertifiedRuntime runtime() {
        return new DbtRuntimeCertificationService(certified())
            .requireCertified();
    }

    private static DbtRuntimeCertificationProperties certified() {
        DbtRuntimeCertificationProperties properties =
            new DbtRuntimeCertificationProperties();
        properties.setStatus("CERTIFIED");
        properties.setProfileId(
            DbtRuntimeCertificationService.EXPECTED_CERTIFICATION_PROFILE_ID
        );
        properties.setCandidateProfileId(
            DbtRuntimeCertificationService.EXPECTED_CANDIDATE_PROFILE_ID
        );
        properties.setPlatform("linux/amd64");
        properties.setDbtCoreVersion("1.10.22");
        properties.setDbtPostgresVersion("1.10.0");
        properties.setAdapter("postgres");
        properties.setDatabaseType("PostgreSQL");
        properties.setRequirementsLockSha256(
            DbtRuntimeCertificationService.EXPECTED_REQUIREMENTS_LOCK_SHA256
        );
        properties.setCandidateImageDigest(
            DbtRuntimeCertificationService.EXPECTED_CANDIDATE_IMAGE_DIGEST
        );
        properties.setImageRef(
            "registry.example/dts-dbt@" +
            DbtRuntimeCertificationService.EXPECTED_CERTIFIED_IMAGE_DIGEST
        );
        properties.setEvidenceManifestSha256(
            DbtRuntimeCertificationService.EXPECTED_EVIDENCE_MANIFEST_SHA256
        );
        return properties;
    }

    private static void assertNotCertified(
        DbtRuntimeCertificationService service
    ) {
        assertThatThrownBy(service::requireCertified)
            .isInstanceOf(ModelReleaseCandidateException.class)
            .extracting(failure ->
                ((ModelReleaseCandidateException) failure).code()
            )
            .isEqualTo("DBT_RUNTIME_NOT_CERTIFIED");
    }
}
