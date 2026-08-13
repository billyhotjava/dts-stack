package com.yuzhi.dts.platform.service.modeling.imports.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.platform.service.modeling.imports.checksum.ModelPackageChecksum;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.ImportIssue;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.ModelPackage;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.PackageModel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Static-only bridge from isolated draft files to the canonical source-project adapter. */
@Component
public class AdvancedDbtDraftStaticValidator {

    private static final String TEMP_PREFIX = "dts-dbt-draft-";
    private static final Set<String> UNSUPPORTED_ISSUES = Set.of("DBT_SOURCE_PROJECT_DYNAMIC_REFERENCE");
    private static final Set<String> YAML_HOOK_KEYS = Set.of(
        "on-run-start",
        "on-run-end",
        "pre-hook",
        "post-hook"
    );
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WorkspaceFactory workspaceFactory;
    private final Path tempRoot;

    public AdvancedDbtDraftStaticValidator() {
        this(defaultWorkspaceFactory(defaultTempRoot()), defaultTempRoot());
    }

    AdvancedDbtDraftStaticValidator(WorkspaceFactory workspaceFactory, Path tempRoot) {
        this.workspaceFactory = Objects.requireNonNull(workspaceFactory, "workspaceFactory is required");
        this.tempRoot = Objects.requireNonNull(tempRoot, "tempRoot is required").toAbsolutePath().normalize();
    }

    public ValidatedProject validate(Map<String, String> files) {
        if (files == null || files.isEmpty()) {
            throw new StaticValidationException("DBT_DRAFT_FILES_REQUIRED", "Static validation requires draft files");
        }
        rejectUnsupportedConstructs(files);
        Workspace workspace;
        try {
            workspace = workspaceFactory.create();
        } catch (IOException exception) {
            throw staticValidationFailed(exception);
        }
        ValidatedProject validated = null;
        Throwable validationFailure = null;
        try {
            validated = validateWorkspace(workspace.root(), files);
        } catch (Throwable failure) {
            validationFailure = failure;
        }
        try {
            workspace.close();
        } catch (IOException exception) {
            if (validationFailure != null) exception.addSuppressed(validationFailure);
            throw new StaticValidationException(
                "DBT_DRAFT_TEMP_CLEANUP_FAILED",
                "The isolated plaintext dbt workspace could not be removed",
                exception
            );
        }
        if (validationFailure instanceof Error error) throw error;
        if (validationFailure != null) throw validationFailure(validationFailure);
        return validated;
    }

    private ValidatedProject validateWorkspace(Path root, Map<String, String> files) throws IOException {
        writeFiles(root, files);
        ModelPackage modelPackage = new DbtSourceProjectModelPackageAdapter()
            .convertIfPresent(root)
            .orElseThrow(() ->
                new StaticValidationException(
                    "DBT_DRAFT_PROJECT_UNSUPPORTED",
                    "Static validation requires one dbt_project.yml"
                )
            );
        List<StaticDiagnostic> diagnostics = modelPackage
            .issues()
            .stream()
            .map(AdvancedDbtDraftStaticValidator::diagnostic)
            .toList();
        if (modelPackage.issues().stream().map(ImportIssue::code).anyMatch(UNSUPPORTED_ISSUES::contains)) {
            throw new StaticValidationException(
                "DBT_DRAFT_STATIC_REFERENCE_UNSUPPORTED",
                "The dbt project contains references that cannot be verified by static validation"
            );
        }
        List<ValidatedNode> nodes = nodes(modelPackage);
        if (nodes.stream().noneMatch(node -> "MODEL".equals(node.nodeKind()))) {
            throw new StaticValidationException(
                "DBT_DRAFT_MODEL_REQUIRED",
                "Static validation did not find an editable dbt model"
            );
        }
        return new ValidatedProject(
            validatedChecksum(files, modelPackage.packageChecksum()),
            modelPackage.packageChecksum(),
            modelPackage.dbt().projectName(),
            nodes,
            diagnostics
        );
    }

    private static RuntimeException validationFailure(Throwable failure) {
        if (failure instanceof StaticValidationException exception) return exception;
        if (failure instanceof DbtSourceProjectModelPackageAdapter.SourceProjectException exception) {
            return new StaticValidationException(exception.code(), exception.getMessage(), exception);
        }
        if (failure instanceof IOException exception) return staticValidationFailed(exception);
        if (failure instanceof RuntimeException exception) return exception;
        return new StaticValidationException(
            "DBT_DRAFT_STATIC_VALIDATION_FAILED",
            "The isolated dbt project could not be validated statically",
            failure
        );
    }

    private static StaticValidationException staticValidationFailed(IOException exception) {
        return new StaticValidationException(
            "DBT_DRAFT_STATIC_VALIDATION_FAILED",
            "The isolated dbt project could not be read for static validation",
            exception
        );
    }

    /** Removes bounded, stale draft workspaces left by abrupt process termination. */
    public int purgeOrphanedWorkspaces(Instant cutoff, int limit) {
        Objects.requireNonNull(cutoff, "cutoff is required");
        if (limit < 1 || limit > 10_000) throw new IllegalArgumentException("limit must be between 1 and 10000");
        if (!Files.isDirectory(tempRoot, LinkOption.NOFOLLOW_LINKS)) return 0;
        try (var candidates = Files.list(tempRoot)) {
            List<Path> expired = candidates
                .filter(path -> path.getFileName().toString().startsWith(TEMP_PREFIX))
                .filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                .filter(path -> lastModified(path).isBefore(cutoff))
                .sorted()
                .limit(limit)
                .toList();
            for (Path path : expired) deleteTreeStrict(path);
            return expired.size();
        } catch (IOException exception) {
            throw new StaticValidationException(
                "DBT_DRAFT_TEMP_CLEANUP_FAILED",
                "Expired isolated dbt workspaces could not be removed",
                exception
            );
        }
    }

    private static void rejectUnsupportedConstructs(Map<String, String> files) {
        files.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            String content = Objects.toString(entry.getValue(), "");
            if (isYaml(entry.getKey())) rejectYamlHooks(entry.getKey(), content);
            JinjaAllowlist.verify(entry.getKey(), content);
        });
    }

    private static boolean isYaml(String path) {
        String lower = Objects.toString(path, "").toLowerCase(Locale.ROOT);
        return lower.endsWith(".yml") || lower.endsWith(".yaml");
    }

    private static void rejectYamlHooks(String path, String content) {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setAllowRecursiveKeys(false);
        options.setMaxAliasesForCollections(0);
        options.setNestingDepthLimit(32);
        options.setCodePointLimit(DbtDraftLimits.MAX_YAML_CODE_POINTS);
        try {
            Object document = new Yaml(new SafeConstructor(options)).load(content);
            scanYaml(document, java.util.Collections.newSetFromMap(new IdentityHashMap<>()), 0, path);
        } catch (StaticValidationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new StaticValidationException(
                "DBT_DRAFT_CONFIG_INVALID",
                "The static-only validator could not safely parse dbt YAML configuration",
                exception
            );
        }
    }

    private static void scanYaml(Object value, Set<Object> visited, int depth, String path) {
        if (value == null) return;
        if (depth > 32) unsupported(path, "deep YAML configuration");
        if (value instanceof Map<?, ?> map) {
            if (!visited.add(map)) unsupported(path, "recursive YAML configuration");
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String)) unsupported(path, "non-text YAML key");
                String rawKey = (String) entry.getKey();
                String key = rawKey.trim().toLowerCase(Locale.ROOT).replace('_', '-');
                while (key.startsWith("+")) key = key.substring(1);
                if (YAML_HOOK_KEYS.contains(key)) unsupported(path, "hook");
                scanYaml(entry.getValue(), visited, depth + 1, path);
            }
        } else if (value instanceof List<?> list) {
            if (!visited.add(list)) unsupported(path, "recursive YAML configuration");
            for (Object item : list) scanYaml(item, visited, depth + 1, path);
        }
    }

    private static void unsupported(String path, String construct) {
        throw new StaticValidationException(
            "DBT_DRAFT_JINJA_UNSUPPORTED",
            "The static-only validator rejects " + construct + " in " + path
        );
    }

    private static final class DbtDraftLimits {

        private static final int MAX_YAML_CODE_POINTS = 2 * 1024 * 1024;

        private DbtDraftLimits() {}
    }

    /** Complete allowlist: comments plus literal ref/source/config expressions; every statement is rejected. */
    private static final class JinjaAllowlist {

        private static final Set<String> MATERIALIZATIONS = Set.of("table", "view", "incremental", "ephemeral");

        private JinjaAllowlist() {}

        private static void verify(String path, String content) {
            int cursor = 0;
            while (cursor < content.length()) {
                int start = content.indexOf('{', cursor);
                if (start < 0 || start + 1 >= content.length()) return;
                char marker = content.charAt(start + 1);
                if (marker != '{' && marker != '%' && marker != '#') {
                    cursor = start + 1;
                    continue;
                }
                char closeMarker = marker == '{' ? '}' : marker;
                int end = findEnd(content, start + 2, closeMarker, marker == '#');
                if (end < 0) unsupported(path, "unterminated Jinja block");
                if (marker == '%') unsupported(path, "Jinja statement");
                if (marker == '{') {
                    String expression = trimWhitespaceControl(content.substring(start + 2, end));
                    new SafeExpression(expression, path).parse();
                }
                cursor = end + 2;
            }
        }

        private static int findEnd(String content, int cursor, char closeMarker, boolean comment) {
            char quote = 0;
            boolean escaped = false;
            for (int index = cursor; index + 1 < content.length(); index++) {
                char current = content.charAt(index);
                if (!comment && quote != 0) {
                    if (escaped) escaped = false;
                    else if (current == '\\') escaped = true;
                    else if (current == quote) quote = 0;
                    continue;
                }
                if (!comment && (current == '\'' || current == '"')) {
                    quote = current;
                    continue;
                }
                if (current == closeMarker && content.charAt(index + 1) == '}') return index;
            }
            return -1;
        }

        private static String trimWhitespaceControl(String value) {
            String result = value.trim();
            if (result.startsWith("-")) result = result.substring(1).trim();
            if (result.endsWith("-")) result = result.substring(0, result.length() - 1).trim();
            return result;
        }

        private static final class SafeExpression {

            private final String expression;
            private final String path;
            private int cursor;

            private SafeExpression(String expression, String path) {
                this.expression = expression;
                this.path = path;
            }

            private void parse() {
                skipWhitespace();
                String function = identifier();
                skipWhitespace();
                expect('(');
                switch (function) {
                    case "ref" -> reference(false);
                    case "source" -> reference(true);
                    case "config" -> config();
                    default -> unsupported(path, "non-allowlisted Jinja expression");
                }
                skipWhitespace();
                if (cursor != expression.length()) unsupported(path, "composed Jinja expression");
            }

            private void reference(boolean source) {
                stringLiteral();
                skipWhitespace();
                if (source || peek(',')) {
                    expect(',');
                    stringLiteral();
                    skipWhitespace();
                }
                expect(')');
            }

            private void config() {
                skipWhitespace();
                if (peek(')')) unsupported(path, "empty config expression");
                Set<String> keys = new HashSet<>();
                while (true) {
                    String key = identifier();
                    if (!keys.add(key)) unsupported(path, "duplicate config key");
                    skipWhitespace();
                    expect('=');
                    skipWhitespace();
                    if ("materialized".equals(key)) {
                        String value = stringLiteral();
                        if (!MATERIALIZATIONS.contains(value)) unsupported(path, "unsupported materialization");
                    } else if ("tags".equals(key)) {
                        stringList();
                    } else if ("alias".equals(key)) {
                        stringLiteral();
                    } else if ("unique_key".equals(key)) {
                        stringList();
                    } else if ("meta".equals(key)) {
                        literalMap();
                    } else {
                        unsupported(path, "non-allowlisted config key");
                    }
                    skipWhitespace();
                    if (peek(')')) {
                        expect(')');
                        return;
                    }
                    expect(',');
                    skipWhitespace();
                }
            }

            private void literalMap() {
                expect('{');
                skipWhitespace();
                int count = 0;
                Set<String> keys = new HashSet<>();
                if (peek('}')) {
                    expect('}');
                    return;
                }
                while (true) {
                    String key = stringLiteral();
                    if (!keys.add(key)) unsupported(path, "duplicate config metadata key");
                    expect(':');
                    literalScalar();
                    if (++count > 32) unsupported(path, "too many config metadata entries");
                    skipWhitespace();
                    if (peek('}')) {
                        expect('}');
                        return;
                    }
                    expect(',');
                    skipWhitespace();
                }
            }

            private void literalScalar() {
                skipWhitespace();
                if (peek('\'') || peek('"')) {
                    stringLiteral();
                    return;
                }
                int start = cursor;
                while (cursor < expression.length() && expression.charAt(cursor) >= '0' && expression.charAt(cursor) <= '9') {
                    cursor++;
                }
                if (cursor == start || cursor - start > 19) unsupported(path, "non-literal config metadata value");
            }

            private void stringList() {
                expect('[');
                skipWhitespace();
                int count = 0;
                if (peek(']')) {
                    expect(']');
                    return;
                }
                while (true) {
                    stringLiteral();
                    if (++count > 64) unsupported(path, "too many config tags");
                    skipWhitespace();
                    if (peek(']')) {
                        expect(']');
                        return;
                    }
                    expect(',');
                    skipWhitespace();
                }
            }

            private String identifier() {
                skipWhitespace();
                int start = cursor;
                if (cursor >= expression.length() || !isIdentifierStart(expression.charAt(cursor))) {
                    unsupported(path, "non-allowlisted Jinja syntax");
                }
                cursor++;
                while (cursor < expression.length() && isIdentifierPart(expression.charAt(cursor))) cursor++;
                return expression.substring(start, cursor);
            }

            private String stringLiteral() {
                skipWhitespace();
                if (cursor >= expression.length()) unsupported(path, "missing literal Jinja argument");
                char quote = expression.charAt(cursor);
                if (quote != '\'' && quote != '"') unsupported(path, "non-literal Jinja argument");
                cursor++;
                StringBuilder value = new StringBuilder();
                boolean escaped = false;
                while (cursor < expression.length()) {
                    char current = expression.charAt(cursor++);
                    if (escaped) {
                        value.append(current);
                        escaped = false;
                    } else if (current == '\\') {
                        escaped = true;
                    } else if (current == quote) {
                        String result = value.toString();
                        if (
                            result.isBlank() ||
                            result.length() > 256 ||
                            result.chars().anyMatch(Character::isISOControl)
                        ) {
                            unsupported(path, "invalid literal Jinja argument");
                        }
                        return result;
                    } else {
                        value.append(current);
                    }
                }
                unsupported(path, "unterminated literal Jinja argument");
                return "";
            }

            private void expect(char expected) {
                skipWhitespace();
                if (!peek(expected)) unsupported(path, "non-allowlisted Jinja syntax");
                cursor++;
            }

            private boolean peek(char expected) {
                return cursor < expression.length() && expression.charAt(cursor) == expected;
            }

            private void skipWhitespace() {
                while (cursor < expression.length() && Character.isWhitespace(expression.charAt(cursor))) cursor++;
            }

            private static boolean isIdentifierStart(char value) {
                return value == '_' || (value >= 'A' && value <= 'Z') || (value >= 'a' && value <= 'z');
            }

            private static boolean isIdentifierPart(char value) {
                return isIdentifierStart(value) || (value >= '0' && value <= '9');
            }
        }
    }

    private List<ValidatedNode> nodes(ModelPackage modelPackage) {
        List<ValidatedNode> result = new ArrayList<>();
        for (PackageModel model : modelPackage.models()) {
            if (model.sql() == null || model.sql().effectiveSql() == null || model.sql().effectiveSql().isBlank()) continue;
            String materialization = model.materialization() == null || model.materialization().isBlank()
                ? "view"
                : model.materialization().trim().toLowerCase();
            String schema = schema(model);
            result.add(
                new ValidatedNode(
                    model.dbtUniqueId(),
                    model.name(),
                    model.resourcePath(),
                    materialization,
                    "MODEL",
                    model.sql().effectiveSql(),
                    model.sql().effectiveSqlChecksum(),
                    schema,
                    ModelPackageChecksum.sha256Text(schema),
                    model.dependencies(),
                    model.conversion() == null ? List.of() : model.conversion().reasonCodes()
                )
            );
        }
        result.sort(Comparator.comparing(ValidatedNode::dbtUniqueId));
        return List.copyOf(result);
    }

    private String schema(PackageModel model) {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.set("columns", objectMapper.valueToTree(model.columns() == null ? List.of() : model.columns()));
        schema.set("tests", objectMapper.valueToTree(model.tests() == null ? List.of() : model.tests()));
        try {
            return objectMapper.writeValueAsString(schema);
        } catch (JsonProcessingException exception) {
            throw new StaticValidationException(
                "DBT_DRAFT_SCHEMA_INVALID",
                "The static dbt schema projection could not be serialized",
                exception
            );
        }
    }

    private static StaticDiagnostic diagnostic(ImportIssue issue) {
        return new StaticDiagnostic(
            issue.code(),
            issue.severity(),
            issue.fieldPath(),
            issue.modelUniqueId(),
            issue.message()
        );
    }

    private static String validatedChecksum(Map<String, String> files, String packageChecksum) {
        StringBuilder canonical = new StringBuilder(Objects.toString(packageChecksum, ""));
        files
            .entrySet()
            .stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry ->
                canonical
                    .append('\n')
                    .append(entry.getKey())
                    .append('\0')
                    .append(ModelPackageChecksum.sha256Text(entry.getValue()))
            );
        return ModelPackageChecksum.sha256Text(canonical.toString());
    }

    private static WorkspaceFactory defaultWorkspaceFactory(Path tempRoot) {
        return () -> {
            Path root = Files.createTempDirectory(tempRoot, TEMP_PREFIX);
            secure(root);
            return new DefaultWorkspace(root);
        };
    }

    private static Path defaultTempRoot() {
        return Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();
    }

    private static void secure(Path root) throws IOException {
        try {
            Files.setPosixFilePermissions(
                root,
                EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE)
            );
        } catch (UnsupportedOperationException ignored) {
            // Non-POSIX test environments still use an unpredictable, isolated temp directory.
        }
    }

    private static void writeFiles(Path root, Map<String, String> files) throws IOException {
        for (Map.Entry<String, String> entry : files.entrySet()) {
            Path target = root.resolve(entry.getKey()).normalize();
            if (!target.startsWith(root)) {
                throw new StaticValidationException(
                    "DBT_DRAFT_PATH_TRAVERSAL",
                    "Draft paths must stay within the isolated project root"
                );
            }
            if (target.getParent() != null) Files.createDirectories(target.getParent());
            Files.writeString(target, entry.getValue(), StandardCharsets.UTF_8);
        }
    }

    private static void deleteTreeStrict(Path root) throws IOException {
        if (root == null || !Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
        IOException failure = null;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    if (failure == null) failure = exception;
                    else failure.addSuppressed(exception);
                }
            }
        } catch (IOException exception) {
            if (failure == null) failure = exception;
            else failure.addSuppressed(exception);
        }
        if (failure != null) throw new WorkspaceCleanupException("Could not delete isolated dbt workspace", failure);
    }

    private static Instant lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toInstant();
        } catch (IOException exception) {
            throw new StaticValidationException(
                "DBT_DRAFT_TEMP_CLEANUP_FAILED",
                "Expired isolated dbt workspace metadata could not be read",
                exception
            );
        }
    }

    interface WorkspaceFactory {
        Workspace create() throws IOException;
    }

    interface Workspace extends AutoCloseable {
        Path root();

        @Override
        void close() throws IOException;
    }

    static final class WorkspaceCleanupException extends IOException {

        WorkspaceCleanupException(String message) {
            super(message);
        }

        WorkspaceCleanupException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private record DefaultWorkspace(Path root) implements Workspace {
        @Override
        public void close() throws IOException {
            deleteTreeStrict(root);
        }
    }

    public record StaticDiagnostic(String code, String severity, String path, String modelUniqueId, String message) {}

    public record ValidatedNode(
        String dbtUniqueId,
        String name,
        String resourcePath,
        String materialization,
        String nodeKind,
        String sql,
        String sqlChecksum,
        String schema,
        String schemaChecksum,
        List<String> dependencies,
        List<String> reasonCodes
    ) {
        public ValidatedNode {
            dependencies = List.copyOf(dependencies == null ? List.of() : dependencies);
            reasonCodes = List.copyOf(reasonCodes == null ? List.of() : reasonCodes);
        }
    }

    public record ValidatedProject(
        String validatedChecksum,
        String packageChecksum,
        String projectKey,
        List<ValidatedNode> nodes,
        List<StaticDiagnostic> diagnostics
    ) {
        public ValidatedProject {
            nodes = List.copyOf(nodes == null ? List.of() : nodes);
            diagnostics = List.copyOf(diagnostics == null ? List.of() : diagnostics);
        }
    }

    public static final class StaticValidationException extends IllegalArgumentException {

        private final String code;

        public StaticValidationException(String code, String message) {
            super(message);
            this.code = code;
        }

        public StaticValidationException(String code, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }
}
