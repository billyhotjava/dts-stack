package com.yuzhi.dts.platform.web.rest.catalog;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.domain.catalog.*;
import com.yuzhi.dts.platform.domain.modeling.DataStandard;
import com.yuzhi.dts.platform.repository.catalog.*;
import com.yuzhi.dts.platform.repository.modeling.DataStandardRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ApiResponses;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import static com.yuzhi.dts.platform.web.rest.catalog.CatalogResourceHelper.CATALOG_MAINTAINER_EXPRESSION;

@RestController
@RequestMapping("/api/catalog")
public class CatalogSchemaResource {

    private final CatalogDatasetRepository datasetRepo;
    private final CatalogTableSchemaRepository tableRepo;
    private final CatalogColumnSchemaRepository columnRepo;
    private final CatalogRowFilterRuleRepository rowFilterRepo;
    private final CatalogMetadataChangeLogRepository metadataChangeLogRepo;
    private final DataStandardRepository dataStandardRepository;
    private final AuditService audit;
    private final AccessChecker accessChecker;
    private final CatalogResourceHelper helper;

    public CatalogSchemaResource(
        CatalogDatasetRepository datasetRepo,
        CatalogTableSchemaRepository tableRepo,
        CatalogColumnSchemaRepository columnRepo,
        CatalogRowFilterRuleRepository rowFilterRepo,
        CatalogMetadataChangeLogRepository metadataChangeLogRepo,
        DataStandardRepository dataStandardRepository,
        AuditService audit,
        AccessChecker accessChecker,
        CatalogResourceHelper helper
    ) {
        this.datasetRepo = datasetRepo;
        this.tableRepo = tableRepo;
        this.columnRepo = columnRepo;
        this.rowFilterRepo = rowFilterRepo;
        this.metadataChangeLogRepo = metadataChangeLogRepo;
        this.dataStandardRepository = dataStandardRepository;
        this.audit = audit;
        this.accessChecker = accessChecker;
        this.helper = helper;
    }

    // --- Tables ---

    @GetMapping("/tables")
    @Transactional(readOnly = true)
    public ApiResponse<Map<String, Object>> listTables(
        @RequestParam UUID datasetId,
        @RequestParam(required = false) String keyword,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        var ds = datasetRepo.findById(datasetId).orElseThrow();
        String effDept = activeDept != null ? activeDept : helper.claim("dept_code");
        if (!accessChecker.canRead(ds) || !accessChecker.departmentAllowed(ds, effDept)) {
            return ApiResponses.error(com.yuzhi.dts.platform.security.policy.PolicyErrorCodes.RESOURCE_NOT_VISIBLE, "Access denied for dataset");
        }
        var list = tableRepo.findByDataset(ds);
        var filtered = list
            .stream()
            .filter(t -> keyword == null || keyword.isBlank() ||
                (t.getName() != null && t.getName().toLowerCase().contains(keyword.toLowerCase())) ||
                (t.getTags() != null && t.getTags().toLowerCase().contains(keyword.toLowerCase()))
            )
            .toList();
        audit.auditAction("CATALOG_ASSET_VIEW", AuditStage.SUCCESS, String.valueOf(datasetId), null);
        return ApiResponses.ok(Map.of("content", filtered, "total", filtered.size()));
    }

    @PostMapping("/tables")
    @Transactional
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<CatalogTableSchema> createTable(
        @Valid @RequestBody CatalogTableSchema table
    ) {
        var saved = tableRepo.save(table);
        audit.auditAction("CATALOG_ASSET_EDIT", AuditStage.SUCCESS, saved.getId().toString(), null);
        return ApiResponses.ok(saved);
    }

    @PutMapping("/tables/{id}")
    @Transactional
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<CatalogTableSchema> updateTable(
        @PathVariable UUID id,
        @Valid @RequestBody CatalogTableSchema patch
    ) {
        var existing = tableRepo.findById(id).orElseThrow();
        Map<String, Object> before = helper.snapshotTable(existing);
        existing.setName(patch.getName());
        existing.setOwner(patch.getOwner());
        existing.setClassification(patch.getClassification());
        existing.setBizDomain(patch.getBizDomain());
        existing.setTags(patch.getTags());
        var saved = tableRepo.save(existing);
        helper.recordTableMetadataChanges(saved, before, "MANUAL");
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "更新数据表元数据");
        auditPayload.put("tableId", id.toString());
        UUID dsId = saved.getDataset() != null ? saved.getDataset().getId() : null;
        if (dsId != null) {
            auditPayload.put("datasetId", dsId.toString());
        }
        audit.auditAction(
            "CATALOG_METADATA_TABLE_EDIT",
            AuditStage.SUCCESS,
            id.toString(),
            auditPayload
        );
        return ApiResponses.ok(saved);
    }

    @GetMapping("/tables/{id}/standard-mapping/validate")
    @Transactional(readOnly = true)
    public ApiResponse<Map<String, Object>> validateTableStandardMapping(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        CatalogTableSchema table = tableRepo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据表不存在"));
        CatalogDataset dataset = table.getDataset();
        if (dataset != null) {
            String effDept = activeDept != null ? activeDept : helper.claim("dept_code");
            if (!accessChecker.canRead(dataset) || !accessChecker.departmentAllowed(dataset, effDept)) {
                return ApiResponses.error(com.yuzhi.dts.platform.security.policy.PolicyErrorCodes.RESOURCE_NOT_VISIBLE, "Access denied for dataset");
            }
        }

        List<CatalogColumnSchema> columns = columnRepo.findByTable(table);
        Map<UUID, DataStandard> standards = helper.loadStandardsById(columns);

        String warehouseLayer = dataset != null ? helper.trimToNull(dataset.getWarehouseLayer()) : null;
        boolean strictDwd = warehouseLayer != null && "DWD".equalsIgnoreCase(warehouseLayer);

        int totalColumns = columns.size();
        int mappedColumns = 0;
        int unmappedColumns = 0;
        int mismatchedColumns = 0;
        List<Map<String, Object>> issues = new ArrayList<>();

        for (CatalogColumnSchema col : columns) {
            if (col == null) continue;
            UUID standardId = col.getStandardId();
            DataStandard standard = standardId != null ? standards.get(standardId) : null;

            if (standardId == null) {
                unmappedColumns++;
                issues.add(helper.buildStandardIssue(col, null, "未绑定数据元（字段标准）"));
                continue;
            }

            mappedColumns++;
            if (standard == null) {
                mismatchedColumns++;
                issues.add(helper.buildStandardIssue(col, null, "关联的数据元不存在或无权限"));
                continue;
            }

            String typeReason = null;
            String nullableReason = null;

            String standardType = helper.trimToNull(standard.getDataType());
            String columnType = helper.trimToNull(col.getDataType());
            if (standardType != null && columnType != null && !helper.isTypeCompatible(standardType, columnType)) {
                typeReason = "字段类型与数据元不一致";
            }

            Boolean standardNullable = standard.getNullable();
            Boolean columnNullable = col.getNullable();
            if (standardNullable != null && Boolean.FALSE.equals(standardNullable) && Boolean.TRUE.equals(columnNullable)) {
                nullableReason = "字段可空性与数据元不一致（数据元要求不可空）";
            }

            if (typeReason != null || nullableReason != null) {
                mismatchedColumns++;
                String reason = typeReason != null && nullableReason != null ? (typeReason + "；" + nullableReason) : (typeReason != null ? typeReason : nullableReason);
                issues.add(helper.buildStandardIssue(col, standard, reason));
            }
        }

        boolean blocking = strictDwd && (unmappedColumns > 0 || mismatchedColumns > 0);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tableId", id.toString());
        payload.put("datasetId", dataset != null && dataset.getId() != null ? dataset.getId().toString() : null);
        payload.put("warehouseLayer", warehouseLayer);
        payload.put("strictDwd", strictDwd);
        payload.put("blocking", blocking);
        payload.put("totalColumns", totalColumns);
        payload.put("mappedColumns", mappedColumns);
        payload.put("unmappedColumns", unmappedColumns);
        payload.put("mismatchedColumns", mismatchedColumns);
        payload.put("issues", issues);

        audit.auditAction(
            "CATALOG_STANDARD_MAPPING_VALIDATE",
            AuditStage.SUCCESS,
            id.toString(),
            Map.of(
                "summary",
                "校验字段与数据元映射",
                "tableId",
                id.toString(),
                "datasetId",
                dataset != null && dataset.getId() != null ? dataset.getId().toString() : null,
                "warehouseLayer",
                warehouseLayer != null ? warehouseLayer : "",
                "blocking",
                blocking
            )
        );
        return ApiResponses.ok(payload);
    }

    @GetMapping("/tables/{id}/standard-mapping/auto-map/preview")
    @Transactional(readOnly = true)
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> previewAutoMapTableStandardMapping(
        @PathVariable UUID id,
        @RequestParam(name = "overwrite", required = false, defaultValue = "false") boolean overwrite,
        @RequestParam(name = "onlyUnmapped", required = false, defaultValue = "true") boolean onlyUnmapped,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        CatalogTableSchema table = tableRepo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据表不存在"));
        CatalogDataset dataset = table.getDataset();
        if (dataset != null) {
            String effDept = activeDept != null ? activeDept : helper.claim("dept_code");
            if (!accessChecker.canRead(dataset) || !accessChecker.departmentAllowed(dataset, effDept)) {
                return ApiResponses.error(com.yuzhi.dts.platform.security.policy.PolicyErrorCodes.RESOURCE_NOT_VISIBLE, "Access denied for dataset");
            }
        }

        List<CatalogColumnSchema> columns = columnRepo.findByTable(table);
        Map<UUID, DataStandard> standardsById = helper.loadStandardsById(columns);

        Set<String> codeCandidates = new LinkedHashSet<>();
        Map<UUID, String> hintedCodes = new HashMap<>();
        for (CatalogColumnSchema col : columns) {
            if (col == null) continue;
            String hint = helper.extractStandardCodeHint(col.getComment());
            if (hint != null) {
                hintedCodes.put(col.getId(), hint);
                codeCandidates.add(hint.toLowerCase(Locale.ROOT));
            }
            if (StringUtils.hasText(col.getName())) {
                codeCandidates.add(col.getName().trim().toLowerCase(Locale.ROOT));
            }
        }

        Map<String, DataStandard> standardsByCode = new HashMap<>();
        if (!codeCandidates.isEmpty()) {
            for (DataStandard s : dataStandardRepository.findByCodeLowerIn(codeCandidates)) {
                if (s != null && StringUtils.hasText(s.getCode())) {
                    standardsByCode.put(s.getCode().trim().toLowerCase(Locale.ROOT), s);
                }
            }
        }

        int totalColumns = columns.size();
        int matchedColumns = 0;
        int conflictColumns = 0;
        int noMatchColumns = 0;
        int willUpdateColumns = 0;
        int skippedColumns = 0;

        List<Map<String, Object>> items = new ArrayList<>();
        for (CatalogColumnSchema col : columns) {
            if (col == null) continue;
            UUID columnId = col.getId();

            UUID currentStandardId = col.getStandardId();
            DataStandard currentStandard = currentStandardId != null ? standardsById.get(currentStandardId) : null;
            String columnName = helper.trimToNull(col.getName());

            String hinted = columnId != null ? hintedCodes.get(columnId) : null;
            DataStandard byHint = hinted != null ? standardsByCode.get(hinted.trim().toLowerCase(Locale.ROOT)) : null;
            DataStandard byName = columnName != null ? standardsByCode.get(columnName.trim().toLowerCase(Locale.ROOT)) : null;

            DataStandard proposed = null;
            String source = null;
            String status = null;
            String reason = null;

            if (byHint != null && byName != null && byHint.getId() != null && byName.getId() != null && !byHint.getId().equals(byName.getId())) {
                conflictColumns++;
                status = "CONFLICT";
                reason = "注释STD与字段名匹配到不同数据元";
            } else if (byHint != null) {
                proposed = byHint;
                source = byName != null ? "COMMENT+NAME" : "COMMENT";
            } else if (byName != null) {
                proposed = byName;
                source = "COLUMN_NAME";
            }

            if (status == null) {
                if (proposed == null) {
                    noMatchColumns++;
                    status = "NO_MATCH";
                } else {
                    matchedColumns++;
                    if (currentStandardId != null) {
                        if (proposed.getId() != null && proposed.getId().equals(currentStandardId)) {
                            skippedColumns++;
                            status = "ALREADY_OK";
                        } else if (onlyUnmapped) {
                            skippedColumns++;
                            status = "SKIP_MAPPED";
                            reason = "字段已绑定数据元（onlyUnmapped=true）";
                        } else if (!overwrite) {
                            skippedColumns++;
                            status = "SKIP_MAPPED";
                            reason = "字段已绑定数据元（overwrite=false）";
                        } else {
                            willUpdateColumns++;
                            status = "WILL_UPDATE";
                        }
                    } else {
                        willUpdateColumns++;
                        status = "WILL_UPDATE";
                    }
                }
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("columnId", columnId != null ? columnId.toString() : null);
            item.put("columnName", columnName);
            item.put("columnDataType", helper.trimToNull(col.getDataType()));
            item.put("hintedStandardCode", hinted);
            item.put("currentStandardId", currentStandardId != null ? currentStandardId.toString() : null);
            item.put("currentStandardCode", currentStandard != null ? currentStandard.getCode() : null);
            item.put("proposedStandardId", proposed != null && proposed.getId() != null ? proposed.getId().toString() : null);
            item.put("proposedStandardCode", proposed != null ? proposed.getCode() : null);
            item.put("proposedStandardName", proposed != null ? proposed.getName() : null);
            item.put("source", source);
            item.put("status", status);
            item.put("reason", reason);
            item.put("willUpdate", "WILL_UPDATE".equals(status));
            items.add(item);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tableId", id.toString());
        payload.put("datasetId", dataset != null && dataset.getId() != null ? dataset.getId().toString() : null);
        payload.put("totalColumns", totalColumns);
        payload.put("matchedColumns", matchedColumns);
        payload.put("noMatchColumns", noMatchColumns);
        payload.put("conflictColumns", conflictColumns);
        payload.put("willUpdateColumns", willUpdateColumns);
        payload.put("skippedColumns", skippedColumns);
        payload.put("overwrite", overwrite);
        payload.put("onlyUnmapped", onlyUnmapped);
        payload.put("items", items);

        audit.auditAction(
            "CATALOG_STANDARD_MAPPING_AUTOMAP_PREVIEW",
            AuditStage.SUCCESS,
            id.toString(),
            Map.of(
                "summary",
                "预览自动匹配字段与数据元",
                "tableId",
                id.toString(),
                "datasetId",
                dataset != null && dataset.getId() != null ? dataset.getId().toString() : null,
                "willUpdate",
                willUpdateColumns,
                "conflicts",
                conflictColumns
            )
        );
        return ApiResponses.ok(payload);
    }

    @PostMapping("/tables/{id}/standard-mapping/auto-map/apply")
    @Transactional
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> applyAutoMapTableStandardMapping(
        @PathVariable UUID id,
        @RequestBody(required = false) CatalogResourceHelper.StandardAutoMapApplyRequest body,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        boolean overwrite = body != null && body.overwrite() != null && body.overwrite().booleanValue();
        boolean onlyUnmapped = body == null || body.onlyUnmapped() == null || body.onlyUnmapped().booleanValue();

        CatalogTableSchema table = tableRepo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据表不存在"));
        CatalogDataset dataset = table.getDataset();
        if (dataset != null) {
            String effDept = activeDept != null ? activeDept : helper.claim("dept_code");
            if (!accessChecker.canRead(dataset) || !accessChecker.departmentAllowed(dataset, effDept)) {
                return ApiResponses.error(com.yuzhi.dts.platform.security.policy.PolicyErrorCodes.RESOURCE_NOT_VISIBLE, "Access denied for dataset");
            }
        }

        List<CatalogColumnSchema> columns = columnRepo.findByTable(table);
        Map<UUID, DataStandard> standardsById = helper.loadStandardsById(columns);

        Set<String> codeCandidates = new LinkedHashSet<>();
        Map<UUID, String> hintedCodes = new HashMap<>();
        for (CatalogColumnSchema col : columns) {
            if (col == null) continue;
            String hint = helper.extractStandardCodeHint(col.getComment());
            if (hint != null) {
                hintedCodes.put(col.getId(), hint);
                codeCandidates.add(hint.toLowerCase(Locale.ROOT));
            }
            if (StringUtils.hasText(col.getName())) {
                codeCandidates.add(col.getName().trim().toLowerCase(Locale.ROOT));
            }
        }
        Map<String, DataStandard> standardsByCode = new HashMap<>();
        if (!codeCandidates.isEmpty()) {
            for (DataStandard s : dataStandardRepository.findByCodeLowerIn(codeCandidates)) {
                if (s != null && StringUtils.hasText(s.getCode())) {
                    standardsByCode.put(s.getCode().trim().toLowerCase(Locale.ROOT), s);
                }
            }
        }

        int applied = 0;
        int conflicts = 0;
        int skipped = 0;
        List<UUID> updatedIds = new ArrayList<>();

        for (CatalogColumnSchema col : columns) {
            if (col == null || col.getId() == null) continue;
            UUID columnId = col.getId();
            UUID currentStandardId = col.getStandardId();

            String hinted = hintedCodes.get(columnId);
            DataStandard byHint = hinted != null ? standardsByCode.get(hinted.trim().toLowerCase(Locale.ROOT)) : null;
            String columnName = helper.trimToNull(col.getName());
            DataStandard byName = columnName != null ? standardsByCode.get(columnName.trim().toLowerCase(Locale.ROOT)) : null;

            if (byHint != null && byName != null && byHint.getId() != null && byName.getId() != null && !byHint.getId().equals(byName.getId())) {
                conflicts++;
                continue;
            }
            DataStandard proposed = byHint != null ? byHint : byName;
            if (proposed == null || proposed.getId() == null) {
                continue;
            }

            if (currentStandardId != null) {
                if (proposed.getId().equals(currentStandardId)) {
                    skipped++;
                    continue;
                }
                if (onlyUnmapped) {
                    skipped++;
                    continue;
                }
                if (!overwrite) {
                    skipped++;
                    continue;
                }
            }

            Map<String, Object> before = helper.snapshotColumn(col);
            col.setStandardId(proposed.getId());
            CatalogColumnSchema saved = columnRepo.save(col);
            helper.recordColumnMetadataChanges(saved, before, "AUTO_MAP");
            standardsById.put(proposed.getId(), proposed);
            applied++;
            updatedIds.add(saved.getId());
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tableId", id.toString());
        payload.put("datasetId", dataset != null && dataset.getId() != null ? dataset.getId().toString() : null);
        payload.put("applied", applied);
        payload.put("conflicts", conflicts);
        payload.put("skipped", skipped);
        payload.put("updatedColumnIds", updatedIds.stream().map(UUID::toString).toList());

        audit.auditAction(
            "CATALOG_STANDARD_MAPPING_AUTOMAP_APPLY",
            AuditStage.SUCCESS,
            id.toString(),
            Map.of(
                "summary",
                "自动匹配字段与数据元",
                "tableId",
                id.toString(),
                "datasetId",
                dataset != null && dataset.getId() != null ? dataset.getId().toString() : null,
                "applied",
                applied,
                "conflicts",
                conflicts,
                "skipped",
                skipped
            )
        );
        return ApiResponses.ok(payload);
    }

    @GetMapping("/tables/{id}/changes")
    @Transactional(readOnly = true)
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> listTableChanges(
        @PathVariable UUID id,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        tableRepo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据表不存在"));
        Page<CatalogMetadataChangeLog> p = metadataChangeLogRepo.findByObjectTypeIgnoreCaseAndObjectId(
            "TABLE",
            id,
            PageRequest.of(Math.max(0, page), Math.min(200, Math.max(1, size)), Sort.by(Sort.Direction.DESC, "createdDate"))
        );
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("content", p.getContent());
        payload.put("page", page);
        payload.put("size", size);
        payload.put("total", p.getTotalElements());
        audit.auditAction("CATALOG_METADATA_CHANGELOG_VIEW", AuditStage.SUCCESS, id.toString(), Map.of("summary", "查看数据表元数据变更历史", "tableId", id.toString()));
        return ApiResponses.ok(payload);
    }

    @DeleteMapping("/tables/{id}")
    @Transactional
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<Boolean> deleteTable(@PathVariable UUID id) {
        tableRepo.deleteById(id);
        audit.auditAction("CATALOG_ASSET_EDIT", AuditStage.SUCCESS, id.toString(), null);
        return ApiResponses.ok(Boolean.TRUE);
    }

    @PostMapping("/tables/import")
    @Transactional
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> importTables(@RequestBody List<Map<String, Object>> payload) {
        int importedTables = 0;
        int importedColumns = 0;
        for (Map<String, Object> t : payload) {
            Object dsId = t.get("datasetId");
            if (dsId == null) continue;
            UUID datasetId = UUID.fromString(String.valueOf(dsId));
            var ds = datasetRepo.findById(datasetId).orElse(null);
            if (ds == null) continue;
            var table = new CatalogTableSchema();
            table.setDataset(ds);
            table.setName(Objects.toString(t.get("name"), null));
            table.setOwner(Objects.toString(t.get("owner"), null));
            table.setClassification(Objects.toString(t.get("classification"), null));
            table.setBizDomain(Objects.toString(t.get("bizDomain"), null));
            table.setTags(Objects.toString(t.get("tags"), null));
            var savedTable = tableRepo.save(table);
            importedTables++;
            Object cols = t.get("columns");
            if (cols instanceof java.util.List<?> list) {
                for (Object c : list) {
                    if (!(c instanceof Map)) continue;
                    Map<?, ?> cm = (Map<?, ?>) c;
                    var col = new CatalogColumnSchema();
                    col.setTable(savedTable);
                    col.setName(Objects.toString(cm.get("name"), null));
                    col.setDataType(Objects.toString(cm.get("dataType"), null));
                    Object nullable = cm.get("nullable");
                    col.setNullable(nullable == null || Boolean.parseBoolean(String.valueOf(nullable)));
                    col.setTags(Objects.toString(cm.get("tags"), null));
                    String comment = Objects.toString(cm.get("displayName"), null);
                    if (!StringUtils.hasText(comment)) {
                        comment = Objects.toString(cm.get("comment"), null);
                    }
                    if (!StringUtils.hasText(comment)) {
                        comment = Objects.toString(cm.get("description"), null);
                    }
                    col.setComment(StringUtils.hasText(comment) ? comment : null);
                    col.setSensitiveTags(Objects.toString(cm.get("sensitiveTags"), null));
                    columnRepo.save(col);
                    importedColumns++;
                }
            }
        }
        audit.auditAction("CATALOG_ASSET_EDIT", AuditStage.SUCCESS, "tables=" + importedTables + ", cols=" + importedColumns, null);
        return ApiResponses.ok(Map.of("tables", importedTables, "columns", importedColumns));
    }

    // --- Columns ---

    @GetMapping("/columns")
    @Transactional(readOnly = true)
    public ApiResponse<Map<String, Object>> listColumns(
        @RequestParam UUID tableId,
        @RequestParam(required = false) String keyword,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        var table = tableRepo.findById(tableId).orElseThrow();
        CatalogDataset dataset = table.getDataset();
        if (dataset != null) {
            String effDept = activeDept != null ? activeDept : helper.claim("dept_code");
            if (!accessChecker.canRead(dataset) || !accessChecker.departmentAllowed(dataset, effDept)) {
                return ApiResponses.error(com.yuzhi.dts.platform.security.policy.PolicyErrorCodes.RESOURCE_NOT_VISIBLE, "Access denied for dataset");
            }
        }
        var list = columnRepo.findByTable(table);
        var filtered = list
            .stream()
            .filter(c -> keyword == null || keyword.isBlank() ||
                (c.getName() != null && c.getName().toLowerCase().contains(keyword.toLowerCase())) ||
                (c.getTags() != null && c.getTags().toLowerCase().contains(keyword.toLowerCase())) ||
                (c.getSensitiveTags() != null && c.getSensitiveTags().toLowerCase().contains(keyword.toLowerCase())) ||
                (c.getComment() != null && c.getComment().toLowerCase().contains(keyword.toLowerCase()))
            )
            .toList();
        Map<UUID, DataStandard> standards = helper.loadStandardsById(filtered);
        List<Map<String, Object>> content = filtered.stream().map(col -> helper.toColumnDto(col, standards)).toList();
        audit.auditAction("CATALOG_ASSET_VIEW", AuditStage.SUCCESS, String.valueOf(tableId), null);
        return ApiResponses.ok(Map.of("content", content, "total", filtered.size()));
    }

    @PostMapping("/columns")
    @Transactional
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<CatalogColumnSchema> createColumn(
        @Valid @RequestBody CatalogColumnSchema column
    ) {
        var saved = columnRepo.save(column);
        audit.auditAction("CATALOG_ASSET_EDIT", AuditStage.SUCCESS, saved.getId().toString(), null);
        return ApiResponses.ok(saved);
    }

    @PutMapping("/columns/{id}")
    @Transactional
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> updateColumn(
        @PathVariable UUID id,
        @Valid @RequestBody CatalogColumnSchema patch
    ) {
        var existing = columnRepo.findById(id).orElseThrow();
        Map<String, Object> before = helper.snapshotColumn(existing);
        existing.setName(patch.getName());
        existing.setDataType(patch.getDataType());
        existing.setNullable(patch.getNullable());
        existing.setTags(patch.getTags());
        existing.setComment(patch.getComment());
        existing.setSensitiveTags(patch.getSensitiveTags());
        existing.setStandardId(patch.getStandardId());
        existing.setStandardRule(helper.trimToNull(patch.getStandardRule()));
        existing.setStandardMismatchReason(helper.trimToNull(patch.getStandardMismatchReason()));
        var saved = columnRepo.save(existing);
        helper.recordColumnMetadataChanges(saved, before, "MANUAL");
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "更新数据字段元数据");
        auditPayload.put("columnId", id.toString());
        UUID tId = saved.getTable() != null ? saved.getTable().getId() : null;
        if (tId != null) {
            auditPayload.put("tableId", tId.toString());
        }
        audit.auditAction(
            "CATALOG_METADATA_COLUMN_EDIT",
            AuditStage.SUCCESS,
            id.toString(),
            auditPayload
        );
        Map<UUID, DataStandard> standards = helper.loadStandardsById(List.of(saved));
        return ApiResponses.ok(helper.toColumnDto(saved, standards));
    }

    @GetMapping("/columns/{id}/changes")
    @Transactional(readOnly = true)
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> listColumnChanges(
        @PathVariable UUID id,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        columnRepo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据字段不存在"));
        Page<CatalogMetadataChangeLog> p = metadataChangeLogRepo.findByObjectTypeIgnoreCaseAndObjectId(
            "COLUMN",
            id,
            PageRequest.of(Math.max(0, page), Math.min(200, Math.max(1, size)), Sort.by(Sort.Direction.DESC, "createdDate"))
        );
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("content", p.getContent());
        payload.put("page", page);
        payload.put("size", size);
        payload.put("total", p.getTotalElements());
        audit.auditAction(
            "CATALOG_METADATA_CHANGELOG_VIEW",
            AuditStage.SUCCESS,
            id.toString(),
            Map.of("summary", "查看数据字段元数据变更历史", "columnId", id.toString())
        );
        return ApiResponses.ok(payload);
    }

    @DeleteMapping("/columns/{id}")
    @Transactional
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<Boolean> deleteColumn(@PathVariable UUID id) {
        columnRepo.deleteById(id);
        audit.auditAction("CATALOG_ASSET_EDIT", AuditStage.SUCCESS, id.toString(), null);
        return ApiResponses.ok(Boolean.TRUE);
    }

    // --- Row Filters ---

    @GetMapping("/row-filter-rules")
    @Transactional(readOnly = true)
    public ApiResponse<List<CatalogRowFilterRule>> listRowFilters(@RequestParam UUID datasetId) {
        var ds = datasetRepo.findById(datasetId).orElseThrow();
        var list = rowFilterRepo.findByDataset(ds);
        audit.auditAction("CATALOG_ASSET_VIEW", AuditStage.SUCCESS, String.valueOf(datasetId), null);
        return ApiResponses.ok(list);
    }

    @PostMapping("/row-filter-rules")
    @Transactional
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<CatalogRowFilterRule> createRowFilter(
        @Valid @RequestBody CatalogRowFilterRule rule
    ) {
        var saved = rowFilterRepo.save(rule);
        audit.auditAction("CATALOG_ASSET_EDIT", AuditStage.SUCCESS, saved.getId().toString(), null);
        return ApiResponses.ok(saved);
    }

    @PutMapping("/row-filter-rules/{id}")
    @Transactional
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<CatalogRowFilterRule> updateRowFilter(
        @PathVariable UUID id,
        @Valid @RequestBody CatalogRowFilterRule patch
    ) {
        var existing = rowFilterRepo.findById(id).orElseThrow();
        existing.setRoles(patch.getRoles());
        existing.setExpression(patch.getExpression());
        var saved = rowFilterRepo.save(existing);
        audit.auditAction("CATALOG_ASSET_EDIT", AuditStage.SUCCESS, id.toString(), null);
        return ApiResponses.ok(saved);
    }

    @DeleteMapping("/row-filter-rules/{id}")
    @Transactional
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<Boolean> deleteRowFilter(@PathVariable UUID id) {
        rowFilterRepo.deleteById(id);
        audit.auditAction("CATALOG_ASSET_EDIT", AuditStage.SUCCESS, id.toString(), null);
        return ApiResponses.ok(Boolean.TRUE);
    }
}
