package com.yuzhi.dts.platform.service.modeling.imports.converter;

import com.yuzhi.dts.platform.service.modeling.imports.checksum.ModelPackageChecksum;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.ConversionMode;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.ConversionResult;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.DbtMetadata;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.Defaults;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.ImportIssue;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.ModelPackage;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.PackageModel;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.SqlArtifact;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Adapts the legacy advanced-modeling {@code models.tsv + SQL} layout without
 * inferring manifest dependencies or business semantics.
 */
public final class LegacyTsvModelPackageAdapter {

    static final long MAX_TSV_BYTES = 4L * 1024 * 1024;
    static final int MAX_TSV_LINES = 1_000;
    static final long MAX_SQL_BYTES = 2L * 1024 * 1024;
    static final long MAX_TOTAL_SQL_BYTES = 16L * 1024 * 1024;

    private static final int MAX_TSV_DEPTH = 4;
    private static final Set<String> TSV_COLUMNS = Set.of(
        "name",
        "layer",
        "sql_path",
        "source_data_source_id",
        "alias",
        "schema_name",
        "materialized",
        "tags",
        "status",
        "enabled",
        "owner_dept",
        "description",
        "csv_path"
    );
    private static final List<String> BLOCKED_REASONS = List.of(
        "LEGACY_MANIFEST_REQUIRED",
        "SEMANTIC_METADATA_REQUIRED",
        "DEPENDENCY_GRAPH_UNVERIFIED"
    );

    public Optional<ModelPackage> convertIfPresent(Path archiveRoot) {
        Path root = normalizeRoot(archiveRoot);
        Optional<Path> locatedTsv = locateModelsTsv(root);
        if (locatedTsv.isEmpty()) {
            return Optional.empty();
        }

        Path tsv = locatedTsv.orElseThrow();
        byte[] tsvBytes = readBounded(tsv, MAX_TSV_BYTES, "LEGACY_TSV_TOO_LARGE", "无法读取 models.tsv");
        List<String> lines = lines(decodeUtf8(tsvBytes, "LEGACY_TSV_INVALID", "models.tsv 必须使用 UTF-8 编码"));
        if (lines.size() > MAX_TSV_LINES) {
            throw error("LEGACY_TSV_TOO_LARGE", "models.tsv 超过 1000 行");
        }
        if (lines.isEmpty()) {
            throw error("LEGACY_TSV_HEADER_INVALID", "models.tsv 缺少表头");
        }

        Header header = parseHeader(stripBom(lines.getFirst()));
        List<PackageModel> models = new ArrayList<>();
        List<ImportIssue> issues = new ArrayList<>();
        Set<String> uniqueIds = new LinkedHashSet<>();
        long totalSqlBytes = 0;

        for (int lineIndex = 1; lineIndex < lines.size(); lineIndex++) {
            String line = lines.get(lineIndex);
            if (line.isBlank()) {
                continue;
            }
            Map<String, String> row = parseRow(header, line, lineIndex + 1);
            if ("false".equalsIgnoreCase(value(row, "enabled"))) {
                continue;
            }

            String name = required(row, "name", lineIndex + 1);
            String sqlPath = required(row, "sql_path", lineIndex + 1);
            String uniqueId = "model.legacy." + sanitizeName(name, lineIndex + 1);
            if (!uniqueIds.add(uniqueId)) {
                throw error("LEGACY_MODEL_DUPLICATE", "models.tsv 包含重复模型标识: " + uniqueId);
            }

            Path sqlFile = resolveSql(root, tsv.getParent(), sqlPath);
            byte[] sqlBytes = readBounded(
                sqlFile,
                MAX_SQL_BYTES,
                "LEGACY_SQL_TOO_LARGE",
                "无法读取 SQL 文件: " + sqlPath
            );
            totalSqlBytes += sqlBytes.length;
            if (totalSqlBytes > MAX_TOTAL_SQL_BYTES) {
                throw error("LEGACY_SQL_TOTAL_TOO_LARGE", "legacy SQL 文件总大小超过 16 MiB");
            }
            String sql = decodeUtf8(sqlBytes, "LEGACY_SQL_INVALID", "SQL 文件必须使用 UTF-8 编码: " + sqlPath);
            if (sql.isBlank()) {
                throw error("LEGACY_SQL_INVALID", "SQL 文件不能为空: " + sqlPath);
            }

            String sqlChecksum = ModelPackageChecksum.sha256Text(sql);
            String resourcePath = root.relativize(sqlFile).toString().replace('\\', '/');
            PackageModel model = new PackageModel(
                uniqueId,
                name,
                nullable(row, "description"),
                resourcePath,
                new SqlArtifact(sql, sqlChecksum, null, null, sql, sqlChecksum, "LEGACY_SQL_FILE"),
                null,
                legacyConfig(row),
                tags(row),
                List.of(),
                List.of(),
                List.of(),
                null,
                new ConversionResult(ConversionMode.BLOCKED, BLOCKED_REASONS)
            );
            models.add(model);
            issues.add(
                new ImportIssue(
                    "LEGACY_MANIFEST_REQUIRED",
                    "ERROR",
                    "$.models[" + uniqueId + "].semantics",
                    uniqueId,
                    "legacy models.tsv 缺少 manifest 依赖图和 meta.dts 业务语义，候选已阻断",
                    "补充 manifest.json 与 meta.dts 后按普通建模导入；原高级建模 models.tsv 流程仍可兼容使用"
                )
            );
        }

        String packageId = "legacy-" + ModelPackageChecksum.sha256(tsvBytes).substring(0, 16);
        ModelPackage withoutChecksum = new ModelPackage(
            ModelPackageContract.SCHEMA_VERSION,
            packageId,
            null,
            new DbtMetadata("legacy", null, "legacy-tsv/v1", null),
            new Defaults(null, null),
            List.of(),
            List.of(),
            List.copyOf(models),
            List.copyOf(issues)
        );
        return Optional.of(ModelPackageChecksum.withChecksum(withoutChecksum));
    }

    private static Path normalizeRoot(Path archiveRoot) {
        if (archiveRoot == null) {
            throw error("LEGACY_ARCHIVE_INVALID", "解压目录不能为空");
        }
        Path root = archiveRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw error("LEGACY_ARCHIVE_INVALID", "解压目录不存在或不可读取");
        }
        return root;
    }

    private static Optional<Path> locateModelsTsv(Path root) {
        List<Path> candidates;
        try (var paths = Files.walk(root, MAX_TSV_DEPTH)) {
            candidates = paths
                .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                .filter(path -> "models.tsv".equalsIgnoreCase(path.getFileName().toString()))
                .sorted(Comparator.comparing(path -> relativePath(root, path)))
                .toList();
        } catch (IOException exception) {
            throw new LegacyArchiveException("LEGACY_ARCHIVE_INVALID", "无法检查解压目录", exception);
        }
        if (candidates.size() > 1) {
            throw error(
                "LEGACY_TSV_MULTIPLE",
                "发现多个 models.tsv: " + candidates.stream().map(path -> relativePath(root, path)).toList()
            );
        }
        return candidates.stream().findFirst();
    }

    private static Header parseHeader(String line) {
        String[] columns = line.split("\t", -1);
        Map<String, Integer> indexes = new LinkedHashMap<>();
        for (int index = 0; index < columns.length; index++) {
            String column = columns[index].trim().toLowerCase(Locale.ROOT);
            if (column.isEmpty() || !TSV_COLUMNS.contains(column) || indexes.putIfAbsent(column, index) != null) {
                throw error("LEGACY_TSV_HEADER_INVALID", "models.tsv 表头包含空列、未知列或重复列");
            }
        }
        if (!indexes.containsKey("name") || !indexes.containsKey("sql_path")) {
            throw error("LEGACY_TSV_HEADER_INVALID", "models.tsv 表头至少需要 name 和 sql_path");
        }
        return new Header(Map.copyOf(indexes), columns.length);
    }

    private static Map<String, String> parseRow(Header header, String line, int lineNumber) {
        String[] fields = line.split("\t", -1);
        if (fields.length > header.columnCount()) {
            throw error("LEGACY_TSV_ROW_INVALID", "models.tsv 第 " + lineNumber + " 行列数超过表头");
        }
        Map<String, String> row = new LinkedHashMap<>();
        header.indexes().forEach((column, index) -> row.put(column, index < fields.length ? fields[index].trim() : ""));
        return row;
    }

    private static Path resolveSql(Path root, Path tsvDirectory, String value) {
        Path relative = safeSqlPath(value);
        List<Path> candidates = new ArrayList<>(2);
        candidates.add(tsvDirectory.resolve(relative).normalize());
        Path rootCandidate = root.resolve(relative).normalize();
        if (!rootCandidate.equals(candidates.getFirst())) {
            candidates.add(rootCandidate);
        }
        for (Path candidate : candidates) {
            if (candidate.startsWith(root) && Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)) {
                return candidate;
            }
        }
        throw error("LEGACY_SQL_MISSING", "找不到 SQL 文件: " + value);
    }

    private static Path safeSqlPath(String value) {
        String normalizedValue = value == null ? "" : value.trim();
        if (
            normalizedValue.isEmpty() ||
            normalizedValue.indexOf('\\') >= 0 ||
            normalizedValue.startsWith("/") ||
            isWindowsAbsolute(normalizedValue) ||
            normalizedValue.chars().anyMatch(Character::isISOControl) ||
            !normalizedValue.toLowerCase(Locale.ROOT).endsWith(".sql")
        ) {
            throw error("LEGACY_SQL_PATH_INVALID", "sql_path 必须是安全的 POSIX .sql 相对路径: " + normalizedValue);
        }
        String[] segments = normalizedValue.split("/", -1);
        for (String segment : segments) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                throw error("LEGACY_SQL_PATH_INVALID", "sql_path 包含非规范或越界路径: " + normalizedValue);
            }
        }
        try {
            Path path = Path.of(normalizedValue);
            if (path.isAbsolute() || !path.normalize().toString().replace('\\', '/').equals(normalizedValue)) {
                throw error("LEGACY_SQL_PATH_INVALID", "sql_path 包含非规范或越界路径: " + normalizedValue);
            }
            return path;
        } catch (InvalidPathException exception) {
            throw new LegacyArchiveException("LEGACY_SQL_PATH_INVALID", "sql_path 无效: " + normalizedValue, exception);
        }
    }

    private static byte[] readBounded(Path path, long maximum, String tooLargeCode, String readFailureMessage) {
        try {
            if (Files.size(path) > maximum) {
                throw error(tooLargeCode, path.getFileName() + " 超过允许大小");
            }
            byte[] bytes = Files.readAllBytes(path);
            if (bytes.length > maximum) {
                throw error(tooLargeCode, path.getFileName() + " 超过允许大小");
            }
            return bytes;
        } catch (LegacyArchiveException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new LegacyArchiveException("LEGACY_ARCHIVE_READ_FAILED", readFailureMessage, exception);
        }
    }

    private static String decodeUtf8(byte[] bytes, String code, String message) {
        try {
            return StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
        } catch (CharacterCodingException exception) {
            throw new LegacyArchiveException(code, message, exception);
        }
    }

    private static List<String> lines(String value) {
        return value.lines().toList();
    }

    private static String stripBom(String value) {
        return value.startsWith("\uFEFF") ? value.substring(1) : value;
    }

    private static String sanitizeName(String name, int lineNumber) {
        String sanitized = Normalizer
            .normalize(name, Normalizer.Form.NFKC)
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "_")
            .replaceAll("^_+|_+$", "");
        if (sanitized.isBlank()) {
            throw error("LEGACY_MODEL_NAME_INVALID", "models.tsv 第 " + lineNumber + " 行 name 无法生成稳定模型标识");
        }
        return sanitized;
    }

    private static Map<String, Object> legacyConfig(Map<String, String> row) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("legacyLayer", value(row, "layer"));
        config.put("legacyMaterialized", value(row, "materialized"));
        config.put("legacySourceDataSourceId", value(row, "source_data_source_id"));
        return Map.copyOf(config);
    }

    private static List<String> tags(Map<String, String> row) {
        String rawTags = value(row, "tags");
        if (rawTags.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        for (String tag : rawTags.split(",")) {
            String normalized = tag.trim();
            if (!normalized.isEmpty()) {
                tags.add(normalized);
            }
        }
        return List.copyOf(tags);
    }

    private static String required(Map<String, String> row, String field, int lineNumber) {
        String value = value(row, field);
        if (value.isEmpty()) {
            throw error("LEGACY_TSV_ROW_INVALID", "models.tsv 第 " + lineNumber + " 行缺少 " + field);
        }
        return value;
    }

    private static String nullable(Map<String, String> row, String field) {
        String value = value(row, field);
        return value.isEmpty() ? null : value;
    }

    private static String value(Map<String, String> row, String field) {
        return row.getOrDefault(field, "");
    }

    private static boolean isWindowsAbsolute(String value) {
        return value.length() >= 3 && Character.isLetter(value.charAt(0)) && value.charAt(1) == ':' && value.charAt(2) == '/';
    }

    private static String relativePath(Path root, Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }

    private static LegacyArchiveException error(String code, String message) {
        return new LegacyArchiveException(code, message);
    }

    private record Header(Map<String, Integer> indexes, int columnCount) {}

    public static final class LegacyArchiveException extends IllegalArgumentException {

        private final String code;

        public LegacyArchiveException(String code, String message) {
            super(message);
            this.code = code;
        }

        public LegacyArchiveException(String code, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }
}
