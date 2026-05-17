package com.yuzhi.dts.platform.web.rest.capability;

import com.yuzhi.dts.platform.config.metrics.DtsMetricsCapabilityProperties;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class PlatformCapabilityResource {

    private final DtsMetricsCapabilityProperties metricsProperties;

    public PlatformCapabilityResource(DtsMetricsCapabilityProperties metricsProperties) {
        this.metricsProperties = metricsProperties;
    }

    @GetMapping("/capabilities")
    public Map<String, Object> capabilities() {
        return buildCapabilities(false);
    }

    @GetMapping("/internal/capabilities")
    @PreAuthorize("hasAuthority('" + AuthoritiesConstants.SERVICE_INTERNAL + "') and @metricsInternalAccess.isMetricsService(authentication)")
    public Map<String, Object> internalCapabilities() {
        return buildCapabilities(true);
    }

    private Map<String, Object> buildCapabilities(boolean internal) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("enabled", true);
        metrics.put("edition", metricsProperties.getEdition());
        metrics.put("apiBasePath", metricsProperties.getApiBasePath());
        metrics.put("serviceName", metricsProperties.getServiceName());
        metrics.put("mode", "remote-service");
        metrics.put("serviceBoundary", "optional-value-added-service");
        metrics.put("partnerDelivery", "metric-pack");
        metrics.put("platformOwned", List.of(
            "IAM",
            "asset_grant",
            "catalog",
            "data-source",
            "audit",
            "dbt-publish-gateway"
        ));
        metrics.put("metricsOwned", List.of(
            "semantic-domain",
            "business-object",
            "metric-dsl",
            "dws-ads-artifact-candidate",
            "metric-pack-validation"
        ));
        metrics.put("requiredPlatformContracts", List.of(
            "/api/internal/capabilities",
            "/api/catalog/assets-v2/{id}/contract",
            "/api/internal/glossary/terms/resolve",
            "/api/internal/domains/resolve",
            "/api/internal/data-standards/resolve",
            "/api/internal/asset-permission/check",
            "/api/internal/asset-permission/policy",
            "/api/etl/dbt/release/submit",
            "platform-audit"
        ));

        Map<String, Object> capabilities = new LinkedHashMap<>();
        capabilities.put("edition", metricsProperties.getEdition());
        capabilities.put("checkedAt", Instant.now().toString());
        capabilities.put("metrics", metrics);
        capabilities.put("goldenPath", goldenPathCapabilities());
        capabilities.put("catalog", catalogCapabilities(internal));
        capabilities.put("permissions", permissionCapabilities(internal));
        capabilities.put("classification", classificationCapabilities());
        capabilities.put("audit", auditCapabilities(internal));
        capabilities.put("dbtPublish", dbtPublishCapabilities(internal));
        return capabilities;
    }

    private Map<String, Object> goldenPathCapabilities() {
        Map<String, Object> goldenPath = new LinkedHashMap<>();
        goldenPath.put("contractVersion", "2026-05-sprint31");
        goldenPath.put("states", List.of(
            "SOURCE",
            "ODS_READY",
            "DBT_READY",
            "CATALOG_READY",
            "SEMANTIC_READY",
            "CONSUMABLE_READY"
        ));
        goldenPath.put("requiredEvidence", List.of(
            "source-registered",
            "ods-precheck",
            "dbt-compile-test-build",
            "asset-contract",
            "schema-contract",
            "lineage-evidence",
            "permission-check",
            "consumer-publish"
        ));
        goldenPath.put("primaryRoute", "JDBC first, file/API capability-gated");
        goldenPath.put("smokeScript", "worklog/v2.2.3/sprint-31-202605/it/scripts/golden-path-smoke.sh");
        return goldenPath;
    }

    private Map<String, Object> catalogCapabilities(boolean internal) {
        Map<String, Object> catalog = new LinkedHashMap<>();
        catalog.put("contractVersion", "2026-05-sprint31a");
        catalog.put("assetTypes", List.of(
            "DATASET",
            "DBT_MODEL",
            "BI_DATASET",
            "SCREEN",
            "METRIC",
            "METRIC_PACK",
            "SEMANTIC_MODEL",
            "DATA_PRODUCT",
            "MODELING_SQL_MODEL",
            "DATA_STANDARD",
            "GLOSSARY_TERM",
            "GOV_INDICATOR",
            "API_SERVICE"
        ));
        catalog.put("identity", "assetType + assetKey + grantAssetId");
        catalog.put("lifecycleStatuses", List.of("DISCOVERED", "PENDING_GOVERNANCE", "ACTIVE", "DEPRECATED", "ARCHIVED", "BLOCKED", "PENDING_REVIEW"));
        catalog.put("governanceStatuses", List.of("GOVERNED", "PENDING_GOVERNANCE", "PENDING_CLAIM", "PENDING_CLASSIFICATION", "PENDING_DOMAIN", "DISABLED"));
        catalog.put("readEndpoints", List.of(
            "/api/catalog/assets-v2",
            "/api/catalog/assets-v2/{id}/contract",
            "/api/catalog/assets-v2/{id}/schema-contract",
            "/api/catalog/assets-v2/governance-gaps",
            "/api/catalog/assets-v2/lineage-failures",
            "/api/catalog/assets-v2/migration/dry-run"
        ));
        catalog.put("migrationDryRunEndpoint", "/api/catalog/assets-v2/migration/dry-run");
        catalog.put("internalOnly", internal);
        return catalog;
    }

    private Map<String, Object> permissionCapabilities(boolean internal) {
        Map<String, Object> permissions = new LinkedHashMap<>();
        permissions.put("source", "platform-asset-grant");
        permissions.put("actions", List.of("READ", "PREVIEW", "EDIT", "PUBLISH", "GRANT", "MANAGE"));
        permissions.put("actionMapping", Map.of(
            "READ", "READ",
            "PREVIEW", "READ",
            "EDIT", "EDIT",
            "PUBLISH", "MANAGE",
            "GRANT", "MANAGE",
            "MANAGE", "MANAGE"
        ));
        permissions.put("grantTargets", List.of("USER", "ROLE", "DEPT"));
        permissions.put("assetIdContract", "use grantAssetType/grantAssetId from catalog contract");
        if (internal) {
            permissions.put("endpoints", List.of(
                "/api/internal/asset-permission/check",
                "/api/internal/asset-permission/policy",
                "/api/internal/asset-permission/batch-check",
                "/api/internal/asset-permission/accessible-ids",
                "/api/internal/asset-permission/grants"
            ));
        }
        return permissions;
    }

    private Map<String, Object> classificationCapabilities() {
        Map<String, Object> classification = new LinkedHashMap<>();
        classification.put("levels", List.of("PUBLIC", "INTERNAL", "SECRET", "CONFIDENTIAL"));
        classification.put("defaultLevel", "INTERNAL");
        classification.put("enforcement", "platform");
        classification.put("missingClassification", "deny");
        return classification;
    }

    private Map<String, Object> auditCapabilities(boolean internal) {
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("enabled", true);
        audit.put("source", "platform-audit");
        audit.put("serviceAuth", internal ? "X-DTS-Service + X-DTS-Service-Token" : "user-session");
        return audit;
    }

    private Map<String, Object> dbtPublishCapabilities(boolean internal) {
        Map<String, Object> dbt = new LinkedHashMap<>();
        dbt.put("mode", "platform-gated");
        dbt.put("requiresGovernanceReady", true);
        dbt.put("governanceGapEndpoint", "/api/catalog/assets-v2/governance-gaps");
        if (internal) {
            dbt.put("publishGateway", "platform");
        }
        return dbt;
    }
}
