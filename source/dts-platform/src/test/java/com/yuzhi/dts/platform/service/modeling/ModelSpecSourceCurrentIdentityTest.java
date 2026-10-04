package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository.SourceBindingState;
import com.yuzhi.dts.platform.security.modeling.ModelingIdentityService;
import com.yuzhi.dts.platform.service.admin.gateway.directory.AdminDirectoryGateway;
import com.yuzhi.dts.platform.service.admin.gateway.directory.AdminDirectoryGateway.ModelingUser;
import com.yuzhi.dts.platform.service.modeling.warehouse.*;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class ModelSpecSourceCurrentIdentityTest {
    private final ModelSpecRepository repository = mock(ModelSpecRepository.class);
    private final SourceReferenceResolver resolver = mock(SourceReferenceResolver.class);
    private final ModelingSourceScopeGuard scope = mock(ModelingSourceScopeGuard.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ModelSpecSourceValidationAdapter adapter = new ModelSpecSourceValidationAdapter(repository,resolver,new WarehousePlanActorProvider(),new ObjectMapper(),mock(ModelSpecPlanWriteAccessPort.class));
    private final UUID plan = UUID.randomUUID(), binding = UUID.randomUUID();
    private final SourceLocator locator = new SourceLocator(UUID.randomUUID(),null,null,null,null,null,null);
    private final ModelingIdentityService identities = new ModelingIdentityService(mock(AdminDirectoryGateway.class));
    @BeforeEach void prepare() throws Exception {
        ReflectionTestUtils.setField(adapter,"sourceScope",scope); ReflectionTestUtils.setField(adapter,"jdbc",jdbc);
        String sourceId = WarehousePlanContract.canonicalSourceId(SourceType.CATALOG_TABLE,locator);
        when(repository.findSourceBinding("tenant",plan,binding)).thenReturn(Optional.of(new SourceBindingState(binding,"CATALOG_TABLE",sourceId,"v1","CONFIRMED",new ObjectMapper().writeValueAsString(locator),"historical-owner","old-department")));
        when(jdbc.queryForObject(anyString(),eq(UUID.class),eq("tenant"),eq(binding))).thenReturn(plan);
    }
    @Test void backgroundUserExecutionUsesCurrentActorNotHistoricalPlanOwner() {
        when(resolver.resolve(any(),any(),any())).thenReturn(SourceReferenceResolver.ResolvedSource.available("source","v1"));
        identities.withIdentity(user(), () -> {
            assertThat(adapter.isCurrentBindingForExecution("tenant",plan,binding,"v1")).isTrue(); return null;
        });
        verify(resolver).resolve(eq(SourceType.CATALOG_TABLE),eq(locator),eq(new SourceReferenceResolver.AccessContext("tenant","stable-user","dept-a")));
        verify(resolver,never()).resolveForExecution(any(),any(),any());
    }
    @Test void anonymousWorkerCannotBorrowStoredOwner() {
        assertThat(adapter.isCurrentBindingForExecution("tenant",plan,binding,"v1")).isFalse();
        verifyNoInteractions(resolver);
    }
    @Test void crossDepartmentAndInvisibleFailuresAreNotSwallowedAsOrdinarySourceFailures() {
        doThrow(new ModelSpecException("MODEL_SPEC_NOT_FOUND","hidden",ModelSpecException.Kind.NOT_FOUND)).when(scope).requireSource(any(),any(),any(),any());
        assertThatThrownBy(() -> identities.withIdentity(user(), () -> adapter.isCurrentBindingForExecution("tenant",plan,binding,"v1"))).isInstanceOf(ModelSpecException.class);
        verifyNoInteractions(resolver);
    }
    private ModelingUser user() { return new ModelingUser("stable-user","alice","Alice","dept-a","甲",List.of("ROLE_DEPT_DATA_OWNER"),true,"GENERAL"); }
}
