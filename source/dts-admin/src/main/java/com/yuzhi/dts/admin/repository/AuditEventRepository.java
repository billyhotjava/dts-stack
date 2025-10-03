package com.yuzhi.dts.admin.repository;

import com.yuzhi.dts.admin.domain.AuditEvent;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    Optional<AuditEvent> findTopByOrderByIdDesc();

    @Query(
        value =
            "select * from audit_event e " +
            "where coalesce(e.actor, '') ilike :actor escape '\\' " +
            "and coalesce(e.module, '') ilike :module escape '\\' " +
            "and coalesce(e.action, '') ilike :action escape '\\' " +
            "and coalesce(e.result, '') ilike :result escape '\\' " +
            "and coalesce(e.resource_type, '') ilike :resourceType escape '\\' " +
            "and coalesce(e.resource_id, '') ilike :resource escape '\\' " +
            "and coalesce(e.request_uri, '') ilike :requestUri escape '\\' " +
            "and e.occurred_at >= coalesce(:from, e.occurred_at) " +
            "and e.occurred_at <= coalesce(:to, e.occurred_at) " +
            "and coalesce(cast(e.client_ip as text), '') ilike :clientIp escape '\\' " +
            "order by e.occurred_at desc, e.id desc",
        countQuery =
            "select count(*) from audit_event e " +
            "where coalesce(e.actor, '') ilike :actor escape '\\' " +
            "and coalesce(e.module, '') ilike :module escape '\\' " +
            "and coalesce(e.action, '') ilike :action escape '\\' " +
            "and coalesce(e.result, '') ilike :result escape '\\' " +
            "and coalesce(e.resource_type, '') ilike :resourceType escape '\\' " +
            "and coalesce(e.resource_id, '') ilike :resource escape '\\' " +
            "and coalesce(e.request_uri, '') ilike :requestUri escape '\\' " +
            "and e.occurred_at >= coalesce(:from, e.occurred_at) " +
            "and e.occurred_at <= coalesce(:to, e.occurred_at) " +
            "and coalesce(cast(e.client_ip as text), '') ilike :clientIp escape '\\'",
        nativeQuery = true
    )
    Page<AuditEvent> search(
        @Param("actor") String actor,
        @Param("module") String module,
        @Param("action") String action,
        @Param("result") String result,
        @Param("resourceType") String resourceType,
        @Param("resource") String resource,
        @Param("requestUri") String requestUri,
        @Param("from") Instant from,
        @Param("to") Instant to,
        @Param("clientIp") String clientIp,
        Pageable pageable
    );

    @Modifying
    int deleteAllByOccurredAtBefore(Instant threshold);
}
