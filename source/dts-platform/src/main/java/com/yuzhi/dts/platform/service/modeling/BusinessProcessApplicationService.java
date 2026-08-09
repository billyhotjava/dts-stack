package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.catalog.ArchitectureDictionaryWriteGuard;
import com.yuzhi.dts.platform.service.sprint64.Sprint64GovernanceService;
import com.yuzhi.dts.platform.service.sprint64.Sprint64GovernanceService.BusinessProcessDto;
import com.yuzhi.dts.platform.service.sprint64.Sprint64GovernanceService.BusinessProcessRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Canonical application boundary for the platform business-process dictionary.
 *
 * <p>The existing Sprint-64 service remains the only ledger implementation. Canonical and
 * compatibility REST surfaces delegate here so authorization and strict write auditing cannot
 * drift, while each surface keeps its historical audit identifier.</p>
 */
@Service
public class BusinessProcessApplicationService {

    private final Sprint64GovernanceService ledger;
    private final ArchitectureDictionaryWriteGuard writeGuard;
    private final AuditService auditService;

    public BusinessProcessApplicationService(
        Sprint64GovernanceService ledger,
        ArchitectureDictionaryWriteGuard writeGuard,
        AuditService auditService
    ) {
        this.ledger = ledger;
        this.writeGuard = writeGuard;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<BusinessProcessDto> list(UUID domainId, AuditSurface surface) {
        List<BusinessProcessDto> data = ledger.listProcesses(domainId);
        auditService.auditAction(
            surface.action("LIST"),
            AuditStage.SUCCESS,
            domainId.toString(),
            Map.of("count", data.size())
        );
        return data;
    }

    @Transactional
    public BusinessProcessDto create(
        UUID domainId,
        BusinessProcessRequest request,
        AuditSurface surface
    ) {
        writeGuard.requireWriteAccess();
        BusinessProcessDto data = ledger.createProcess(domainId, request);
        auditService.auditActionStrict(
            surface.action("CREATE"),
            AuditStage.SUCCESS,
            data.processId(),
            Map.of("domainId", domainId.toString())
        );
        return data;
    }

    @Transactional
    public void delete(UUID domainId, String processId, AuditSurface surface) {
        writeGuard.requireWriteAccess();
        ledger.deleteProcess(domainId, processId);
        auditService.auditActionStrict(
            surface.action("DELETE"),
            AuditStage.SUCCESS,
            processId,
            Map.of("domainId", domainId.toString())
        );
    }

    public enum AuditSurface {
        CANONICAL("MODELING_BUSINESS_PROCESS_"),
        LEGACY("SPRINT64_PROCESS_");

        private final String prefix;

        AuditSurface(String prefix) {
            this.prefix = prefix;
        }

        private String action(String operation) {
            return prefix + operation;
        }
    }
}
