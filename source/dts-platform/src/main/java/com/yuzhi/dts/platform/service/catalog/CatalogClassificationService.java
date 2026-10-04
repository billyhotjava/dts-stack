package com.yuzhi.dts.platform.service.catalog;

import com.yuzhi.dts.common.security.SecurityLevelCatalog;
import com.yuzhi.dts.common.security.SecurityLevelCatalog.DataSecurityLevel;
import com.yuzhi.dts.platform.config.Constants;
import com.yuzhi.dts.platform.domain.catalog.CatalogClassificationEvent;
import com.yuzhi.dts.platform.domain.catalog.CatalogClassificationSnapshot;
import com.yuzhi.dts.platform.repository.catalog.CatalogClassificationEventRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogClassificationSnapshotRepository;
import com.yuzhi.dts.platform.security.SecurityUtils;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CatalogClassificationService {

    public static final String STATUS_PROPAGATION_PENDING = "PROPAGATION_PENDING";
    public static final String STATUS_PROPAGATED = "PROPAGATED";

    private static final List<String> SUBJECT_TYPES = List.of("ASSET", "COLUMN", "FILE", "SCREEN_COMPONENT");
    private static final List<String> ORIGIN_TYPES = List.of(
        "SOURCE_DECLARATION",
        "FILE_DECLARATION",
        "SENSITIVE_DETECTION",
        "UPSTREAM_INHERITANCE",
        "MANUAL_FLOOR",
        "MIGRATION"
    );

    private final CatalogClassificationSnapshotRepository snapshotRepository;
    private final CatalogClassificationEventRepository eventRepository;
    private final List<CatalogClassificationProjection> projections;
    @org.springframework.beans.factory.annotation.Autowired
    private CatalogClassificationWriteLock writeLock;

    public CatalogClassificationService(
        CatalogClassificationSnapshotRepository snapshotRepository,
        CatalogClassificationEventRepository eventRepository,
        List<CatalogClassificationProjection> projections
    ) {
        this.snapshotRepository = snapshotRepository;
        this.eventRepository = eventRepository;
        this.projections = List.copyOf(projections);
    }

    public Optional<CatalogClassificationSnapshot> resolve(String subjectType, String subjectKey) {
        return snapshotRepository.findBySubjectTypeAndSubjectKey(
            normalizeSubjectType(subjectType),
            requireText(subjectKey, "subjectKey", 512)
        );
    }

    public Explanation explain(String subjectType, String subjectKey) {
        String normalizedType = normalizeSubjectType(subjectType);
        String normalizedKey = requireText(subjectKey, "subjectKey", 512);
        CatalogClassificationSnapshot snapshot = snapshotRepository
            .findBySubjectTypeAndSubjectKey(normalizedType, normalizedKey)
            .orElseThrow(() ->
                new CatalogClassificationException(
                    "CLASSIFICATION_NOT_FOUND",
                    "Classification subject does not exist: " + normalizedType + "/" + normalizedKey
                )
            );
        return new Explanation(
            snapshot,
            eventRepository.findBySubjectTypeAndSubjectKeyOrderByOccurredAtAsc(normalizedType, normalizedKey)
        );
    }

    @Transactional
    public CatalogClassificationSnapshot seal(SealCommand command) {
        Objects.requireNonNull(command, "command");
        String subjectType = normalizeSubjectType(command.subjectType());
        String subjectKey = requireText(command.subjectKey(), "subjectKey", 512);
        writeLock.lock(subjectType, subjectKey);
        String assetType = optionalUpper(command.assetType(), 32);
        String declared = optionalDataCode(command.declaredLevel());
        String detected = optionalDataCode(command.detectedLevel());
        String manualFloor = optionalDataCode(command.manualFloor());
        String inherited = SecurityLevelCatalog.maxDataCode(command.upstreamLevels());
        DataSecurityLevel effective = SecurityLevelCatalog.maxDataLevel(declared, detected, manualFloor, inherited);
        if (effective == null) {
            throw new CatalogClassificationException(
                "CLASSIFICATION_REQUIRED",
                "At least one declared, detected, manual floor or upstream classification is required"
            );
        }

        String originType = normalizeOriginType(command.originType());
        String originRef = optionalText(command.originRef(), 512);
        String checksum = requireText(command.evidenceChecksum(), "evidenceChecksum", 64);
        Instant now = Instant.now();
        String actor = currentActor();

        int inserted = snapshotRepository.insertSealedIfAbsent(
            UUID.randomUUID(),
            subjectType,
            subjectKey,
            assetType,
            declared,
            detected,
            manualFloor,
            effective.code(),
            originType,
            originRef,
            now,
            checksum,
            actor,
            now
        );
        CatalogClassificationSnapshot snapshot = lockRequired(subjectType, subjectKey);
        if (inserted == 0) {
            assertIdempotentSeal(snapshot, declared, originType, originRef, checksum);
            return snapshot;
        }

        appendEvent(
            snapshot,
            "SEALED",
            null,
            effective.code(),
            effective.code(),
            originType,
            originRef,
            command.evidenceJson(),
            actor,
            now
        );
        return project(snapshot);
    }

    /**
     * Preserve the immutable original declaration while accepting an explicitly higher source floor.
     */
    @Transactional
    public CatalogClassificationSnapshot sealOrRaise(SealCommand command) {
        Objects.requireNonNull(command, "command");
        String subjectType = normalizeSubjectType(command.subjectType());
        String subjectKey = requireText(command.subjectKey(), "subjectKey", 512);
        writeLock.lock(subjectType, subjectKey);
        Optional<CatalogClassificationSnapshot> existing = snapshotRepository.findBySubjectTypeAndSubjectKey(
            subjectType,
            subjectKey
        );
        if (existing.isEmpty()) {
            return seal(command);
        }
        CatalogClassificationSnapshot snapshot = existing.orElseThrow();
        String declared = optionalDataCode(command.declaredLevel());
        String detected = optionalDataCode(command.detectedLevel());
        String floor = optionalDataCode(command.manualFloor());
        String inherited = SecurityLevelCatalog.maxDataCode(command.upstreamLevels());
        String candidate = SecurityLevelCatalog.maxDataCode(declared, detected, floor, inherited);
        if (candidate == null) {
            throw new CatalogClassificationException(
                "CLASSIFICATION_REQUIRED",
                "At least one source classification candidate is required"
            );
        }
        boolean sameEvidence =
            Objects.equals(snapshot.getDeclaredLevel(), declared) &&
            Objects.equals(snapshot.getEvidenceChecksum(), command.evidenceChecksum()) &&
            Objects.equals(snapshot.getOriginRef(), optionalText(command.originRef(), 512));
        if (sameEvidence) {
            return snapshot;
        }
        if (SecurityLevelCatalog.isDataDowngrade(snapshot.getEffectiveLevel(), candidate)) {
            throw new CatalogClassificationException(
                "CLASSIFICATION_DOWNGRADE_FORBIDDEN",
                "A sealed source classification can only be raised"
            );
        }
        return raiseManualFloor(
            new LevelCommand(
                subjectType,
                subjectKey,
                candidate,
                command.originRef(),
                command.evidenceJson()
            )
        );
    }

    @Transactional
    public CatalogClassificationSnapshot addDetectedLevel(LevelCommand command) {
        return raise(command, "DETECTION_RAISED", "SENSITIVE_DETECTION", LevelTarget.DETECTED);
    }

    @Transactional
    public CatalogClassificationSnapshot raiseManualFloor(LevelCommand command) {
        return raise(command, "MANUAL_FLOOR_RAISED", "MANUAL_FLOOR", LevelTarget.MANUAL_FLOOR);
    }

    @Transactional
    public CatalogClassificationSnapshot inherit(InheritCommand command) {
        Objects.requireNonNull(command, "command");
        DataSecurityLevel inherited = SecurityLevelCatalog.maxDataLevel(command.upstreamLevels());
        if (inherited == null) {
            throw new CatalogClassificationException(
                "UPSTREAM_CLASSIFICATION_REQUIRED",
                "At least one upstream classification is required"
            );
        }
        return raise(
            new LevelCommand(
                command.subjectType(),
                command.subjectKey(),
                inherited.code(),
                command.triggerRef(),
                command.evidenceJson()
            ),
            "UPSTREAM_INHERITED",
            "UPSTREAM_INHERITANCE",
            LevelTarget.INHERITED
        );
    }

    private CatalogClassificationSnapshot raise(
        LevelCommand command,
        String eventType,
        String triggerType,
        LevelTarget target
    ) {
        Objects.requireNonNull(command, "command");
        String subjectType = normalizeSubjectType(command.subjectType());
        String subjectKey = requireText(command.subjectKey(), "subjectKey", 512);
        writeLock.lock(subjectType, subjectKey);
        String candidate = SecurityLevelCatalog.requireDataLevel(command.candidateLevel()).code();
        CatalogClassificationSnapshot snapshot = lockRequired(subjectType, subjectKey);
        String previousEffective = snapshot.getEffectiveLevel();
        String previousTarget = switch (target) {
            case DETECTED -> snapshot.getDetectedLevel();
            case MANUAL_FLOOR -> snapshot.getManualFloor();
            case INHERITED -> null;
        };

        String targetLevel = SecurityLevelCatalog.maxDataCode(previousTarget, candidate);
        String resulting = SecurityLevelCatalog.maxDataCode(previousEffective, targetLevel);
        boolean targetChanged = previousTarget == null || !previousTarget.equals(targetLevel);
        boolean effectiveChanged = !previousEffective.equals(resulting);
        if (!targetChanged && !effectiveChanged && target != LevelTarget.INHERITED) {
            return snapshot;
        }
        if (!effectiveChanged && target == LevelTarget.INHERITED) {
            return snapshot;
        }

        if (target == LevelTarget.DETECTED) {
            snapshot.setDetectedLevel(targetLevel);
        } else if (target == LevelTarget.MANUAL_FLOOR) {
            snapshot.setManualFloor(targetLevel);
        }
        snapshot.setEffectiveLevel(resulting);
        snapshot.setPropagationStatus(STATUS_PROPAGATION_PENDING);
        snapshot = snapshotRepository.save(snapshot);

        Instant now = Instant.now();
        appendEvent(
            snapshot,
            eventType,
            previousEffective,
            candidate,
            resulting,
            triggerType,
            optionalText(command.triggerRef(), 512),
            command.evidenceJson(),
            currentActor(),
            now
        );
        return project(snapshot);
    }

    private CatalogClassificationSnapshot project(CatalogClassificationSnapshot snapshot) {
        for (CatalogClassificationProjection projection : projections) {
            if (projection.supports(snapshot)) {
                projection.project(snapshot);
            }
        }
        snapshot.setPropagationStatus(STATUS_PROPAGATED);
        return snapshotRepository.saveAndFlush(snapshot);
    }

    private CatalogClassificationSnapshot lockRequired(String subjectType, String subjectKey) {
        return snapshotRepository
            .lockBySubject(subjectType, subjectKey)
            .orElseThrow(() ->
                new CatalogClassificationException(
                    "CLASSIFICATION_NOT_FOUND",
                    "Classification subject does not exist: " + subjectType + "/" + subjectKey
                )
            );
    }

    private void assertIdempotentSeal(
        CatalogClassificationSnapshot snapshot,
        String declared,
        String originType,
        String originRef,
        String checksum
    ) {
        boolean same =
            Objects.equals(snapshot.getDeclaredLevel(), declared) &&
            Objects.equals(snapshot.getOriginType(), originType) &&
            Objects.equals(snapshot.getOriginRef(), originRef) &&
            Objects.equals(snapshot.getEvidenceChecksum(), checksum);
        if (!same) {
            throw new CatalogClassificationException(
                "CLASSIFICATION_SEAL_CONFLICT",
                "The subject was already sealed with different immutable source evidence"
            );
        }
    }

    private void appendEvent(
        CatalogClassificationSnapshot snapshot,
        String eventType,
        String previous,
        String candidate,
        String resulting,
        String triggerType,
        String triggerRef,
        String evidenceJson,
        String actor,
        Instant occurredAt
    ) {
        CatalogClassificationEvent event = new CatalogClassificationEvent();
        event.setSubjectType(snapshot.getSubjectType());
        event.setSubjectKey(snapshot.getSubjectKey());
        event.setAssetType(snapshot.getAssetType());
        event.setEventType(eventType);
        event.setPreviousLevel(previous);
        event.setCandidateLevel(candidate);
        event.setResultingLevel(resulting);
        event.setTriggerType(triggerType);
        event.setTriggerRef(triggerRef);
        event.setEvidenceJson(evidenceJson);
        event.setActor(actor);
        event.setOccurredAt(occurredAt);
        event.setSnapshotVersion(snapshot.getRecordVersion() == null ? 0L : snapshot.getRecordVersion());
        event.setCreatedBy(actor);
        event.setCreatedDate(occurredAt);
        event.setLastModifiedBy(actor);
        event.setLastModifiedDate(occurredAt);
        eventRepository.save(event);
    }

    private static String normalizeSubjectType(String raw) {
        String value = requireText(raw, "subjectType", 32).toUpperCase(Locale.ROOT);
        if (!SUBJECT_TYPES.contains(value)) {
            throw new CatalogClassificationException(
                "CLASSIFICATION_SUBJECT_TYPE_INVALID",
                "Unsupported classification subject type: " + raw
            );
        }
        return value;
    }

    private static String normalizeOriginType(String raw) {
        String value = requireText(raw, "originType", 48).toUpperCase(Locale.ROOT);
        if (!ORIGIN_TYPES.contains(value)) {
            throw new CatalogClassificationException(
                "CLASSIFICATION_ORIGIN_TYPE_INVALID",
                "Unsupported classification origin type: " + raw
            );
        }
        return value;
    }

    private static String optionalDataCode(Object raw) {
        if (raw == null || raw.toString().isBlank()) {
            return null;
        }
        return SecurityLevelCatalog.requireDataLevel(raw).code();
    }

    private static String optionalUpper(String raw, int maxLength) {
        String value = optionalText(raw, maxLength);
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }

    private static String optionalText(String raw, int maxLength) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        if (value.length() > maxLength) {
            throw new CatalogClassificationException(
                "CLASSIFICATION_FIELD_TOO_LONG",
                "Classification field exceeds " + maxLength + " characters"
            );
        }
        return value;
    }

    private static String requireText(String raw, String field, int maxLength) {
        String value = optionalText(raw, maxLength);
        if (value == null) {
            throw new CatalogClassificationException(
                "CLASSIFICATION_FIELD_REQUIRED",
                "Classification field is required: " + field
            );
        }
        return value;
    }

    private static String currentActor() {
        return SecurityUtils.getCurrentUserLogin().orElse(Constants.SYSTEM);
    }

    private enum LevelTarget {
        DETECTED,
        MANUAL_FLOOR,
        INHERITED,
    }

    public record SealCommand(
        String subjectType,
        String subjectKey,
        String assetType,
        Object declaredLevel,
        Object detectedLevel,
        Object manualFloor,
        Collection<?> upstreamLevels,
        String originType,
        String originRef,
        String evidenceChecksum,
        String evidenceJson
    ) {}

    public record LevelCommand(
        String subjectType,
        String subjectKey,
        Object candidateLevel,
        String triggerRef,
        String evidenceJson
    ) {}

    public record InheritCommand(
        String subjectType,
        String subjectKey,
        Collection<?> upstreamLevels,
        String triggerRef,
        String evidenceJson
    ) {}

    public record Explanation(
        CatalogClassificationSnapshot snapshot,
        List<CatalogClassificationEvent> events
    ) {}
}
