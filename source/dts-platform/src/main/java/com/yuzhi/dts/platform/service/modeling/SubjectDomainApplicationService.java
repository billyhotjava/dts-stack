package com.yuzhi.dts.platform.service.modeling;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.repository.modeling.SubjectDomainRepository;
import com.yuzhi.dts.platform.repository.modeling.SubjectDomainRepository.StoredSubjectDomain;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.catalog.ArchitectureDictionaryWriteGuard;
import com.yuzhi.dts.platform.service.modeling.SubjectDomainContract.CreateCommand;
import com.yuzhi.dts.platform.service.modeling.SubjectDomainContract.ExpectedVersion;
import com.yuzhi.dts.platform.service.modeling.SubjectDomainContract.FieldIssue;
import com.yuzhi.dts.platform.service.modeling.SubjectDomainContract.Status;
import com.yuzhi.dts.platform.service.modeling.SubjectDomainContract.UpdateCommand;
import com.yuzhi.dts.platform.service.modeling.SubjectDomainContract.View;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Canonical owner for the modeling subject-domain ledger (数据集市 → 主题域). */
@Service
public class SubjectDomainApplicationService {

    public static final int DEFAULT_LIST_LIMIT = 10;
    public static final int MAX_LIST_LIMIT = 100;

    private final SubjectDomainRepository repository;
    private final AuditService auditService;
    private final ObjectWriter canonicalWriter;
    private final ArchitectureDictionaryWriteGuard writeGuard;

    public SubjectDomainApplicationService(
        SubjectDomainRepository repository,
        AuditService auditService,
        ObjectMapper objectMapper,
        ArchitectureDictionaryWriteGuard writeGuard
    ) {
        this.repository = repository;
        this.auditService = auditService;
        this.writeGuard = writeGuard;
        this.canonicalWriter = objectMapper
            .copy()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .writer();
    }

    @Transactional
    public CreateResult create(String tenantId, String actorId, CreateCommand command) {
        writeGuard.requireWriteAccess();
        requireContext(tenantId, actorId);
        reject(SubjectDomainContract.validateCreate(command));
        String requestHash = hash(command);
        StoredSubjectDomain replay = repository.findByIdempotencyKey(tenantId, command.idempotencyKey()).orElse(null);
        if (replay != null) {
            if (!Objects.equals(replay.idempotencyRequestHash(), requestHash)) {
                throw conflict("SUBJECT_DOMAIN_IDEMPOTENCY_REPLAY_MISMATCH", "Idempotency key was already used with different content");
            }
            return new CreateResult(view(tenantId, replay), true);
        }
        requireActiveMart(tenantId, command.martId());
        requireUniqueName(tenantId, command.name(), null);
        Instant now = Instant.now();
        UUID id = UUID.randomUUID();
        View created = withChecksum(
            new View(
                id,
                command.code(),
                command.name(),
                command.purpose(),
                command.martId(),
                Status.DRAFT,
                1,
                "",
                now,
                now
            )
        );
        try {
            int inserted = repository.insert(tenantId, actorId, command, created, requestHash);
            if (inserted == 0) {
                return create(tenantId, actorId, command);
            }
        } catch (DataIntegrityViolationException error) {
            throw conflict("SUBJECT_DOMAIN_DUPLICATE", "Subject domain code or name already exists");
        }
        audit("MODELING_SUBJECT_DOMAIN_CREATE", actorId, created, Map.of("status", created.status().name()));
        return new CreateResult(created, false);
    }

    @Transactional(readOnly = true)
    public View get(String tenantId, UUID id) {
        requireTenant(tenantId);
        return view(tenantId, repository.find(tenantId, id).orElseThrow(() -> notFound(id)));
    }

    @Transactional(readOnly = true)
    public List<View> list(String tenantId, UUID martId, Status status, String keyword, int offset, int limit) {
        requireTenant(tenantId);
        if (offset < 0 || limit < 1 || limit > MAX_LIST_LIMIT) {
            throw badRequest("SUBJECT_DOMAIN_LIST_WINDOW_INVALID", "Offset must be non-negative and limit must be between 1 and 100");
        }
        return repository
            .list(tenantId, martId, status, keyword, offset, limit)
            .stream()
            .map(item -> view(tenantId, item))
            .toList();
    }

    @Transactional
    public View update(String tenantId, String actorId, UUID id, ExpectedVersion expected, UpdateCommand command) {
        writeGuard.requireWriteAccess();
        requireContext(tenantId, actorId);
        reject(SubjectDomainContract.validateUpdate(command));
        requireActiveMart(tenantId, command.martId());
        View current = get(tenantId, id);
        requireExpected(current, expected);
        if (current.status() == Status.RETIRED) {
            throw conflict("SUBJECT_DOMAIN_RETIRED_IMMUTABLE", "Retired subject domain cannot be edited");
        }
        requireUniqueName(tenantId, command.name(), id);
        View replacement = withChecksum(
            new View(
                current.id(),
                current.code(),
                command.name(),
                command.purpose(),
                command.martId(),
                current.status(),
                current.revision() + 1,
                "",
                current.createdAt(),
                Instant.now()
            )
        );
        compareAndSet(tenantId, actorId, expected, replacement);
        audit("MODELING_SUBJECT_DOMAIN_UPDATE", actorId, replacement, Map.of("revision", replacement.revision()));
        return replacement;
    }

    @Transactional
    public View confirm(String tenantId, String actorId, UUID id, ExpectedVersion expected) {
        writeGuard.requireWriteAccess();
        return transition(tenantId, actorId, id, expected, Status.CURRENT);
    }

    @Transactional
    public View retire(String tenantId, String actorId, UUID id, ExpectedVersion expected) {
        writeGuard.requireWriteAccess();
        return transition(tenantId, actorId, id, expected, Status.RETIRED);
    }

    private View transition(
        String tenantId,
        String actorId,
        UUID id,
        ExpectedVersion expected,
        Status target
    ) {
        requireContext(tenantId, actorId);
        View current = get(tenantId, id);
        requireExpected(current, expected);
        if (current.status() == target) {
            return current;
        }
        if (current.status() == Status.RETIRED || (target == Status.CURRENT && current.status() != Status.DRAFT)) {
            throw conflict("SUBJECT_DOMAIN_STATUS_TRANSITION_INVALID", "Subject domain status transition is not allowed");
        }
        View replacement = withChecksum(
            new View(
                current.id(),
                current.code(),
                current.name(),
                current.purpose(),
                current.martId(),
                target,
                current.revision() + 1,
                "",
                current.createdAt(),
                Instant.now()
            )
        );
        compareAndSet(tenantId, actorId, expected, replacement);
        audit("MODELING_SUBJECT_DOMAIN_" + target.name(), actorId, replacement, Map.of("status", target.name()));
        return replacement;
    }

    private void compareAndSet(String tenantId, String actorId, ExpectedVersion expected, View replacement) {
        if (repository.compareAndSet(tenantId, actorId, expected, replacement) == 0) {
            throw conflict("SUBJECT_DOMAIN_REVISION_CONFLICT", "Subject domain changed; refresh before saving");
        }
    }

    private View view(String tenantId, StoredSubjectDomain stored) {
        return new View(
            stored.id(),
            stored.code(),
            stored.name(),
            stored.purpose(),
            stored.martId(),
            stored.status(),
            stored.revision(),
            stored.checksum(),
            stored.createdAt(),
            stored.updatedAt()
        );
    }

    private View withChecksum(View view) {
        String checksum = hash(
            List.of(
                view.code(),
                view.name(),
                view.purpose(),
                view.martId(),
                view.status(),
                view.revision()
            )
        );
        return new View(
            view.id(),
            view.code(),
            view.name(),
            view.purpose(),
            view.martId(),
            view.status(),
            view.revision(),
            checksum,
            view.createdAt(),
            view.updatedAt()
        );
    }

    private void requireActiveMart(String tenantId, UUID martId) {
        if (martId == null || !repository.activeMartExists(tenantId, martId)) {
            throw new ModelSpecException(
                "SUBJECT_DOMAIN_MART_NOT_FOUND",
                "The data mart does not exist or is retired",
                ModelSpecException.Kind.UNPROCESSABLE
            );
        }
    }

    private void requireUniqueName(String tenantId, String name, UUID excludingId) {
        if (repository.activeNameExists(tenantId, name, excludingId)) {
            throw conflict("SUBJECT_DOMAIN_NAME_DUPLICATE", "An active subject domain with this name already exists");
        }
    }

    private static void requireExpected(View current, ExpectedVersion expected) {
        if (expected == null) {
            throw new ModelSpecException(
                "SUBJECT_DOMAIN_IF_MATCH_REQUIRED",
                "If-Match is required",
                ModelSpecException.Kind.PRECONDITION_REQUIRED
            );
        }
        if (
            !Objects.equals(current.id(), expected.id()) ||
            current.revision() != expected.revision() ||
            !Objects.equals(current.checksum(), expected.checksum())
        ) {
            throw conflict("SUBJECT_DOMAIN_REVISION_CONFLICT", "Subject domain changed; refresh before saving");
        }
    }

    private static void reject(List<FieldIssue> issues) {
        if (!issues.isEmpty()) {
            throw new ModelSpecException(
                issues.getFirst().code(),
                issues.getFirst().message(),
                ModelSpecException.Kind.UNPROCESSABLE,
                issues
            );
        }
    }

    private String hash(Object value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonicalWriter.writeValueAsBytes(value)));
        } catch (NoSuchAlgorithmException | JsonProcessingException error) {
            throw new IllegalStateException("Unable to calculate subject domain checksum", error);
        }
    }

    private void audit(String action, String actorId, View view, Map<String, Object> details) {
        Map<String, Object> payload = new java.util.LinkedHashMap<>(details);
        payload.put("actor", actorId);
        payload.put("code", view.code());
        payload.put("name", view.name());
        auditService.auditActionStrict(action, AuditStage.SUCCESS, view.id().toString(), payload);
    }

    private static void requireContext(String tenantId, String actorId) {
        requireTenant(tenantId);
        if (actorId == null || actorId.isBlank()) {
            throw new ModelSpecException(
                "SUBJECT_DOMAIN_ACTOR_REQUIRED",
                "Authenticated actor is required",
                ModelSpecException.Kind.FORBIDDEN
            );
        }
    }

    private static void requireTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw badRequest("SUBJECT_DOMAIN_TENANT_REQUIRED", "Server tenant is required");
        }
    }

    private static ModelSpecException notFound(UUID id) {
        return new ModelSpecException(
            "SUBJECT_DOMAIN_NOT_FOUND",
            "Subject domain not found: " + id,
            ModelSpecException.Kind.NOT_FOUND
        );
    }

    private static ModelSpecException badRequest(String code, String message) {
        return new ModelSpecException(code, message, ModelSpecException.Kind.BAD_REQUEST);
    }

    private static ModelSpecException conflict(String code, String message) {
        return new ModelSpecException(code, message, ModelSpecException.Kind.CONFLICT);
    }

    public static String etag(View view) {
        return "\"subject-domain:" + view.id() + ":" + view.revision() + ":" + view.checksum() + "\"";
    }

    public record CreateResult(View subjectDomain, boolean replayed) {}
}
