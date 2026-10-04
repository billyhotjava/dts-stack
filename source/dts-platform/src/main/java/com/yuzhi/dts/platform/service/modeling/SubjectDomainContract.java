package com.yuzhi.dts.platform.service.modeling;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Contract for the modeling subject-domain ledger. Aligned with DataWorks: a subject domain
 * organizes a data mart by analysis perspective (application-side chain
 * 业务分类 → 数据集市 → 主题域).
 */
public final class SubjectDomainContract {

    private static final Pattern CODE = Pattern.compile("^[A-Z][A-Z0-9_]{1,63}$");

    private SubjectDomainContract() {}

    public enum Status {
        DRAFT,
        CURRENT,
        RETIRED,
    }

    public record FieldIssue(String field, String code, String message) {}

    public record CreateCommand(String code, String name, String purpose, UUID martId, String idempotencyKey) {
        public CreateCommand {
            code = trimToNull(code);
            name = trimToNull(name);
            purpose = trimToNull(purpose);
            idempotencyKey = trimToNull(idempotencyKey);
        }
    }

    public record UpdateCommand(String name, String purpose, UUID martId) {
        public UpdateCommand {
            name = trimToNull(name);
            purpose = trimToNull(purpose);
        }
    }

    public record View(
        UUID id,
        String code,
        String name,
        String purpose,
        UUID martId,
        Status status,
        int revision,
        String checksum,
        Instant createdAt,
        Instant updatedAt
    ) {
        public View {
            purpose = trimToNull(purpose);
        }
    }

    public record ExpectedVersion(UUID id, int revision, String checksum) {}

    public static List<FieldIssue> validateCreate(CreateCommand command) {
        if (command == null) {
            return List.of(new FieldIssue("command", "SUBJECT_DOMAIN_REQUEST_REQUIRED", "Subject domain request is required"));
        }
        List<FieldIssue> issues = validateBusinessFields(
            command.code(),
            command.name(),
            command.purpose(),
            command.martId()
        );
        if (isBlank(command.code())) {
            issues.add(0, new FieldIssue("code", "SUBJECT_DOMAIN_CODE_REQUIRED", "Code is required"));
        }
        if (isBlank(command.idempotencyKey())) {
            issues.add(new FieldIssue("idempotencyKey", "SUBJECT_DOMAIN_IDEMPOTENCY_KEY_REQUIRED", "Idempotency key is required"));
        }
        return List.copyOf(issues);
    }

    public static List<FieldIssue> validateUpdate(UpdateCommand command) {
        if (command == null) {
            return List.of(new FieldIssue("command", "SUBJECT_DOMAIN_REQUEST_REQUIRED", "Subject domain request is required"));
        }
        return List.copyOf(validateBusinessFields(null, command.name(), command.purpose(), command.martId()));
    }

    private static List<FieldIssue> validateBusinessFields(String code, String name, String purpose, UUID martId) {
        List<FieldIssue> issues = new ArrayList<>();
        if (!isBlank(code) && !CODE.matcher(code).matches()) {
            issues.add(new FieldIssue("code", "SUBJECT_DOMAIN_CODE_INVALID", "Code must use upper-case letters, digits and underscores"));
        }
        if (isBlank(name)) {
            issues.add(new FieldIssue("name", "SUBJECT_DOMAIN_NAME_REQUIRED", "Name is required"));
        }
        if (martId == null) {
            issues.add(new FieldIssue("martId", "SUBJECT_DOMAIN_MART_REQUIRED", "A data mart is required"));
        }
        return issues;
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
