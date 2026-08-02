package com.yuzhi.dts.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Immutable evidence pins for the single certified dbt materialization runtime. */
@Component
@ConfigurationProperties(
    prefix = "dts.modeling.materialization.dbt-runtime-certification"
)
public class DbtRuntimeCertificationProperties {

    private String status = "NOT_CERTIFIED";
    private String profileId;
    private String candidateProfileId;
    private String platform;
    private String dbtCoreVersion;
    private String dbtPostgresVersion;
    private String adapter;
    private String databaseType;
    private String requirementsLockSha256;
    private String candidateImageDigest;
    private String imageRef;
    private String evidenceManifestSha256;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getProfileId() {
        return profileId;
    }

    public void setProfileId(String profileId) {
        this.profileId = profileId;
    }

    public String getCandidateProfileId() {
        return candidateProfileId;
    }

    public void setCandidateProfileId(String candidateProfileId) {
        this.candidateProfileId = candidateProfileId;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public String getDbtCoreVersion() {
        return dbtCoreVersion;
    }

    public void setDbtCoreVersion(String dbtCoreVersion) {
        this.dbtCoreVersion = dbtCoreVersion;
    }

    public String getDbtPostgresVersion() {
        return dbtPostgresVersion;
    }

    public void setDbtPostgresVersion(String dbtPostgresVersion) {
        this.dbtPostgresVersion = dbtPostgresVersion;
    }

    public String getAdapter() {
        return adapter;
    }

    public void setAdapter(String adapter) {
        this.adapter = adapter;
    }

    public String getDatabaseType() {
        return databaseType;
    }

    public void setDatabaseType(String databaseType) {
        this.databaseType = databaseType;
    }

    public String getRequirementsLockSha256() {
        return requirementsLockSha256;
    }

    public void setRequirementsLockSha256(
        String requirementsLockSha256
    ) {
        this.requirementsLockSha256 = requirementsLockSha256;
    }

    public String getImageRef() {
        return imageRef;
    }

    public String getCandidateImageDigest() {
        return candidateImageDigest;
    }

    public void setCandidateImageDigest(String candidateImageDigest) {
        this.candidateImageDigest = candidateImageDigest;
    }

    public void setImageRef(String imageRef) {
        this.imageRef = imageRef;
    }

    public String getEvidenceManifestSha256() {
        return evidenceManifestSha256;
    }

    public void setEvidenceManifestSha256(
        String evidenceManifestSha256
    ) {
        this.evidenceManifestSha256 = evidenceManifestSha256;
    }
}
