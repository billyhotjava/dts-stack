package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.catalog.SchemaDriftConsumerReferenceReadPort;
import com.yuzhi.dts.platform.service.catalog.SchemaDriftDetailsReader;
import com.yuzhi.dts.platform.service.modeling.warehouse.CatalogDomainResolutionPort;
import com.yuzhi.dts.platform.service.modeling.warehouse.SourceReferenceResolver;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider.WarehousePlanActor;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanApplicationService;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class ModelingContextInitializationPostgresTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.6");
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private JdbcTemplate jdbc;
    private ModelingContextInitializationService contexts;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
            drop table if exists saved_model, modeling_warehouse_plan_source, modeling_warehouse_plan_policy, modeling_warehouse_plan;
            create table modeling_warehouse_plan (
                id uuid primary key, tenant_id text not null, owner text, code text, name text, objective text, scope text,
                owner_id text, owner_department_id text, onboarding_mode text, lifecycle_status text, status text,
                version int, business_scope_version int, sources_version int, source_mappings_version int, policy_version int,
                idempotency_key text, idempotency_request_hash text, idempotency_response_snapshot jsonb,
                created_date timestamptz, last_modified_date timestamptz, unique(tenant_id,idempotency_key));
            create table modeling_warehouse_plan_policy (
                plan_id uuid references modeling_warehouse_plan(id), tenant_id text, layer_policy_code text, naming_policy_ref text,
                history_policy text, default_time_zone text, conceptual_design_allowed boolean, standard_coverage text,
                quality_gate text, created_date timestamptz, last_modified_date timestamptz);
            create table modeling_warehouse_plan_source (
                id uuid, tenant_id text, plan_id uuid, source_type text, source_id text, source_version text,
                confirmation_status text, exclusion_reason text);
            create table saved_model (operation_id text primary key, plan_id uuid references modeling_warehouse_plan(id));
            """);
        var transactions = new DataSourceTransactionManager(dataSource);
        var plans = new WarehousePlanApplicationService(jdbc, mapper, mock(CatalogDomainResolutionPort.class),
            mock(SourceReferenceResolver.class), mock(AuditService.class), mock(SchemaDriftConsumerReferenceReadPort.class),
            mock(SchemaDriftDetailsReader.class), transactions);
        var departments = new ModelingDepartmentScope(mock(com.yuzhi.dts.platform.service.admin.gateway.directory.AdminDirectoryGateway.class));
        org.springframework.test.util.ReflectionTestUtils.setField(plans, "departmentScope", departments);
        var proxy = new ProxyFactory(new ModelingContextInitializationService(jdbc, plans, departments));
        proxy.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        contexts = (ModelingContextInitializationService) proxy.getProxy();
        authenticate("alice");
    }

    @AfterEach
    void cleanup() { SecurityContextHolder.clearContext(); }

    @Test
    void readingAnEmptyDefaultDoesNotCreateAnythingOrAdoptAnOrdinaryPlan() {
        assertThat(contexts.existingContextId("tenant")).isNull();
        assertThat(count("modeling_warehouse_plan")).isZero();
        assertThat(count("modeling_warehouse_plan_policy")).isZero();
        UUID ordinary = UUID.randomUUID();
        jdbc.update("insert into modeling_warehouse_plan(id,tenant_id,idempotency_key,lifecycle_status) values (?, 'tenant', 'ordinary', 'DRAFT')", ordinary);
        assertThat(contexts.existingContextId("tenant")).isNull();
        assertThat(count("modeling_warehouse_plan")).isEqualTo(1);
        UUID actual = save("tenant", "alice", "first");
        assertThat(actual).isNotEqualTo(ordinary);
        assertThat(contexts.existingContextId("tenant")).isEqualTo(actual);
        assertThat(contexts.existingContextId("another-tenant")).isNull();
    }

    @Test
    void aReadOnlyDefaultCanBeReadForSourcesButCannotBeUsedForNewWrites() {
        UUID id = save("tenant", "alice", "first");
        jdbc.update("update modeling_warehouse_plan set lifecycle_status='ARCHIVED' where id=?", id);
        assertThat(contexts.existingContextId("tenant")).isEqualTo(id);
        assertThatThrownBy(() -> save("tenant", "alice", "second")).isInstanceOfSatisfying(ModelSpecException.class,
            error -> assertThat(error.code()).isEqualTo("MODELING_CONTEXT_NOT_WRITABLE"));
        assertThat(count("saved_model")).isEqualTo(1);
    }

    @Test
    void firstSaveCreatesTheRealPlanAndPolicyAndRetryReusesThem() {
        UUID first = save("tenant", "alice", "operation-1");
        assertThat(save("tenant", "alice", "operation-1")).isEqualTo(first);
        assertThat(count("modeling_warehouse_plan")).isEqualTo(1);
        assertThat(count("modeling_warehouse_plan_policy")).isEqualTo(1);
        assertThat(count("saved_model")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select idempotency_response_snapshot is not null from modeling_warehouse_plan", Boolean.class)).isTrue();
    }

    @Test
    void failedSaveRollsBackModelContextAndPolicyBeforeRetry() {
        assertThatThrownBy(() -> contexts.withContext("tenant", actor("alice"), request(), nodes -> {
            jdbc.update("insert into saved_model values ('failed', ?)", UUID.fromString(nodes.getFirst().get("planId").asText()));
            throw new IllegalStateException("model validation failed");
        })).hasMessage("model validation failed");
        assertThat(count("modeling_warehouse_plan")).isZero();
        assertThat(count("modeling_warehouse_plan_policy")).isZero();
        assertThat(count("saved_model")).isZero();
        assertThat(save("tenant", "alice", "failed")).isNotNull();
    }

    @Test
    void concurrentDifferentActorsShareOneContext() throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(); return save("tenant", "alice", "a"); });
            var second = executor.submit(() -> { start.await(); return save("tenant", "bob", "b"); });
            start.countDown();
            assertThat(first.get(15, TimeUnit.SECONDS)).isEqualTo(second.get(15, TimeUnit.SECONDS));
        }
        assertThat(count("modeling_warehouse_plan")).isEqualTo(1);
        assertThat(count("modeling_warehouse_plan_policy")).isEqualTo(1);
        assertThat(count("saved_model")).isEqualTo(2);
    }

    @Test
    void coldStartWaiterRecoversAfterTheFirstTransactionRollsBack() throws Exception {
        var initialized = new CountDownLatch(1);
        var waiterStarted = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var failing = executor.submit(() -> {
                authenticate("alice");
                assertThatThrownBy(() -> contexts.withContext("tenant", actor("alice"), request(), nodes -> {
                    initialized.countDown();
                    try { assertThat(waiterStarted.await(5, TimeUnit.SECONDS)).isTrue(); }
                    catch (InterruptedException error) { throw new IllegalStateException(error); }
                    throw new IllegalStateException("rollback");
                })).hasMessage("rollback");
            });
            var waiting = executor.submit(() -> {
                assertThat(initialized.await(5, TimeUnit.SECONDS)).isTrue();
                waiterStarted.countDown();
                return save("tenant", "bob", "b");
            });
            failing.get(15, TimeUnit.SECONDS);
            assertThat(waiting.get(15, TimeUnit.SECONDS)).isNotNull();
        }
        assertThat(count("modeling_warehouse_plan")).isEqualTo(1);
        assertThat(count("saved_model")).isEqualTo(1);
    }

    @Test
    void tenantsDoNotShareDepartmentContexts() {
        UUID legacy = save("tenant", "alice", "a");
        assertThat(save("tenant", "bob", "b")).isEqualTo(legacy);
        assertThat(save("another-tenant", "bob", "c")).isNotEqualTo(legacy);
        assertThat(count("modeling_warehouse_plan")).isEqualTo(2);
    }

    @Test void foreignDepartmentCannotReadExplicitPlan() {
        UUID plan = save("tenant","alice","first");
        var identities = new com.yuzhi.dts.platform.security.modeling.ModelingIdentityService(mock(com.yuzhi.dts.platform.service.admin.gateway.directory.AdminDirectoryGateway.class));
        var outsider = new com.yuzhi.dts.platform.service.admin.gateway.directory.AdminDirectoryGateway.ModelingUser("outsider","outsider","他部用户","dept-b","乙",List.of("ROLE_DEPT_DATA_OWNER"),true,"GENERAL");
        assertThatThrownBy(() -> identities.withIdentity(outsider, () -> contexts.withContext("tenant",new WarehousePlanActor("outsider","dept-b"),List.of(mapper.createObjectNode().put("planId",plan.toString())),nodes -> nodes)))
            .isInstanceOf(com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanApplicationService.WarehousePlanException.class);
        assertThat(count("saved_model")).isEqualTo(1);
    }

    private UUID save(String tenant, String user, String operationId) {
        authenticate(user);
        return contexts.withContext(tenant, actor(user), request(), nodes -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            UUID id = UUID.fromString(nodes.getFirst().get("planId").asText());
            assertThat(nodes.get(1).get("planId").asText()).isEqualTo(id.toString());
            jdbc.update("insert into saved_model values (?, ?) on conflict do nothing", operationId, id);
            return id;
        });
    }

    private List<JsonNode> request() { return List.of(mapper.createObjectNode(), mapper.createObjectNode().putNull("planId")); }
    private WarehousePlanActor actor(String user) { return new WarehousePlanActor(user, "dept-a"); }
    private int count(String table) { return jdbc.queryForObject("select count(*) from " + table, Integer.class); }
    private static final ThreadLocal<com.yuzhi.dts.platform.security.modeling.ModelingIdentityService.Scope> IDENTITY = new ThreadLocal<>();
    private static void authenticate(String user) {
        if (IDENTITY.get() != null) IDENTITY.get().close();
        var directory = mock(com.yuzhi.dts.platform.service.admin.gateway.directory.AdminDirectoryGateway.class);
        org.mockito.Mockito.when(directory.currentModelingUser(user)).thenReturn(new com.yuzhi.dts.platform.service.admin.gateway.directory.AdminDirectoryGateway.ModelingUser(user,user,user,"dept-a","甲",List.of("ROLE_DEPT_DATA_OWNER"),true,"GENERAL"));
        IDENTITY.set(new com.yuzhi.dts.platform.security.modeling.ModelingIdentityService(directory).openCurrentUser(user));
    }
}
