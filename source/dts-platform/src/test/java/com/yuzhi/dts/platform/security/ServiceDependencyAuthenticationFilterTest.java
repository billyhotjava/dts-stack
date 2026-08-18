package com.yuzhi.dts.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.config.PlatformInboundServiceAuthProperties;
import com.yuzhi.dts.platform.security.policy.PersonnelLevel;
import com.yuzhi.dts.platform.service.catalog.CatalogConsumerClassificationService.DeriveCommand;
import com.yuzhi.dts.platform.service.services.SvcTokenAuthService;
import com.yuzhi.dts.platform.service.services.SvcTokenAuthService.TokenPrincipal;
import com.yuzhi.dts.platform.web.rest.catalog.CatalogConsumerClassificationResource;
import jakarta.servlet.FilterChain;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class ServiceDependencyAuthenticationFilterTest {

    private static final String SERVICE_HEADER = "X-DTS-Service";
    private static final String TOKEN_HEADER = "X-DTS-Service-Token";
    private static final String INGESTION_RUNTIME_DETAIL = "/api/infra/data-sources/11111111-1111-1111-1111-111111111111/runtime-detail";
    private static final String ANALYTICS_RUNTIME_DETAIL = "/api/infra/data-sources/22222222-2222-2222-2222-222222222222/runtime-detail";
    private static final String ANALYTICS_ASSET_PERMISSION_CHECK = "/api/internal/asset-permission/check";
    private static final String ANALYTICS_CLASSIFICATION_DERIVE =
        "/api/catalog/classifications/consumers/derive";
    private static final String ANALYTICS_CLASSIFICATION_GUARD =
        "/api/catalog/classifications/consumers/guard";
    private static final String ANALYTICS_CLASSIFICATION_ACCESS_BINDINGS =
        "/api/catalog/classifications/consumers/access-bindings";
    private static final String ANALYTICS_CLASSIFICATION_ACCESS_BINDINGS_GUARD =
        "/api/catalog/classifications/consumers/access-bindings/guard";
    private static final String ANALYTICS_CLASSIFICATION_EXPORT_SEAL =
        "/api/catalog/classifications/consumers/exports/seal";
    private static final String METRICS_ASSET_PERMISSION_POLICY_V1 = "/api/internal/v1/asset-permission/policy";
    private static final String METRICS_ASSET_PERMISSION_POLICY_LEGACY = "/api/internal/asset-permission/policy";
    private static final String ANALYTICS_ASSET_PERMISSION_BATCH_CHECK = "/api/internal/asset-permission/batch-check";
    private static final String ANALYTICS_ASSET_PERMISSION_ACCESSIBLE_IDS = "/api/internal/asset-permission/accessible-ids";
    private static final String ANALYTICS_ASSET_PERMISSION_OWNERSHIP = "/api/internal/asset-permission/ownership";
    private static final String ANALYTICS_DATASET_CONTRACT =
        "/api/internal/analysis-datasets/11111111-1111-1111-1111-111111111111/versions/3";
    private static final String ANALYTICS_REPORT_REGISTRATION =
        "/api/internal/reports/registrations";
    private static final String INTERNAL_CAPABILITIES = "/api/internal/capabilities";
    private static final String INTERNAL_GLOSSARY_TERMS_RESOLVE = "/api/internal/glossary/terms/resolve";
    private static final String INTERNAL_DOMAINS_RESOLVE = "/api/internal/domains/resolve";
    private static final String INTERNAL_DATA_STANDARDS_RESOLVE = "/api/internal/data-standards/resolve";
    private static final String METRICS_ASSET_CONTRACT = "/api/catalog/assets-v2/33333333-3333-3333-3333-333333333333/contract";
    private static final String METRICS_ASSET_SCHEMA_CONTRACT = "/api/catalog/assets-v2/33333333-3333-3333-3333-333333333333/schema-contract";
    private static final String METRICS_LINEAGE_BACKFILL_DRY_RUN = "/api/internal/v1/lineage/backfill/dry-run";
    private static final String METRICS_PERMISSION_DENIED_AUDIT = "/api/internal/v1/asset-permission/audit/denied";
    private static final String METRICS_PERMISSION_DENIED_AUDIT_CSV = "/api/internal/v1/asset-permission/audit/denied.csv";
    private static final String METRICS_POLICY_INJECTION_AUDIT = "/api/internal/v1/asset-permission/audit/policy-injection";
    private static final String METRICS_MODEL_VALIDATION = "/api/internal/metrics/model-validation";
    private static final String AIRFLOW_PROFILE_LEASE =
        "/api/internal/modeling/materialization/profile-leases/10000000-0000-0000-0000-000000000001";
    private static final String AIRFLOW_RUNTIME_SPEC =
        "/api/internal/modeling/materialization/runtime-specs/consume";
    private static final String AIRFLOW_RUN_GROUP =
        "/api/internal/modeling/materialization/run-groups/10000000-0000-0000-0000-000000000001";
    private static final String AIRFLOW_PLAN_BINDING =
        "/api/internal/modeling/execution-bindings/10000000-0000-0000-0000-000000000001";
    private static final String AIRFLOW_PLAN_RUNTIME_SPEC =
        "/api/internal/modeling/execution-bindings/runtime-specs/consume";
    private static final String AIRFLOW_PLAN_RUN_GROUP =
        "/api/internal/modeling/execution-bindings/run-groups/10000000-0000-0000-0000-000000000001";
    private static final String AIRFLOW_LEGACY_DBT_SYNC =
        "/api/etl/dbt/models/sync";

    private PlatformInboundServiceAuthProperties props;
    private SvcTokenAuthService svcTokenAuthService;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        props = new PlatformInboundServiceAuthProperties();
        Map<String, String> trusted = new LinkedHashMap<>();
        trusted.put("dts-ingestion", "ingestion-secret");
        trusted.put("dts-analytics", "analytics-secret");
        trusted.put("dts-metrics", "metrics-secret");
        trusted.put("dts-airflow", "airflow-secret");
        props.setTrustedServices(trusted);
        svcTokenAuthService = mock(SvcTokenAuthService.class);
        chain = mock(FilterChain.class);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void noServiceHeader_doesNotAuthenticate() throws Exception {
        ServiceDependencyAuthenticationFilter filter = new ServiceDependencyAuthenticationFilter(props, svcTokenAuthService);
        MockHttpServletRequest req = new MockHttpServletRequest();
        // no header
        filter.doFilter(req, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(any(), any());
    }

    @Test
    void serviceHeaderWithoutToken_doesNotAuthenticate_F3StrictMode() throws Exception {
        ServiceDependencyAuthenticationFilter filter = new ServiceDependencyAuthenticationFilter(props, svcTokenAuthService);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(SERVICE_HEADER, "dts-ingestion");
        req.setMethod("GET");
        req.setRequestURI(INGESTION_RUNTIME_DETAIL);
        // no token header
        filter.doFilter(req, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void serviceHeaderWithWrongToken_doesNotAuthenticate() throws Exception {
        ServiceDependencyAuthenticationFilter filter = new ServiceDependencyAuthenticationFilter(props, svcTokenAuthService);
        when(svcTokenAuthService.authenticateService("wrong-token", "dts-ingestion")).thenReturn(null);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(SERVICE_HEADER, "dts-ingestion");
        req.addHeader(TOKEN_HEADER, "wrong-token");
        req.setMethod("GET");
        req.setRequestURI(INGESTION_RUNTIME_DETAIL);

        filter.doFilter(req, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void serviceHeaderWithMatchingPerPairToken_authenticates() throws Exception {
        ServiceDependencyAuthenticationFilter filter = new ServiceDependencyAuthenticationFilter(props, svcTokenAuthService);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(SERVICE_HEADER, "dts-ingestion");
        req.addHeader(TOKEN_HEADER, "ingestion-secret");
        req.setMethod("GET");
        req.setRequestURI(INGESTION_RUNTIME_DETAIL);

        filter.doFilter(req, new MockHttpServletResponse(), chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo("service:dts-ingestion");
        assertThat(auth.getAuthorities()).extracting(Object::toString).contains(AuthoritiesConstants.SERVICE_INTERNAL);
        // Other service's secret 不能用来认证 ingestion
    }

    @Test
    void analyticsMatchingToken_canAccessRuntimeDataSourceDetail() throws Exception {
        ServiceDependencyAuthenticationFilter filter = new ServiceDependencyAuthenticationFilter(props, svcTokenAuthService);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(SERVICE_HEADER, "dts-analytics");
        req.addHeader(TOKEN_HEADER, "analytics-secret");
        req.setMethod("GET");
        req.setRequestURI(ANALYTICS_RUNTIME_DETAIL);

        filter.doFilter(req, new MockHttpServletResponse(), chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo("service:dts-analytics");
        assertThat(auth.getAuthorities()).extracting(Object::toString).contains(AuthoritiesConstants.SERVICE_INTERNAL);
    }

    @Test
    void analyticsMatchingToken_canAccessAssetPermissionCheckEndpoints() throws Exception {
        assertAnalyticsCanAccessPost(ANALYTICS_ASSET_PERMISSION_CHECK);
        assertAnalyticsCanAccessPost(ANALYTICS_ASSET_PERMISSION_BATCH_CHECK);
        assertAnalyticsCanAccessPost(ANALYTICS_ASSET_PERMISSION_ACCESSIBLE_IDS);
        assertAnalyticsCanAccessPost(ANALYTICS_ASSET_PERMISSION_OWNERSHIP);
    }

    @Test
    void analyticsMatchingToken_canAccessClassificationConsumerContract() throws Exception {
        assertAnalyticsCanAccessPost(ANALYTICS_CLASSIFICATION_DERIVE);
        assertAnalyticsCanAccessGet(ANALYTICS_CLASSIFICATION_GUARD);
        assertAnalyticsCanAccessPost(ANALYTICS_CLASSIFICATION_ACCESS_BINDINGS);
        assertAnalyticsCanAccessGet(ANALYTICS_CLASSIFICATION_ACCESS_BINDINGS_GUARD);
        assertAnalyticsCanAccessPost(ANALYTICS_CLASSIFICATION_EXPORT_SEAL);
    }

    @Test
    void analyticsMatchingToken_canReadAnalysisDatasetContractAndRegisterReport() throws Exception {
        assertAnalyticsCanAccess("GET", ANALYTICS_DATASET_CONTRACT);
        assertAnalyticsCanAccess("PUT", ANALYTICS_REPORT_REGISTRATION);
    }

    @Test
    void analyticsGovernedBiEndpointsRejectAdjacentMethodsAndPaths() throws Exception {
        assertAnalyticsCannotAccess("POST", ANALYTICS_DATASET_CONTRACT);
        assertAnalyticsCannotAccess("GET", ANALYTICS_REPORT_REGISTRATION);
        assertAnalyticsCannotAccess("GET", ANALYTICS_DATASET_CONTRACT + "/extra");
        assertAnalyticsCannotAccess("GET", "/api/internal/analysis-datasets/1-1-1-1-1/versions/3");
        assertAnalyticsCannotAccess(
            "GET",
            "/api/internal/analysis-datasets/11111111-1111-1111-1111-111111111111/versions/+3"
        );
        assertAnalyticsCannotAccess("PUT", ANALYTICS_REPORT_REGISTRATION + "/extra");
    }

    @Test
    void classificationDeriveAcceptsTheAuthorityGrantedToAuthenticatedServices() throws Exception {
        Method derive = CatalogConsumerClassificationResource.class.getDeclaredMethod(
            "derive",
            DeriveCommand.class
        );

        PreAuthorize authorization = derive.getAnnotation(PreAuthorize.class);

        assertThat(authorization).isNotNull();
        assertThat(authorization.value())
            .contains(AuthoritiesConstants.SERVICE_INTERNAL)
            .doesNotContain("ROLE_INTERNAL_SERVICE");
    }

    @Test
    void analyticsMatchingToken_cannotAccessClassificationExplainEndpoint() throws Exception {
        ServiceDependencyAuthenticationFilter filter = new ServiceDependencyAuthenticationFilter(props, svcTokenAuthService);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(SERVICE_HEADER, "dts-analytics");
        req.addHeader(TOKEN_HEADER, "analytics-secret");
        req.setMethod("GET");
        req.setRequestURI("/api/catalog/classifications/consumers/explain");

        filter.doFilter(req, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(svcTokenAuthService, never()).authenticateService(any(), any());
    }

    @Test
    void metricsMatchingToken_canAccessInternalCapabilities() throws Exception {
        ServiceDependencyAuthenticationFilter filter = new ServiceDependencyAuthenticationFilter(props, svcTokenAuthService);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(SERVICE_HEADER, "dts-metrics");
        req.addHeader(TOKEN_HEADER, "metrics-secret");
        req.setMethod("GET");
        req.setRequestURI(INTERNAL_CAPABILITIES);

        filter.doFilter(req, new MockHttpServletResponse(), chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo("service:dts-metrics");
        assertThat(auth.getAuthorities()).extracting(Object::toString).contains(AuthoritiesConstants.SERVICE_INTERNAL);
    }

    @Test
    void metricsMatchingToken_canReadAndCheckAssetPermissionsOnly() throws Exception {
        assertMetricsCanAccess("POST", ANALYTICS_ASSET_PERMISSION_CHECK);
        assertMetricsCanAccess("POST", METRICS_ASSET_PERMISSION_POLICY_V1);
        assertMetricsCanAccess("POST", METRICS_ASSET_PERMISSION_POLICY_LEGACY);
        assertMetricsCanAccess("POST", ANALYTICS_ASSET_PERMISSION_BATCH_CHECK);
        assertMetricsCanAccess("POST", ANALYTICS_ASSET_PERMISSION_ACCESSIBLE_IDS);
        assertMetricsCanAccess("GET", "/api/internal/asset-permission/grants");
    }

    @Test
    void metricsMatchingToken_canReadCatalogContractsAndUseDbtGateway() throws Exception {
        assertMetricsCanAccess("GET", "/api/catalog/assets-v2");
        assertMetricsCanAccess("GET", METRICS_ASSET_CONTRACT);
        assertMetricsCanAccess("GET", METRICS_ASSET_SCHEMA_CONTRACT);
        assertMetricsCanAccess("GET", METRICS_LINEAGE_BACKFILL_DRY_RUN);
        assertMetricsCanAccess("GET", METRICS_PERMISSION_DENIED_AUDIT);
        assertMetricsCanAccess("GET", METRICS_PERMISSION_DENIED_AUDIT_CSV);
        assertMetricsCanAccess("GET", METRICS_POLICY_INJECTION_AUDIT);
        assertMetricsCanAccess("POST", METRICS_POLICY_INJECTION_AUDIT);
        assertMetricsCanAccess("POST", METRICS_MODEL_VALIDATION);
        assertMetricsCanAccess(
            "POST",
            "/api/internal/catalog/taggable-assets/register"
        );
        assertMetricsCanAccess("POST", "/api/etl/dbt/release-gate/check");
        assertMetricsCanAccess("POST", "/api/etl/dbt/release/submit");
    }

    @Test
    void metricsMatchingToken_canResolveGlossaryTerms() throws Exception {
        assertMetricsCanAccess("POST", INTERNAL_GLOSSARY_TERMS_RESOLVE);
    }

    @Test
    void metricsMatchingToken_canResolvePlatformDomainsAndDataStandards() throws Exception {
        assertMetricsCanAccess("POST", INTERNAL_DOMAINS_RESOLVE);
        assertMetricsCanAccess("POST", INTERNAL_DATA_STANDARDS_RESOLVE);
    }

    @Test
    void airflowPairwiseTokenCanOnlyUseExactMaterializationRuntimePaths()
        throws Exception {
        assertAirflowCanAccess("POST", AIRFLOW_RUNTIME_SPEC);
        assertAirflowCanAccess("POST", AIRFLOW_PROFILE_LEASE + "/consume");
        assertAirflowCanAccess("DELETE", AIRFLOW_PROFILE_LEASE);
        assertAirflowCanAccess("POST", AIRFLOW_RUN_GROUP + "/sync-probe");
        assertAirflowCanAccess("POST", AIRFLOW_RUN_GROUP + "/finalize");
        assertAirflowCanAccess("POST", AIRFLOW_PLAN_BINDING + "/scheduled-runs/open");
        assertAirflowCanAccess("POST", AIRFLOW_PLAN_RUNTIME_SPEC);
        assertAirflowCanAccess("POST", AIRFLOW_PLAN_RUN_GROUP + "/sync-probe");
        assertAirflowCanAccess("POST", AIRFLOW_PLAN_RUN_GROUP + "/finalize");

        SecurityContextHolder.clearContext();
        ServiceDependencyAuthenticationFilter filter =
            new ServiceDependencyAuthenticationFilter(
                props,
                svcTokenAuthService
            );
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(SERVICE_HEADER, "dts-airflow");
        req.addHeader(TOKEN_HEADER, "airflow-secret");
        req.setMethod("POST");
        req.setRequestURI(AIRFLOW_PROFILE_LEASE + "/arbitrary");

        filter.doFilter(req, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        SecurityContextHolder.clearContext();
        MockHttpServletRequest adjacent =
            new MockHttpServletRequest();
        adjacent.addHeader(SERVICE_HEADER, "dts-airflow");
        adjacent.addHeader(TOKEN_HEADER, "airflow-secret");
        adjacent.setMethod("POST");
        adjacent.setRequestURI(
            AIRFLOW_PLAN_BINDING + "/scheduled-runs/delete"
        );

        filter.doFilter(
            adjacent,
            new MockHttpServletResponse(),
            chain
        );

        assertThat(
            SecurityContextHolder.getContext().getAuthentication()
        )
            .isNull();
    }

    @Test
    void airflowPairwiseTokenCanOnlyUseExactLegacyDbtSyncCallback()
        throws Exception {
        assertAirflowCanAccess("POST", AIRFLOW_LEGACY_DBT_SYNC);

        assertAirflowCannotAccess("GET", AIRFLOW_LEGACY_DBT_SYNC);
        assertAirflowCannotAccess(
            "POST",
            AIRFLOW_LEGACY_DBT_SYNC + "/extra"
        );
    }

    @Test
    void airflowPairwiseTokenAllowsOnlyCanonicalProfileLeaseRenewPath()
        throws Exception {
        assertAirflowCanAccess("POST", AIRFLOW_PROFILE_LEASE + "/renew");

        assertAirflowCannotAccess("GET", AIRFLOW_PROFILE_LEASE + "/renew");
        assertAirflowCannotAccess(
            "POST",
            AIRFLOW_PROFILE_LEASE + "/renew/extra"
        );
        assertAirflowCannotAccess(
            "POST",
            "/api/internal/modeling/materialization/profile-leases/1-1-1-1-1/consume"
        );
    }

    @Test
    void metricsMatchingToken_cannotMutateAssetPermissionGrants() throws Exception {
        ServiceDependencyAuthenticationFilter filter = new ServiceDependencyAuthenticationFilter(props, svcTokenAuthService);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(SERVICE_HEADER, "dts-metrics");
        req.addHeader(TOKEN_HEADER, "metrics-secret");
        req.setMethod("POST");
        req.setRequestURI("/api/internal/asset-permission/grants");

        filter.doFilter(req, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(svcTokenAuthService, never()).authenticateService(any(), any());
    }

    @Test
    void analyticsMatchingToken_cannotAccessArbitraryInternalEndpoint() throws Exception {
        ServiceDependencyAuthenticationFilter filter = new ServiceDependencyAuthenticationFilter(props, svcTokenAuthService);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(SERVICE_HEADER, "dts-analytics");
        req.addHeader(TOKEN_HEADER, "analytics-secret");
        req.setMethod("POST");
        req.setRequestURI("/api/internal/not-allowed");

        filter.doFilter(req, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(svcTokenAuthService, never()).authenticateService(any(), any());
    }

    @Test
    void serviceHeaderWithDynamicSvcToken_authenticates() throws Exception {
        when(svcTokenAuthService.authenticateService("db-managed-token", "dts-ingestion"))
            .thenReturn(new TokenPrincipal("service:dts-ingestion", null, PersonnelLevel.GENERAL, null));
        ServiceDependencyAuthenticationFilter filter = new ServiceDependencyAuthenticationFilter(props, svcTokenAuthService);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(SERVICE_HEADER, "dts-ingestion");
        req.addHeader(TOKEN_HEADER, "db-managed-token");
        req.setMethod("GET");
        req.setRequestURI(INGESTION_RUNTIME_DETAIL);

        filter.doFilter(req, new MockHttpServletResponse(), chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo("service:dts-ingestion");
    }

    @Test
    void unknownServiceName_doesNotAuthenticate() throws Exception {
        ServiceDependencyAuthenticationFilter filter = new ServiceDependencyAuthenticationFilter(props, svcTokenAuthService);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(SERVICE_HEADER, "dts-malicious");
        req.addHeader(TOKEN_HEADER, "any-token");

        filter.doFilter(req, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(svcTokenAuthService, never()).authenticateService(any(), any());
    }

    @Test
    void existingAuthentication_isPreserved() throws Exception {
        ServiceDependencyAuthenticationFilter filter = new ServiceDependencyAuthenticationFilter(props, svcTokenAuthService);
        UsernamePasswordAuthenticationToken existing = new UsernamePasswordAuthenticationToken(
            "real-user",
            "n/a",
            List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        SecurityContextHolder.getContext().setAuthentication(existing);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(SERVICE_HEADER, "dts-ingestion");
        req.addHeader(TOKEN_HEADER, "ingestion-secret");
        req.setMethod("GET");
        req.setRequestURI(INGESTION_RUNTIME_DETAIL);

        filter.doFilter(req, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(existing);
    }

    @Test
    void disabledAuthProperties_skipsInjection() throws Exception {
        props.setEnabled(false);
        ServiceDependencyAuthenticationFilter filter = new ServiceDependencyAuthenticationFilter(props, svcTokenAuthService);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(SERVICE_HEADER, "dts-ingestion");
        req.addHeader(TOKEN_HEADER, "ingestion-secret");
        req.setMethod("GET");
        req.setRequestURI(INGESTION_RUNTIME_DETAIL);

        filter.doFilter(req, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void matchingTokenOnDisallowedEndpoint_doesNotAuthenticate() throws Exception {
        ServiceDependencyAuthenticationFilter filter = new ServiceDependencyAuthenticationFilter(props, svcTokenAuthService);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(SERVICE_HEADER, "dts-ingestion");
        req.addHeader(TOKEN_HEADER, "ingestion-secret");
        req.setMethod("GET");
        req.setRequestURI("/api/services/tokens");

        filter.doFilter(req, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(svcTokenAuthService, never()).authenticateService(any(), any());
    }

    private void assertAnalyticsCanAccessPost(String path) throws Exception {
        assertAnalyticsCanAccess("POST", path);
    }

    private void assertAnalyticsCanAccessGet(String path) throws Exception {
        assertAnalyticsCanAccess("GET", path);
    }

    private void assertAnalyticsCanAccess(String method, String path) throws Exception {
        SecurityContextHolder.clearContext();
        FilterChain localChain = mock(FilterChain.class);
        ServiceDependencyAuthenticationFilter filter = new ServiceDependencyAuthenticationFilter(props, svcTokenAuthService);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(SERVICE_HEADER, "dts-analytics");
        req.addHeader(TOKEN_HEADER, "analytics-secret");
        req.setMethod(method);
        req.setRequestURI(path);

        filter.doFilter(req, new MockHttpServletResponse(), localChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo("service:dts-analytics");
        assertThat(auth.getAuthorities()).extracting(Object::toString).contains(AuthoritiesConstants.SERVICE_INTERNAL);
        verify(localChain).doFilter(any(), any());
    }

    private void assertAnalyticsCannotAccess(String method, String path) throws Exception {
        SecurityContextHolder.clearContext();
        FilterChain localChain = mock(FilterChain.class);
        ServiceDependencyAuthenticationFilter filter = new ServiceDependencyAuthenticationFilter(props, svcTokenAuthService);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(SERVICE_HEADER, "dts-analytics");
        req.addHeader(TOKEN_HEADER, "analytics-secret");
        req.setMethod(method);
        req.setRequestURI(path);

        filter.doFilter(req, new MockHttpServletResponse(), localChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(localChain).doFilter(any(), any());
    }

    private void assertMetricsCanAccess(String method, String path) throws Exception {
        SecurityContextHolder.clearContext();
        FilterChain localChain = mock(FilterChain.class);
        ServiceDependencyAuthenticationFilter filter = new ServiceDependencyAuthenticationFilter(props, svcTokenAuthService);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(SERVICE_HEADER, "dts-metrics");
        req.addHeader(TOKEN_HEADER, "metrics-secret");
        req.setMethod(method);
        req.setRequestURI(path);

        filter.doFilter(req, new MockHttpServletResponse(), localChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo("service:dts-metrics");
        assertThat(auth.getAuthorities()).extracting(Object::toString).contains(AuthoritiesConstants.SERVICE_INTERNAL);
        verify(localChain).doFilter(any(), any());
    }

    private void assertAirflowCanAccess(String method, String path)
        throws Exception {
        SecurityContextHolder.clearContext();
        FilterChain localChain = mock(FilterChain.class);
        ServiceDependencyAuthenticationFilter filter =
            new ServiceDependencyAuthenticationFilter(
                props,
                svcTokenAuthService
            );
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(SERVICE_HEADER, "dts-airflow");
        req.addHeader(TOKEN_HEADER, "airflow-secret");
        req.setMethod(method);
        req.setRequestURI(path);

        filter.doFilter(req, new MockHttpServletResponse(), localChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo("service:dts-airflow");
        assertThat(auth.getAuthorities())
            .extracting(Object::toString)
            .contains(AuthoritiesConstants.SERVICE_INTERNAL);
        verify(localChain).doFilter(any(), any());
    }

    private void assertAirflowCannotAccess(String method, String path)
        throws Exception {
        SecurityContextHolder.clearContext();
        FilterChain localChain = mock(FilterChain.class);
        ServiceDependencyAuthenticationFilter filter =
            new ServiceDependencyAuthenticationFilter(
                props,
                svcTokenAuthService
            );
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(SERVICE_HEADER, "dts-airflow");
        req.addHeader(TOKEN_HEADER, "airflow-secret");
        req.setMethod(method);
        req.setRequestURI(path);

        filter.doFilter(req, new MockHttpServletResponse(), localChain);

        assertThat(
            SecurityContextHolder.getContext().getAuthentication()
        )
            .isNull();
        verify(localChain).doFilter(any(), any());
    }
}
