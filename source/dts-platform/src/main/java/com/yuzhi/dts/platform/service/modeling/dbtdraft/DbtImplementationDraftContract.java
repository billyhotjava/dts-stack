package com.yuzhi.dts.platform.service.modeling.dbtdraft;

import com.fasterxml.jackson.databind.JsonNode;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencySnapshotResolver.Snapshot;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** HTTP-neutral contract for isolated, revision-pinned advanced dbt drafts. */
public final class DbtImplementationDraftContract {

    public static final int MAX_FILES = 128;
    public static final int MAX_FILE_BYTES = 2 * 1024 * 1024;
    public static final int MAX_TOTAL_BYTES = 16 * 1024 * 1024;
    private static final int MAX_PATH_LENGTH = 512;
    private static final Pattern WINDOWS_ABSOLUTE = Pattern.compile("^[A-Za-z]:/.*");
    private static final Pattern SHA_256 = Pattern.compile("^[0-9a-f]{64}$");
    private static final Pattern SENSITIVE_NAME_TOKEN = Pattern.compile(
        "(?:^|[._-])(?:credentials?|secrets?|tokens?|password|passwd|api[_-]?key|private[_-]?key)(?:[._-]|$)"
    );
    private static final Set<String> SENSITIVE_BASENAMES = Set.of(
        "profiles.yml",
        "profiles.yaml",
        ".npmrc",
        ".pypirc",
        ".netrc",
        "id_rsa",
        "id_dsa",
        "id_ecdsa",
        "id_ed25519",
        "service-account.json",
        "service_account.json"
    );
    private static final Set<String> SENSITIVE_EXTENSIONS = Set.of(
        ".key",
        ".pem",
        ".p12",
        ".pfx",
        ".jks",
        ".keystore",
        ".crt",
        ".cer",
        ".der"
    );

    private DbtImplementationDraftContract() {}

    public enum DraftState {
        DRAFT,
        VALIDATED,
        COMMITTING,
        COMMITTED,
    }

    /** Provenance is descriptive evidence only and must never be used as an authorization switch. */
    public enum AuthoringOrigin {
        SYSTEM_GENERATED,
        MANUAL_CODE,
        DBT_ZIP_IMPORT,
        UNKNOWN;

        public static AuthoringOrigin fromStorage(String value) {
            if (value == null || value.isBlank()) return UNKNOWN;
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return UNKNOWN;
            }
        }
    }

    public enum ErrorKind {
        BAD_REQUEST,
        FORBIDDEN,
        NOT_FOUND,
        GONE,
        CONFLICT,
        PRECONDITION_FAILED,
        UNPROCESSABLE,
        SYSTEM_ERROR,
    }

    public static final class DraftException extends RuntimeException {

        private final String code;
        private final ErrorKind kind;
        private final Map<String, Object> details;

        public DraftException(String code, String message, ErrorKind kind) {
            this(code, message, kind, Map.of());
        }

        public DraftException(String code, String message, ErrorKind kind, Map<String, Object> details) {
            super(message);
            this.code = requiredText(code, "code", 128);
            this.kind = Objects.requireNonNull(kind, "kind is required");
            this.details = Map.copyOf(details == null ? Map.of() : details);
        }

        public String code() {
            return code;
        }

        public ErrorKind kind() {
            return kind;
        }

        public Map<String, Object> details() {
            return details;
        }
    }

    public record FileInput(
        @NotBlank @Size(max = MAX_PATH_LENGTH) String path,
        @NotNull @Size(max = MAX_FILE_BYTES) String content
    ) {}

    public record CreateDraftRequest(
        @NotNull UUID planId,
        @Positive int baseModelRevision,
        @NotBlank @Size(min = 64, max = 64) @jakarta.validation.constraints.Pattern(regexp = "^[0-9a-fA-F]{64}$") String baseModelChecksum,
        @Positive Integer baseImplementationRevision,
        @Size(min = 64, max = 64) @jakarta.validation.constraints.Pattern(regexp = "^[0-9a-fA-F]{64}$") String baseImplementationChecksum,
        @Size(max = 63) @jakarta.validation.constraints.Pattern(regexp = "^[a-z][a-z0-9_]*$") String targetPhysicalName,
        @NotBlank @Size(max = 128) String idempotencyKey
    ) {
        /** Compatibility constructor for callers that create a draft from an existing implementation. */
        public CreateDraftRequest(
            UUID planId,
            int baseModelRevision,
            String baseModelChecksum,
            Integer baseImplementationRevision,
            String baseImplementationChecksum,
            String idempotencyKey
        ) {
            this(
                planId,
                baseModelRevision,
                baseModelChecksum,
                baseImplementationRevision,
                baseImplementationChecksum,
                null,
                idempotencyKey
            );
        }
    }

    /** Server-derived metadata used only by the unified authoring facade. */
    public record AuthoringSeed(
        JsonNode modelSpecSnapshot,
        JsonNode projectionSummary,
        AuthoringOrigin origin,
        @NotBlank @Size(min = 64, max = 64) String requestHash
    ) {
        public AuthoringSeed {
            origin = origin == null ? AuthoringOrigin.UNKNOWN : origin;
        }
    }

    public record SaveFilesRequest(
        @NotBlank @Size(max = 64) String expectedEtag,
        @NotEmpty @Size(max = MAX_FILES) List<@NotNull @Valid FileInput> files
    ) {}

    public record ValidateDraftRequest(@NotBlank @Size(max = 64) String expectedEtag) {}

    public record CommitDraftRequest(
        @NotBlank @Size(max = 64) String expectedEtag,
        @NotBlank @Size(min = 64, max = 64) @jakarta.validation.constraints.Pattern(regexp = "^[0-9a-fA-F]{64}$") String validatedChecksum,
        @Size(min = 64, max = 64) @jakarta.validation.constraints.Pattern(regexp = "^[0-9a-fA-F]{64}$") String dependencyChecksum,
        @NotBlank @Size(max = 128) String idempotencyKey
    ) {
        public CommitDraftRequest(String expectedEtag, String validatedChecksum, String idempotencyKey) {
            this(expectedEtag, validatedChecksum, null, idempotencyKey);
        }
    }

    public record DraftView(
        UUID draftId,
        UUID planId,
        UUID modelSpecId,
        int baseModelRevision,
        String baseModelChecksum,
        Integer baseImplementationRevision,
        String baseImplementationChecksum,
        DraftState state,
        String etag,
        Instant expiresAt,
        SourceBundleView sourceBundle,
        JsonNode modelSpecSnapshot,
        JsonNode projectionSummary,
        AuthoringOrigin authoringOrigin
    ) {
        public DraftView(
            UUID draftId,
            UUID planId,
            UUID modelSpecId,
            int baseModelRevision,
            String baseModelChecksum,
            Integer baseImplementationRevision,
            String baseImplementationChecksum,
            DraftState state,
            String etag,
            Instant expiresAt,
            SourceBundleView sourceBundle
        ) {
            this(
                draftId,
                planId,
                modelSpecId,
                baseModelRevision,
                baseModelChecksum,
                baseImplementationRevision,
                baseImplementationChecksum,
                state,
                etag,
                expiresAt,
                sourceBundle,
                null,
                null,
                AuthoringOrigin.UNKNOWN
            );
        }

        public DraftView {
            authoringOrigin = authoringOrigin == null ? AuthoringOrigin.UNKNOWN : authoringOrigin;
        }
    }

    /** Content-bearing projection returned only by the maintainer-authorized draft creation route. */
    public record SourceBundleView(
        String projectKey,
        String projectChecksum,
        String bundleChecksum,
        SourceBundleKind sourceKind,
        boolean lossless,
        List<BundleFileView> files,
        String dependencyChecksum,
        Snapshot dependencySnapshot,
        Map<String, String> managedDependencyAliases
    ) {
        public SourceBundleView(
            String projectKey,
            String projectChecksum,
            String bundleChecksum,
            SourceBundleKind sourceKind,
            boolean lossless,
            List<BundleFileView> files
        ) {
            this(projectKey, projectChecksum, bundleChecksum, sourceKind, lossless, files, null, null, Map.of());
        }

        public SourceBundleView {
            if (sourceKind == null) throw new IllegalArgumentException("sourceKind is required");
            files = List.copyOf(files == null ? List.of() : files);
            managedDependencyAliases = Map.copyOf(
                managedDependencyAliases == null ? Map.of() : managedDependencyAliases
            );
        }
    }

    public enum SourceBundleKind {
        FROZEN_SOURCE_BUNDLE,
        CANONICAL_ARTIFACT_RECONSTRUCTION,
        CANONICAL_INITIALIZATION,
    }

    public record BundleFileView(String path, String content, String checksum, long byteSize) {}

    public record SaveFilesView(UUID draftId, String etag, Instant expiresAt, int fileCount, long totalBytes) {}

    public record Diagnostic(String code, String severity, String path, String modelUniqueId, String message) {}

    public record ProposedNode(
        String dbtUniqueId,
        String name,
        String resourcePath,
        String materialization,
        String nodeKind,
        List<String> dependencies
    ) {
        public ProposedNode {
            dependencies = List.copyOf(dependencies == null ? List.of() : dependencies);
        }
    }

    public record ValidationView(
        UUID draftId,
        DraftState state,
        String etag,
        Instant expiresAt,
        String validatedChecksum,
        List<Diagnostic> diagnostics,
        List<ProposedNode> proposedStructure,
        DependencyValidationView dependencyValidation
    ) {
        public ValidationView(
            UUID draftId,
            DraftState state,
            String etag,
            Instant expiresAt,
            String validatedChecksum,
            List<Diagnostic> diagnostics,
            List<ProposedNode> proposedStructure
        ) {
            this(draftId, state, etag, expiresAt, validatedChecksum, diagnostics, proposedStructure, null);
        }

        public ValidationView {
            diagnostics = List.copyOf(diagnostics == null ? List.of() : diagnostics);
            proposedStructure = List.copyOf(proposedStructure == null ? List.of() : proposedStructure);
        }
    }

    public record DependencyValidationView(
        String dependencyChecksum,
        List<String> matched,
        List<String> missing,
        List<String> undeclared
    ) {
        public DependencyValidationView {
            matched = List.copyOf(matched == null ? List.of() : matched);
            missing = List.copyOf(missing == null ? List.of() : missing);
            undeclared = List.copyOf(undeclared == null ? List.of() : undeclared);
        }
    }

    public record CommitView(
        UUID draftId,
        UUID modelSpecId,
        int modelRevision,
        String modelChecksum,
        UUID implementationId,
        int implementationRevision,
        String implementationChecksum,
        int artifactCount,
        String etag,
        String dependencyChecksum
    ) {
        public CommitView(
            UUID draftId,
            UUID modelSpecId,
            int modelRevision,
            String modelChecksum,
            UUID implementationId,
            int implementationRevision,
            String implementationChecksum,
            int artifactCount,
            String etag
        ) {
            this(
                draftId,
                modelSpecId,
                modelRevision,
                modelChecksum,
                implementationId,
                implementationRevision,
                implementationChecksum,
                artifactCount,
                etag,
                null
            );
        }
    }

    public static List<FileInput> normalizeFiles(List<FileInput> input) {
        if (input == null || input.isEmpty()) {
            throw badRequest("DBT_DRAFT_FILES_REQUIRED", "At least one dbt project file is required");
        }
        if (input.size() > MAX_FILES) {
            throw badRequest("DBT_DRAFT_FILE_COUNT_EXCEEDED", "A draft may contain at most 128 files");
        }
        List<FileInput> normalized = new ArrayList<>(input.size());
        Set<String> paths = new HashSet<>();
        long totalBytes = 0;
        for (FileInput file : input) {
            if (file == null || file.content() == null) {
                throw badRequest("DBT_DRAFT_FILE_INVALID", "Draft files and content are required");
            }
            String path = normalizePath(file.path());
            if (!paths.add(path)) {
                throw badRequest("DBT_DRAFT_PATH_DUPLICATE", "Normalized draft file paths must be unique");
            }
            String content = file.content().replace("\r\n", "\n").replace('\r', '\n');
            int size = content.getBytes(StandardCharsets.UTF_8).length;
            if (size > MAX_FILE_BYTES) {
                throw badRequest("DBT_DRAFT_FILE_TOO_LARGE", "Each draft file must not exceed 2 MiB");
            }
            totalBytes += size;
            if (totalBytes > MAX_TOTAL_BYTES) {
                throw badRequest("DBT_DRAFT_TOTAL_TOO_LARGE", "Draft content must not exceed 16 MiB");
            }
            normalized.add(new FileInput(path, content));
        }
        normalized.sort(Comparator.comparing(FileInput::path));
        return List.copyOf(normalized);
    }

    public static String normalizePath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw badRequest("DBT_DRAFT_PATH_INVALID", "Draft file path must be project-relative");
        }
        String candidate = rawPath.trim().replace('\\', '/');
        if (
            candidate.length() > MAX_PATH_LENGTH ||
            candidate.startsWith("/") ||
            WINDOWS_ABSOLUTE.matcher(candidate).matches() ||
            candidate.indexOf('\0') >= 0 ||
            candidate.chars().anyMatch(Character::isISOControl)
        ) {
            throw badRequest("DBT_DRAFT_PATH_INVALID", "Draft file path must be project-relative");
        }
        String[] segments = candidate.split("/", -1);
        List<String> clean = new ArrayList<>();
        for (String segment : segments) {
            if (segment.isEmpty() || ".".equals(segment)) continue;
            if ("..".equals(segment)) {
                throw badRequest("DBT_DRAFT_PATH_TRAVERSAL", "Draft file path must be project-relative and cannot contain '..'");
            }
            clean.add(segment);
        }
        if (clean.isEmpty()) {
            throw badRequest("DBT_DRAFT_PATH_INVALID", "Draft file path must name a project file");
        }
        String normalized = String.join("/", clean);
        rejectSensitivePath(normalized);
        return normalized;
    }

    private static void rejectSensitivePath(String normalizedPath) {
        String lower = normalizedPath.toLowerCase(Locale.ROOT);
        for (String segment : lower.split("/")) {
            if (
                segment.equals(".env") ||
                segment.startsWith(".env.") ||
                segment.equals(".envrc") ||
                segment.equals(".ssh") ||
                SENSITIVE_BASENAMES.contains(segment) ||
                SENSITIVE_NAME_TOKEN.matcher(segment).find() ||
                SENSITIVE_EXTENSIONS.stream().anyMatch(segment::endsWith)
            ) {
                throw badRequest(
                    "DBT_DRAFT_SENSITIVE_FILE_FORBIDDEN",
                    "Credential, profile, private-key and certificate files are not accepted in dbt drafts"
                );
            }
        }
        if (lower.equals(".docker/config.json") || lower.endsWith("/.docker/config.json")) {
            throw badRequest(
                "DBT_DRAFT_SENSITIVE_FILE_FORBIDDEN",
                "Credential, profile, private-key and certificate files are not accepted in dbt drafts"
            );
        }
    }

    public static String requiredChecksum(String value, String field) {
        String checksum = requiredText(value, field, 64).toLowerCase();
        if (!SHA_256.matcher(checksum).matches()) {
            throw badRequest("DBT_DRAFT_CHECKSUM_INVALID", field + " must be a SHA-256 checksum");
        }
        return checksum;
    }

    public static String requiredText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw badRequest("DBT_DRAFT_REQUEST_INVALID", field + " is required");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw badRequest("DBT_DRAFT_REQUEST_INVALID", field + " is too long");
        }
        return normalized;
    }

    public static DraftException badRequest(String code, String message) {
        return new DraftException(code, message, ErrorKind.BAD_REQUEST);
    }

    public static DraftException forbidden(String code, String message) {
        return new DraftException(code, message, ErrorKind.FORBIDDEN);
    }

    public static DraftException conflict(String code, String message) {
        return new DraftException(code, message, ErrorKind.CONFLICT);
    }

    public static DraftException precondition(String code, String message) {
        return new DraftException(code, message, ErrorKind.PRECONDITION_FAILED);
    }

    public static DraftException unprocessable(String code, String message) {
        return new DraftException(code, message, ErrorKind.UNPROCESSABLE);
    }

    public static DraftException systemError(String correlationId, Throwable cause) {
        DraftException exception = new DraftException(
            "DBT_DRAFT_SYSTEM_FAILURE",
            "The advanced dbt draft operation failed; use the correlation ID for support",
            ErrorKind.SYSTEM_ERROR,
            Map.of("correlationId", correlationId)
        );
        exception.initCause(cause);
        return exception;
    }

    public static DraftException withCorrelation(DraftException exception, String correlationId) {
        if (exception.details().containsKey("correlationId")) return exception;
        LinkedHashMap<String, Object> details = new LinkedHashMap<>(exception.details());
        details.put("correlationId", correlationId);
        DraftException correlated = new DraftException(
            exception.code(),
            exception.getMessage(),
            exception.kind(),
            Map.copyOf(details)
        );
        correlated.initCause(exception);
        return correlated;
    }

    public static Map<String, String> contentByPath(List<FileInput> files) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        normalizeFiles(files).forEach(file -> result.put(file.path(), file.content()));
        return Map.copyOf(result);
    }
}
