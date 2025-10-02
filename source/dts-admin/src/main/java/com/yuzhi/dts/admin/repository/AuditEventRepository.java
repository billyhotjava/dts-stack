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
            "where (:actor is null or e.actor ilike :actor escape '\\') " +
            "and (:module is null or e.module ilike :module escape '\\') " +
            "and (:action is null or e.action ilike :action escape '\\') " +
            "and (:result is null or e.result ilike :result escape '\\') " +
            "and (:resourceType is null or e.resource_type ilike :resourceType escape '\\') " +
            "and (:resource is null or e.resource_id ilike :resource escape '\\') " +
            "and (:requestUri is null or e.request_uri ilike :requestUri escape '\\') " +
            "and (:from is null or e.occurred_at >= :from) " +
            "and (:to is null or e.occurred_at <= :to) " +
            "and (:clientIp is null or cast(e.client_ip as text) ilike :clientIp escape '\\') " +
            "order by e.occurred_at desc, e.id desc",
        countQuery =
            "select count(*) from audit_event e " +
            "where (:actor is null or e.actor ilike :actor escape '\\') " +
            "and (:module is null or e.module ilike :module escape '\\') " +
            "and (:action is null or e.action ilike :action escape '\\') " +
            "and (:result is null or e.result ilike :result escape '\\') " +
            "and (:resourceType is null or e.resource_type ilike :resourceType escape '\\') " +
            "and (:resource is null or e.resource_id ilike :resource escape '\\') " +
            "and (:requestUri is null or e.request_uri ilike :requestUri escape '\\') " +
            "and (:from is null or e.occurred_at >= :from) " +
            "and (:to is null or e.occurred_at <= :to) " +
            "and (:clientIp is null or cast(e.client_ip as text) ilike :clientIp escape '\\')",
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
