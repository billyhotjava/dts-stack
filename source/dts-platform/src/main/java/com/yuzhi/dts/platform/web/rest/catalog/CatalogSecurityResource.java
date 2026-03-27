package com.yuzhi.dts.platform.web.rest.catalog;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.domain.catalog.*;
import com.yuzhi.dts.platform.repository.catalog.*;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ApiResponses;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import static com.yuzhi.dts.platform.web.rest.catalog.CatalogResourceHelper.CATALOG_MAINTAINER_EXPRESSION;

@RestController
@RequestMapping("/api/catalog")
public class CatalogSecurityResource {

    private final CatalogDatasetRepository datasetRepo;
    private final CatalogDatasetSecurityMappingRepository datasetSecurityMappingRepo;
    private final CatalogTableSchemaRepository tableRepo;
    private final CatalogColumnSchemaRepository columnRepo;
    private final CatalogDatasetGrantRepository grantRepo;
    private final AuditService audit;
    private final AccessChecker accessChecker;
    private final CatalogResourceHelper helper;

    public CatalogSecurityResource(
        CatalogDatasetRepository datasetRepo,
        CatalogDatasetSecurityMappingRepository datasetSecurityMappingRepo,
        CatalogTableSchemaRepository tableRepo,
        CatalogColumnSchemaRepository columnRepo,
        CatalogDatasetGrantRepository grantRepo,
        AuditService audit,
        AccessChecker accessChecker,
        CatalogResourceHelper helper
    ) {
        this.datasetRepo = datasetRepo;
        this.datasetSecurityMappingRepo = datasetSecurityMappingRepo;
        this.tableRepo = tableRepo;
        this.columnRepo = columnRepo;
        this.grantRepo = grantRepo;
        this.audit = audit;
        this.accessChecker = accessChecker;
        this.helper = helper;
    }

    @GetMapping("/datasets/{id}/security-mapping")
    @Transactional(readOnly = true)
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> getDatasetSecurityMapping(@PathVariable UUID id) {
        datasetRepo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据集不存在"));
        CatalogDatasetSecurityMapping mapping = datasetSecurityMappingRepo.findById(id).orElse(null);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("datasetId", id.toString());
        payload.put("dataLevelField", mapping != null ? mapping.getDataLevelField() : null);
        payload.put("deptField", mapping != null ? mapping.getDeptField() : null);
        audit.auditAction(
            "CATALOG_SECURITY_MAPPING_VIEW",
            AuditStage.SUCCESS,
            id.toString(),
            Map.of("summary", "查看行级安全字段映射", "datasetId", id.toString())
        );
        return ApiResponses.ok(payload);
    }

    @PutMapping("/datasets/{id}/security-mapping")
    @Transactional
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> upsertDatasetSecurityMapping(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        CatalogDataset dataset = datasetRepo
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据集不存在"));
        helper.ensureDatasetEditPermission(dataset);

        List<CatalogTableSchema> tables = tableRepo.findByDataset(dataset);
        if (tables == null || tables.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "数据集尚未采集 Schema，无法配置字段映射");
        }
        Set<String> columnsLower = new HashSet<>();
        for (CatalogTableSchema table : tables) {
            for (CatalogColumnSchema column : columnRepo.findByTable(table)) {
                if (column != null && StringUtils.hasText(column.getName())) {
                    columnsLower.add(column.getName().trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        if (columnsLower.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "数据集尚未采集字段信息，无法配置字段映射");
        }

        String dataLevelField = helper.trimToNull(helper.safeText(body.get("dataLevelField")));
        String deptField = helper.trimToNull(helper.safeText(body.get("deptField")));
        if (dataLevelField != null && !columnsLower.contains(dataLevelField.toLowerCase(Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "数据密级字段不存在：" + dataLevelField);
        }
        if (deptField != null && !columnsLower.contains(deptField.toLowerCase(Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "部门字段不存在：" + deptField);
        }

        CatalogDatasetSecurityMapping mapping = datasetSecurityMappingRepo.findById(id).orElse(null);
        Map<String, Object> before = new LinkedHashMap<>();
        if (mapping != null) {
            before.put("dataLevelField", mapping.getDataLevelField());
            before.put("deptField", mapping.getDeptField());
        }

        if (dataLevelField == null && deptField == null) {
            if (mapping != null) {
                datasetSecurityMappingRepo.delete(mapping);
            }
            audit.auditAction(
                "CATALOG_SECURITY_MAPPING_EDIT",
                AuditStage.SUCCESS,
                id.toString(),
                Map.of("summary", "清除行级安全字段映射", "datasetId", id.toString(), "before", before, "after", Map.of())
            );
            return ApiResponses.ok(Map.of("datasetId", id.toString(), "dataLevelField", null, "deptField", null));
        }

        if (mapping == null) {
            mapping = new CatalogDatasetSecurityMapping();
            mapping.setDatasetId(id);
        }
        mapping.setDataLevelField(dataLevelField);
        mapping.setDeptField(deptField);
        CatalogDatasetSecurityMapping saved = datasetSecurityMappingRepo.save(mapping);

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("dataLevelField", saved.getDataLevelField());
        after.put("deptField", saved.getDeptField());

        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "配置行级安全字段映射");
        auditPayload.put("datasetId", id.toString());
        auditPayload.put("before", before);
        auditPayload.put("after", after);
        audit.auditAction("CATALOG_SECURITY_MAPPING_EDIT", AuditStage.SUCCESS, id.toString(), auditPayload);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("datasetId", id.toString());
        payload.put("dataLevelField", saved.getDataLevelField());
        payload.put("deptField", saved.getDeptField());
        return ApiResponses.ok(payload);
    }

    @GetMapping("/datasets/{id}/grants")
    @Transactional(readOnly = true)
    public ApiResponse<List<Map<String, Object>>> listDatasetGrants(@PathVariable UUID id) {
        CatalogDataset dataset = datasetRepo.findById(id).orElseThrow();
        helper.ensureDatasetEditPermission(dataset);
        List<Map<String, Object>> list = grantRepo
            .findByDatasetIdOrderByCreatedDateAsc(id)
            .stream()
            .map(helper::toGrantDto)
            .toList();
        audit.audit("READ", "catalog.dataset.grant", id.toString());
        return ApiResponses.ok(list);
    }

    @PostMapping("/datasets/{id}/grants")
    @Transactional
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> createDatasetGrant(
        @PathVariable UUID id,
        @RequestBody(required = false) CatalogResourceHelper.DatasetGrantRequest body
    ) {
        CatalogDataset dataset = datasetRepo.findById(id).orElseThrow();
        helper.ensureDatasetEditPermission(dataset);
        if (!helper.canManageGrants()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅研究所数据管理员可分配访问权限");
        }
        if (body == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求体不能为空");
        }
        String rawUsername = body.username();
        if (!StringUtils.hasText(rawUsername)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名不能为空");
        }
        final String username = rawUsername.trim();
        String userId = StringUtils.hasText(body.userId()) ? body.userId().trim() : null;
        final String normalizedUserId = userId;
        final String normalizedUsername = username;
        String displayName = StringUtils.hasText(body.displayName()) ? body.displayName().trim() : null;
        String deptCode = StringUtils.hasText(body.deptCode()) ? body.deptCode().trim() : null;
        if (grantRepo.existsForDatasetAndUser(id, normalizedUserId, normalizedUsername)) {
            CatalogDatasetGrant existing = grantRepo
                .findByDatasetIdOrderByCreatedDateAsc(id)
                .stream()
                .filter(g ->
                    (normalizedUserId != null && normalizedUserId.equals(g.getGranteeId())) ||
                    normalizedUsername.equalsIgnoreCase(g.getGranteeUsername())
                )
                .findFirst()
                .orElse(null);
            return ApiResponses.ok(existing != null ? helper.toGrantDto(existing) : Map.of("username", normalizedUsername, "duplicate", Boolean.TRUE));
        }
        CatalogDatasetGrant grant = new CatalogDatasetGrant();
        grant.setDataset(dataset);
        grant.setGranteeId(normalizedUserId);
        grant.setGranteeUsername(username);
        grant.setGranteeName(displayName);
        grant.setGranteeDept(deptCode);
        CatalogDatasetGrant saved = grantRepo.save(grant);
        audit.audit("CREATE", "catalog.dataset.grant", saved.getId().toString());
        return ApiResponses.ok(helper.toGrantDto(saved));
    }

    @DeleteMapping("/datasets/{datasetId}/grants/{grantId}")
    @Transactional
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<Boolean> deleteDatasetGrant(@PathVariable UUID datasetId, @PathVariable UUID grantId) {
        CatalogDatasetGrant grant = grantRepo.findById(grantId).orElseThrow();
        CatalogDataset dataset = grant.getDataset();
        if (dataset == null || dataset.getId() == null || !datasetId.equals(dataset.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "授权记录与数据集不匹配");
        }
        helper.ensureDatasetEditPermission(dataset);
        if (!helper.canManageGrants()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅研究所数据管理员可修改访问权限");
        }
        grantRepo.deleteById(grantId);
        audit.audit("DELETE", "catalog.dataset.grant", grantId.toString());
        return ApiResponses.ok(Boolean.TRUE);
    }
}
