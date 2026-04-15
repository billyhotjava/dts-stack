package com.yuzhi.dts.platform.service.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogColumnSchema;
import com.yuzhi.dts.platform.domain.catalog.CatalogTableSchema;
import com.yuzhi.dts.platform.domain.modeling.DataStandard;
import com.yuzhi.dts.platform.repository.catalog.CatalogColumnSchemaRepository;
import com.yuzhi.dts.platform.repository.modeling.DataStandardRepository;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CatalogColumnSyncService {

    private static final Logger LOG = LoggerFactory.getLogger(CatalogColumnSyncService.class);
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DRAFT = "DRAFT";

    private final CatalogColumnSchemaRepository columnRepository;
    private final DataStandardRepository dataStandardRepository;

    public CatalogColumnSyncService(
        CatalogColumnSchemaRepository columnRepository,
        DataStandardRepository dataStandardRepository
    ) {
        this.columnRepository = columnRepository;
        this.dataStandardRepository = dataStandardRepository;
    }

    public List<ColumnSpec> parseCsv(Path path) {
        if (path == null || !Files.exists(path)) {
            return List.of();
        }
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return parseCsv(reader);
        } catch (IOException ex) {
            LOG.warn("[column-sync] failed to read csv {}: {}", path, ex.getMessage());
            return List.of();
        }
    }

    public List<ColumnSpec> parseCsv(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        try (Reader reader = new java.io.StringReader(text)) {
            return parseCsv(reader);
        } catch (IOException ex) {
            return List.of();
        }
    }

    public List<ColumnSpec> parseManifestColumns(Map<String, Object> columns) {
        if (columns == null || columns.isEmpty()) {
            return List.of();
        }
        List<ColumnSpec> result = new ArrayList<>();
        for (Map.Entry<String, Object> entry : columns.entrySet()) {
            String name = safe(entry.getKey());
            if (!StringUtils.hasText(name)) {
                continue;
            }
            Map<String, Object> meta = asMap(entry.getValue());
            String dataType = safe(meta.get("data_type"));
            String comment = safe(meta.get("description"));
            result.add(new ColumnSpec(name, dataType, null, comment, null, null, null, null));
        }
        return result;
    }

    public int upsertColumns(CatalogTableSchema table, Collection<ColumnSpec> specs) {
        return upsertColumns(table, specs, STATUS_ACTIVE);
    }

    /**
     * Upserts a table's columns in a single short transaction. Uses REQUIRED propagation
     * so that callers already in a transaction (e.g. user-triggered modeling flows) join
     * their outer tx; callers without a tx (e.g. the dbt scheduler after the outer-tx
     * removal) get their own per-table tx, keeping locks short-lived.
     */
    @Transactional
    public int upsertColumns(CatalogTableSchema table, Collection<ColumnSpec> specs, String status) {
        if (table == null || specs == null || specs.isEmpty()) {
            return 0;
        }
        // Serialize concurrent writers on the same catalog table's columns to avoid
        // Postgres row-level deadlocks when two flows (scheduler, REST sync, modeling upsert…)
        // update overlapping rows in different orders. The advisory lock is held for the
        // duration of the surrounding transaction and released on commit/rollback.
        if (table.getId() != null) {
            try {
                columnRepository.acquireTableColumnsLock(advisoryLockKey(table.getId()));
            } catch (Exception ex) {
                LOG.warn("[column-sync] failed to acquire advisory lock for table {}: {}", table.getId(), ex.getMessage());
            }
        }
        String normalizedStatus = StringUtils.hasText(status) ? status.trim().toUpperCase(Locale.ROOT) : null;
        List<CatalogColumnSchema> existing = columnRepository.findByTable(table);
        Map<String, CatalogColumnSchema> byName = new LinkedHashMap<>();
        for (CatalogColumnSchema col : existing) {
            if (col == null || !StringUtils.hasText(col.getName())) continue;
            byName.put(col.getName().trim().toLowerCase(Locale.ROOT), col);
        }
        Set<String> standardCodes = new LinkedHashSet<>();
        for (ColumnSpec spec : specs) {
            if (StringUtils.hasText(spec.standardCode())) {
                standardCodes.add(spec.standardCode().trim().toLowerCase(Locale.ROOT));
            }
        }
        Map<String, DataStandard> standardsByCode = loadStandards(standardCodes);
        // Iterate by a stable order (lower-cased name) so concurrent callers acquire
        // row locks in the same sequence — defense-in-depth alongside the advisory lock.
        List<ColumnSpec> orderedSpecs = new ArrayList<>(specs);
        orderedSpecs.sort((a, b) -> {
            String na = a == null || a.name() == null ? "" : a.name().trim().toLowerCase(Locale.ROOT);
            String nb = b == null || b.name() == null ? "" : b.name().trim().toLowerCase(Locale.ROOT);
            return na.compareTo(nb);
        });
        int updated = 0;
        for (ColumnSpec spec : orderedSpecs) {
            if (spec == null || !StringUtils.hasText(spec.name())) continue;
            String key = spec.name().trim().toLowerCase(Locale.ROOT);
            CatalogColumnSchema column = byName.get(key);
            boolean created = false;
            if (column == null) {
                column = new CatalogColumnSchema();
                column.setTable(table);
                column.setName(spec.name().trim());
                created = true;
            }
            String dataType = StringUtils.hasText(spec.dataType()) ? spec.dataType().trim() : "STRING";
            column.setDataType(dataType);
            if (spec.nullable() != null) {
                column.setNullable(spec.nullable());
            }
            if (StringUtils.hasText(spec.comment())) {
                column.setComment(spec.comment().trim());
            }
            if (StringUtils.hasText(spec.tags())) {
                column.setTags(spec.tags().trim());
            }
            if (StringUtils.hasText(spec.sensitiveTags())) {
                column.setSensitiveTags(spec.sensitiveTags().trim());
            }
            if (StringUtils.hasText(spec.standardCode())) {
                DataStandard standard = standardsByCode.get(spec.standardCode().trim().toLowerCase(Locale.ROOT));
                if (standard != null) {
                    column.setStandardId(standard.getId());
                    column.setStandardMismatchReason(null);
                } else {
                    column.setStandardId(null);
                    column.setStandardMismatchReason("标准码未匹配: " + spec.standardCode().trim());
                }
            }
            if (StringUtils.hasText(spec.standardRule())) {
                column.setStandardRule(spec.standardRule().trim());
            }
            if (StringUtils.hasText(normalizedStatus)) {
                if (STATUS_ACTIVE.equals(normalizedStatus)) {
                    column.setStatus(STATUS_ACTIVE);
                } else if (STATUS_DRAFT.equals(normalizedStatus)) {
                    String current = column.getStatus();
                    if (!STATUS_ACTIVE.equalsIgnoreCase(current)) {
                        column.setStatus(STATUS_DRAFT);
                    }
                } else {
                    column.setStatus(normalizedStatus);
                }
            } else if (!StringUtils.hasText(column.getStatus())) {
                column.setStatus(STATUS_ACTIVE);
            }
            columnRepository.save(column);
            updated += created ? 1 : 1;
        }
        return updated;
    }

    private List<ColumnSpec> parseCsv(Reader reader) throws IOException {
        BufferedReader br = reader instanceof BufferedReader ? (BufferedReader) reader : new BufferedReader(reader);
        String headerLine = br.readLine();
        if (!StringUtils.hasText(headerLine)) {
            return List.of();
        }
        List<String> headers = CsvHelper.parseCsvLine(CsvHelper.stripBom(headerLine));
        Map<String, Integer> idx = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String key = headers.get(i) == null ? "" : headers.get(i).trim().toLowerCase(Locale.ROOT);
            if (!key.isEmpty() && !idx.containsKey(key)) {
                idx.put(key, i);
            }
        }
        List<ColumnSpec> result = new ArrayList<>();
        String line;
        while ((line = br.readLine()) != null) {
            if (line.isBlank()) continue;
            List<String> values = CsvHelper.parseCsvLine(line);
            String name = valueOf(idx, values, "name", "column", "field", "field_name", "column_name");
            if (!StringUtils.hasText(name)) {
                continue;
            }
            String dataType = valueOf(idx, values, "data_type", "type");
            String nullableText = valueOf(idx, values, "nullable", "is_nullable");
            Boolean nullable = parseNullable(nullableText);
            String comment = valueOf(idx, values, "comment", "description", "desc");
            String tags = valueOf(idx, values, "tags");
            String sensitive = valueOf(idx, values, "sensitive_tags", "sensitive");
            String standardCode = valueOf(idx, values, "standard_code", "standard", "data_standard");
            String standardRule = valueOf(idx, values, "standard_rule", "rule");
            result.add(new ColumnSpec(name.trim(), dataType, nullable, comment, tags, sensitive, standardCode, standardRule));
        }
        return result;
    }

    private Map<String, DataStandard> loadStandards(Set<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return Map.of();
        }
        List<DataStandard> standards = dataStandardRepository.findByCodeLowerIn(codes);
        Map<String, DataStandard> result = new LinkedHashMap<>();
        for (DataStandard ds : standards) {
            if (ds == null || !StringUtils.hasText(ds.getCode())) continue;
            result.put(ds.getCode().trim().toLowerCase(Locale.ROOT), ds);
        }
        return result;
    }

    private String valueOf(Map<String, Integer> idx, List<String> values, String... keys) {
        if (idx == null || values == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (key == null) continue;
            Integer pos = idx.get(key.toLowerCase(Locale.ROOT));
            if (pos == null || pos < 0 || pos >= values.size()) {
                continue;
            }
            String value = values.get(pos);
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private Boolean parseNullable(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String normalized = text.trim().toLowerCase(Locale.ROOT);
        if ("true".equals(normalized) || "yes".equals(normalized) || "y".equals(normalized) || "1".equals(normalized)) {
            return Boolean.TRUE;
        }
        if ("false".equals(normalized) || "no".equals(normalized) || "n".equals(normalized) || "0".equals(normalized)) {
            return Boolean.FALSE;
        }
        return null;
    }

    private String safe(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    /**
     * Map a UUID to a stable long suitable for pg_advisory_xact_lock(bigint).
     * Namespace the key so it doesn't collide with other advisory locks elsewhere.
     */
    private static long advisoryLockKey(UUID id) {
        long mixed = id.getMostSignificantBits() ^ id.getLeastSignificantBits();
        // Namespace: arbitrary constant unlikely to collide with other call sites.
        return mixed ^ 0x5C01_0C01_0000_0001L;
    }

    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((k, v) -> result.put(String.valueOf(k), v));
            return result;
        }
        return Map.of();
    }

    public record ColumnSpec(
        String name,
        String dataType,
        Boolean nullable,
        String comment,
        String tags,
        String sensitiveTags,
        String standardCode,
        String standardRule
    ) {}

    private static final class CsvHelper {
        private static String stripBom(String text) {
            if (text == null || text.isEmpty()) return text;
            if (text.charAt(0) == '\uFEFF') {
                return text.substring(1);
            }
            return text;
        }

        private static List<String> parseCsvLine(String line) {
            List<String> out = new ArrayList<>();
            if (line == null) return out;
            StringBuilder current = new StringBuilder();
            boolean quoted = false;
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                if (c == '"') {
                    if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        quoted = !quoted;
                    }
                    continue;
                }
                if (c == ',' && !quoted) {
                    out.add(current.toString());
                    current.setLength(0);
                    continue;
                }
                current.append(c);
            }
            out.add(current.toString());
            return out;
        }
    }
}
