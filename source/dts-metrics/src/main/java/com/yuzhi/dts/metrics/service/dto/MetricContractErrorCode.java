package com.yuzhi.dts.metrics.service.dto;

public enum MetricContractErrorCode {
    INVALID_LAYER("invalid_layer"),
    GRAIN_MISMATCH("grain_mismatch"),
    STANDARD_CODE_REQUIRED("standard_code_required"),
    ASSET_PERMISSION_DENIED("asset_permission_denied"),
    GRAPH_VALIDATION_FAILED("graph_validation_failed"),
    DBT_VALIDATION_FAILED("dbt_validation_failed"),
    PLATFORM_CONTRACT_UNAVAILABLE("platform_contract_unavailable"),
    ARTIFACT_REQUIRED("artifact_required"),
    DBT_VALIDATION_REQUIRED("dbt_validation_required"),
    PUBLISHED_VERSION_REQUIRED("published_version_required"),
    ROLLBACK_TARGET_REQUIRED("rollback_target_required"),
    ROLLBACK_TARGET_NOT_FOUND("rollback_target_not_found"),
    ROLLBACK_TARGET_MUST_DIFFER("rollback_target_must_differ"),
    MODEL_LIFECYCLE_STATE_NOT_FOUND("model_lifecycle_state_not_found");

    private final String code;

    MetricContractErrorCode(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
