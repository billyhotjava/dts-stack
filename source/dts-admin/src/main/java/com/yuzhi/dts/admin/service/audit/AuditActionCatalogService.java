package com.yuzhi.dts.admin.service.audit;

import com.yuzhi.dts.admin.domain.audit.AuditActionCatalogEntry;
import com.yuzhi.dts.admin.domain.audit.AuditClassificationMiss;
import com.yuzhi.dts.admin.repository.audit.AuditActionCatalogRepository;
import com.yuzhi.dts.admin.repository.audit.AuditClassificationMissRepository;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional(readOnly = true)
public class AuditActionCatalogService {

    private static final Logger log = LoggerFactory.getLogger(AuditActionCatalogService.class);
    private static final String DEFAULT_SOURCE_SYSTEM = "admin";

    private final AuditActionCatalogRepository actionCatalogRepository;
    private final AuditClassificationMissRepository classificationMissRepository;

    public AuditActionCatalogService(
        AuditActionCatalogRepository actionCatalogRepository,
        AuditClassificationMissRepository classificationMissRepository
    ) {
        this.actionCatalogRepository = Objects.requireNonNull(actionCatalogRepository, "actionCatalogRepository required");
        this.classificationMissRepository = Objects.requireNonNull(
            classificationMissRepository,
            "classificationMissRepository required"
        );
    }

    public Optional<ResolvedAction> resolve(String sourceSystem, String actionCode) {
        String normalizedSource = normalizeSourceSystem(sourceSystem);
        String normalizedAction = normalizeActionCode(actionCode);
        if (!StringUtils.hasText(normalizedAction)) {
            return Optional.empty();
        }
        return actionCatalogRepository
            .findFirstBySourceSystemIgnoreCaseAndActionCodeIgnoreCaseAndEnabledTrue(normalizedSource, normalizedAction)
            .map(this::toResolvedAction);
    }

    @Transactional
    public void recordMiss(AuditActionRequest request, String reason) {
        if (request == null) {
            return;
        }
        try {
            Instant now = Instant.now();
            String sourceSystem = normalizeSourceSystem(request.sourceSystem());
            String actionCode = normalizeActionCode(request.buttonCode());
            String requestUri = trimToNull(request.requestUri());
            String httpMethod = normalizeHttpMethod(request.httpMethod());
            String missReason = StringUtils.hasText(reason) ? reason.trim() : "NO_CATALOG_MATCH";
            AuditClassificationMiss miss = findExistingMiss(sourceSystem, actionCode, requestUri, httpMethod, missReason)
                .orElseGet(AuditClassificationMiss::new);
            boolean isNew = miss.getFirstSeenAt() == null;
            miss.setSourceSystem(sourceSystem);
            miss.setActionCode(actionCode);
            miss.setModuleKeyRaw(trimToNull(request.moduleKeyOverride()));
            miss.setRequestUri(requestUri);
            miss.setHttpMethod(httpMethod);
            miss.setActorId(trimToNull(request.actorId()));
            miss.setReason(missReason);
            if (isNew) {
                miss.setFirstSeenAt(now);
            }
            miss.setLastSeenAt(now);
            miss.setOccurrenceCount(isNew ? 1L : safeIncrement(miss.getOccurrenceCount()));
            classificationMissRepository.save(miss);
        } catch (RuntimeException ex) {
            log.warn("Failed to record audit classification miss for action {}: {}", request.buttonCode(), ex.getMessage());
        }
    }

    private Optional<AuditClassificationMiss> findExistingMiss(
        String sourceSystem,
        String actionCode,
        String requestUri,
        String httpMethod,
        String reason
    ) {
        AuditClassificationMiss probe = new AuditClassificationMiss();
        probe.setSourceSystem(sourceSystem);
        probe.setActionCode(actionCode);
        probe.setRequestUri(requestUri);
        probe.setHttpMethod(httpMethod);
        probe.setReason(reason);
        ExampleMatcher matcher = ExampleMatcher
            .matching()
            .withIgnorePaths("id", "moduleKeyRaw", "actorId", "firstSeenAt", "lastSeenAt", "occurrenceCount");
        return classificationMissRepository.findOne(Example.of(probe, matcher));
    }

    private long safeIncrement(Long value) {
        return value == null || value < 1L ? 1L : value + 1L;
    }

    private ResolvedAction toResolvedAction(AuditActionCatalogEntry entry) {
        return new ResolvedAction(
            normalizeSourceSystem(entry.getSourceSystem()),
            trimToNull(entry.getModuleKey()),
            trimToNull(entry.getModuleName()),
            trimToNull(entry.getOperationCode()),
            trimToNull(entry.getOperationName()),
            resolveOperationKind(entry.getOperationKind()),
            trimToNull(entry.getResourceType()),
            Boolean.TRUE.equals(entry.getAllowEmptyTargets())
        );
    }

    private AuditOperationKind resolveOperationKind(String operationKind) {
        if (!StringUtils.hasText(operationKind)) {
            return AuditOperationKind.OTHER;
        }
        try {
            return AuditOperationKind.valueOf(operationKind.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return AuditOperationKind.OTHER;
        }
    }

    private String normalizeSourceSystem(String sourceSystem) {
        return StringUtils.hasText(sourceSystem) ? sourceSystem.trim().toLowerCase(Locale.ROOT) : DEFAULT_SOURCE_SYSTEM;
    }

    private String normalizeActionCode(String actionCode) {
        return StringUtils.hasText(actionCode) ? actionCode.trim().toUpperCase(Locale.ROOT) : null;
    }

    private String normalizeHttpMethod(String method) {
        return StringUtils.hasText(method) ? method.trim().toUpperCase(Locale.ROOT) : null;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    public record ResolvedAction(
        String sourceSystem,
        String moduleKey,
        String moduleName,
        String operationCode,
        String operationName,
        AuditOperationKind operationKind,
        String resourceType,
        boolean allowEmptyTargets
    ) {}
}
