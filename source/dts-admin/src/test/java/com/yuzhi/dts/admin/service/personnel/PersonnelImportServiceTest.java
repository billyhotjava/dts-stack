package com.yuzhi.dts.admin.service.personnel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.admin.config.MdmGatewayProperties;
import com.yuzhi.dts.admin.domain.AdminKeycloakUser;
import com.yuzhi.dts.admin.domain.PersonImportBatch;
import com.yuzhi.dts.admin.domain.PersonImportRecord;
import com.yuzhi.dts.admin.domain.enumeration.PersonRecordStatus;
import com.yuzhi.dts.admin.repository.AdminKeycloakUserRepository;
import com.yuzhi.dts.admin.repository.PersonImportBatchRepository;
import com.yuzhi.dts.admin.repository.PersonImportRecordRepository;
import com.yuzhi.dts.admin.service.audit.AuditV2Service;
import com.yuzhi.dts.admin.service.dto.personnel.PersonnelImportResult;
import com.yuzhi.dts.admin.service.dto.personnel.PersonnelPayload;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

class PersonnelImportServiceTest {

    @Test
    void importFromMdmShouldPersistFailureAndCompleteBatchWhenRecordSaveFails() {
        PersonImportBatchRepository batchRepository = mock(PersonImportBatchRepository.class);
        PersonImportRecordRepository recordRepository = mock(PersonImportRecordRepository.class);
        KeycloakUserProvisioningService provisioningService = mock(KeycloakUserProvisioningService.class);
        AdminKeycloakUserRepository adminKeycloakUserRepository = mock(AdminKeycloakUserRepository.class);
        PersonImportBatch savedBatch = new PersonImportBatch();
        savedBatch.setId(42L);
        when(batchRepository.save(any(PersonImportBatch.class))).thenAnswer((Answer<PersonImportBatch>) invocation -> {
            PersonImportBatch batch = invocation.getArgument(0);
            if (batch.getId() == null) {
                batch.setId(42L);
            }
            return batch;
        });
        when(batchRepository.getReferenceById(42L)).thenReturn(savedBatch);
        when(provisioningService.provision(any(PersonnelPayload.class)))
            .thenReturn(new KeycloakUserProvisioningService.ProvisionResult("kc-1", null, false, null));
        when(adminKeycloakUserRepository.findByKeycloakId("kc-1")).thenReturn(java.util.Optional.empty());
        when(adminKeycloakUserRepository.findByUsernameIgnoreCase("alice")).thenReturn(java.util.Optional.empty());
        when(adminKeycloakUserRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(recordRepository.save(any(PersonImportRecord.class)))
            .thenThrow(new DataIntegrityViolationException("duplicate keycloak user id"))
            .thenAnswer(invocation -> invocation.getArgument(0));

        PersonnelImportService service = new PersonnelImportService(
            batchRepository,
            recordRepository,
            mock(PersonnelExcelParser.class),
            mock(PersonnelApiClient.class),
            mock(AuditV2Service.class),
            provisioningService,
            adminKeycloakUserRepository,
            new ObjectMapper(),
            new MdmGatewayProperties(),
            new NoopTransactionManager()
        );

        PersonnelImportResult result = service.importFromMdm("mdm-ref", java.util.List.of(payload("alice")), Map.of());

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.successRecords()).isZero();
        assertThat(result.failureRecords()).isEqualTo(1);
    }


    @Test
    void importFromMdmShouldRecordRejectedRowsAndStillImportValidOnes() {
        PersonImportBatchRepository batchRepository = mock(PersonImportBatchRepository.class);
        PersonImportRecordRepository recordRepository = mock(PersonImportRecordRepository.class);
        KeycloakUserProvisioningService provisioningService = mock(KeycloakUserProvisioningService.class);
        AdminKeycloakUserRepository adminKeycloakUserRepository = mock(AdminKeycloakUserRepository.class);
        PersonImportBatch savedBatch = new PersonImportBatch();
        savedBatch.setId(9L);
        when(batchRepository.save(any(PersonImportBatch.class))).thenAnswer((Answer<PersonImportBatch>) invocation -> {
            PersonImportBatch batch = invocation.getArgument(0);
            if (batch.getId() == null) {
                batch.setId(9L);
            }
            return batch;
        });
        when(batchRepository.getReferenceById(9L)).thenReturn(savedBatch);
        when(provisioningService.provision(any(PersonnelPayload.class)))
            .thenReturn(new KeycloakUserProvisioningService.ProvisionResult("kc-1", null, false, null));
        when(adminKeycloakUserRepository.findByKeycloakId("kc-1")).thenReturn(java.util.Optional.empty());
        when(adminKeycloakUserRepository.findByUsernameIgnoreCase("alice")).thenReturn(java.util.Optional.empty());
        when(adminKeycloakUserRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        java.util.List<PersonImportRecord> saved = new java.util.ArrayList<>();
        when(recordRepository.save(any(PersonImportRecord.class))).thenAnswer(invocation -> {
            saved.add(invocation.getArgument(0));
            return invocation.getArgument(0);
        });

        PersonnelImportService service = new PersonnelImportService(
            batchRepository,
            recordRepository,
            mock(PersonnelExcelParser.class),
            mock(PersonnelApiClient.class),
            mock(AuditV2Service.class),
            provisioningService,
            adminKeycloakUserRepository,
            new ObjectMapper(),
            new MdmGatewayProperties(),
            new NoopTransactionManager()
        );
        PersonnelPayload broken = payloadWithDept("bob", null, null);

        PersonnelImportResult result = service.importFromMdm(
            "mdm-ref",
            java.util.List.of(payload("alice")),
            java.util.List.of(new PersonnelImportService.RejectedPayload(broken, "第 2 条人员缺少必填字段: deptCode")),
            Map.of()
        );

        assertThat(result.status()).isEqualTo("COMPLETED_WITH_ERRORS");
        assertThat(result.totalRecords()).isEqualTo(2);
        assertThat(result.successRecords()).isEqualTo(1);
        assertThat(result.failureRecords()).isEqualTo(1);
        assertThat(saved).extracting(PersonImportRecord::getStatus)
            .containsExactlyInAnyOrder(PersonRecordStatus.SUCCESS, PersonRecordStatus.FAILED);
        PersonImportRecord rejectedRecord = saved.stream().filter(r -> r.getStatus() == PersonRecordStatus.FAILED).findFirst().orElseThrow();
        assertThat(rejectedRecord.getAccount()).isEqualTo("bob");
        assertThat(rejectedRecord.getMessage()).contains("deptCode");
        verify(provisioningService, never()).provision(broken);
    }

    @Test
    void importFromMdmShouldOverwriteDeptAndGroupPathWhenPersonTransfers() {
        PersonImportBatchRepository batchRepository = mock(PersonImportBatchRepository.class);
        PersonImportRecordRepository recordRepository = mock(PersonImportRecordRepository.class);
        KeycloakUserProvisioningService provisioningService = mock(KeycloakUserProvisioningService.class);
        AdminKeycloakUserRepository adminKeycloakUserRepository = mock(AdminKeycloakUserRepository.class);
        PersonImportBatch savedBatch = new PersonImportBatch();
        savedBatch.setId(7L);
        when(batchRepository.save(any(PersonImportBatch.class))).thenAnswer((Answer<PersonImportBatch>) invocation -> {
            PersonImportBatch batch = invocation.getArgument(0);
            if (batch.getId() == null) {
                batch.setId(7L);
            }
            return batch;
        });
        when(batchRepository.getReferenceById(7L)).thenReturn(savedBatch);
        when(provisioningService.provision(any(PersonnelPayload.class)))
            .thenReturn(new KeycloakUserProvisioningService.ProvisionResult("kc-1", java.util.List.of("/总部/新部门", "/专项组"), false, null));

        // 已存在的快照停留在调岗前的部门
        AdminKeycloakUser existing = new AdminKeycloakUser();
        existing.setKeycloakId("kc-1");
        existing.setUsername("alice");
        existing.setDeptCode("D001");
        existing.setDeptName("旧部门");
        existing.setGroupPaths(new java.util.ArrayList<>(java.util.List.of("/总部/旧部门")));
        when(adminKeycloakUserRepository.findByKeycloakId("kc-1")).thenReturn(java.util.Optional.of(existing));
        when(adminKeycloakUserRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(recordRepository.save(any(PersonImportRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PersonnelImportService service = new PersonnelImportService(
            batchRepository,
            recordRepository,
            mock(PersonnelExcelParser.class),
            mock(PersonnelApiClient.class),
            mock(AuditV2Service.class),
            provisioningService,
            adminKeycloakUserRepository,
            new ObjectMapper(),
            new MdmGatewayProperties(),
            new NoopTransactionManager()
        );

        PersonnelImportResult result = service.importFromMdm("mdm-ref", java.util.List.of(payloadWithDept("alice", "D002", "新部门")), Map.of());

        assertThat(result.successRecords()).isEqualTo(1);
        assertThat(existing.getDeptCode()).isEqualTo("D002");
        assertThat(existing.getDeptName()).isEqualTo("新部门");
        assertThat(existing.getGroupPaths()).containsExactly("/总部/新部门", "/专项组");
    }

    @Test
    void importFromMdmShouldSnapshotNewUserAsDisabled() {
        AdminKeycloakUserRepository snapshots = mock(AdminKeycloakUserRepository.class);
        KeycloakUserProvisioningService provisioning = mock(KeycloakUserProvisioningService.class);
        when(provisioning.provision(any(PersonnelPayload.class)))
            .thenReturn(new KeycloakUserProvisioningService.ProvisionResult("kc-new", null, true, Boolean.FALSE));
        when(snapshots.findByKeycloakId("kc-new")).thenReturn(java.util.Optional.empty());
        when(snapshots.findByUsernameIgnoreCase("alice")).thenReturn(java.util.Optional.empty());
        java.util.List<AdminKeycloakUser> saved = new java.util.ArrayList<>();
        when(snapshots.save(any())).thenAnswer(invocation -> {
            saved.add(invocation.getArgument(0));
            return invocation.getArgument(0);
        });

        PersonnelImportResult result = simpleService(provisioning, snapshots).importFromMdm("mdm-ref", java.util.List.of(payload("alice")), Map.of());

        assertThat(result.successRecords()).isEqualTo(1);
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).isEnabled()).isFalse();
    }

    @Test
    void importFromMdmShouldKeepEnabledStateOfExistingSnapshot() {
        AdminKeycloakUserRepository snapshots = mock(AdminKeycloakUserRepository.class);
        KeycloakUserProvisioningService provisioning = mock(KeycloakUserProvisioningService.class);
        when(provisioning.provision(any(PersonnelPayload.class)))
            .thenReturn(new KeycloakUserProvisioningService.ProvisionResult("kc-1", null, false, Boolean.TRUE));
        AdminKeycloakUser existing = new AdminKeycloakUser();
        existing.setKeycloakId("kc-1");
        existing.setUsername("alice");
        existing.setEnabled(true);
        when(snapshots.findByKeycloakId("kc-1")).thenReturn(java.util.Optional.of(existing));
        when(snapshots.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        simpleService(provisioning, snapshots).importFromMdm("mdm-ref", java.util.List.of(payload("alice")), Map.of());

        assertThat(existing.isEnabled()).isTrue();
    }

    @Test
    void importFromMdmShouldMirrorKeycloakDisabledStateWhenSnapshotMissing() {
        AdminKeycloakUserRepository snapshots = mock(AdminKeycloakUserRepository.class);
        KeycloakUserProvisioningService provisioning = mock(KeycloakUserProvisioningService.class);
        // Keycloak 里账号已存在且为禁用（例如上次新建后本地事务回滚），本地没有快照
        when(provisioning.provision(any(PersonnelPayload.class)))
            .thenReturn(new KeycloakUserProvisioningService.ProvisionResult("kc-2", null, false, Boolean.FALSE));
        when(snapshots.findByKeycloakId("kc-2")).thenReturn(java.util.Optional.empty());
        when(snapshots.findByUsernameIgnoreCase("alice")).thenReturn(java.util.Optional.empty());
        java.util.List<AdminKeycloakUser> saved = new java.util.ArrayList<>();
        when(snapshots.save(any())).thenAnswer(invocation -> {
            saved.add(invocation.getArgument(0));
            return invocation.getArgument(0);
        });

        simpleService(provisioning, snapshots).importFromMdm("mdm-ref", java.util.List.of(payload("alice")), Map.of());

        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).isEnabled()).isFalse();
    }

    private PersonnelImportService simpleService(KeycloakUserProvisioningService provisioning, AdminKeycloakUserRepository snapshots) {
        PersonImportBatchRepository batchRepository = mock(PersonImportBatchRepository.class);
        PersonImportRecordRepository recordRepository = mock(PersonImportRecordRepository.class);
        PersonImportBatch savedBatch = new PersonImportBatch();
        savedBatch.setId(11L);
        when(batchRepository.save(any(PersonImportBatch.class))).thenAnswer((Answer<PersonImportBatch>) invocation -> {
            PersonImportBatch batch = invocation.getArgument(0);
            if (batch.getId() == null) {
                batch.setId(11L);
            }
            return batch;
        });
        when(batchRepository.getReferenceById(11L)).thenReturn(savedBatch);
        when(recordRepository.save(any(PersonImportRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));
        return new PersonnelImportService(
            batchRepository,
            recordRepository,
            mock(PersonnelExcelParser.class),
            mock(PersonnelApiClient.class),
            mock(AuditV2Service.class),
            provisioning,
            snapshots,
            new ObjectMapper(),
            new MdmGatewayProperties(),
            new NoopTransactionManager()
        );
    }

    private PersonnelPayload payloadWithDept(String account, String deptCode, String deptName) {
        return new PersonnelPayload(
            "P001",
            "EXT001",
            account,
            "Alice",
            null,
            deptCode,
            deptName,
            null,
            null,
            null,
            null,
            null,
            "ACTIVE",
            null,
            null,
            Map.of("person_security_level", "3")
        );
    }

    private PersonnelPayload payload(String account) {
        return new PersonnelPayload(
            "P001",
            "EXT001",
            account,
            "Alice",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "ACTIVE",
            null,
            null,
            Map.of("person_security_level", "3")
        );
    }

    private static final class NoopTransactionManager implements PlatformTransactionManager {

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {}

        @Override
        public void rollback(TransactionStatus status) {}
    }
}
