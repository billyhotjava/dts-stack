package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.config.DbtRuntimeCertificationProperties;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/** Resolves only the immutable Sprint-83 PostgreSQL runtime certified by F0/T05. */
@Service
public class DbtRuntimeCertificationService {

    static final String EXPECTED_CANDIDATE_PROFILE_ID =
        "H83-RT01-LINUX-AMD64-DBT11022-PG1100-LOCK-" +
        "01d7c02b6bf4fefdfc188cbf9ef8aed4fb243c227c060103f195c4ca45af5f02";
    static final String EXPECTED_REQUIREMENTS_LOCK_SHA256 =
        "01d7c02b6bf4fefdfc188cbf9ef8aed4fb243c227c060103f195c4ca45af5f02";
    static final String EXPECTED_CERTIFICATION_PROFILE_ID =
        "H83-CERT-RT01-LINUX-AMD64-EVIDENCE-" +
        "bcd2fc84b05b9508990538c6642be7ac7b35073ec034e6b1980b22f556345a68";
    static final String EXPECTED_CANDIDATE_IMAGE_DIGEST =
        "sha256:fe1d15f1b4215e693dadfc1d99be2ae07c7e50e8df144504005feb41cc7686b7";
    static final String EXPECTED_CERTIFIED_IMAGE_DIGEST =
        "sha256:2f6dddb7237fdb7141f452b6d09da0379ef7569f2f82560473f304b265cbbd85";
    static final String EXPECTED_EVIDENCE_MANIFEST_SHA256 =
        "bcd2fc84b05b9508990538c6642be7ac7b35073ec034e6b1980b22f556345a68";

    private static final Pattern DIGEST_IMAGE = Pattern.compile(
        "^[A-Za-z0-9][A-Za-z0-9._/:@-]{0,255}@sha256:[0-9a-f]{64}$"
    );

    private final DbtRuntimeCertificationProperties properties;

    public DbtRuntimeCertificationService(
        DbtRuntimeCertificationProperties properties
    ) {
        this.properties = Objects.requireNonNull(
            properties,
            "properties is required"
        );
    }

    public CertifiedRuntime requireCertified() {
        if (
            !"CERTIFIED".equals(properties.getStatus()) ||
            !EXPECTED_CERTIFICATION_PROFILE_ID.equals(
                properties.getProfileId()
            ) ||
            !EXPECTED_CANDIDATE_PROFILE_ID.equals(
                properties.getCandidateProfileId()
            ) ||
            !"linux/amd64".equals(properties.getPlatform()) ||
            !"1.10.22".equals(properties.getDbtCoreVersion()) ||
            !"1.10.0".equals(properties.getDbtPostgresVersion()) ||
            !"postgres".equals(properties.getAdapter()) ||
            !"PostgreSQL".equals(properties.getDatabaseType()) ||
            !EXPECTED_REQUIREMENTS_LOCK_SHA256.equals(
                properties.getRequirementsLockSha256()
            ) ||
            !EXPECTED_CANDIDATE_IMAGE_DIGEST.equals(
                properties.getCandidateImageDigest()
            ) ||
            !validImage(properties.getImageRef()) ||
            !EXPECTED_EVIDENCE_MANIFEST_SHA256.equals(
                properties.getEvidenceManifestSha256()
            )
        ) {
            throw notCertified();
        }
        return new CertifiedRuntime(
            properties.getProfileId(),
            properties.getCandidateProfileId(),
            properties.getPlatform(),
            properties.getDbtCoreVersion(),
            properties.getDbtPostgresVersion(),
            properties.getAdapter(),
            properties.getDatabaseType(),
            properties.getRequirementsLockSha256(),
            properties.getCandidateImageDigest(),
            properties.getImageRef(),
            properties.getEvidenceManifestSha256()
        );
    }

    private static boolean validImage(String value) {
        return (
            value != null &&
            DIGEST_IMAGE.matcher(value).matches() &&
            value.endsWith("@" + EXPECTED_CERTIFIED_IMAGE_DIGEST)
        );
    }

    private static ModelReleaseCandidateException notCertified() {
        return new ModelReleaseCandidateException(
            "DBT_RUNTIME_NOT_CERTIFIED",
            "Certified dbt runtime is unavailable",
            ModelReleaseCandidateException.Kind.PRECONDITION_REQUIRED
        );
    }

    public record CertifiedRuntime(
        String profileId,
        String candidateProfileId,
        String platform,
        String dbtCoreVersion,
        String dbtPostgresVersion,
        String adapter,
        String databaseType,
        String requirementsLockSha256,
        String candidateImageDigest,
        String imageRef,
        String evidenceManifestSha256
    ) {}
}
