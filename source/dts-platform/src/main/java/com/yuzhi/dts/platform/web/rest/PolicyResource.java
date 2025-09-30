package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.domain.catalog.CatalogAccessPolicy;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogSecureView;
import com.yuzhi.dts.platform.repository.catalog.CatalogAccessPolicyRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogMaskingRuleRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogRowFilterRuleRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogSecureViewRepository;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.security.SecurityViewService;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * Policy and secure view APIs: effective, apply, rebuild.
 */
@RestController
@RequestMapping("/api")
@Transactional
public class PolicyResource {

    private final CatalogDatasetRepository datasetRepo;
    private final CatalogAccessPolicyRepository policyRepo;
    private final CatalogRowFilterRuleRepository rowRepo;
    private final CatalogMaskingRuleRepository maskRepo;
    private final CatalogSecureViewRepository viewRepo;
    private final SecurityViewService securityViewService;
    private final AuditService audit;

    public PolicyResource(
        CatalogDatasetRepository datasetRepo,
        CatalogAccessPolicyRepository policyRepo,
        CatalogRowFilterRuleRepository rowRepo,
        CatalogMaskingRuleRepository maskRepo,
        CatalogSecureViewRepository viewRepo,
        SecurityViewService securityViewService,
        AuditService audit
    ) {
        this.datasetRepo = datasetRepo;
        this.policyRepo = policyRepo;
        this.rowRepo = rowRepo;
        this.maskRepo = maskRepo;
        this.viewRepo = viewRepo;
        this.securityViewService = securityViewService;
        this.audit = audit;
    }

    /**
     * GET /api/policy/effective?datasetId=...
     * Combine AccessPolicy + RowFilterRule + MaskingRule into a single effective spec.
     */
    @GetMapping("/policy/effective")
    public ApiResponse<Map<String, Object>> effective(@RequestParam UUID datasetId) {
        CatalogDataset ds = datasetRepo.findById(datasetId).orElseThrow();
        CatalogAccessPolicy p = policyRepo.findByDataset(ds).orElse(null);
        var rows = rowRepo.findByDataset(ds);
        var masks = maskRepo.findByDataset(ds);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("datasetId", datasetId);
        m.put("allowRoles", p != null ? p.getAllowRoles() : null);
        m.put("defaultMasking", p != null ? p.getDefaultMasking() : null);
        m.put("rowFilter", p != null ? p.getRowFilter() : null);
        m.put(
            "rowFilters",
            rows.stream().map(it -> Map.of("id", it.getId(), "roles", it.getRoles(), "expression", it.getExpression())).toList()
        );
        m.put(
            "maskingRules",
            masks.stream().map(it -> Map.of("id", it.getId(), "column", it.getColumn(), "function", it.getFunction(), "args", it.getArgs())).toList()
        );
        audit.audit("READ", "policy.effective", String.valueOf(datasetId));
        return ApiResponses.ok(m);
    }

    /**
     * POST /api/policy/{datasetId}/apply
     * Generate/refresh secure view entities based on SecurityViewService preview.
     */
    @PostMapping("/policy/{datasetId}/apply")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.CATALOG_ADMIN + "','" + AuthoritiesConstants.ADMIN + "')")
    public ApiResponse<Map<String, Object>> apply(@PathVariable UUID datasetId, @RequestBody(required = false) Map<String, Object> body) {
        CatalogDataset ds = datasetRepo.findById(datasetId).orElseThrow();
        CatalogAccessPolicy p = policyRepo.findByDataset(ds).orElse(null);
        Map<String, String> sqls = securityViewService.previewViews(ds, p);
        List<CatalogSecureView> toSave = new ArrayList<>();
        for (String key : sqls.keySet()) {
            if (!key.startsWith("sv_")) continue; // skip grants
            CatalogSecureView v = new CatalogSecureView();
            v.setDataset(ds);
            v.setViewName(key);
            v.setLevel(
                key.endsWith("_public") ? "PUBLIC" : key.endsWith("_internal") ? "INTERNAL" : key.endsWith("_secret") ? "SECRET" : "TOP_SECRET"
            );
            v.setRefresh(Objects.toString(body != null ? body.get("refresh") : null, "NONE"));
            v.setRowFilter(p != null ? p.getRowFilter() : null);
            toSave.add(v);
        }
        viewRepo.saveAll(toSave);
        audit.audit("APPLY", "policy.views", String.valueOf(datasetId));
        return ApiResponses.ok(Map.of("generated", toSave.size(), "statements", sqls));
    }

    /**
     * POST /api/secure-views/{id}/rebuild
     * Rebuild a single secure view using current policy (returns DDL for operators to apply).
     */
    @PostMapping("/secure-views/{id}/rebuild")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.CATALOG_ADMIN + "','" + AuthoritiesConstants.ADMIN + "')")
    public ApiResponse<Map<String, Object>> rebuild(@PathVariable UUID id) {
        CatalogSecureView v = viewRepo.findById(id).orElseThrow();
        CatalogDataset ds = v.getDataset();
        CatalogAccessPolicy p = policyRepo.findByDataset(ds).orElse(null);
        Map<String, String> sqls = securityViewService.previewViews(ds, p);
        String stmt = sqls.getOrDefault(v.getViewName(), null);
        audit.audit("REBUILD", "policy.view", id.toString());
        return ApiResponses.ok(Map.of("view", v.getViewName(), "statement", stmt));
    }
}

