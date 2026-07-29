package com.yuzhi.dts.platform.service.governance;

import com.yuzhi.dts.platform.repository.governance.MeasurementUnitRepository;
import com.yuzhi.dts.platform.repository.governance.MeasurementUnitRepository.ModelSpecReference;
import com.yuzhi.dts.platform.repository.governance.MeasurementUnitRepository.StoredRevision;
import com.yuzhi.dts.platform.repository.governance.MeasurementUnitRepository.StoredUnit;
import com.yuzhi.dts.platform.repository.governance.MeasurementUnitRepository.UnitDependent;
import com.yuzhi.dts.platform.service.governance.MeasurementUnitContract.ExpectedVersion;
import com.yuzhi.dts.platform.service.governance.MeasurementUnitContract.MeasurementUnitCommand;
import com.yuzhi.dts.platform.service.governance.MeasurementUnitContract.MeasurementUnitStatus;
import com.yuzhi.dts.platform.service.governance.MeasurementUnitContract.MeasurementUnitView;
import com.yuzhi.dts.platform.service.governance.MeasurementUnitContract.ReferenceImpact;
import com.yuzhi.dts.platform.service.governance.MeasurementUnitContract.ReferenceItem;
import com.yuzhi.dts.platform.service.modeling.ModelSpecDomainReadAccessPort;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Command/query owner for stable, versioned measurement-unit definitions. */
@Service
public class MeasurementUnitApplicationService {

    private final MeasurementUnitRepository repository;
    private final MeasurementUnitSnapshotCodec codec;
    private final ModelSpecDomainReadAccessPort domainReadAccess;
    private final Clock clock;
    private final Supplier<UUID> idGenerator;
    private final String serverTenantId;

    @Autowired
    public MeasurementUnitApplicationService(
        MeasurementUnitRepository repository,
        MeasurementUnitSnapshotCodec codec,
        ModelSpecDomainReadAccessPort domainReadAccess,
        @Value("${dts.platform.modeling.default-tenant-id:default}") String serverTenantId
    ) {
        this(repository, codec, domainReadAccess, Clock.systemUTC(), UUID::randomUUID, serverTenantId);
    }

    MeasurementUnitApplicationService(
        MeasurementUnitRepository repository,
        MeasurementUnitSnapshotCodec codec,
        ModelSpecDomainReadAccessPort domainReadAccess,
        Clock clock,
        Supplier<UUID> idGenerator,
        String serverTenantId
    ) {
        this.repository = repository;
        this.codec = codec;
        this.domainReadAccess = domainReadAccess;
        this.clock = clock;
        this.idGenerator = idGenerator;
        this.serverTenantId = serverTenantId;
    }

    @Transactional
    public MeasurementUnitView create(String actorId, MeasurementUnitCommand request) {
        requireActor(actorId);
        repository.lockMutationGraph();
        MeasurementUnitCommand command = validated(request);
        rejectDuplicateCode(command.code(), null);
        UUID unitId = idGenerator.get();
        validateBase(unitId, command.quantityKind(), command.baseUnitRef());
        Instant now = clock.instant();
        MeasurementUnitView created = codec.toView(
            unitId,
            command,
            MeasurementUnitStatus.ACTIVE,
            1,
            now,
            now
        );
        int inserted;
        try {
            inserted = repository.insertCurrent(created, actorId);
        } catch (DataIntegrityViolationException exception) {
            throw translateIntegrity(exception);
        }
        if (inserted != 1) {
            throw new MeasurementUnitException(
                "MEASUREMENT_UNIT_CREATE_CONFLICT",
                "Measurement unit creation did not converge",
                MeasurementUnitException.Kind.CONFLICT
            );
        }
        repository.insertRevision(created, codec.write(created), actorId);
        return created;
    }

    @Transactional
    public MeasurementUnitView update(
        String actorId,
        UUID unitId,
        ExpectedVersion expected,
        MeasurementUnitCommand request
    ) {
        requireActor(actorId);
        repository.lockMutationGraph();
        StoredUnit stored = findStored(unitId);
        MeasurementUnitView current = toView(stored);
        requireExpected(current, expected);
        if (current.status() != MeasurementUnitStatus.ACTIVE) {
            throw new MeasurementUnitException(
                "MEASUREMENT_UNIT_INACTIVE",
                "Inactive measurement units cannot be updated",
                MeasurementUnitException.Kind.CONFLICT
            );
        }
        MeasurementUnitCommand command = validated(request);
        rejectDuplicateCode(command.code(), unitId);
        rejectIncompatibleDependents(unitId, current.quantityKind(), command.quantityKind());
        validateBase(unitId, command.quantityKind(), command.baseUnitRef());
        MeasurementUnitView replacement = codec.toView(
            unitId,
            command,
            current.status(),
            current.version() + 1,
            current.createdAt(),
            clock.instant()
        );
        if (replacement.checksum().equals(current.checksum())) return current;
        compareAndAppend(actorId, stored, replacement);
        return replacement;
    }

    /**
     * Package-import write path. It preserves the same checksum/version/CAS invariants as interactive edits,
     * while allowing a previously rolled-back (inactive) package unit to be reactivated.
     */
    @Transactional
    public MeasurementUnitView replaceForImport(
        String actorId,
        UUID unitId,
        ExpectedVersion expected,
        MeasurementUnitCommand request,
        MeasurementUnitStatus targetStatus
    ) {
        requireActor(actorId);
        Objects.requireNonNull(targetStatus, "targetStatus");
        repository.lockMutationGraph();
        StoredUnit stored = findStored(unitId);
        MeasurementUnitView current = toView(stored);
        requireExpected(current, expected);
        MeasurementUnitCommand command = validated(request);
        rejectDuplicateCode(command.code(), unitId);
        rejectIncompatibleDependents(unitId, current.quantityKind(), command.quantityKind());
        if (targetStatus == MeasurementUnitStatus.ACTIVE) {
            validateBase(unitId, command.quantityKind(), command.baseUnitRef());
        } else {
            rejectActiveDependents(unitId);
        }
        MeasurementUnitView replacement = codec.toView(
            unitId,
            command,
            targetStatus,
            current.version() + 1,
            current.createdAt(),
            clock.instant()
        );
        if (replacement.checksum().equals(current.checksum())) return current;
        compareAndAppend(actorId, stored, replacement);
        return replacement;
    }

    /**
     * A created unit cannot be hard-deleted because its revision ledger is immutable. Package rollback therefore
     * writes an INACTIVE tombstone and fails closed when a model started referencing the unit after import.
     */
    @Transactional
    public MeasurementUnitView rollbackCreatedImport(String actorId, UUID unitId, ExpectedVersion expected) {
        requireActor(actorId);
        repository.lockMutationGraph();
        StoredUnit stored = findStored(unitId);
        MeasurementUnitView current = toView(stored);
        requireExpected(current, expected);
        rejectActiveDependents(unitId);
        if (!repository.listModelSpecReferences(serverTenantId, unitId).isEmpty()) {
            throw new MeasurementUnitException(
                "MEASUREMENT_UNIT_IMPORT_ROLLBACK_REFERENCED",
                "Imported measurement unit is referenced and cannot be rolled back",
                MeasurementUnitException.Kind.CONFLICT
            );
        }
        if (current.status() == MeasurementUnitStatus.INACTIVE) return current;
        MeasurementUnitView replacement = codec.toView(
            unitId,
            MeasurementUnitContract.fromView(current),
            MeasurementUnitStatus.INACTIVE,
            current.version() + 1,
            current.createdAt(),
            clock.instant()
        );
        compareAndAppend(actorId, stored, replacement);
        return replacement;
    }

    @Transactional
    public MeasurementUnitView deactivate(String actorId, UUID unitId, ExpectedVersion expected) {
        requireActor(actorId);
        repository.lockMutationGraph();
        StoredUnit stored = findStored(unitId);
        MeasurementUnitView current = toView(stored);
        requireExpected(current, expected);
        if (current.status() == MeasurementUnitStatus.INACTIVE) return current;
        rejectActiveDependents(unitId);
        MeasurementUnitView replacement = codec.toView(
            unitId,
            MeasurementUnitContract.fromView(current),
            MeasurementUnitStatus.INACTIVE,
            current.version() + 1,
            current.createdAt(),
            clock.instant()
        );
        compareAndAppend(actorId, stored, replacement);
        return replacement;
    }

    @Transactional(readOnly = true)
    public MeasurementUnitView get(UUID unitId) {
        return toView(findStored(unitId));
    }

    @Transactional(readOnly = true)
    public List<MeasurementUnitView> list() {
        return repository.listCurrent().stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public List<MeasurementUnitView> versions(UUID unitId) {
        findStored(unitId);
        return repository.listRevisions(unitId).stream().map(this::readRevision).toList();
    }

    @Transactional(readOnly = true)
    public ReferenceImpact references(UUID unitId) {
        MeasurementUnitView current = toView(findStored(unitId));
        List<ReferenceItem> items = new ArrayList<>();
        for (UnitDependent dependent : repository.listUnitDependents(unitId)) {
            items.add(
                new ReferenceItem(
                    "MEASUREMENT_UNIT",
                    dependent.unitId(),
                    dependent.code() + " · " + dependent.name(),
                    null,
                    dependent.version(),
                    "DEPENDENT",
                    "/governance/standards/units/" + dependent.unitId(),
                    false
                )
            );
        }
        int restricted = 0;
        for (ModelSpecReference reference : repository.listModelSpecReferences(serverTenantId, unitId)) {
            boolean canRead = reference.domainId() != null && domainReadAccess.canRead(reference.domainId());
            if (!canRead) {
                restricted++;
                items.add(
                    new ReferenceItem(
                        "MODEL_SPEC",
                        null,
                        null,
                        null,
                        null,
                        "RESTRICTED",
                        null,
                        true
                    )
                );
                continue;
            }
            items.add(
                new ReferenceItem(
                    "MODEL_SPEC",
                    reference.modelSpecId(),
                    reference.displayName(),
                    reference.referencedVersion(),
                    current.version(),
                    reference.referencedVersion() == current.version() ? "CURRENT" : "STALE",
                    "/modeling/models/" + reference.modelSpecId(),
                    false
                )
            );
        }
        return new ReferenceImpact(items.size(), restricted, items);
    }

    private void compareAndAppend(String actorId, StoredUnit stored, MeasurementUnitView replacement) {
        int updated;
        try {
            updated = repository.compareAndSet(stored, replacement, actorId);
        } catch (DataIntegrityViolationException exception) {
            throw translateIntegrity(exception);
        }
        if (updated != 1) {
            throw new MeasurementUnitException(
                "MEASUREMENT_UNIT_VERSION_CONFLICT",
                "Measurement unit was changed by another request",
                MeasurementUnitException.Kind.CONFLICT
            );
        }
        repository.insertRevision(replacement, codec.write(replacement), actorId);
    }

    private MeasurementUnitCommand validated(MeasurementUnitCommand request) {
        List<MeasurementUnitContract.FieldIssue> issues = MeasurementUnitContract.validate(request);
        if (!issues.isEmpty()) {
            throw new MeasurementUnitException(
                "MEASUREMENT_UNIT_REQUEST_INVALID",
                "Measurement unit request contains invalid fields",
                MeasurementUnitException.Kind.UNPROCESSABLE,
                issues
            );
        }
        return MeasurementUnitContract.normalize(request);
    }

    private void rejectDuplicateCode(String code, UUID currentId) {
        repository
            .findByCode(code)
            .filter(duplicate -> !Objects.equals(duplicate.id(), currentId))
            .ifPresent(duplicate -> {
                throw duplicateCode();
            });
    }

    private void validateBase(UUID unitId, String quantityKind, UUID baseUnitRef) {
        if (baseUnitRef == null) return;
        Set<UUID> visited = new HashSet<>();
        visited.add(unitId);
        UUID cursor = baseUnitRef;
        while (cursor != null) {
            if (!visited.add(cursor)) {
                throw new MeasurementUnitException(
                    "MEASUREMENT_UNIT_BASE_CYCLE",
                    "Measurement unit base relationship contains a cycle",
                    MeasurementUnitException.Kind.UNPROCESSABLE
                );
            }
            StoredUnit base = repository.findCurrent(cursor).orElseThrow(MeasurementUnitApplicationService::invalidBase);
            if (base.status() != MeasurementUnitStatus.ACTIVE || !Objects.equals(quantityKind, base.quantityKind())) {
                throw invalidBase();
            }
            cursor = base.baseUnitRef();
        }
    }

    private MeasurementUnitView readRevision(StoredRevision stored) {
        MeasurementUnitView view = codec.readView(stored.snapshotJson());
        if (
            !Objects.equals(stored.unitId(), view.id()) ||
            stored.version() != view.version() ||
            !Objects.equals(stored.checksum(), view.checksum()) ||
            !Objects.equals(view.checksum(), codec.contentChecksum(view))
        ) {
            throw new MeasurementUnitException(
                "MEASUREMENT_UNIT_REVISION_CORRUPT",
                "Measurement unit revision ledger is inconsistent",
                MeasurementUnitException.Kind.CONFLICT
            );
        }
        return view;
    }

    private StoredUnit findStored(UUID unitId) {
        if (unitId == null) throw notFound();
        return repository.findCurrent(unitId).orElseThrow(MeasurementUnitApplicationService::notFound);
    }

    private static void requireExpected(MeasurementUnitView current, ExpectedVersion expected) {
        if (expected == null) {
            throw new MeasurementUnitException(
                "MEASUREMENT_UNIT_IF_MATCH_REQUIRED",
                "A strong If-Match precondition is required",
                MeasurementUnitException.Kind.PRECONDITION_REQUIRED
            );
        }
        if (!Objects.equals(current.id(), expected.unitId())) {
            throw new MeasurementUnitException(
                "MEASUREMENT_UNIT_IF_MATCH_INVALID",
                "If-Match identifies a different measurement unit",
                MeasurementUnitException.Kind.BAD_REQUEST
            );
        }
        if (current.version() != expected.version() || !Objects.equals(current.checksum(), expected.checksum())) {
            throw new MeasurementUnitException(
                "MEASUREMENT_UNIT_VERSION_CONFLICT",
                "Measurement unit was changed by another request",
                MeasurementUnitException.Kind.CONFLICT,
                Map.of("currentVersion", current.version(), "currentChecksum", current.checksum())
            );
        }
    }

    private static void requireActor(String actorId) {
        if (actorId == null || actorId.isBlank()) {
            throw new MeasurementUnitException(
                "MEASUREMENT_UNIT_ACTOR_REQUIRED",
                "Authenticated actor is required for measurement unit writes",
                MeasurementUnitException.Kind.BAD_REQUEST
            );
        }
    }

    private void rejectIncompatibleDependents(UUID unitId, String currentQuantityKind, String replacementQuantityKind) {
        if (!Objects.equals(currentQuantityKind, replacementQuantityKind)) rejectActiveDependents(unitId);
    }

    private void rejectActiveDependents(UUID unitId) {
        boolean hasActiveDependent = repository
            .listUnitDependents(unitId)
            .stream()
            .anyMatch(dependent -> dependent.status() == MeasurementUnitStatus.ACTIVE);
        if (hasActiveDependent) {
            throw new MeasurementUnitException(
                "MEASUREMENT_UNIT_ACTIVE_DEPENDENTS",
                "Measurement unit has active dependent units",
                MeasurementUnitException.Kind.CONFLICT
            );
        }
    }

    private MeasurementUnitView toView(StoredUnit stored) {
        MeasurementUnitView view = new MeasurementUnitView(
            stored.id(),
            stored.code(),
            stored.name(),
            stored.symbol(),
            stored.quantityKind(),
            stored.conversionFactor(),
            stored.baseUnitRef(),
            stored.precision(),
            stored.status(),
            stored.version(),
            stored.checksum(),
            stored.createdAt(),
            stored.updatedAt()
        );
        if (!Objects.equals(view.checksum(), codec.contentChecksum(view))) {
            throw new MeasurementUnitException(
                "MEASUREMENT_UNIT_CURRENT_CORRUPT",
                "Measurement unit current head is inconsistent",
                MeasurementUnitException.Kind.CONFLICT
            );
        }
        return view;
    }

    private static MeasurementUnitException invalidBase() {
        return new MeasurementUnitException(
            "MEASUREMENT_UNIT_BASE_INVALID",
            "Base measurement unit is unavailable or incompatible",
            MeasurementUnitException.Kind.UNPROCESSABLE
        );
    }

    private static MeasurementUnitException duplicateCode() {
        return new MeasurementUnitException(
            "MEASUREMENT_UNIT_CODE_CONFLICT",
            "Measurement unit code already exists",
            MeasurementUnitException.Kind.CONFLICT
        );
    }

    private static MeasurementUnitException translateIntegrity(DataIntegrityViolationException exception) {
        Throwable cause = exception.getMostSpecificCause();
        String message = cause == null ? null : cause.getMessage();
        if (message != null && message.contains("uk_measurement_unit_code_ci")) return duplicateCode();
        return new MeasurementUnitException(
            "MEASUREMENT_UNIT_PERSISTENCE_CONFLICT",
            "Measurement unit write conflicts with persisted constraints",
            MeasurementUnitException.Kind.CONFLICT
        );
    }

    private static MeasurementUnitException notFound() {
        return new MeasurementUnitException(
            "MEASUREMENT_UNIT_NOT_FOUND",
            "Measurement unit was not found",
            MeasurementUnitException.Kind.NOT_FOUND
        );
    }
}
