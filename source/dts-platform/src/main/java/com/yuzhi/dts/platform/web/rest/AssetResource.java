package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.domain.catalog.CatalogColumnSchema;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogTableSchema;
import com.yuzhi.dts.platform.repository.catalog.CatalogColumnSchemaRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogMaskingRuleRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogRowFilterRuleRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTableSchemaRepository;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import jakarta.validation.Valid;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * Asset APIs aligned with .cotmp definitions, delegating to catalog repositories.
 */
@RestController
@RequestMapping("/api")
@Transactional
public class AssetResource {

    private final CatalogDatasetRepository datasetRepo;
    private final CatalogTableSchemaRepository tableRepo;
    private final CatalogColumnSchemaRepository columnRepo;
    private final CatalogRowFilterRuleRepository rowFilterRepo;
    private final CatalogMaskingRuleRepository maskingRepo;
    private final AccessChecker accessChecker;
    private final AuditService audit;

    public AssetResource(
        CatalogDatasetRepository datasetRepo,
        CatalogTableSchemaRepository tableRepo,
        CatalogColumnSchemaRepository columnRepo,
        CatalogRowFilterRuleRepository rowFilterRepo,
        CatalogMaskingRuleRepository maskingRepo,
        AccessChecker accessChecker,
        AuditService audit
    ) {
        this.datasetRepo = datasetRepo;
        this.tableRepo = tableRepo;
        this.columnRepo = columnRepo;
        this.rowFilterRepo = rowFilterRepo;
        this.maskingRepo = maskingRepo;
        this.accessChecker = accessChecker;
        this.audit = audit;
    }

    /**
     * POST /api/datasets/{id}/sync-schema
     * MVP: if dataset has no tables, create one using hiveTable/name and a few example columns.
     */
    @PostMapping("/datasets/{id}/sync-schema")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.CATALOG_ADMIN + "','" + AuthoritiesConstants.ADMIN + "')")
    public ApiResponse<Map<String, Object>> syncSchema(@PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        CatalogDataset ds = datasetRepo.findById(id).orElseThrow();
        // If already has tables, do nothing (idempotent MVP)
        List<CatalogTableSchema> existing = tableRepo.findByDataset(ds);
        int importedTables = 0;
        int importedColumns = 0;
        if (existing.isEmpty()) {
            CatalogTableSchema table = new CatalogTableSchema();
            table.setDataset(ds);
            String name = (ds.getHiveTable() != null && !ds.getHiveTable().isBlank()) ? ds.getHiveTable() : ds.getName();
            table.setName(name);
            CatalogTableSchema saved = tableRepo.save(table);
            importedTables++;

            // create example columns, or from body.columns if provided
            List<Map<String, Object>> cols;
            Object colsObj = body != null ? body.get("columns") : null;
            if (colsObj instanceof List<?>) {
                cols = ((List<?>) colsObj)
                    .stream()
                    .filter(Map.class::isInstance)
                    .map(m -> (Map<String, Object>) m)
                    .collect(Collectors.toList());
            } else {
                cols = List.of(
                    Map.of("name", "id", "dataType", "string", "nullable", false),
                    Map.of("name", "name", "dataType", "string", "nullable", true),
                    Map.of("name", "email", "dataType", "string", "nullable", true),
                    Map.of("name", "created_at", "dataType", "timestamp", "nullable", true),
                    Map.of("name", "level", "dataType", "string", "nullable", true)
                );
            }
            for (Map<String, Object> c : cols) {
                CatalogColumnSchema col = new CatalogColumnSchema();
                col.setTable(saved);
                col.setName(Objects.toString(c.get("name"), null));
                col.setDataType(Objects.toString(c.get("dataType"), "string"));
                Object nullable = c.get("nullable");
                col.setNullable(nullable == null || Boolean.parseBoolean(String.valueOf(nullable)));
                col.setTags(Objects.toString(c.get("tags"), null));
                col.setSensitiveTags(Objects.toString(c.get("sensitiveTags"), null));
                columnRepo.save(col);
                importedColumns++;
            }
        }
        Map<String, Object> resp = Map.of("tables", importedTables, "columns", importedColumns);
        audit.audit("SYNC", "dataset.schema", id.toString());
        return ApiResponses.ok(resp);
    }

    /**
     * GET /api/datasets/{id}/preview?rows=50
     * Returns sample rows applying simple masking and noting row filter presence.
     */
    @GetMapping("/datasets/{id}/preview")
    public ApiResponse<Map<String, Object>> preview(@PathVariable UUID id, @RequestParam(defaultValue = "50") int rows) {
        CatalogDataset ds = datasetRepo.findById(id).orElseThrow();
        if (!accessChecker.canRead(ds)) {
            audit.audit("DENY", "dataset.preview", id.toString());
            return ApiResponses.error("Access denied");
        }

        // headers based on first table columns if exists
        List<CatalogTableSchema> tables = tableRepo.findByDataset(ds);
        List<String> headers = new ArrayList<>();
        if (!tables.isEmpty()) {
            List<CatalogColumnSchema> cols = columnRepo.findByTable(tables.get(0));
            for (CatalogColumnSchema c : cols) headers.add(c.getName());
        }
        if (headers.isEmpty()) headers = List.of("id", "name", "email", "level");

        // masking rules by dataset
        var masking = maskingRepo.findByDataset(ds);
        Map<String, String> maskingMap = new HashMap<>(); // column -> function
        for (var m : masking) maskingMap.put(m.getColumn(), m.getFunction());

        // generate sample rows
        ThreadLocalRandom r = ThreadLocalRandom.current();
        List<Map<String, Object>> data = new ArrayList<>();
        int n = Math.max(1, Math.min(rows, 200));
        for (int i = 0; i < n; i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (String h : headers) {
                Object v;
                if (h.toLowerCase().contains("id")) v = UUID.randomUUID().toString().substring(0, 8);
                else if (h.toLowerCase().contains("email")) v = "user" + r.nextInt(10, 99) + "@example.com";
                else if (h.equalsIgnoreCase("level")) v = List.of("PUBLIC", "INTERNAL", "SECRET").get(r.nextInt(0, 3));
                else v = "v" + r.nextInt(1, 1000);
                // apply simple masking if configured
                String fn = maskingMap.get(h);
                if (fn != null) v = applyMask(fn, String.valueOf(v));
                row.put(h, v);
            }
            data.add(row);
        }

        int filtered = data.size(); // placeholder; real row filter pushdown not executed here
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("headers", headers);
        result.put("rows", data);
        result.put("rowCount", filtered);
        audit.audit("READ", "dataset.preview", id.toString());
        return ApiResponses.ok(result);
    }

    private Object applyMask(String function, String value) {
        return switch (function == null ? "" : function.toLowerCase()) {
            case "hash" -> Integer.toHexString(Objects.hashCode(value));
            case "mask_email" -> value.replaceAll("(^.).*(@.*$)", "$1***$2");
            case "mask_phone" -> value.replaceAll("(\\\\d{3})\\\\d{4}(\\\\d{4})", "$1****$2");
            case "partial" -> value.length() <= 2 ? "*".repeat(value.length()) : value.charAt(0) + "***" + value.charAt(value.length() - 1);
            default -> value;
        };
    }
}
