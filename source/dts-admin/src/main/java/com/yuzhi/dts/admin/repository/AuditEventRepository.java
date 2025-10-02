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
        value =
            "select * from audit_event e " +
            "where (:actor is null or e.actor ilike concat('%', :actor, '%')) " +
            "and (coalesce(:module, '') = '' or e.module ilike concat('%', :module, '%')) " +
            "and (:action is null or e.action ilike concat('%', :action, '%')) " +
            "and (coalesce(:result, '') = '' or e.result ilike concat('%', :result, '%')) " +
            "and (coalesce(:resourceType, '') = '' or e.resource_type ilike concat('%', :resourceType, '%')) " +
            "and (:resource is null or e.resource_id ilike concat('%', :resource, '%')) " +
            "and (:requestUri is null or e.request_uri ilike concat('%', :requestUri, '%')) " +
            "and e.occurred_at >= coalesce(:from, e.occurred_at) " +
            "and e.occurred_at <= coalesce(:to, e.occurred_at) " +
            "and (:clientIp is null or cast(e.client_ip as text) ilike concat('%', :clientIp, '%')) " +
            "order by e.occurred_at desc, e.id desc",
        countQuery =
            "select count(*) from audit_event e " +
            "where (:actor is null or e.actor ilike concat('%', :actor, '%')) " +
            "and (coalesce(:module, '') = '' or e.module ilike concat('%', :module, '%')) " +
            "and (:action is null or e.action ilike concat('%', :action, '%')) " +
            "and (coalesce(:result, '') = '' or e.result ilike concat('%', :result, '%')) " +
            "and (coalesce(:resourceType, '') = '' or e.resource_type ilike concat('%', :resourceType, '%')) " +
            "and (:resource is null or e.resource_id ilike concat('%', :resource, '%')) " +
            "and (:requestUri is null or e.request_uri ilike concat('%', :requestUri, '%')) " +
            "and e.occurred_at >= coalesce(:from, e.occurred_at) " +
            "and e.occurred_at <= coalesce(:to, e.occurred_at) " +
            "and (:clientIp is null or cast(e.client_ip as text) ilike concat('%', :clientIp, '%'))",
        nativeQuery = true
    )
    Page<AuditEvent> search(
        String actor,
        String module,
        String action,
        String result,
        String resourceType,
        String resource,
        String requestUri,
        Instant from,
        Instant to,
        String clientIp,
        Pageable pageable
    );

    @Modifying
    int deleteAllByOccurredAtBefore(Instant threshold);
}
