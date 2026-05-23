package com.yuzhi.dts.admin.service.audit;

import com.yuzhi.dts.admin.domain.audit.AuditEntry;
import com.yuzhi.dts.admin.domain.audit.AuditEntryTarget;
import com.yuzhi.dts.admin.domain.audit.AuditActionCatalogEntry;
import com.yuzhi.dts.admin.domain.audit.AuditModuleCatalog;
import com.yuzhi.dts.admin.repository.audit.AuditActionCatalogRepository;
import com.yuzhi.dts.admin.repository.audit.AuditEntryRepository;
import com.yuzhi.dts.admin.repository.audit.AuditModuleCatalogRepository;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuditEntryQueryService {

    private static final Logger log = LoggerFactory.getLogger(AuditEntryQueryService.class);

    private final AuditEntryRepository repository;
    private final AuditResourceDictionaryService dictionaryService;
    private final AuditModuleCatalogRepository moduleCatalogRepository;
    private final AuditActionCatalogRepository actionCatalogRepository;

    public AuditEntryQueryService(
        AuditEntryRepository repository,
        AuditResourceDictionaryService dictionaryService,
        AuditModuleCatalogRepository moduleCatalogRepository,
        AuditActionCatalogRepository actionCatalogRepository
    ) {
        this.repository = repository;
        this.dictionaryService = dictionaryService;
        this.moduleCatalogRepository = moduleCatalogRepository;
        this.actionCatalogRepository = actionCatalogRepository;
    }

    public Page<AuditEntryView> search(AuditSearchCriteria criteria, Pageable pageable) {
        Specification<AuditEntry> spec = buildSpecification(criteria);
        Page<AuditEntry> page = repository.findAll(spec, pageable);
        return page.map(entry -> AuditEntryView.from(entry, false));
    }

    public Optional<AuditEntryView> findById(Long id, boolean includeDetails) {
        if (id == null) {
            return Optional.empty();
        }
        Optional<AuditEntry> entry = includeDetails ? repository.findDetailedById(id) : repository.findById(id);
        return entry.map(e -> AuditEntryView.from(e, includeDetails));
    }

    @Transactional
    public long purgeAll() {
        long count = repository.count();
        repository.deleteAllInBatch();
        return count;
    }

    public List<ModuleOption> listModuleOptions() {
        List<ModuleOption> options = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (AuditModuleCatalog module : moduleCatalogRepository.findAllByEnabledTrueOrderByOrderValueAscModuleKeyAsc()) {
            String normalized = normalizeKey(module.getModuleKey());
            if (!hasText(normalized) || !seen.add(normalized)) {
                continue;
            }
            options.add(new ModuleOption(normalized, firstNonBlank(module.getModuleName(), resolveModuleLabel(normalized))));
        }
        List<String> keys = repository.findDistinctModuleKeys();
        if (keys == null) {
            keys = List.of();
        }
        for (String raw : keys) {
            if (!hasText(raw)) {
                continue;
            }
            String normalized = normalizeKey(raw);
            if (!seen.add(normalized)) {
                continue;
            }
            options.add(new ModuleOption(normalized, resolveModuleLabel(normalized)));
        }
        if (options.isEmpty()) {
            options.add(new ModuleOption("general", resolveModuleLabel("general")));
        }
        return List.copyOf(options);
    }

    public List<AuditGroupOption> listGroupOptions() {
        LinkedHashMap<String, AuditGroupOption> grouped = new LinkedHashMap<>();
        for (AuditActionCatalogEntry entry : actionCatalogRepository.findAllByEnabledTrueOrderByModuleKeyAscActionCodeAsc()) {
            String moduleKey = normalizeKey(entry.getModuleKey());
            if (!hasText(moduleKey)) {
                continue;
            }
            grouped.putIfAbsent(
                moduleKey,
                new AuditGroupOption(
                    moduleKey,
                    firstNonBlank(entry.getModuleName(), resolveModuleLabel(moduleKey)),
                    firstNonBlank(entry.getModuleName(), moduleKey),
                    firstNonBlank(entry.getModuleName(), moduleKey),
                    normalizeKey(entry.getSourceSystem())
                )
            );
        }
        return List.copyOf(grouped.values());
    }

    public List<AuditCategoryOption> listCategoryOptions() {
        LinkedHashMap<String, AuditCategoryOption> grouped = new LinkedHashMap<>();
        for (AuditActionCatalogEntry entry : actionCatalogRepository.findAllByEnabledTrueOrderByModuleKeyAscActionCodeAsc()) {
            String moduleKey = normalizeKey(entry.getModuleKey());
            if (!hasText(moduleKey)) {
                continue;
            }
            String resourceType = normalizeKey(firstNonBlank(entry.getResourceType(), entry.getModuleKey()));
            String entryKey = moduleKey + ":" + resourceType;
            grouped.putIfAbsent(
                entryKey,
                new AuditCategoryOption(
                    moduleKey,
                    firstNonBlank(entry.getModuleName(), resolveModuleLabel(moduleKey)),
                    entryKey,
                    firstNonBlank(entry.getResourceType(), entry.getModuleName(), moduleKey)
                )
            );
        }
        return List.copyOf(grouped.values());
    }

    private String resolveModuleLabel(String key) {
        if (!hasText(key)) {
            return "通用";
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        return dictionaryService.resolveCategory(normalized).orElseGet(() ->
            dictionaryService.resolveLabel(normalized).orElseGet(() -> switch (normalized) {
                case "admin" -> "系统管理";
                case "approval" -> "审批管理";
                case "catalog" -> "数据资产";
                case "platform" -> "业务平台";
                default -> normalized;
            })
        );
    }

    private String normalizeKey(String value) {
        return hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : null;
    }

    private String firstNonBlank(String... candidates) {
        if (candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            if (hasText(candidate)) {
                return candidate.trim();
            }
        }
        return null;
    }

    private Specification<AuditEntry> buildSpecification(AuditSearchCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (criteria == null) {
                return cb.and(predicates.toArray(Predicate[]::new));
            }

            if (!query.getResultType().equals(Long.class)) {
                query.distinct(true);
            }

            if (hasText(criteria.actor())) {
                String pattern = likePattern(criteria.actor());
                Predicate byId = cb.like(cb.lower(root.get("actorId")), pattern);
                Predicate byName = cb.like(cb.lower(root.get("actorName")), pattern);
                predicates.add(cb.or(byId, byName));
            }
            if (hasText(criteria.module())) {
                predicates.add(cb.like(cb.lower(root.get("moduleKey")), likePattern(criteria.module())));
            }
            if (hasText(criteria.operationKind())) {
                predicates.add(cb.like(cb.lower(root.get("operationKind")), likePattern(criteria.operationKind())));
            }
            if (hasText(criteria.action())) {
                Predicate byOpName = cb.like(cb.lower(root.get("operationName")), likePattern(criteria.action()));
                Predicate byOpCode = cb.like(cb.lower(root.get("operationCode")), likePattern(criteria.action()));
                Predicate bySummary = cb.like(cb.lower(root.get("summary")), likePattern(criteria.action()));
                predicates.add(cb.or(byOpName, byOpCode, bySummary));
            }
            if (hasText(criteria.operationGroup())) {
                predicates.add(cb.like(cb.lower(root.get("moduleKey")), likePattern(criteria.operationGroup())));
            }
            if (hasText(criteria.sourceSystem())) {
                predicates.add(cb.like(cb.lower(root.get("sourceSystem")), likePattern(criteria.sourceSystem())));
            }
            if (hasText(criteria.result())) {
                predicates.add(cb.like(cb.lower(root.get("result")), likePattern(criteria.result())));
            }
            if (criteria.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), criteria.from()));
            }
            if (criteria.to() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("occurredAt"), truncateToEndOfSecond(criteria.to())));
            }
            if (hasText(criteria.keyword())) {
                String pattern = likePattern(criteria.keyword());
                Predicate inSummary = cb.like(cb.lower(root.get("summary")), pattern);
                Predicate inOperation = cb.like(cb.lower(root.get("operationName")), pattern);
                Predicate inRequest = cb.like(cb.lower(root.get("requestUri")), pattern);
                predicates.add(cb.or(inSummary, inOperation, inRequest));
            }
            if (hasText(criteria.clientIp())) {
                predicates.add(cb.like(ipAsText(root.get("clientIp"), cb), likePattern(criteria.clientIp())));
            }
            if (hasText(criteria.targetTable()) || hasText(criteria.targetId())) {
                Join<AuditEntry, AuditEntryTarget> targetJoin = root.join("targets", JoinType.LEFT);
                if (hasText(criteria.targetTable())) {
                    predicates.add(cb.like(cb.lower(targetJoin.get("targetTable")), likePattern(criteria.targetTable())));
                }
                if (hasText(criteria.targetId())) {
                    predicates.add(cb.like(cb.lower(targetJoin.get("targetId")), likePattern(criteria.targetId())));
                }
            }
            if (hasText(criteria.changeRequestRef())) {
                predicates.add(cb.equal(root.get("changeRequestRef"), criteria.changeRequestRef().trim()));
            }
            if (criteria.hasChangeRequest() != null) {
                if (Boolean.TRUE.equals(criteria.hasChangeRequest())) {
                    predicates.add(cb.isNotNull(root.get("changeRequestRef")));
                } else {
                    predicates.add(cb.isNull(root.get("changeRequestRef")));
                }
            }
            Set<String> allowed = criteria.allowedActors();
            if (!allowed.isEmpty()) {
                Predicate byActor = root.get("actorId").in(allowed);
                predicates.add(byActor);
            }
            Set<String> excluded = criteria.excludedActors();
            if (!excluded.isEmpty()) {
                Predicate notIn = cb.not(root.get("actorId").in(excluded));
                predicates.add(notIn);
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Expression<String> ipAsText(Expression<?> path, jakarta.persistence.criteria.CriteriaBuilder cb) {
        return cb.lower(cb.coalesce(cb.function("host", String.class, path), ""));
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String likePattern(String value) {
        String sanitized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (sanitized.isEmpty()) {
            return "%";
        }
        String escaped = sanitized.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private static Instant truncateToEndOfSecond(Instant instant) {
        if (instant == null) {
            return null;
        }
        return instant.plusNanos(999_999_999 - (instant.getNano() % 1_000_000_000));
    }
}
