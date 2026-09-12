package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.modeling.ModelingIdentityService;
import com.yuzhi.dts.platform.security.modeling.ModelingIdentityException;
import com.yuzhi.dts.platform.service.admin.gateway.directory.AdminDirectoryGateway;
import com.yuzhi.dts.platform.service.admin.gateway.directory.AdminDirectoryGateway.ModelingUser;
import com.yuzhi.dts.platform.service.audit.AuditService;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import liquibase.*;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers
class ModelingPermissionScopePostgresIT {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.6");
    private AnnotationConfigApplicationContext context;
    private JdbcTemplate jdbc;
    private ModelSpecAccessService access;
    private ModelingIdentityService identities;
    private TransactionTemplate transaction;
    private final Map<String, ModelingUser> directory = new ConcurrentHashMap<>();
    private final UUID planA = UUID.randomUUID(), planB = UUID.randomUUID(), domain = UUID.randomUUID();
    private final UUID shared = UUID.randomUUID(), ads = UUID.randomUUID(), foreign = UUID.randomUUID();

    @Configuration @EnableTransactionManagement static class Transactions {}

    @BeforeEach void prepare() throws Exception {
        var ds = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("drop schema public cascade; create schema public");
        jdbc.execute("""
            create table portal_sessions(id uuid primary key);
            create table modeling_warehouse_plan(id uuid primary key, tenant_id varchar(128), owner_department_id varchar(128), lifecycle_status varchar(32), idempotency_key text);
            create table modeling_model_spec(id uuid primary key, tenant_id varchar(128), plan_id uuid references modeling_warehouse_plan(id), domain_id uuid, model_type varchar(32), status varchar(32), unique(tenant_id,id));
            create table modeling_operational_run_dispatch(id uuid primary key);
            create table permission_audit(action text, outcome text);
            create table modeling_plan_execution_binding(id uuid primary key, tenant_id varchar(128), plan_id uuid, version integer, desired_scope_checksum text, deployment_status text, deployed_checksum text, desired_deployment_checksum text);
            create table modeling_plan_execution_binding_entry(id uuid primary key,tenant_id varchar(128),binding_id uuid,model_spec_id uuid);
            """);
        try (var connection=ds.getConnection()) {
            var database=DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (var migration=new Liquibase("config/liquibase/changelog/20260912_01_modeling_permission_scope.xml",new ClassLoaderResourceAccessor(),database)) {
                migration.update(new Contexts(),new LabelExpression());
            }
        }
        var gateway=mock(AdminDirectoryGateway.class);
        when(gateway.currentModelingUser(anyString())).thenAnswer(call -> directory.get(call.getArgument(0)));
        var domains=mock(ModelSpecDomainReadAccessPort.class);
        when(domains.visibleDomainIds()).thenReturn(Set.of(domain));
        var audit=mock(AuditService.class);
        doAnswer(call -> { jdbc.update("insert into permission_audit values (?,?)",call.getArgument(1),call.getArgument(5));return null; })
            .when(audit).recordAs(anyString(),anyString(),anyString(),anyString(),nullable(String.class),anyString(),anyMap(),anyMap());
        context=new AnnotationConfigApplicationContext(); context.register(Transactions.class);
        context.registerBean(JdbcTemplate.class,()->jdbc);
        context.registerBean(DataSourceTransactionManager.class,()->new DataSourceTransactionManager(ds));
        context.registerBean(AdminDirectoryGateway.class,()->gateway);
        context.registerBean(ModelSpecDomainReadAccessPort.class,()->domains);
        context.registerBean(AuditService.class,()->audit);
        context.registerBean(ModelingPermissionAudit.class);
        context.registerBean(ModelSpecAccessService.class,()->new ModelSpecAccessService(jdbc,gateway,domains,context.getBean(ModelingPermissionAudit.class),"tenant"));
        context.refresh(); access=context.getBean(ModelSpecAccessService.class);
        identities=new ModelingIdentityService(gateway); transaction=new TransactionTemplate(context.getBean(DataSourceTransactionManager.class));
        directory.put("owner",user("owner","dept-a",AuthoritiesConstants.DEPT_DATA_OWNER));
        directory.put("editor",user("editor","dept-a",AuthoritiesConstants.DEPT_DATA_OWNER));
        directory.put("leader",user("leader","dept-a",AuthoritiesConstants.DEPT_LEADER));
        directory.put("institute",user("institute","dept-b",AuthoritiesConstants.INST_DATA_OWNER));
        directory.put("outsider",user("outsider","dept-b",AuthoritiesConstants.DEPT_DATA_OWNER));
        jdbc.update("insert into modeling_warehouse_plan values (?,'tenant','dept-a','DRAFT','modeling-context:dept:dept-a:v1')",planA);
        jdbc.update("insert into modeling_warehouse_plan values (?,'tenant','dept-b','DRAFT','modeling-context:dept:dept-b:v1')",planB);
        insert(shared,planA,"FACT",null); insert(ads,planA,"APPLICATION","owner"); insert(foreign,planB,"FACT",null);
    }
    @AfterEach void cleanup() { if(context!=null) context.close(); }

    @Test void publicLayerIsJointButAdsNeedsOwnerManagerOrExplicitEditor() {
        as("editor",()-> {
            assertThat(access.canEdit("tenant",shared,"editor")).isTrue();
            assertThat(access.canEdit("tenant",ads,"editor")).isFalse();
            assertThat(access.canReadPlan("tenant",planB)).isFalse();
            assertThat(access.capabilities("tenant",List.of(foreign))).isEmpty(); return null;
        });
        as("owner",()->{assertThat(access.canEdit("tenant",ads,"owner")).isTrue();return null;});
        as("leader",()->{assertThat(access.capabilities("tenant",List.of(ads)).get(ads).canManage()).isTrue();return null;});
        as("institute",()->{assertThat(access.canEdit("tenant",ads,"institute")).isTrue();return null;});
    }
    @Test void employeeOrDeletedIdentityNeverBorrowsOwnerAndRenamingDoesNotChangeOwnership() {
        directory.put("owner",user("owner","dept-a",AuthoritiesConstants.EMPLOYEE));
        assertThatThrownBy(()->as("owner",()->access.canEdit("tenant",ads,"owner"))).isInstanceOfSatisfying(ModelingIdentityException.class,ex->assertThat(ex.status()).isEqualTo(403));
        directory.put("owner",new ModelingUser("owner","renamed","新姓名","dept-a","甲",List.of(AuthoritiesConstants.DEPT_DATA_OWNER),true,"GENERAL"));
        as("owner",()->{assertThat(access.canEdit("tenant",ads,"owner")).isTrue();return null;});
        directory.remove("owner");
        assertThatThrownBy(()->as("owner",()->true)).isInstanceOf(ModelingIdentityException.class);
    }
    @Test void grantsAreIdempotentRevalidatedAndScopedToCurrentDepartment() {
        var grant=as("owner",()->access.grant("tenant",ads,grant("USER","editor")));
        assertThat(grant.created()).isTrue();
        assertThat(as("owner",()->access.grant("tenant",ads,grant("USER","editor"))).created()).isFalse();
        as("editor",()->{access.requireEdit("tenant",ads,"editor");return null;});
        directory.put("editor",user("editor","dept-b",AuthoritiesConstants.DEPT_DATA_OWNER));
        as("editor",()->{assertThat(access.canEdit("tenant",ads,"editor")).isFalse();return null;});
        as("owner",()->{access.revoke("tenant",ads,grant.grant().id());access.revoke("tenant",ads,grant.grant().id());return null;});
        assertThat(jdbc.queryForObject("select count(*) from modeling_model_access where revoked_at is null",Integer.class)).isZero();
    }
    @Test void roleGrantNeverIncludesSameRoleInAnotherDepartment() {
        as("owner",()->access.grant("tenant",ads,grant("ROLE",AuthoritiesConstants.DEPT_DATA_OWNER)));
        as("editor",()->{assertThat(access.canEdit("tenant",ads,"editor")).isTrue();return null;});
        as("outsider",()->{assertThat(access.canEdit("tenant",ads,"outsider")).isFalse();return null;});
        assertThatThrownBy(()->as("owner",()->access.grant("tenant",ads,grant("USER","outsider"))))
            .isInstanceOfSatisfying(ModelSpecException.class,ex->assertThat(ex.code()).isEqualTo("MODEL_ACCESS_CROSS_DEPARTMENT_PENDING_APPROVAL"));
    }
    @Test void wholeOperationFailsBeforeMutationAndDenialAuditSurvivesRollback() {
        assertThatThrownBy(()->as("editor",()->transaction.execute(status->{
            access.requireOperation("tenant",List.of(shared,ads),"editor");
            jdbc.update("update modeling_model_spec set status='ARCHIVED' where id=?",shared);return null;
        }))).isInstanceOf(ModelSpecException.class);
        assertThat(jdbc.queryForObject("select status from modeling_model_spec where id=?",String.class,shared)).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("select count(*) from permission_audit where action='MODELING_OPERATION_SCOPE_DENIED' and outcome='FAILED'",Integer.class)).isEqualTo(1);
    }
    @Test void concurrentGrantCreatesOneActiveRecordAndSameReceipt() throws Exception {
        try(var pool=Executors.newFixedThreadPool(2)) {
            var barrier=new CyclicBarrier(2);
            Callable<UUID> create=()->{barrier.await(5,TimeUnit.SECONDS);return as("owner",()->access.grant("tenant",ads,grant("USER","editor")).grant().id());};
            var a=pool.submit(create);var b=pool.submit(create);
            assertThat(a.get(15,TimeUnit.SECONDS)).isEqualTo(b.get(15,TimeUnit.SECONDS));
        }
        assertThat(jdbc.queryForObject("select count(*) from modeling_model_access where revoked_at is null",Integer.class)).isEqualTo(1);
    }
    @Test void queuedEditWaitingOnRevokeReadsCommittedRevocation() throws Exception {
        var grant=as("owner",()->access.grant("tenant",ads,grant("USER","editor"))).grant();
        var locked=new CountDownLatch(1);var attempted=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var revoke=pool.submit(()->as("owner",()->transaction.execute(status->{
                access.revoke("tenant",ads,grant.id());locked.countDown();
                try { assertThat(attempted.await(5,TimeUnit.SECONDS)).isTrue(); } catch(InterruptedException ex){throw new IllegalStateException(ex);}return null;
            })));
            var edit=pool.submit(()->{assertThat(locked.await(5,TimeUnit.SECONDS)).isTrue();attempted.countDown();
                assertThatThrownBy(()->as("editor",()->{access.requireEdit("tenant",ads,"editor");return null;})).isInstanceOf(ModelSpecException.class);return null;});
            revoke.get(15,TimeUnit.SECONDS);edit.get(15,TimeUnit.SECONDS);
        }
    }
    @Test void leavingAdsRevokesGrantsAndReturningPreservesOriginalOwner() {
        as("owner",()->access.grant("tenant",ads,grant("USER","editor")));
        as("leader",()->transaction.execute(status->{ access.reclassifyAccess("tenant",ads,"leader",ModelSpecContract.ModelType.APPLICATION,ModelSpecContract.ModelType.SUMMARY);jdbc.update("update modeling_model_spec set model_type='SUMMARY' where id=?",ads);return null; }));
        as("leader",()->transaction.execute(status->{ access.reclassifyAccess("tenant",ads,"leader",ModelSpecContract.ModelType.SUMMARY,ModelSpecContract.ModelType.APPLICATION);jdbc.update("update modeling_model_spec set model_type='APPLICATION' where id=?",ads);return null; }));
        assertThat(jdbc.queryForObject("select owner_id from modeling_model_spec where id=?",String.class,ads)).isEqualTo("owner");
        as("editor",()->{assertThat(access.canEdit("tenant",ads,"editor")).isFalse();return null;});
    }
    private ModelSpecAccessService.GrantCommand grant(String type,String id) {return new ModelSpecAccessService.GrantCommand(type,id,"EDITOR");}
    private <T> T as(String actor,Supplier<T> work) {return identities.asCurrentUser(actor,work);}
    private ModelingUser user(String id,String department,String role) {return new ModelingUser(id,id,id,department,department,List.of(role),true,"GENERAL");}
    private void insert(UUID id,UUID plan,String type,String owner) {jdbc.update("insert into modeling_model_spec(id,tenant_id,plan_id,domain_id,model_type,status,owner_id,owner_display_name) values (?,'tenant',?,?,?,'DRAFT',?,?)",id,plan,domain,type,owner,owner);}
}
