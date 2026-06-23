package com.yuzhi.dts.admin.service.personnel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.admin.config.MdmGatewayProperties;
import com.yuzhi.dts.admin.domain.PersonImportBatch;
import com.yuzhi.dts.admin.domain.PersonImportRecord;
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
        when(provisioningService.provision(any(PersonnelPayload.class))).thenReturn("kc-1");
        when(adminKeycloakUserRepository.findByKeycloakId("kc-1")).thenReturn(java.util.Optional.empty());
        when(adminKeycloakUserRepository.findByUsernameIgnoreCase("alice")).thenReturn(java.util.Optional.empty());
        when(adminKeycloakUserRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(recordRepository.save(any(PersonImportRecord.class)))
            .thenThrow(new DataIntegrityViolationException("duplicate keycloak user id"))
            .thenAnswer(invocation -> invocation.getArgument(0));

        PersonnelImportService service = new PersonnelImportService(
            batchRepository,
            recordRepository,
            mock(PersonnelProfileService.class),
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
