package com.yuzhi.dts.admin.repository;

import com.yuzhi.dts.admin.domain.AuditEvent;
import java.time.Instant;
import java.util.List;
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
        value = "select distinct lower(e.module) as module from audit_event e where e.module is not null and trim(e.module) <> '' order by module",
        nativeQuery = true
    )
    List<String> findDistinctModules();

    @Query(
        value =
            "select * from audit_event e " +
            "where coalesce(e.actor, '') ilike :actor escape '\\' " +
            "and coalesce(e.module, '') ilike :module escape '\\' " +
            "and coalesce(e.action, '') ilike :action escape '\\' " +
            "and coalesce(e.source_system, '') ilike :sourceSystem escape '\\' " +
            "and coalesce(e.source_system, '') ilike :sourceSystem escape '\\' " +
            "and coalesce(e.event_type, '') ilike :eventType escape '\\' " +
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
            "and coalesce(e.source_system, '') ilike :sourceSystem escape '\\' " +
            "and coalesce(e.source_system, '') ilike :sourceSystem escape '\\' " +
            "and coalesce(e.event_type, '') ilike :eventType escape '\\' " +
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
        @Param("sourceSystem") String sourceSystem,
        @Param("eventType") String eventType,
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

    @Query(
        value =
            "select * from audit_event e " +
            "where coalesce(e.actor, '') ilike :actor escape '\\' " +
            "and coalesce(e.module, '') ilike :module escape '\\' " +
            "and coalesce(e.action, '') ilike :action escape '\\' " +
            "and coalesce(e.source_system, '') ilike :sourceSystem escape '\\' " +
            "and coalesce(e.event_type, '') ilike :eventType escape '\\' " +
            "and coalesce(e.result, '') ilike :result escape '\\' " +
            "and coalesce(e.resource_type, '') ilike :resourceType escape '\\' " +
            "and coalesce(e.resource_id, '') ilike :resource escape '\\' " +
            "and coalesce(e.request_uri, '') ilike :requestUri escape '\\' " +
            "and e.occurred_at >= coalesce(:from, e.occurred_at) " +
            "and e.occurred_at <= coalesce(:to, e.occurred_at) " +
            "and coalesce(cast(e.client_ip as text), '') ilike :clientIp escape '\\' " +
            "and lower(coalesce(e.actor, '')) in (:allowedActors) " +
            "order by e.occurred_at desc, e.id desc",
        countQuery =
            "select count(*) from audit_event e " +
            "where coalesce(e.actor, '') ilike :actor escape '\\' " +
            "and coalesce(e.module, '') ilike :module escape '\\' " +
            "and coalesce(e.action, '') ilike :action escape '\\' " +
            "and coalesce(e.source_system, '') ilike :sourceSystem escape '\\' " +
            "and coalesce(e.event_type, '') ilike :eventType escape '\\' " +
            "and coalesce(e.result, '') ilike :result escape '\\' " +
            "and coalesce(e.resource_type, '') ilike :resourceType escape '\\' " +
            "and coalesce(e.resource_id, '') ilike :resource escape '\\' " +
            "and coalesce(e.request_uri, '') ilike :requestUri escape '\\' " +
            "and e.occurred_at >= coalesce(:from, e.occurred_at) " +
            "and e.occurred_at <= coalesce(:to, e.occurred_at) " +
            "and coalesce(cast(e.client_ip as text), '') ilike :clientIp escape '\\' " +
            "and lower(coalesce(e.actor, '')) in (:allowedActors)",
        nativeQuery = true
    )
    Page<AuditEvent> searchAllowedActors(
        @Param("actor") String actor,
        @Param("module") String module,
        @Param("action") String action,
        @Param("sourceSystem") String sourceSystem,
        @Param("eventType") String eventType,
        @Param("result") String result,
        @Param("resourceType") String resourceType,
        @Param("resource") String resource,
        @Param("requestUri") String requestUri,
        @Param("from") Instant from,
        @Param("to") Instant to,
        @Param("clientIp") String clientIp,
        @Param("allowedActors") java.util.List<String> allowedActors,
        Pageable pageable
    );

    @Query(
        value =
            "select * from audit_event e " +
            "where coalesce(e.actor, '') ilike :actor escape '\\' " +
            "and coalesce(e.module, '') ilike :module escape '\\' " +
            "and coalesce(e.action, '') ilike :action escape '\\' " +
            "and coalesce(e.source_system, '') ilike :sourceSystem escape '\\' " +
            "and coalesce(e.event_type, '') ilike :eventType escape '\\' " +
            "and coalesce(e.result, '') ilike :result escape '\\' " +
            "and coalesce(e.resource_type, '') ilike :resourceType escape '\\' " +
            "and coalesce(e.resource_id, '') ilike :resource escape '\\' " +
            "and coalesce(e.request_uri, '') ilike :requestUri escape '\\' " +
            "and e.occurred_at >= coalesce(:from, e.occurred_at) " +
            "and e.occurred_at <= coalesce(:to, e.occurred_at) " +
            "and coalesce(cast(e.client_ip as text), '') ilike :clientIp escape '\\' " +
            "and coalesce(e.actor_role, '') in (:roles) " +
            "order by e.occurred_at desc, e.id desc",
        countQuery =
            "select count(*) from audit_event e " +
            "where coalesce(e.actor, '') ilike :actor escape '\\' " +
            "and coalesce(e.module, '') ilike :module escape '\\' " +
            "and coalesce(e.action, '') ilike :action escape '\\' " +
            "and coalesce(e.source_system, '') ilike :sourceSystem escape '\\' " +
            "and coalesce(e.event_type, '') ilike :eventType escape '\\' " +
            "and coalesce(e.result, '') ilike :result escape '\\' " +
            "and coalesce(e.resource_type, '') ilike :resourceType escape '\\' " +
            "and coalesce(e.resource_id, '') ilike :resource escape '\\' " +
            "and coalesce(e.request_uri, '') ilike :requestUri escape '\\' " +
            "and e.occurred_at >= coalesce(:from, e.occurred_at) " +
            "and e.occurred_at <= coalesce(:to, e.occurred_at) " +
            "and coalesce(cast(e.client_ip as text), '') ilike :clientIp escape '\\' " +
            "and coalesce(e.actor_role, '') in (:roles)",
        nativeQuery = true
    )
    Page<AuditEvent> searchAllowedRoles(
        @Param("actor") String actor,
        @Param("module") String module,
        @Param("action") String action,
        @Param("sourceSystem") String sourceSystem,
        @Param("eventType") String eventType,
        @Param("result") String result,
        @Param("resourceType") String resourceType,
        @Param("resource") String resource,
        @Param("requestUri") String requestUri,
        @Param("from") Instant from,
        @Param("to") Instant to,
        @Param("clientIp") String clientIp,
        @Param("roles") java.util.List<String> roles,
        Pageable pageable
    );

    @Query(
        value =
            "select * from audit_event e " +
            "where coalesce(e.actor, '') ilike :actor escape '\\' " +
            "and coalesce(e.module, '') ilike :module escape '\\' " +
            "and coalesce(e.action, '') ilike :action escape '\\' " +
            "and coalesce(e.source_system, '') ilike :sourceSystem escape '\\' " +
            "and coalesce(e.event_type, '') ilike :eventType escape '\\' " +
            "and coalesce(e.result, '') ilike :result escape '\\' " +
            "and coalesce(e.resource_type, '') ilike :resourceType escape '\\' " +
            "and coalesce(e.resource_id, '') ilike :resource escape '\\' " +
            "and coalesce(e.request_uri, '') ilike :requestUri escape '\\' " +
            "and e.occurred_at >= coalesce(:from, e.occurred_at) " +
            "and e.occurred_at <= coalesce(:to, e.occurred_at) " +
            "and coalesce(cast(e.client_ip as text), '') ilike :clientIp escape '\\' " +
            "and coalesce(e.actor_role, '') <> :excluded " +
            "order by e.occurred_at desc, e.id desc",
        countQuery =
            "select count(*) from audit_event e " +
            "where coalesce(e.actor, '') ilike :actor escape '\\' " +
            "and coalesce(e.module, '') ilike :module escape '\\' " +
            "and coalesce(e.action, '') ilike :action escape '\\' " +
            "and coalesce(e.source_system, '') ilike :sourceSystem escape '\\' " +
            "and coalesce(e.event_type, '') ilike :eventType escape '\\' " +
            "and coalesce(e.result, '') ilike :result escape '\\' " +
            "and coalesce(e.resource_type, '') ilike :resourceType escape '\\' " +
            "and coalesce(e.resource_id, '') ilike :resource escape '\\' " +
            "and coalesce(e.request_uri, '') ilike :requestUri escape '\\' " +
            "and e.occurred_at >= coalesce(:from, e.occurred_at) " +
            "and e.occurred_at <= coalesce(:to, e.occurred_at) " +
            "and coalesce(cast(e.client_ip as text), '') ilike :clientIp escape '\\' " +
            "and coalesce(e.actor_role, '') <> :excluded",
        nativeQuery = true
    )
    Page<AuditEvent> searchExcludeRole(
        @Param("actor") String actor,
        @Param("module") String module,
        @Param("action") String action,
        @Param("sourceSystem") String sourceSystem,
        @Param("eventType") String eventType,
        @Param("result") String result,
        @Param("resourceType") String resourceType,
        @Param("resource") String resource,
        @Param("requestUri") String requestUri,
        @Param("from") Instant from,
        @Param("to") Instant to,
        @Param("clientIp") String clientIp,
        @Param("excluded") String excluded,
        Pageable pageable
    );

    @Query(
        value =
            "select * from audit_event e " +
            "where coalesce(e.actor, '') ilike :actor escape '\\' " +
            "and coalesce(e.module, '') ilike :module escape '\\' " +
            "and coalesce(e.action, '') ilike :action escape '\\' " +
            "and coalesce(e.source_system, '') ilike :sourceSystem escape '\\' " +
            "and coalesce(e.event_type, '') ilike :eventType escape '\\' " +
            "and coalesce(e.result, '') ilike :result escape '\\' " +
            "and coalesce(e.resource_type, '') ilike :resourceType escape '\\' " +
            "and coalesce(e.resource_id, '') ilike :resource escape '\\' " +
            "and coalesce(e.request_uri, '') ilike :requestUri escape '\\' " +
            "and e.occurred_at >= coalesce(:from, e.occurred_at) " +
            "and e.occurred_at <= coalesce(:to, e.occurred_at) " +
            "and coalesce(cast(e.client_ip as text), '') ilike :clientIp escape '\\' " +
            "and coalesce(e.actor_role, '') not in (:excluded) " +
            "order by e.occurred_at desc, e.id desc",
        countQuery =
            "select count(*) from audit_event e " +
            "where coalesce(e.actor, '') ilike :actor escape '\\' " +
            "and coalesce(e.module, '') ilike :module escape '\\' " +
            "and coalesce(e.action, '') ilike :action escape '\\' " +
            "and coalesce(e.source_system, '') ilike :sourceSystem escape '\\' " +
            "and coalesce(e.event_type, '') ilike :eventType escape '\\' " +
            "and coalesce(e.result, '') ilike :result escape '\\' " +
            "and coalesce(e.resource_type, '') ilike :resourceType escape '\\' " +
            "and coalesce(e.resource_id, '') ilike :resource escape '\\' " +
            "and coalesce(e.request_uri, '') ilike :requestUri escape '\\' " +
            "and e.occurred_at >= coalesce(:from, e.occurred_at) " +
            "and e.occurred_at <= coalesce(:to, e.occurred_at) " +
            "and coalesce(cast(e.client_ip as text), '') ilike :clientIp escape '\\' " +
            "and coalesce(e.actor_role, '') not in (:excluded)",
        nativeQuery = true
    )
    Page<AuditEvent> searchExcludeRoles(
        @Param("actor") String actor,
        @Param("module") String module,
        @Param("action") String action,
        @Param("sourceSystem") String sourceSystem,
        @Param("eventType") String eventType,
        @Param("result") String result,
        @Param("resourceType") String resourceType,
        @Param("resource") String resource,
        @Param("requestUri") String requestUri,
        @Param("from") Instant from,
        @Param("to") Instant to,
        @Param("clientIp") String clientIp,
        @Param("excluded") java.util.List<String> excluded,
        Pageable pageable
    );

    @Query(
        value =
            "select * from audit_event e " +
            "where coalesce(e.actor, '') ilike :actor escape '\\' " +
            "and coalesce(e.module, '') ilike :module escape '\\' " +
            "and coalesce(e.action, '') ilike :action escape '\\' " +
            "and coalesce(e.source_system, '') ilike :sourceSystem escape '\\' " +
            "and coalesce(e.event_type, '') ilike :eventType escape '\\' " +
            "and coalesce(e.result, '') ilike :result escape '\\' " +
            "and coalesce(e.resource_type, '') ilike :resourceType escape '\\' " +
            "and coalesce(e.resource_id, '') ilike :resource escape '\\' " +
            "and coalesce(e.request_uri, '') ilike :requestUri escape '\\' " +
            "and e.occurred_at >= coalesce(:from, e.occurred_at) " +
            "and e.occurred_at <= coalesce(:to, e.occurred_at) " +
            "and coalesce(cast(e.client_ip as text), '') ilike :clientIp escape '\\' " +
            "and coalesce(e.actor_role, '') in (:roles) " +
            "and lower(coalesce(e.actor, '')) not in (:excludedActors) " +
            "order by e.occurred_at desc, e.id desc",
        countQuery =
            "select count(*) from audit_event e " +
            "where coalesce(e.actor, '') ilike :actor escape '\\' " +
            "and coalesce(e.module, '') ilike :module escape '\\' " +
            "and coalesce(e.action, '') ilike :action escape '\\' " +
            "and coalesce(e.source_system, '') ilike :sourceSystem escape '\\' " +
            "and coalesce(e.event_type, '') ilike :eventType escape '\\' " +
            "and coalesce(e.result, '') ilike :result escape '\\' " +
            "and coalesce(e.resource_type, '') ilike :resourceType escape '\\' " +
            "and coalesce(e.resource_id, '') ilike :resource escape '\\' " +
            "and coalesce(e.request_uri, '') ilike :requestUri escape '\\' " +
            "and e.occurred_at >= coalesce(:from, e.occurred_at) " +
            "and e.occurred_at <= coalesce(:to, e.occurred_at) " +
            "and coalesce(cast(e.client_ip as text), '') ilike :clientIp escape '\\' " +
            "and coalesce(e.actor_role, '') in (:roles) " +
            "and lower(coalesce(e.actor, '')) not in (:excludedActors)",
        nativeQuery = true
    )
    Page<AuditEvent> searchAllowedRolesExcludeActors(
        @Param("actor") String actor,
        @Param("module") String module,
        @Param("action") String action,
        @Param("sourceSystem") String sourceSystem,
        @Param("eventType") String eventType,
        @Param("result") String result,
        @Param("resourceType") String resourceType,
        @Param("resource") String resource,
        @Param("requestUri") String requestUri,
        @Param("from") Instant from,
        @Param("to") Instant to,
        @Param("clientIp") String clientIp,
        @Param("roles") java.util.List<String> roles,
        @Param("excludedActors") java.util.List<String> excludedActors,
        Pageable pageable
    );

    @Query(
        value =
            "select * from audit_event e " +
            "where coalesce(e.actor, '') ilike :actor escape '\\' " +
            "and coalesce(e.module, '') ilike :module escape '\\' " +
            "and coalesce(e.action, '') ilike :action escape '\\' " +
            "and coalesce(e.source_system, '') ilike :sourceSystem escape '\\' " +
            "and coalesce(e.event_type, '') ilike :eventType escape '\\' " +
            "and coalesce(e.result, '') ilike :result escape '\\' " +
            "and coalesce(e.resource_type, '') ilike :resourceType escape '\\' " +
            "and coalesce(e.resource_id, '') ilike :resource escape '\\' " +
            "and coalesce(e.request_uri, '') ilike :requestUri escape '\\' " +
            "and e.occurred_at >= coalesce(:from, e.occurred_at) " +
            "and e.occurred_at <= coalesce(:to, e.occurred_at) " +
            "and coalesce(cast(e.client_ip as text), '') ilike :clientIp escape '\\' " +
            "and lower(coalesce(e.actor, '')) not in (:excludedActors) " +
            "order by e.occurred_at desc, e.id desc",
        countQuery =
            "select count(*) from audit_event e " +
            "where coalesce(e.actor, '') ilike :actor escape '\\' " +
            "and coalesce(e.module, '') ilike :module escape '\\' " +
            "and coalesce(e.action, '') ilike :action escape '\\' " +
            "and coalesce(e.source_system, '') ilike :sourceSystem escape '\\' " +
            "and coalesce(e.event_type, '') ilike :eventType escape '\\' " +
            "and coalesce(e.result, '') ilike :result escape '\\' " +
            "and coalesce(e.resource_type, '') ilike :resourceType escape '\\' " +
            "and coalesce(e.resource_id, '') ilike :resource escape '\\' " +
            "and coalesce(e.request_uri, '') ilike :requestUri escape '\\' " +
            "and e.occurred_at >= coalesce(:from, e.occurred_at) " +
            "and e.occurred_at <= coalesce(:to, e.occurred_at) " +
            "and coalesce(cast(e.client_ip as text), '') ilike :clientIp escape '\\' " +
            "and lower(coalesce(e.actor, '')) not in (:excludedActors)",
        nativeQuery = true
    )
    Page<AuditEvent> searchExcludeActors(
        @Param("actor") String actor,
        @Param("module") String module,
        @Param("action") String action,
        @Param("sourceSystem") String sourceSystem,
        @Param("eventType") String eventType,
        @Param("result") String result,
        @Param("resourceType") String resourceType,
        @Param("resource") String resource,
        @Param("requestUri") String requestUri,
        @Param("from") Instant from,
        @Param("to") Instant to,
        @Param("clientIp") String clientIp,
        @Param("excludedActors") java.util.List<String> excludedActors,
        Pageable pageable
    );

    @Query(
        value =
            "select * from audit_event e " +
            "where coalesce(e.actor, '') ilike :actor escape '\\' " +
            "and coalesce(e.module, '') ilike :module escape '\\' " +
            "and coalesce(e.action, '') ilike :action escape '\\' " +
            "and coalesce(e.source_system, '') ilike :sourceSystem escape '\\' " +
            "and coalesce(e.event_type, '') ilike :eventType escape '\\' " +
            "and coalesce(e.result, '') ilike :result escape '\\' " +
            "and coalesce(e.resource_type, '') ilike :resourceType escape '\\' " +
            "and coalesce(e.resource_id, '') ilike :resource escape '\\' " +
            "and coalesce(e.request_uri, '') ilike :requestUri escape '\\' " +
            "and e.occurred_at >= coalesce(:from, e.occurred_at) " +
            "and e.occurred_at <= coalesce(:to, e.occurred_at) " +
            "and coalesce(cast(e.client_ip as text), '') ilike :clientIp escape '\\' " +
            "and coalesce(e.actor_role, '') not in (:excludedRoles) " +
            "and lower(coalesce(e.actor, '')) not in (:excludedActors) " +
            "order by e.occurred_at desc, e.id desc",
        countQuery =
            "select count(*) from audit_event e " +
            "where coalesce(e.actor, '') ilike :actor escape '\\' " +
            "and coalesce(e.module, '') ilike :module escape '\\' " +
            "and coalesce(e.action, '') ilike :action escape '\\' " +
            "and coalesce(e.source_system, '') ilike :sourceSystem escape '\\' " +
            "and coalesce(e.event_type, '') ilike :eventType escape '\\' " +
            "and coalesce(e.result, '') ilike :result escape '\\' " +
            "and coalesce(e.resource_type, '') ilike :resourceType escape '\\' " +
            "and coalesce(e.resource_id, '') ilike :resource escape '\\' " +
            "and coalesce(e.request_uri, '') ilike :requestUri escape '\\' " +
            "and e.occurred_at >= coalesce(:from, e.occurred_at) " +
            "and e.occurred_at <= coalesce(:to, e.occurred_at) " +
            "and coalesce(cast(e.client_ip as text), '') ilike :clientIp escape '\\' " +
            "and coalesce(e.actor_role, '') not in (:excludedRoles) " +
            "and lower(coalesce(e.actor, '')) not in (:excludedActors)",
        nativeQuery = true
    )
    Page<AuditEvent> searchExcludeRolesExcludeActors(
        @Param("actor") String actor,
        @Param("module") String module,
        @Param("action") String action,
        @Param("sourceSystem") String sourceSystem,
        @Param("eventType") String eventType,
        @Param("result") String result,
        @Param("resourceType") String resourceType,
        @Param("resource") String resource,
        @Param("requestUri") String requestUri,
        @Param("from") Instant from,
        @Param("to") Instant to,
        @Param("clientIp") String clientIp,
        @Param("excludedRoles") java.util.List<String> excludedRoles,
        @Param("excludedActors") java.util.List<String> excludedActors,
        Pageable pageable
    );
}
