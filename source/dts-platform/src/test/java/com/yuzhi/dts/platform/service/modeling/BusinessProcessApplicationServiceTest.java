package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.catalog.ArchitectureDictionaryWriteGuard;
import com.yuzhi.dts.platform.service.modeling.BusinessProcessApplicationService.AuditSurface;
import com.yuzhi.dts.platform.service.sprint64.Sprint64GovernanceService;
import com.yuzhi.dts.platform.service.sprint64.Sprint64GovernanceService.BusinessProcessDto;
import com.yuzhi.dts.platform.service.sprint64.Sprint64GovernanceService.BusinessProcessRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BusinessProcessApplicationServiceTest {

    private final Sprint64GovernanceService ledger = mock(Sprint64GovernanceService.class);
    private final ArchitectureDictionaryWriteGuard writeGuard = mock(ArchitectureDictionaryWriteGuard.class);
    private final AuditService auditService = mock(AuditService.class);
    private final BusinessProcessApplicationService service = new BusinessProcessApplicationService(
        ledger,
        writeGuard,
        auditService
    );
    private final UUID domainId = UUID.randomUUID();

    @Test
    void canonicalCreateUsesSharedLedgerGuardAndStrictAudit() {
        BusinessProcessRequest request = new BusinessProcessRequest("budget", "预算管理", "预算执行过程");
        BusinessProcessDto process = process("BUDGET");
        when(ledger.createProcess(domainId, request)).thenReturn(process);

        BusinessProcessDto created = service.create(domainId, request, AuditSurface.CANONICAL);

        assertThat(created).isEqualTo(process);
        verify(writeGuard).requireWriteAccess();
        verify(ledger).createProcess(domainId, request);
        verify(auditService).auditActionStrict(
            eq("MODELING_BUSINESS_PROCESS_CREATE"),
            eq(AuditStage.SUCCESS),
            eq("BUDGET"),
            any()
        );
    }

    @Test
    void legacyDeleteKeepsCompatibilityAuditIdentifier() {
        service.delete(domainId, "budget", AuditSurface.LEGACY);

        verify(writeGuard).requireWriteAccess();
        verify(ledger).deleteProcess(domainId, "budget");
        verify(auditService).auditActionStrict(
            eq("SPRINT64_PROCESS_DELETE"),
            eq(AuditStage.SUCCESS),
            eq("budget"),
            any()
        );
    }

    @Test
    void readsUseTheSameLedgerAndSurfaceAuditIdentifier() {
        when(ledger.listProcesses(domainId)).thenReturn(List.of(process("BUDGET")));

        List<BusinessProcessDto> result = service.list(domainId, AuditSurface.CANONICAL);

        assertThat(result).hasSize(1);
        verify(ledger).listProcesses(domainId);
        verify(auditService).auditAction(
            eq("MODELING_BUSINESS_PROCESS_LIST"),
            eq(AuditStage.SUCCESS),
            eq(domainId.toString()),
            any()
        );
    }

    private BusinessProcessDto process(String processId) {
        Instant now = Instant.parse("2026-08-09T12:00:00Z");
        return new BusinessProcessDto(
            UUID.randomUUID(),
            1,
            processId,
            domainId,
            "预算管理",
            "预算执行过程",
            "MANUAL",
            null,
            null,
            true,
            now,
            now
        );
    }
}
