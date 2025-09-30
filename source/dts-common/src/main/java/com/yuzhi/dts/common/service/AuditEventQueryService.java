package com.yuzhi.dts.common.service;

import com.yuzhi.dts.common.domain.AuditEvent;
import com.yuzhi.dts.common.repository.AuditEventRepository;
import com.yuzhi.dts.common.service.dto.AuditEventDTO;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import java.util.Optional;

@Service
public class AuditEventQueryService {

    private final AuditEventRepository repository;

    public AuditEventQueryService(AuditEventRepository repository) {
        this.repository = repository;
    }

    public Page<AuditEventDTO> findByCriteria(AuditEventCriteria criteria, Pageable pageable) {
        Specification<AuditEvent> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (criteria.getActor() != null) {
                predicates.add(cb.equal(root.get("actor"), criteria.getActor()));
            }
            if (criteria.getAction() != null) {
                predicates.add(cb.equal(root.get("action"), criteria.getAction()));
            }
            if (criteria.getTargetKind() != null) {
                predicates.add(cb.equal(root.get("targetKind"), criteria.getTargetKind()));
            }
            if (criteria.getTargetRefPrefix() != null) {
                predicates.add(cb.like(root.get("targetRef"), criteria.getTargetRefPrefix() + "%"));
            }
            if (criteria.getFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), criteria.getFrom()));
            }
            if (criteria.getTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), criteria.getTo()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return repository.findAll(spec, pageable).map(this::toDto);
    }

    public Optional<AuditEventDTO> findOne(Long id) {
        return repository.findById(id).map(this::toDto);
    }

    public AuditEventDTO toDto(AuditEvent e) {
        AuditEventDTO dto = new AuditEventDTO();
        dto.setId(e.getId());
        dto.setActor(e.getActor());
        dto.setAction(e.getAction());
        dto.setTargetRef(e.getTargetRef());
        dto.setTargetKind(e.getTargetKind());
        dto.setCreatedAt(e.getCreatedAt());
        return dto;
    }
}
