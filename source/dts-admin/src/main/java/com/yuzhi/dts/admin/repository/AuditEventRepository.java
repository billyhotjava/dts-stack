package com.yuzhi.dts.admin.repository;

import com.yuzhi.dts.admin.domain.AuditEvent;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    Optional<AuditEvent> findTopByOrderByIdDesc();

    @Query(
        "select e from AuditEvent e " +
        "where (:actor is null or lower(e.actor) like lower(concat('%', :actor, '%'))) " +
        "and (:module is null or lower(e.module) = lower(:module)) " +
        "and (:action is null or lower(e.action) like lower(concat('%', :action, '%'))) " +
        "and (:result is null or lower(e.result) = lower(:result)) " +
        "and (:resource is null or lower(e.resourceId) like lower(concat('%', :resource, '%'))) " +
        "and (:from is null or e.occurredAt >= :from) " +
        "and (:to is null or e.occurredAt <= :to) " +
        "and (:clientIp is null or e.clientIp = :clientIp)"
    )
    Page<AuditEvent> search(
        String actor,
        String module,
        String action,
        String result,
        String resource,
        Instant from,
        Instant to,
        String clientIp,
        Pageable pageable
    );

    @Modifying
    int deleteAllByOccurredAtBefore(Instant threshold);
}
