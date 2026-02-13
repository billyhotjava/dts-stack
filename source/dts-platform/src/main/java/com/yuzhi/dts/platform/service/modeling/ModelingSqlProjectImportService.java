package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.domain.modeling.ModelingSqlModel;
import com.yuzhi.dts.platform.repository.modeling.ModelingSqlModelRepository;
import com.yuzhi.dts.platform.service.modeling.ModelingSqlModelService.SqlModelRequest;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class ModelingSqlProjectImportService {

    private static final Logger LOG = LoggerFactory.getLogger(ModelingSqlProjectImportService.class);
    private static final Pattern NON_SAFE = Pattern.compile("[^a-z0-9_]+");
    private static final Pattern MODEL_NAME_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*$");
    private static final int MAX_MODELS_PER_IMPORT = 500;
    private static final int MAX_ZIP_BYTES = 30 * 1024 * 1024;

    private final ModelingSqlModelService sqlModelService;
    private final ModelingSqlModelRepository sqlModelRepository;

    public ModelingSqlProjectImportService(ModelingSqlModelService sqlModelService, ModelingSqlModelRepository sqlModelRepository) {
        this.sqlModelService = sqlModelService;
        this.sqlModelRepository = sqlModelRepository;
    }

    public SqlModelProjectImportResult importProjectZip(
        SqlModelProjectImportRequest request,
        byte[] zipBytes,
        String zipName,
        String activeDeptHeader
    ) {
        if (request == null || request.planId() == null) {
            throw new IllegalArgumentException("请选择项目空间");
        }
        if (zipBytes == null || zipBytes.length == 0) {
            throw new IllegalArgumentException("ZIP 文件不能为空");
        }
        if (zipBytes.length > MAX_ZIP_BYTES) {
            throw new IllegalArgumentException("ZIP 文件过大，请控制在 30MB 以内");
        }

        ConflictStrategy strategy = parseConflictStrategy(request.onConflict());
        boolean dryRun = Boolean.TRUE.equals(request.dryRun());
        String packageFingerprint = computePackageFingerprint(zipBytes);
        Map<String, String> textEntries = readZipTextEntries(zipBytes);
        if (textEntries.isEmpty()) {
            throw new IllegalArgumentException("ZIP 中未找到可导入的 SQL/CSV/TSV 文件");
        }

        List<String> warnings = new ArrayList<>();
        List<ImportCandidate> candidates = parseCandidates(request, textEntries, warnings);
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("ZIP 中未识别到可导入内容（请提供 manifest/models.tsv、manifest/indicators.tsv 或标准分层 SQL 目录）");
        }
        if (candidates.size() > MAX_MODELS_PER_IMPORT) {
            throw new IllegalArgumentException("单次导入模型数量超过限制: " + candidates.size());
        }

        int created = 0;
        int updated = 0;
        int skipped = 0;
        int failed = 0;
        List<String> details = new ArrayList<>();
        Set<String> importedNames = new LinkedHashSet<>();

        for (ImportCandidate candidate : candidates) {
            if (candidate == null || !StringUtils.hasText(candidate.name()) || !StringUtils.hasText(candidate.sqlText())) {
                continue;
            }
            String normalizedName = candidate.name().trim();
            String duplicateKey = normalizedName.toLowerCase(Locale.ROOT);
            if (!importedNames.add(duplicateKey)) {
                skipped++;
                String reason = normalizedName + " (同一 ZIP 中重复模型名，已跳过)";
                details.add(reason);
                warnings.add(reason);
                continue;
            }

            UUID sourceDataSourceId = candidate.sourceDataSourceId() != null ? candidate.sourceDataSourceId() : request.sourceDataSourceId();
            SqlModelRequest payload = new SqlModelRequest(
                request.planId(),
                normalizedName,
                candidate.alias(),
                candidate.layer(),
                sourceDataSourceId,
                candidate.schemaName(),
                candidate.materialized(),
                candidate.tags(),
                candidate.description(),
                candidate.sqlText(),
                candidate.enabled(),
                candidate.status(),
                candidate.ownerDept()
            );

            ModelingSqlModel existing = sqlModelRepository.findFirstByPlanIdAndNameIgnoreCase(request.planId(), normalizedName).orElse(null);
            try {
                if (existing == null) {
                    if (dryRun) {
                        created++;
                        details.add(normalizedName + " (预检新增)");
                        continue;
                    }
                    if (StringUtils.hasText(candidate.csvText())) {
                        sqlModelService.importFromFiles(payload, candidate.sqlText(), candidate.csvText(), activeDeptHeader);
                    } else {
                        sqlModelService.create(payload, activeDeptHeader);
                    }
                    created++;
                    details.add(normalizedName + " (新增)");
                    continue;
                }

                if (strategy == ConflictStrategy.SKIP) {
                    skipped++;
                    details.add(normalizedName + (dryRun ? " (已存在，预检按 skip 跳过)" : " (已存在，按 skip 跳过)"));
                    continue;
                }
                if (strategy == ConflictStrategy.FAIL) {
                    failed++;
                    details.add(normalizedName + (dryRun ? " (已存在，预检按 fail 失败)" : " (已存在，按 fail 失败)"));
                    continue;
                }

                if (dryRun) {
                    if (StringUtils.hasText(candidate.csvText())) {
                        warnings.add(normalizedName + " (预检提示: overwrite 场景不会自动覆盖 CSV 列定义)");
                    }
                    updated++;
                    details.add(normalizedName + " (预检覆盖更新)");
                    continue;
                }

                sqlModelService.update(existing.getId(), payload, activeDeptHeader);
                if (StringUtils.hasText(candidate.csvText())) {
                    warnings.add(normalizedName + " (覆盖更新时未应用 CSV 列定义，可在模型编辑后重新上传 CSV)");
                }
                updated++;
                details.add(normalizedName + " (覆盖更新)");
            } catch (RuntimeException ex) {
                failed++;
                String reason = ex.getMessage();
                if (!StringUtils.hasText(reason)) {
                    reason = ex.getClass().getSimpleName();
                }
                details.add(normalizedName + " (失败: " + reason + ")");
                LOG.warn("[sql-model-project-import] model={} failed: {}", normalizedName, reason);
            }
        }

        LOG.info(
            "[sql-model-project-import] zip={} planId={} dryRun={} fingerprint={} total={} created={} updated={} skipped={} failed={}",
            zipName,
            request.planId(),
            dryRun,
            packageFingerprint,
            candidates.size(),
            created,
            updated,
            skipped,
            failed
        );

        return new SqlModelProjectImportResult(
            candidates.size(),
            created,
            updated,
            skipped,
            failed,
            warnings,
            details,
            dryRun,
            packageFingerprint
        );
    }

    private List<ImportCandidate> parseCandidates(
        SqlModelProjectImportRequest request,
        Map<String, String> textEntries,
        List<String> warnings
    ) {
        List<ImportCandidate> result = new ArrayList<>();

        String manifestPath = resolveManifestPath(textEntries.keySet());
        if (manifestPath != null) {
            result.addAll(parseManifestCandidates(request, manifestPath, textEntries, warnings));
        } else {
            warnings.add("未检测到 manifest/models.tsv，已按 SQL 分层目录自动识别导入");
            result.addAll(parseDirectoryCandidates(request, textEntries, warnings));
        }

        String indicatorManifestPath = resolveIndicatorManifestPath(textEntries.keySet());
        if (indicatorManifestPath != null) {
            result.addAll(parseIndicatorManifestCandidates(request, indicatorManifestPath, textEntries, warnings));
        }

        return result;
    }

    private String resolveManifestPath(Set<String> paths) {
        if (paths == null || paths.isEmpty()) {
            return null;
        }
        if (paths.contains("manifest/models.tsv")) {
            return "manifest/models.tsv";
        }
        if (paths.contains("models.tsv")) {
            return "models.tsv";
        }
        for (String path : paths) {
            if (path != null && path.toLowerCase(Locale.ROOT).endsWith("/manifest/models.tsv")) {
                return path;
            }
        }
        return null;
    }

    private String resolveIndicatorManifestPath(Set<String> paths) {
        if (paths == null || paths.isEmpty()) {
            return null;
        }
        if (paths.contains("manifest/indicators.tsv")) {
            return "manifest/indicators.tsv";
        }
        if (paths.contains("indicators.tsv")) {
            return "indicators.tsv";
        }
        for (String path : paths) {
            if (path != null && path.toLowerCase(Locale.ROOT).endsWith("/manifest/indicators.tsv")) {
                return path;
            }
        }
        return null;
    }

    private List<ImportCandidate> parseIndicatorManifestCandidates(
        SqlModelProjectImportRequest request,
        String manifestPath,
        Map<String, String> textEntries,
        List<String> warnings
    ) {
        String manifest = textEntries.get(manifestPath);
        if (!StringUtils.hasText(manifest)) {
            warnings.add("指标 manifest 文件为空: " + manifestPath + "，已忽略");
            return List.of();
        }

        String[] lines = manifest.split("\r?\n");
        if (lines.length < 2) {
            warnings.add("指标 manifest 内容无效（至少需要 header + 1 行数据）: " + manifestPath);
            return List.of();
        }

        Map<String, Integer> headerIndex = parseHeader(lines[0]);
        if (!headerIndex.containsKey("code")) {
            warnings.add("指标 manifest 缺少必填列 code: " + manifestPath);
            return List.of();
        }

        List<ImportCandidate> result = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            String row = lines[i];
            if (!StringUtils.hasText(row) || row.trim().startsWith("#")) {
                continue;
            }
            String[] cols = row.split("\t", -1);
            String rawCode = readCol(cols, headerIndex, "code");
            if (!StringUtils.hasText(rawCode)) {
                warnings.add("指标 manifest 第 " + (i + 1) + " 行缺少 code，已跳过");
                continue;
            }

            String code = slugify(rawCode);
            if (!StringUtils.hasText(code)) {
                warnings.add("指标 manifest 第 " + (i + 1) + " 行 code 无效: " + rawCode + "，已跳过");
                continue;
            }

            String rawName = readCol(cols, headerIndex, "name");
            if (!StringUtils.hasText(rawName)) {
                rawName = readCol(cols, headerIndex, "indicator_name");
            }
            String modelName = normalizeModelName(rawName, "ADS");
            if (!StringUtils.hasText(modelName)) {
                modelName = normalizeModelName(code, "ADS");
            }
            if (!StringUtils.hasText(modelName)) {
                warnings.add("指标 manifest 第 " + (i + 1) + " 行模型名无效: " + rawCode + "，已跳过");
                continue;
            }

            String sqlText = trimToNull(readCol(cols, headerIndex, "expression_sql"));
            if (!StringUtils.hasText(sqlText)) {
                String sqlPath = readCol(cols, headerIndex, "sql_path");
                sqlText = resolveEntryContent(textEntries, manifestPath, sqlPath);
            }
            if (!StringUtils.hasText(sqlText)) {
                warnings.add("指标 manifest 第 " + (i + 1) + " 行缺少 expression_sql/sql_path，已跳过");
                continue;
            }

            String description = trimToNull(readCol(cols, headerIndex, "description"));
            String definition = trimToNull(readCol(cols, headerIndex, "definition"));
            if (!StringUtils.hasText(description)) {
                description = StringUtils.hasText(definition)
                    ? ("指标导入[" + rawCode + "]: " + definition)
                    : ("指标导入[" + rawCode + "]");
            }

            UUID sourceId = parseUuid(readCol(cols, headerIndex, "source_data_source_id"));
            String schemaName = trimToNull(readCol(cols, headerIndex, "schema_name"));
            String materialized = trimToNull(readCol(cols, headerIndex, "materialized"));
            String tags = mergeTags(request.tags(), readCol(cols, headerIndex, "tags"));
            tags = mergeTags(tags, "indicator");
            String status = trimToNull(readCol(cols, headerIndex, "status"));
            Boolean enabled = parseBoolean(readCol(cols, headerIndex, "enabled"), request.enabled());
            String ownerDept = trimToNull(readCol(cols, headerIndex, "owner_dept"));

            result.add(
                new ImportCandidate(
                    modelName,
                    "ADS",
                    sourceId,
                    StringUtils.hasText(rawName) ? trimToNull(rawName) : trimToNull(rawCode),
                    schemaName,
                    materialized,
                    tags,
                    status,
                    enabled,
                    ownerDept,
                    description,
                    sqlText,
                    null
                )
            );
        }
        return result;
    }

    private List<ImportCandidate> parseManifestCandidates(
        SqlModelProjectImportRequest request,
        String manifestPath,
        Map<String, String> textEntries,
        List<String> warnings
    ) {
        String manifest = textEntries.get(manifestPath);
        if (!StringUtils.hasText(manifest)) {
            throw new IllegalArgumentException("manifest 文件为空: " + manifestPath);
        }
        String[] lines = manifest.split("\\r?\\n");
        if (lines.length < 2) {
            throw new IllegalArgumentException("manifest 内容无效: 至少需要 header + 1 行数据");
        }

        Map<String, Integer> headerIndex = parseHeader(lines[0]);
        if (!headerIndex.containsKey("name") || !headerIndex.containsKey("layer") || !headerIndex.containsKey("sql_path")) {
            throw new IllegalArgumentException("manifest 缺少必填列: name/layer/sql_path");
        }

        List<ImportCandidate> result = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            String row = lines[i];
            if (!StringUtils.hasText(row)) {
                continue;
            }
            if (row.trim().startsWith("#")) {
                continue;
            }
            String[] cols = row.split("\\t", -1);
            String rawName = readCol(cols, headerIndex, "name");
            String rawLayer = readCol(cols, headerIndex, "layer");
            String rawSqlPath = readCol(cols, headerIndex, "sql_path");
            if (!StringUtils.hasText(rawName) || !StringUtils.hasText(rawLayer) || !StringUtils.hasText(rawSqlPath)) {
                warnings.add("manifest 第 " + (i + 1) + " 行缺少 name/layer/sql_path，已跳过");
                continue;
            }

            String layer = normalizeLayer(rawLayer);
            if (layer == null) {
                warnings.add("manifest 第 " + (i + 1) + " 行 layer 无效: " + rawLayer + "，已跳过");
                continue;
            }
            String name = normalizeModelName(rawName, layer);
            if (!StringUtils.hasText(name)) {
                warnings.add("manifest 第 " + (i + 1) + " 行模型名无效: " + rawName + "，已跳过");
                continue;
            }

            String sqlText = resolveEntryContent(textEntries, manifestPath, rawSqlPath);
            if (!StringUtils.hasText(sqlText)) {
                warnings.add("manifest 第 " + (i + 1) + " 行 SQL 文件不存在: " + rawSqlPath + "，已跳过");
                continue;
            }

            String csvText = null;
            String rawCsvPath = readCol(cols, headerIndex, "csv_path");
            if (StringUtils.hasText(rawCsvPath)) {
                csvText = resolveEntryContent(textEntries, manifestPath, rawCsvPath);
                if (!StringUtils.hasText(csvText)) {
                    warnings.add(name + " (CSV 未找到: " + rawCsvPath + ")");
                }
            }

            UUID sourceId = parseUuid(readCol(cols, headerIndex, "source_data_source_id"));
            String alias = trimToNull(readCol(cols, headerIndex, "alias"));
            String schemaName = trimToNull(readCol(cols, headerIndex, "schema_name"));
            String materialized = trimToNull(readCol(cols, headerIndex, "materialized"));
            String tags = mergeTags(request.tags(), readCol(cols, headerIndex, "tags"));
            String status = trimToNull(readCol(cols, headerIndex, "status"));
            Boolean enabled = parseBoolean(readCol(cols, headerIndex, "enabled"), request.enabled());
            String ownerDept = trimToNull(readCol(cols, headerIndex, "owner_dept"));
            String description = trimToNull(readCol(cols, headerIndex, "description"));
            if (!StringUtils.hasText(description)) {
                description = "项目包导入: " + manifestPath;
            }

            result.add(
                new ImportCandidate(
                    name,
                    layer,
                    sourceId,
                    alias,
                    schemaName,
                    materialized,
                    tags,
                    status,
                    enabled,
                    ownerDept,
                    description,
                    sqlText,
                    csvText
                )
            );
        }
        return result;
    }

    private List<ImportCandidate> parseDirectoryCandidates(
        SqlModelProjectImportRequest request,
        Map<String, String> textEntries,
        List<String> warnings
    ) {
        List<String> sqlEntries = textEntries
            .keySet()
            .stream()
            .filter(path -> path.toLowerCase(Locale.ROOT).endsWith(".sql"))
            .sorted(Comparator.naturalOrder())
            .toList();

        List<ImportCandidate> result = new ArrayList<>();
        for (String entry : sqlEntries) {
            String layer = resolveLayerFromPath(entry);
            if (layer == null) {
                continue;
            }
            String baseName = entry.substring(entry.lastIndexOf('/') + 1);
            if (baseName.toLowerCase(Locale.ROOT).endsWith(".sql")) {
                baseName = baseName.substring(0, baseName.length() - 4);
            }
            String modelName = normalizeModelName(baseName, layer);
            if (!StringUtils.hasText(modelName)) {
                warnings.add(entry + " (模型名无效，已跳过)");
                continue;
            }
            String sqlText = textEntries.get(entry);
            if (!StringUtils.hasText(sqlText)) {
                continue;
            }
            result.add(
                new ImportCandidate(
                    modelName,
                    layer,
                    request.sourceDataSourceId(),
                    null,
                    null,
                    request.materialized(),
                    request.tags(),
                    request.status(),
                    request.enabled(),
                    request.ownerDept(),
                    "项目包自动识别: " + entry,
                    sqlText,
                    null
                )
            );
        }
        return result;
    }

    private String resolveLayerFromPath(String path) {
        if (!StringUtils.hasText(path)) {
            return null;
        }
        String normalized = path.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("01-dim/") || normalized.contains("/01-dim/") || normalized.startsWith("dim/") || normalized.contains("/dim/")) {
            return "DWD";
        }
        if (normalized.startsWith("02-dwd/") || normalized.contains("/02-dwd/") || normalized.startsWith("dwd/") || normalized.contains("/dwd/")) {
            return "DWD";
        }
        if (normalized.startsWith("03-dws/") || normalized.contains("/03-dws/") || normalized.startsWith("dws/") || normalized.contains("/dws/")) {
            return "DWS";
        }
        if (normalized.startsWith("04-ads/") || normalized.contains("/04-ads/") || normalized.startsWith("ads/") || normalized.contains("/ads/")) {
            return "ADS";
        }
        if (normalized.startsWith("ods/") || normalized.contains("/ods/")) {
            return "ODS";
        }
        return null;
    }

    private String resolveEntryContent(Map<String, String> entries, String basePath, String rawPath) {
        String normalizedPath = normalizeZipEntryPath(rawPath);
        if (!StringUtils.hasText(normalizedPath)) {
            return null;
        }
        if (entries.containsKey(normalizedPath)) {
            return entries.get(normalizedPath);
        }
        String baseDir = "";
        if (StringUtils.hasText(basePath) && basePath.contains("/")) {
            baseDir = basePath.substring(0, basePath.lastIndexOf('/'));
        }
        if (StringUtils.hasText(baseDir)) {
            String inBase = normalizeZipEntryPath(baseDir + "/" + normalizedPath);
            if (StringUtils.hasText(inBase) && entries.containsKey(inBase)) {
                return entries.get(inBase);
            }
            String inParent = normalizeZipEntryPath(baseDir + "/../" + normalizedPath);
            if (StringUtils.hasText(inParent) && entries.containsKey(inParent)) {
                return entries.get(inParent);
            }
        }
        return null;
    }

    private Map<String, String> readZipTextEntries(byte[] zipBytes) {
        Map<String, String> entries = new LinkedHashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String entryName = normalizeZipEntryPath(entry.getName());
                if (!StringUtils.hasText(entryName)) {
                    continue;
                }
                String lower = entryName.toLowerCase(Locale.ROOT);
                if (!lower.endsWith(".sql") && !lower.endsWith(".csv") && !lower.endsWith(".tsv")) {
                    continue;
                }
                byte[] bytes = readAllBytes(zis);
                entries.put(entryName, new String(bytes, StandardCharsets.UTF_8));
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException("解析 ZIP 失败: " + ex.getMessage());
        }
        return entries;
    }

    private byte[] readAllBytes(ZipInputStream zis) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int len;
        while ((len = zis.read(buf)) >= 0) {
            if (len == 0) {
                continue;
            }
            out.write(buf, 0, len);
        }
        return out.toByteArray();
    }

    private Map<String, Integer> parseHeader(String headerLine) {
        String[] columns = headerLine.split("\\t", -1);
        Map<String, Integer> index = new LinkedHashMap<>();
        for (int i = 0; i < columns.length; i++) {
            String key = trimToNull(columns[i]);
            if (key == null) {
                continue;
            }
            index.put(key.toLowerCase(Locale.ROOT), i);
        }
        return index;
    }

    private String readCol(String[] cols, Map<String, Integer> index, String key) {
        Integer idx = index.get(key.toLowerCase(Locale.ROOT));
        if (idx == null || idx < 0 || idx >= cols.length) {
            return null;
        }
        return trimToNull(cols[idx]);
    }

    private UUID parseUuid(String raw) {
        String text = trimToNull(raw);
        if (text == null) {
            return null;
        }
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private Boolean parseBoolean(String raw, Boolean fallback) {
        String text = trimToNull(raw);
        if (text == null) {
            return fallback;
        }
        if ("true".equalsIgnoreCase(text)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(text)) {
            return Boolean.FALSE;
        }
        return fallback;
    }

    private String normalizeLayer(String layer) {
        String normalized = trimToNull(layer);
        if (normalized == null) {
            return null;
        }
        String upper = normalized.toUpperCase(Locale.ROOT);
        if ("DIM".equals(upper)) {
            return "DWD";
        }
        if ("ODS".equals(upper) || "DWD".equals(upper) || "DWS".equals(upper) || "ADS".equals(upper)) {
            return upper;
        }
        return null;
    }

    private String normalizeModelName(String rawName, String layer) {
        String normalized = slugify(rawName);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        if (!normalized.startsWith("ods_") && !normalized.startsWith("dwd_") && !normalized.startsWith("dws_") && !normalized.startsWith("ads_") && !normalized.startsWith("dim_")) {
            String prefix = "DWD".equals(layer)
                ? "dwd_"
                : "DWS".equals(layer)
                    ? "dws_"
                    : "ADS".equals(layer)
                        ? "ads_"
                        : "ods_";
            normalized = prefix + normalized;
        }
        if (!Character.isLetter(normalized.charAt(0))) {
            normalized = "m_" + normalized;
        }
        return MODEL_NAME_PATTERN.matcher(normalized).matches() ? normalized : null;
    }

    private String slugify(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String slug = NON_SAFE.matcher(value.trim().toLowerCase(Locale.ROOT)).replaceAll("_");
        slug = slug.replaceAll("^_+", "").replaceAll("_+$", "");
        return StringUtils.hasText(slug) ? slug : null;
    }

    private String normalizeZipEntryPath(String path) {
        if (!StringUtils.hasText(path)) {
            return null;
        }
        String normalized = path.replace("\\", "/").trim();
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        String[] parts = normalized.split("/");
        List<String> clean = new ArrayList<>();
        for (String part : parts) {
            if (!StringUtils.hasText(part) || ".".equals(part)) {
                continue;
            }
            if ("..".equals(part)) {
                if (clean.isEmpty()) {
                    return null;
                }
                clean.remove(clean.size() - 1);
                continue;
            }
            clean.add(part);
        }
        if (clean.isEmpty()) {
            return null;
        }
        return String.join("/", clean);
    }

    private String computePackageFingerprint(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format(Locale.ROOT, "%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            return Integer.toHexString(bytes.length);
        }
    }

    private String mergeTags(String left, String right) {
        String l = trimToNull(left);
        String r = trimToNull(right);
        if (l == null) {
            return r;
        }
        if (r == null) {
            return l;
        }
        return l + "," + r;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private ConflictStrategy parseConflictStrategy(String raw) {
        String normalized = trimToNull(raw);
        if (normalized == null) {
            return ConflictStrategy.SKIP;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if ("skip".equals(lower)) {
            return ConflictStrategy.SKIP;
        }
        if ("overwrite".equals(lower)) {
            return ConflictStrategy.OVERWRITE;
        }
        if ("fail".equals(lower)) {
            return ConflictStrategy.FAIL;
        }
        throw new IllegalArgumentException("冲突策略无效: " + raw + "（允许: skip/overwrite/fail）");
    }

    private enum ConflictStrategy {
        SKIP,
        OVERWRITE,
        FAIL,
    }

    public record SqlModelProjectImportRequest(
        UUID planId,
        UUID sourceDataSourceId,
        String onConflict,
        String materialized,
        String tags,
        String status,
        Boolean enabled,
        String ownerDept,
        Boolean dryRun
    ) {}

    public record SqlModelProjectImportResult(
        int total,
        int created,
        int updated,
        int skipped,
        int failed,
        List<String> warnings,
        List<String> details,
        boolean dryRun,
        String packageFingerprint
    ) {}

    private record ImportCandidate(
        String name,
        String layer,
        UUID sourceDataSourceId,
        String alias,
        String schemaName,
        String materialized,
        String tags,
        String status,
        Boolean enabled,
        String ownerDept,
        String description,
        String sqlText,
        String csvText
    ) {}
}
