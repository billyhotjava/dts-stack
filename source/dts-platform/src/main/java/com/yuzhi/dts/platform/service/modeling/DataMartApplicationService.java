package com.yuzhi.dts.platform.service.modeling;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.repository.modeling.DataMartRepository;
import com.yuzhi.dts.platform.repository.modeling.DataMartRepository.PlanBinding;
import com.yuzhi.dts.platform.repository.modeling.DataMartRepository.StoredDataMart;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.modeling.DataMartContract.CreateCommand;
import com.yuzhi.dts.platform.service.modeling.DataMartContract.ExpectedVersion;
import com.yuzhi.dts.platform.service.modeling.DataMartContract.FieldIssue;
import com.yuzhi.dts.platform.service.modeling.DataMartContract.PlanBaselineCommand;
import com.yuzhi.dts.platform.service.modeling.DataMartContract.PlanBaselineView;
import com.yuzhi.dts.platform.service.modeling.DataMartContract.Status;
import com.yuzhi.dts.platform.service.modeling.DataMartContract.UpdateCommand;
import com.yuzhi.dts.platform.service.modeling.DataMartContract.View;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DataMartApplicationService {

    public static final int DEFAULT_LIST_LIMIT = 10;
    public static final int MAX_LIST_LIMIT = 100;

    private final DataMartRepository repository;
    private final AuditService auditService;
    private final ObjectWriter canonicalWriter;

    public DataMartApplicationService(DataMartRepository repository, AuditService auditService, ObjectMapper objectMapper) {
        this.repository = repository;
        this.auditService = auditService;
        this.canonicalWriter = objectMapper
            .copy()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .writer();
    }

    @Transactional
    public CreateResult create(String tenantId, String actorId, CreateCommand command) {
        requireContext(tenantId, actorId);
        reject(DataMartContract.validateCreate(command));
        String requestHash = hash(command);
        StoredDataMart replay = repository.findByIdempotencyKey(tenantId, command.idempotencyKey()).orElse(null);
        if (replay != null) {
            if (!Objects.equals(replay.idempotencyRequestHash(), requestHash)) {
                throw conflict("DATA_MART_IDEMPOTENCY_REPLAY_MISMATCH", "Idempotency key was already used with different content");
            }
            return new CreateResult(view(tenantId, replay), true);
        }
        requireBusinessCategories(command.businessCategoryIds());
        requireUniqueName(tenantId, command.name(), null);
        Instant now = Instant.now();
        UUID id = UUID.randomUUID();
        View created = withChecksum(
            new View(
                id,
                command.code(),
                command.name(),
                command.purpose(),
                command.ownerId(),
                List.copyOf(command.businessCategoryIds()),
                Status.DRAFT,
                1,
                "",
                0,
                now,
                now
            )
        );
        try {
            int inserted = repository.insert(tenantId, actorId, command, created, requestHash, json(created));
            if (inserted == 0) {
                return create(tenantId, actorId, command);
            }
        } catch (DataIntegrityViolationException error) {
            throw conflict("DATA_MART_DUPLICATE", "Data mart code or name already exists");
        }
        audit("MODELING_DATA_MART_CREATE", actorId, created, Map.of("status", created.status().name()));
        return new CreateResult(created, false);
    }

    @Transactional(readOnly = true)
    public View get(String tenantId, UUID id) {
        requireTenant(tenantId);
        return view(tenantId, repository.find(tenantId, id).orElseThrow(() -> notFound(id)));
    }

    @Transactional(readOnly = true)
    public List<View> list(String tenantId, UUID businessCategoryId, Status status, int offset, int limit) {
        return list(tenantId, businessCategoryId, status, null, offset, limit);
    }

    @Transactional(readOnly = true)
    public List<View> list(
        String tenantId,
        UUID businessCategoryId,
        Status status,
        String keyword,
        int offset,
        int limit
    ) {
        requireTenant(tenantId);
        if (offset < 0 || limit < 1 || limit > MAX_LIST_LIMIT) {
            throw badRequest("DATA_MART_LIST_WINDOW_INVALID", "Offset must be non-negative and limit must be between 1 and 100");
        }
        List<StoredDataMart> stored = repository.list(tenantId, businessCategoryId, status, keyword, offset, limit);
        List<UUID> ids = stored.stream().map(StoredDataMart::id).toList();
        Map<UUID, List<UUID>> businessCategories = repository.businessCategoryIds(tenantId, ids);
        Map<UUID, Long> usages = repository.usageCounts(tenantId, ids);
        return stored
            .stream()
            .map(item -> view(
                item,
                businessCategories.getOrDefault(item.id(), List.of()),
                usages.getOrDefault(item.id(), 0L)
            ))
            .toList();
    }

    @Transactional
    public View update(String tenantId, String actorId, UUID id, ExpectedVersion expected, UpdateCommand command) {
        requireContext(tenantId, actorId);
        reject(DataMartContract.validateUpdate(command));
        requireBusinessCategories(command.businessCategoryIds());
        View current = get(tenantId, id);
        requireExpected(current, expected);
        if (current.status() == Status.RETIRED) {
            throw conflict("DATA_MART_RETIRED_IMMUTABLE", "Retired data mart cannot be edited");
        }
        if (
            current.usageCount() > 0 &&
            !Set.copyOf(current.businessCategoryIds()).equals(Set.copyOf(command.businessCategoryIds()))
        ) {
            throw conflict(
                "DATA_MART_SCOPE_IN_USE",
                "Remove the data mart from plans, dimensions and models before changing its business categories"
            );
        }
        requireUniqueName(tenantId, command.name(), id);
        View replacement = withChecksum(
            new View(
                current.id(),
                current.code(),
                command.name(),
                command.purpose(),
                command.ownerId(),
                List.copyOf(command.businessCategoryIds()),
                current.status(),
                current.revision() + 1,
                "",
                current.usageCount(),
                current.createdAt(),
                Instant.now()
            )
        );
        compareAndSet(tenantId, actorId, expected, replacement);
        audit("MODELING_DATA_MART_UPDATE", actorId, replacement, Map.of("revision", replacement.revision()));
        return replacement;
    }

    @Transactional
    public View confirm(String tenantId, String actorId, UUID id, ExpectedVersion expected) {
        return transition(tenantId, actorId, id, expected, Status.CURRENT);
    }

    @Transactional
    public View retire(String tenantId, String actorId, UUID id, ExpectedVersion expected) {
        View current = get(tenantId, id);
        if (current.usageCount() > 0) {
            throw conflict("DATA_MART_IN_USE", "Remove this data mart from planning baselines before retiring it");
        }
        return transition(tenantId, actorId, id, expected, Status.RETIRED);
    }

    @Transactional(readOnly = true)
    public PlanBaselineView getPlanBaseline(String tenantId, UUID planId) {
        requireTenant(tenantId);
        PlanBinding binding = repository.readPlanBinding(tenantId, planId);
        if (binding == null) {
            throw new ModelSpecException("WAREHOUSE_PLAN_NOT_FOUND", "Warehouse plan not found", ModelSpecException.Kind.NOT_FOUND);
        }
        return new PlanBaselineView(binding.planId(), binding.dataMartIds(), binding.version());
    }

    @Transactional
    public PlanBaselineView savePlanBaseline(
        String tenantId,
        String actorId,
        UUID planId,
        PlanBaselineCommand command
    ) {
        requireContext(tenantId, actorId);
        reject(DataMartContract.validatePlanBaseline(command));
        PlanBinding current = repository.readPlanBinding(tenantId, planId);
        if (current == null) {
            throw new ModelSpecException("WAREHOUSE_PLAN_NOT_FOUND", "Warehouse plan not found", ModelSpecException.Kind.NOT_FOUND);
        }
        if (current.version() != command.expectedVersion()) {
            throw conflict("DATA_MART_BASELINE_VERSION_CONFLICT", "Planning baseline changed; refresh before saving");
        }
        if (!repository.allCurrentForPlan(tenantId, planId, command.dataMartIds())) {
            throw new ModelSpecException(
                "DATA_MART_BASELINE_REQUIRES_CURRENT",
                "Only confirmed data marts whose business categories are confirmed by this plan can be bound",
                ModelSpecException.Kind.UNPROCESSABLE
            );
        }
        int updated = repository.replacePlanBinding(
            tenantId,
            actorId,
            planId,
            command.expectedVersion(),
            List.copyOf(command.dataMartIds()),
            Instant.now()
        );
        if (updated == 0) {
            throw conflict("DATA_MART_BASELINE_VERSION_CONFLICT", "Planning baseline changed; refresh before saving");
        }
        PlanBaselineView view = new PlanBaselineView(planId, List.copyOf(command.dataMartIds()), command.expectedVersion() + 1);
        auditService.auditAction(
            "MODELING_WAREHOUSE_DATA_MART_BASELINE_SAVE",
            AuditStage.SUCCESS,
            planId.toString(),
            Map.of("actor", actorId, "dataMartCount", view.dataMartIds().size(), "version", view.version())
        );
        return view;
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
            throw conflict("DATA_MART_STATUS_TRANSITION_INVALID", "Data mart status transition is not allowed");
        }
        View replacement = withChecksum(
            new View(
                current.id(),
                current.code(),
                current.name(),
                current.purpose(),
                current.ownerId(),
                current.businessCategoryIds(),
                target,
                current.revision() + 1,
                "",
                current.usageCount(),
                current.createdAt(),
                Instant.now()
            )
        );
        compareAndSet(tenantId, actorId, expected, replacement);
        audit("MODELING_DATA_MART_" + target.name(), actorId, replacement, Map.of("status", target.name()));
        return replacement;
    }

    private void compareAndSet(String tenantId, String actorId, ExpectedVersion expected, View replacement) {
        if (repository.compareAndSet(tenantId, actorId, expected, replacement, json(replacement)) == 0) {
            throw conflict("DATA_MART_REVISION_CONFLICT", "Data mart changed; refresh before saving");
        }
    }

    private View view(String tenantId, StoredDataMart stored) {
        List<UUID> ids = List.of(stored.id());
        return view(
            stored,
            repository.businessCategoryIds(tenantId, ids).getOrDefault(stored.id(), List.of()),
            repository.usageCounts(tenantId, ids).getOrDefault(stored.id(), 0L)
        );
    }

    private static View view(StoredDataMart stored, List<UUID> businessCategoryIds, long usageCount) {
        return new View(
            stored.id(),
            stored.code(),
            stored.name(),
            stored.purpose(),
            stored.ownerId(),
            List.copyOf(businessCategoryIds),
            stored.status(),
            stored.revision(),
            stored.checksum(),
            usageCount,
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
                view.ownerId(),
                view.businessCategoryIds(),
                view.status(),
                view.revision()
            )
        );
        return new View(
            view.id(),
            view.code(),
            view.name(),
            view.purpose(),
            view.ownerId(),
            view.businessCategoryIds(),
            view.status(),
            view.revision(),
            checksum,
            view.usageCount(),
            view.createdAt(),
            view.updatedAt()
        );
    }

    private void requireBusinessCategories(List<UUID> businessCategoryIds) {
        if (!repository.businessCategoriesExist(businessCategoryIds)) {
            throw new ModelSpecException(
                "DATA_MART_BUSINESS_CATEGORY_NOT_FOUND",
                "One or more business categories do not exist",
                ModelSpecException.Kind.UNPROCESSABLE
            );
        }
    }

    private void requireUniqueName(String tenantId, String name, UUID excludingId) {
        if (repository.activeNameExists(tenantId, name, excludingId)) {
            throw conflict("DATA_MART_NAME_DUPLICATE", "An active data mart with this name already exists");
        }
    }

    private static void requireExpected(View current, ExpectedVersion expected) {
        if (expected == null) {
            throw new ModelSpecException(
                "DATA_MART_IF_MATCH_REQUIRED",
                "If-Match is required",
                ModelSpecException.Kind.PRECONDITION_REQUIRED
            );
        }
        if (
            !Objects.equals(current.id(), expected.id()) ||
            current.revision() != expected.revision() ||
            !Objects.equals(current.checksum(), expected.checksum())
        ) {
            throw conflict("DATA_MART_REVISION_CONFLICT", "Data mart changed; refresh before saving");
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
            throw new IllegalStateException("Unable to calculate data mart checksum", error);
        }
    }

    private String json(Object value) {
        try {
            return canonicalWriter.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Unable to serialize data mart snapshot", error);
        }
    }

    private void audit(String action, String actorId, View view, Map<String, Object> details) {
        Map<String, Object> payload = new java.util.LinkedHashMap<>(details);
        payload.put("actor", actorId);
        payload.put("code", view.code());
        payload.put("name", view.name());
        auditService.auditAction(action, AuditStage.SUCCESS, view.id().toString(), payload);
    }

    private static void requireContext(String tenantId, String actorId) {
        requireTenant(tenantId);
        if (actorId == null || actorId.isBlank()) {
            throw new ModelSpecException("DATA_MART_ACTOR_REQUIRED", "Authenticated actor is required", ModelSpecException.Kind.FORBIDDEN);
        }
    }

    private static void requireTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw badRequest("DATA_MART_TENANT_REQUIRED", "Server tenant is required");
        }
    }

    private static ModelSpecException notFound(UUID id) {
        return new ModelSpecException(
            "DATA_MART_NOT_FOUND",
            "Data mart not found: " + id,
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
        return "\"data-mart:" + view.id() + ":" + view.revision() + ":" + view.checksum() + "\"";
    }

    public record CreateResult(View dataMart, boolean replayed) {}
}
