package com.yuzhi.dts.admin.service.audit;

import com.yuzhi.dts.admin.domain.audit.AuditEntry;
import com.yuzhi.dts.admin.repository.audit.AuditEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditIngestPersistenceService {

    private final AuditV2Service auditV2Service;
    private final AuditEntryRepository repository;

    public AuditIngestPersistenceService(AuditV2Service auditV2Service, AuditEntryRepository repository) {
        this.auditV2Service = auditV2Service;
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditEntry persistIdempotent(
        String producer,
        String eventId,
        String payloadHash,
        AuditActionRequest request
    ) {
        AuditEntry entry = auditV2Service.record(request);
        if (entry == null) {
            throw new IllegalStateException("idempotent audit event was not persisted");
        }
        entry.setIngestProducer(producer);
        entry.setIngestEventId(eventId);
        entry.setIngestPayloadHash(payloadHash);
        return repository.saveAndFlush(entry);
    }
}
