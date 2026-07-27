package com.yuzhi.dts.platform.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Server-owned P0 execution target boundary. It intentionally contains no warehouse credential;
 * F2/T04 resolves the key to a task-scoped secret lease.
 */
@Component
@ConfigurationProperties(prefix = "dts.modeling.materialization")
public class ModelMaterializationProperties {

    private boolean enabled = true;
    private String executionTargetKey = "postgres-primary";
    private String adapter = "postgres";
    private String profileKey = "dts";
    private String targetName = "dev";
    private String releaseBuildDagId = "dts_release_build_postgres_primary";
    private String runtimeProfileRoot = "/run/dts-dbt-runtime";
    private boolean runtimeProfileRequireTmpfs = true;
    private long runtimeProfileExpectedUid = 0;
    private Duration runtimeProfileLeaseTtl = Duration.ofMinutes(10);
    private String runtimeSpecSigningKey;
    private Duration runtimeSpecTokenTtl = Duration.ofMinutes(15);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getExecutionTargetKey() {
        return executionTargetKey;
    }

    public void setExecutionTargetKey(String executionTargetKey) {
        this.executionTargetKey = executionTargetKey;
    }

    public String getAdapter() {
        return adapter;
    }

    public void setAdapter(String adapter) {
        this.adapter = adapter;
    }

    public String getProfileKey() {
        return profileKey;
    }

    public void setProfileKey(String profileKey) {
        this.profileKey = profileKey;
    }

    public String getTargetName() {
        return targetName;
    }

    public void setTargetName(String targetName) {
        this.targetName = targetName;
    }

    public String getReleaseBuildDagId() {
        return releaseBuildDagId;
    }

    public void setReleaseBuildDagId(String releaseBuildDagId) {
        this.releaseBuildDagId = releaseBuildDagId;
    }

    public String getRuntimeProfileRoot() {
        return runtimeProfileRoot;
    }

    public void setRuntimeProfileRoot(String runtimeProfileRoot) {
        this.runtimeProfileRoot = runtimeProfileRoot;
    }

    public boolean isRuntimeProfileRequireTmpfs() {
        return runtimeProfileRequireTmpfs;
    }

    public void setRuntimeProfileRequireTmpfs(
        boolean runtimeProfileRequireTmpfs
    ) {
        this.runtimeProfileRequireTmpfs = runtimeProfileRequireTmpfs;
    }

    public long getRuntimeProfileExpectedUid() {
        return runtimeProfileExpectedUid;
    }

    public void setRuntimeProfileExpectedUid(long runtimeProfileExpectedUid) {
        this.runtimeProfileExpectedUid = runtimeProfileExpectedUid;
    }

    public Duration getRuntimeProfileLeaseTtl() {
        return runtimeProfileLeaseTtl;
    }

    public void setRuntimeProfileLeaseTtl(Duration runtimeProfileLeaseTtl) {
        this.runtimeProfileLeaseTtl = runtimeProfileLeaseTtl;
    }

    public String getRuntimeSpecSigningKey() {
        return runtimeSpecSigningKey;
    }

    public void setRuntimeSpecSigningKey(String runtimeSpecSigningKey) {
        this.runtimeSpecSigningKey = runtimeSpecSigningKey;
    }

    public Duration getRuntimeSpecTokenTtl() {
        return runtimeSpecTokenTtl;
    }

    public void setRuntimeSpecTokenTtl(Duration runtimeSpecTokenTtl) {
        this.runtimeSpecTokenTtl = runtimeSpecTokenTtl;
    }
}
