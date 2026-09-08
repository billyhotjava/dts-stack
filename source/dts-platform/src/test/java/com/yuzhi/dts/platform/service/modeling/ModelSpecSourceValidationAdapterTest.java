package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository.PhysicalSourceProjection;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository.SourceBindingState;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.Layer;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.SourceKind;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.SourceRef;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.SourceRole;
import com.yuzhi.dts.platform.service.modeling.warehouse.SourceReferenceResolver;
import com.yuzhi.dts.platform.service.modeling.warehouse.SourceReferenceResolver.AccessContext;
import com.yuzhi.dts.platform.service.modeling.warehouse.SourceReferenceResolver.ResolvedSource;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider.WarehousePlanActor;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceLocator;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceType;
import java.util.Optional;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class ModelSpecSourceValidationAdapterTest {

    private static final String TENANT = "tenant-a";
    private static final String ACTOR = "alice";
    private static final String DEPARTMENT = "dept-a";
    private static final UUID PLAN_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID BINDING_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final UUID ASSET_ID = UUID.fromString("60000000-0000-0000-0000-000000000001");
    private static final String LOCATOR_JSON = "{\"assetId\":\"" + ASSET_ID + "\"}";

    @Mock
    private ModelSpecRepository repository;

    @Mock
    private SourceReferenceResolver resolver;

    @Mock
    private WarehousePlanActorProvider actorProvider;

    @Mock
    private ModelSpecPlanWriteAccessPort planWriteAccess;

    private ModelSpecSourceValidationAdapter validation;

    @ParameterizedTest
    @CsvSource({
        "ROLE_INST_DATA_OWNER, dept-b, AVAILABLE, v1, CONFIRMED, true",
        "ROLE_DEPT_DATA_OWNER, dept-a, AVAILABLE, v1, CONFIRMED, true",
        "ROLE_DEPT_DATA_OWNER, dept-b, AVAILABLE, v1, CONFIRMED, false",
        "ROLE_USER, dept-a, AVAILABLE, v1, CONFIRMED, false",
        "ROLE_INST_DATA_OWNER, dept-b, FORBIDDEN, v1, CONFIRMED, false",
        "ROLE_INST_DATA_OWNER, dept-b, AVAILABLE, v2, CONFIRMED, false",
        "ROLE_INST_DATA_OWNER, dept-b, AVAILABLE, v1, CANDIDATE, false"
    })
    void validatesNonOwnerWithRealPlanAuthorizationAndActualSourceContext(
        String role, String planDepartment, String sourceStatus, String liveVersion, String confirmation, boolean expected
    ) {
        JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(TENANT), org.mockito.ArgumentMatchers.eq(PLAN_ID)))
            .thenReturn(List.of(Map.of("owner_id", "another-owner", "owner_department_id", planDepartment)));
        Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "none").subject(ACTOR)
            .claim("preferred_username", ACTOR).claim("dept_code", DEPARTMENT).claim("roles", List.of(role)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        try {
            ModelSpecPlanWriteAccessAdapter authorization = new ModelSpecPlanWriteAccessAdapter(jdbc);
            ModelSpecSourceValidationAdapter adapter = new ModelSpecSourceValidationAdapter(
                repository, resolver, actorProvider, new ObjectMapper(), authorization
            );
            SourceBindingState binding = new SourceBindingState(
                BINDING_ID, "CATALOG_TABLE", ASSET_ID.toString(), "v1", confirmation, LOCATOR_JSON, "another-owner", planDepartment
            );
            lenient().when(repository.lockSourceBinding(TENANT, PLAN_ID, BINDING_ID)).thenReturn(Optional.of(binding));
            SourceLocator locator = new SourceLocator(ASSET_ID, null, null, null, null, null, null);
            AccessContext context = new AccessContext(TENANT, ACTOR, DEPARTMENT);
            lenient().when(resolver.resolve(SourceType.CATALOG_TABLE, locator, context))
                .thenReturn("FORBIDDEN".equals(sourceStatus) ? ResolvedSource.forbidden() : ResolvedSource.available("客户表", liveVersion));

            assertThat(adapter.isCurrentBinding(TENANT, PLAN_ID, ACTOR, source(ASSET_ID.toString(), "v1"))).isEqualTo(expected);
            if (authorization.canMaintain(TENANT, PLAN_ID, ACTOR) && "CONFIRMED".equals(confirmation)) {
                verify(resolver).resolve(SourceType.CATALOG_TABLE, locator, context);
            } else {
                verify(resolver, never()).resolve(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
            }
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @BeforeEach
    void setUp() {
        validation = new ModelSpecSourceValidationAdapter(repository, resolver, actorProvider, new ObjectMapper().findAndRegisterModules(), planWriteAccess);
        lenient().when(actorProvider.currentActor()).thenReturn(new WarehousePlanActor(ACTOR, DEPARTMENT));
        lenient().when(planWriteAccess.canMaintain(TENANT, PLAN_ID, ACTOR)).thenReturn(true);
    }

    @Test
    void resolvesTheLockedServerBindingWithAuthenticatedActorContext() {
        SourceBindingState binding = confirmedBinding("v1", LOCATOR_JSON);
        when(repository.lockSourceBinding(TENANT, PLAN_ID, BINDING_ID)).thenReturn(Optional.of(binding));
        when(resolver.resolve(SourceType.CATALOG_TABLE, new SourceLocator(ASSET_ID, null, null, null, null, null, null), new AccessContext(TENANT, ACTOR, DEPARTMENT)))
            .thenReturn(ResolvedSource.available("客户表", "v1"));

        assertThat(validation.isCurrentBinding(TENANT, PLAN_ID, ACTOR, source(ASSET_ID.toString(), "v1"))).isTrue();

        ArgumentCaptor<SourceLocator> locator = ArgumentCaptor.forClass(SourceLocator.class);
        verify(resolver).resolve(
            org.mockito.ArgumentMatchers.eq(SourceType.CATALOG_TABLE),
            locator.capture(),
            org.mockito.ArgumentMatchers.eq(new AccessContext(TENANT, ACTOR, DEPARTMENT))
        );
        assertThat(locator.getValue().assetId()).isEqualTo(ASSET_ID);
    }

    @Test
    void resolvesGateEvidenceWithThePersistedPlanOwnerWithoutTakingAWriteLock() {
        SourceBindingState binding = confirmedBinding("v1", LOCATOR_JSON);
        when(repository.findSourceBinding(TENANT, PLAN_ID, BINDING_ID)).thenReturn(Optional.of(binding));
        when(resolver.resolve(SourceType.CATALOG_TABLE, new SourceLocator(ASSET_ID, null, null, null, null, null, null), new AccessContext(TENANT, ACTOR, DEPARTMENT)))
            .thenReturn(ResolvedSource.available("客户表", "v1"));

        assertThat(validation.isCurrentBindingForGate(TENANT, PLAN_ID, source(ASSET_ID.toString(), "v1"))).isTrue();

        verify(repository, never()).lockSourceBinding(TENANT, PLAN_ID, BINDING_ID);
    }

    @Test
    void resolvesMaterializationEvidenceThroughTheBackgroundExecutionPath() {
        SourceBindingState binding = confirmedBinding("v1", LOCATOR_JSON);
        SourceLocator locator = new SourceLocator(ASSET_ID, null, null, null, null, null, null);
        AccessContext context = new AccessContext(TENANT, ACTOR, DEPARTMENT);
        when(repository.findSourceBinding(TENANT, PLAN_ID, BINDING_ID)).thenReturn(Optional.of(binding));
        when(resolver.resolveForExecution(SourceType.CATALOG_TABLE, locator, context))
            .thenReturn(ResolvedSource.available("客户表", "v1"));

        assertThat(validation.isCurrentBindingForExecution(TENANT, PLAN_ID, BINDING_ID, "v1")).isTrue();

        verify(resolver).resolveForExecution(SourceType.CATALOG_TABLE, locator, context);
        verify(resolver, never()).resolve(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void resolvesMaterializationCompilerRelationThroughTheBackgroundExecutionPath() {
        SourceBindingState binding = confirmedBinding("v1", LOCATOR_JSON);
        SourceLocator locator = new SourceLocator(ASSET_ID, null, null, null, null, null, null);
        AccessContext context = new AccessContext(TENANT, ACTOR, DEPARTMENT);
        when(repository.findSourceBinding(TENANT, PLAN_ID, BINDING_ID)).thenReturn(Optional.of(binding));
        when(resolver.resolveForExecution(SourceType.CATALOG_TABLE, locator, context))
            .thenReturn(ResolvedSource.available("客户表", "v1"));
        when(repository.findCurrentPhysicalSource(TENANT, PLAN_ID, BINDING_ID, "v1"))
            .thenReturn(Optional.of(new PhysicalSourceProjection(SourceKind.TABLE, "public.orders", Layer.ODS, "v1")));

        assertThat(validation.resolveCurrentBindingForExecutionCompiler(TENANT, PLAN_ID, BINDING_ID, "v1"))
            .contains(source("public.orders", "v1"));

        verify(resolver).resolveForExecution(SourceType.CATALOG_TABLE, locator, context);
        verify(resolver, never()).resolve(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void gateEvidenceFailsClosedForMissingForbiddenProviderErrorsAndVersionDrift() {
        when(repository.findSourceBinding(TENANT, PLAN_ID, BINDING_ID)).thenReturn(Optional.of(confirmedBinding("v1", LOCATOR_JSON)));
        SourceLocator locator = new SourceLocator(ASSET_ID, null, null, null, null, null, null);
        AccessContext context = new AccessContext(TENANT, ACTOR, DEPARTMENT);
        when(resolver.resolve(SourceType.CATALOG_TABLE, locator, context))
            .thenReturn(ResolvedSource.missing(), ResolvedSource.forbidden(), ResolvedSource.providerError(), ResolvedSource.available("客户表", "v2"));

        SourceRef source = source(ASSET_ID.toString(), "v1");
        assertThat(validation.isCurrentBindingForGate(TENANT, PLAN_ID, source)).isFalse();
        assertThat(validation.isCurrentBindingForGate(TENANT, PLAN_ID, source)).isFalse();
        assertThat(validation.isCurrentBindingForGate(TENANT, PLAN_ID, source)).isFalse();
        assertThat(validation.isCurrentBindingForGate(TENANT, PLAN_ID, source)).isFalse();
    }

    @Test
    void treatsClientKindReferenceAndVersionOnlyAsConsistencyTokens() {
        when(repository.lockSourceBinding(TENANT, PLAN_ID, BINDING_ID)).thenReturn(Optional.of(confirmedBinding("v1", LOCATOR_JSON)));

        assertThat(validation.isCurrentBinding(TENANT, PLAN_ID, ACTOR, source("forged.ref", "v1"))).isFalse();
        assertThat(validation.isCurrentBinding(TENANT, PLAN_ID, ACTOR, source(ASSET_ID.toString(), "forged-version"))).isFalse();
        assertThat(
            validation.isCurrentBinding(
                TENANT,
                PLAN_ID,
                ACTOR,
                new SourceRef(SourceKind.DATASET, ASSET_ID.toString(), Layer.ODS, SourceRole.PRIMARY, null, null, null, 0, BINDING_ID, "v1")
            )
        )
            .isFalse();

        verify(resolver, never()).resolve(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void failsClosedForMissingForbiddenProviderErrorsAndVersionDrift() {
        when(repository.lockSourceBinding(TENANT, PLAN_ID, BINDING_ID)).thenReturn(Optional.of(confirmedBinding("v1", LOCATOR_JSON)));
        SourceLocator locator = new SourceLocator(ASSET_ID, null, null, null, null, null, null);
        AccessContext context = new AccessContext(TENANT, ACTOR, DEPARTMENT);
        when(resolver.resolve(SourceType.CATALOG_TABLE, locator, context))
            .thenReturn(ResolvedSource.missing(), ResolvedSource.forbidden(), ResolvedSource.providerError(), ResolvedSource.available("客户表", "v2"));

        SourceRef source = source(ASSET_ID.toString(), "v1");
        assertThat(validation.isCurrentBinding(TENANT, PLAN_ID, ACTOR, source)).isFalse();
        assertThat(validation.isCurrentBinding(TENANT, PLAN_ID, ACTOR, source)).isFalse();
        assertThat(validation.isCurrentBinding(TENANT, PLAN_ID, ACTOR, source)).isFalse();
        assertThat(validation.isCurrentBinding(TENANT, PLAN_ID, ACTOR, source)).isFalse();
    }

    @Test
    void failsClosedForCrossPlanMissingLocatorActorMismatchAndResolverFailure() {
        SourceRef source = source(ASSET_ID.toString(), "v1");
        when(repository.lockSourceBinding(TENANT, PLAN_ID, BINDING_ID)).thenReturn(Optional.empty());
        assertThat(validation.isCurrentBinding(TENANT, PLAN_ID, ACTOR, source)).isFalse();

        when(repository.lockSourceBinding(TENANT, PLAN_ID, BINDING_ID)).thenReturn(Optional.of(confirmedBinding("v1", "{broken")));
        assertThat(validation.isCurrentBinding(TENANT, PLAN_ID, ACTOR, source)).isFalse();

        when(actorProvider.currentActor()).thenReturn(new WarehousePlanActor("mallory", DEPARTMENT));
        assertThat(validation.isCurrentBinding(TENANT, PLAN_ID, ACTOR, source)).isFalse();

        when(actorProvider.currentActor()).thenReturn(new WarehousePlanActor(ACTOR, DEPARTMENT));
        when(repository.lockSourceBinding(TENANT, PLAN_ID, BINDING_ID)).thenReturn(Optional.of(confirmedBinding("v1", LOCATOR_JSON)));
        when(resolver.resolve(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
            .thenThrow(new IllegalStateException("provider unavailable"));
        assertThat(validation.isCurrentBinding(TENANT, PLAN_ID, ACTOR, source)).isFalse();
    }

    @Test
    void failsClosedWhenStoredSourceIdAndLocatorDisagree() {
        SourceBindingState inconsistent = new SourceBindingState(
            BINDING_ID,
            "CATALOG_TABLE",
            "forged-source-id",
            "v1",
            "CONFIRMED",
            LOCATOR_JSON,
            ACTOR,
            DEPARTMENT
        );
        when(repository.lockSourceBinding(TENANT, PLAN_ID, BINDING_ID)).thenReturn(Optional.of(inconsistent));

        assertThat(validation.isCurrentBinding(TENANT, PLAN_ID, ACTOR, source("forged-source-id", "v1"))).isFalse();
        verify(resolver, never()).resolve(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void rejectsLocatorsThatMixMultipleSourceIdentities() {
        String mixedLocator = "{\"assetId\":\"" + ASSET_ID + "\",\"fileId\":\"70000000-0000-0000-0000-000000000001\"}";
        when(repository.lockSourceBinding(TENANT, PLAN_ID, BINDING_ID))
            .thenReturn(Optional.of(confirmedBinding("v1", mixedLocator)));

        assertThat(validation.isCurrentBinding(TENANT, PLAN_ID, ACTOR, source(ASSET_ID.toString(), "v1"))).isFalse();
        verify(resolver, never()).resolve(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void rejectsAPlanOwnerWhoseAuthenticatedDepartmentHasChanged() {
        when(actorProvider.currentActor()).thenReturn(new WarehousePlanActor(ACTOR, "dept-b"));
        when(planWriteAccess.canMaintain(TENANT, PLAN_ID, ACTOR)).thenReturn(false);

        assertThat(validation.isCurrentBinding(TENANT, PLAN_ID, ACTOR, source(ASSET_ID.toString(), "v1"))).isFalse();
        verify(resolver, never()).resolve(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void acceptsAnInstituteOwnerWhenBothPlanAndActorHaveNoDepartment() {
        when(actorProvider.currentActor()).thenReturn(new WarehousePlanActor(ACTOR, null));
        SourceBindingState binding = new SourceBindingState(
            BINDING_ID,
            "CATALOG_TABLE",
            ASSET_ID.toString(),
            "v1",
            "CONFIRMED",
            LOCATOR_JSON,
            ACTOR,
            null
        );
        when(repository.lockSourceBinding(TENANT, PLAN_ID, BINDING_ID)).thenReturn(Optional.of(binding));
        when(
            resolver.resolve(
                SourceType.CATALOG_TABLE,
                new SourceLocator(ASSET_ID, null, null, null, null, null, null),
                new AccessContext(TENANT, ACTOR, null)
            )
        )
            .thenReturn(ResolvedSource.available("客户表", "v1"));

        assertThat(validation.isCurrentBinding(TENANT, PLAN_ID, ACTOR, source(ASSET_ID.toString(), "v1"))).isTrue();
    }

    private static SourceBindingState confirmedBinding(String version, String locatorJson) {
        return new SourceBindingState(
            BINDING_ID,
            "CATALOG_TABLE",
            ASSET_ID.toString(),
            version,
            "CONFIRMED",
            locatorJson,
            ACTOR,
            DEPARTMENT
        );
    }

    private static SourceRef source(String ref, String version) {
        return new SourceRef(SourceKind.TABLE, ref, Layer.ODS, SourceRole.PRIMARY, null, null, null, 0, BINDING_ID, version);
    }
}
