package com.yuzhi.dts.platform.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.repository.permission.AssetPermissionAuditRepository;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetTagPermissionIdentityResolver.ResolvedPermissionIdentity;
import com.yuzhi.dts.platform.service.catalog.dto.AssetRef;
import com.yuzhi.dts.platform.service.permission.AssetPermissionAuditService;
import com.yuzhi.dts.platform.service.permission.AssetPermissionService;
import com.yuzhi.dts.platform.service.permission.AssetPermissionService.PermissionDecision;
import com.yuzhi.dts.platform.service.permission.AssetPermissionService.PermissionResult;
import com.yuzhi.dts.platform.web.rest.catalog.CatalogResourceHelper;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.aop.support.AopUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringJUnitConfig(CatalogAssetTagTransactionBoundaryTest.Config.class)
class CatalogAssetTagTransactionBoundaryTest {

    private static final String ASSET_KEY =
        "tenant:default/env:prod/dialect:generic/api_service:customer_query";
    private static final String ASSET_ID = "11111111-1111-1111-1111-111111111111";

    @Autowired
    private CatalogAssetTagPermissionIdentityResolver identityResolver;

    @Autowired
    private AssetPermissionService permissionService;

    @Autowired
    private BoundaryProbe boundaryProbe;

    @Autowired
    private CatalogAssetTagWriteGuard writeGuard;

    @Autowired
    private RecordingPermissionAuditService permissionAuditService;

    private boolean resolverSawTransaction;
    private String resolverTransactionName;

    @BeforeEach
    void setUp() {
        SecurityContextHolder
            .getContext()
            .setAuthentication(
                new UsernamePasswordAuthenticationToken(
                    "analyst",
                    "n/a",
                    List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))
                )
            );
        AssetRef requested = new AssetRef("API_SERVICE", ASSET_KEY);
        when(identityResolver.resolveAll(List.of(requested)))
            .thenAnswer(invocation -> {
                resolverSawTransaction = TransactionSynchronizationManager.isActualTransactionActive();
                resolverTransactionName =
                    TransactionSynchronizationManager.getCurrentTransactionName();
                return List.of(
                    new ResolvedPermissionIdentity(
                        CatalogAssetType.API_SERVICE,
                        ASSET_KEY,
                        "API_SERVICE",
                        ASSET_ID,
                        null,
                        "api-code"
                    )
                );
            });
        when(
            permissionService.check(
                eq("analyst"),
                anyList(),
                isNull(),
                eq("API_SERVICE"),
                eq(ASSET_ID)
            )
        ).thenReturn(PermissionResult.allowed("EDIT", "explicit_grant"));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void writeGuardSuspendsCallerTransactionAndPermissionAuditUsesAnIndependentTransaction() {
        assertThat(AopUtils.isAopProxy(boundaryProbe)).isTrue();
        assertThat(AopUtils.isAopProxy(writeGuard)).isTrue();
        assertThat(AopUtils.isAopProxy(permissionAuditService)).isTrue();
        boundaryProbe.authorize(new AssetRef("API_SERVICE", ASSET_KEY));

        assertThat(boundaryProbe.transactionActiveBeforeGuard()).isTrue();
        assertThat(resolverSawTransaction).isTrue();
        assertThat(resolverTransactionName)
            .isNotEqualTo(boundaryProbe.transactionNameBeforeGuard())
            .contains("CatalogAssetTagPermissionIdentityResolver");
        assertThat(permissionAuditService.transactionActiveWhileRecording()).isTrue();
        assertThat(permissionAuditService.transactionNameWhileRecording())
            .isNotEqualTo(boundaryProbe.transactionNameBeforeGuard())
            .contains("RecordingPermissionAuditService");
        assertThat(boundaryProbe.transactionActiveAfterGuard()).isTrue();
        assertThat(boundaryProbe.transactionNameAfterGuard())
            .isEqualTo(boundaryProbe.transactionNameBeforeGuard());
    }

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {

        @Bean
        PlatformTransactionManager transactionManager() {
            return new InMemoryTransactionManager();
        }

        @Bean
        CatalogAssetTagPermissionIdentityResolver identityResolver() {
            return mock(CatalogAssetTagPermissionIdentityResolver.class);
        }

        @Bean
        AssetPermissionService permissionService() {
            return mock(AssetPermissionService.class);
        }

        @Bean
        CatalogResourceHelper catalogResourceHelper() {
            return mock(CatalogResourceHelper.class);
        }

        @Bean
        RecordingPermissionAuditService permissionAuditService() {
            return new RecordingPermissionAuditService(mock(AssetPermissionAuditRepository.class));
        }

        @Bean
        CatalogAssetTagWriteGuard writeGuard(
            CatalogAssetTagPermissionIdentityResolver identityResolver,
            AssetPermissionService permissionService,
            RecordingPermissionAuditService permissionAuditService,
            CatalogResourceHelper catalogResourceHelper
        ) {
            return new CatalogAssetTagWriteGuard(
                identityResolver,
                permissionService,
                permissionAuditService,
                catalogResourceHelper
            );
        }

        @Bean
        BoundaryProbe boundaryProbe(CatalogAssetTagWriteGuard writeGuard) {
            return new BoundaryProbe(writeGuard);
        }
    }

    public static class BoundaryProbe {

        private final CatalogAssetTagWriteGuard writeGuard;
        private boolean transactionActiveBeforeGuard;
        private boolean transactionActiveAfterGuard;
        private String transactionNameBeforeGuard;
        private String transactionNameAfterGuard;

        public BoundaryProbe(CatalogAssetTagWriteGuard writeGuard) {
            this.writeGuard = writeGuard;
        }

        @Transactional
        public void authorize(AssetRef asset) {
            transactionActiveBeforeGuard =
                TransactionSynchronizationManager.isActualTransactionActive();
            transactionNameBeforeGuard =
                TransactionSynchronizationManager.getCurrentTransactionName();
            writeGuard.authorizeAll(List.of(asset));
            transactionActiveAfterGuard =
                TransactionSynchronizationManager.isActualTransactionActive();
            transactionNameAfterGuard =
                TransactionSynchronizationManager.getCurrentTransactionName();
        }

        public boolean transactionActiveBeforeGuard() {
            return transactionActiveBeforeGuard;
        }

        public boolean transactionActiveAfterGuard() {
            return transactionActiveAfterGuard;
        }

        public String transactionNameBeforeGuard() {
            return transactionNameBeforeGuard;
        }

        public String transactionNameAfterGuard() {
            return transactionNameAfterGuard;
        }
    }

    public static class RecordingPermissionAuditService extends AssetPermissionAuditService {

        private boolean transactionActiveWhileRecording;
        private String transactionNameWhileRecording;

        public RecordingPermissionAuditService(
            AssetPermissionAuditRepository auditRepository
        ) {
            super(auditRepository, null);
        }

        @Override
        public void recordDecision(
            PermissionDecision decision,
            String targetUser,
            String operator
        ) {
            transactionActiveWhileRecording =
                TransactionSynchronizationManager.isActualTransactionActive();
            transactionNameWhileRecording =
                TransactionSynchronizationManager.getCurrentTransactionName();
        }

        public boolean transactionActiveWhileRecording() {
            return transactionActiveWhileRecording;
        }

        public String transactionNameWhileRecording() {
            return transactionNameWhileRecording;
        }
    }

    static final class InMemoryTransactionManager extends AbstractPlatformTransactionManager {

        private final Object resourceKey = new Object();

        @Override
        protected Object doGetTransaction() {
            return new TransactionObject(
                TransactionSynchronizationManager.hasResource(resourceKey)
            );
        }

        @Override
        protected boolean isExistingTransaction(Object transaction) {
            return ((TransactionObject) transaction).existing();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            TransactionSynchronizationManager.bindResource(resourceKey, new Object());
        }

        @Override
        protected Object doSuspend(Object transaction) {
            return TransactionSynchronizationManager.unbindResource(resourceKey);
        }

        @Override
        protected void doResume(Object transaction, Object suspendedResources) {
            TransactionSynchronizationManager.bindResource(resourceKey, suspendedResources);
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {}

        @Override
        protected void doRollback(DefaultTransactionStatus status) {}

        @Override
        protected void doCleanupAfterCompletion(Object transaction) {
            if (TransactionSynchronizationManager.hasResource(resourceKey)) {
                TransactionSynchronizationManager.unbindResource(resourceKey);
            }
        }

        private record TransactionObject(boolean existing) {}
    }
}
