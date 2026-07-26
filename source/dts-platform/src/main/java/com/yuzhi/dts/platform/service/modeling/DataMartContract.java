package com.yuzhi.dts.platform.service.modeling;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Contract for a planning data mart. It is deliberately separate from the published asset ledger. */
public final class DataMartContract {

    private static final Pattern CODE = Pattern.compile("^[A-Z][A-Z0-9_]{1,63}$");

    private DataMartContract() {}

    public enum Status {
        DRAFT,
        CURRENT,
        RETIRED,
    }

    public record FieldIssue(String field, String code, String message) {}

    public record CreateCommand(
        String code,
        String name,
        String purpose,
        String ownerId,
        List<UUID> domainIds,
        String idempotencyKey
    ) {
        public CreateCommand {
            code = trimToNull(code);
            name = trimToNull(name);
            purpose = trimToNull(purpose);
            ownerId = trimToNull(ownerId);
            domainIds = domainIds == null ? null : List.copyOf(domainIds);
            idempotencyKey = trimToNull(idempotencyKey);
        }
    }

    public record UpdateCommand(String name, String purpose, String ownerId, List<UUID> domainIds) {
        public UpdateCommand {
            name = trimToNull(name);
            purpose = trimToNull(purpose);
            ownerId = trimToNull(ownerId);
            domainIds = domainIds == null ? null : List.copyOf(domainIds);
        }
    }

    public record View(
        UUID id,
        String code,
        String name,
        String purpose,
        String ownerId,
        List<UUID> domainIds,
        Status status,
        int revision,
        String checksum,
        long usageCount,
        Instant createdAt,
        Instant updatedAt
    ) {
        public View {
            domainIds = domainIds == null ? List.of() : List.copyOf(domainIds);
        }
    }

    public record ExpectedVersion(UUID id, int revision, String checksum) {}

    public record PlanBaselineCommand(List<UUID> dataMartIds, int expectedVersion) {
        public PlanBaselineCommand {
            dataMartIds = dataMartIds == null ? null : List.copyOf(dataMartIds);
        }
    }

    public record PlanBaselineView(UUID planId, List<UUID> dataMartIds, int version) {
        public PlanBaselineView {
            dataMartIds = dataMartIds == null ? List.of() : List.copyOf(dataMartIds);
        }
    }

    public static List<FieldIssue> validateCreate(CreateCommand command) {
        if (command == null) {
            return List.of(new FieldIssue("command", "DATA_MART_REQUEST_REQUIRED", "Data mart request is required"));
        }
        List<FieldIssue> issues = validateBusinessFields(
            command.code(),
            command.name(),
            command.purpose(),
            command.ownerId(),
            command.domainIds()
        );
        if (isBlank(command.code())) {
            issues.add(0, new FieldIssue("code", "DATA_MART_CODE_REQUIRED", "Code is required"));
        }
        if (isBlank(command.idempotencyKey())) {
            issues.add(new FieldIssue("idempotencyKey", "DATA_MART_IDEMPOTENCY_KEY_REQUIRED", "Idempotency key is required"));
        }
        return List.copyOf(issues);
    }

    public static List<FieldIssue> validateUpdate(UpdateCommand command) {
        if (command == null) {
            return List.of(new FieldIssue("command", "DATA_MART_REQUEST_REQUIRED", "Data mart request is required"));
        }
        return List.copyOf(validateBusinessFields(null, command.name(), command.purpose(), command.ownerId(), command.domainIds()));
    }

    public static List<FieldIssue> validatePlanBaseline(PlanBaselineCommand command) {
        if (command == null) {
            return List.of(new FieldIssue("command", "DATA_MART_BASELINE_REQUEST_REQUIRED", "Data mart baseline request is required"));
        }
        List<FieldIssue> issues = new ArrayList<>();
        if (command.expectedVersion() < 0) {
            issues.add(new FieldIssue("expectedVersion", "DATA_MART_BASELINE_VERSION_INVALID", "Expected version must not be negative"));
        }
        if (!validIds(command.dataMartIds(), true)) {
            issues.add(new FieldIssue("dataMartIds", "DATA_MART_BASELINE_IDS_INVALID", "Data mart ids must be unique and contain at most 100 entries"));
        }
        return List.copyOf(issues);
    }

    private static List<FieldIssue> validateBusinessFields(
        String code,
        String name,
        String purpose,
        String ownerId,
        List<UUID> domainIds
    ) {
        List<FieldIssue> issues = new ArrayList<>();
        if (!isBlank(code) && !CODE.matcher(code).matches()) {
            issues.add(new FieldIssue("code", "DATA_MART_CODE_INVALID", "Code must use upper-case letters, digits and underscores"));
        }
        if (isBlank(name)) {
            issues.add(new FieldIssue("name", "DATA_MART_NAME_REQUIRED", "Name is required"));
        }
        if (isBlank(purpose)) {
            issues.add(new FieldIssue("purpose", "DATA_MART_PURPOSE_REQUIRED", "Purpose is required"));
        }
        if (isBlank(ownerId)) {
            issues.add(new FieldIssue("ownerId", "DATA_MART_OWNER_REQUIRED", "Owner is required"));
        }
        if (!validIds(domainIds, false)) {
            issues.add(new FieldIssue("domainIds", "DATA_MART_DOMAIN_IDS_INVALID", "Select 1 to 100 unique business categories"));
        }
        return issues;
    }

    private static boolean validIds(List<UUID> ids, boolean emptyAllowed) {
        if (ids == null || ids.size() > 100 || (!emptyAllowed && ids.isEmpty())) {
            return false;
        }
        Set<UUID> unique = new HashSet<>();
        for (UUID id : ids) {
            if (id == null || !unique.add(id)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
