package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.domain.explore.ExploreSavedQuery;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import com.yuzhi.dts.platform.repository.explore.ExploreSavedQueryRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/explore")
@Transactional
public class ExploreResource {

    private final ExploreSavedQueryRepository savedRepo;
    private final AuditService audit;
    private final CatalogDatasetRepository datasetRepo;
    private final AccessChecker accessChecker;

    public ExploreResource(ExploreSavedQueryRepository savedRepo, AuditService audit, CatalogDatasetRepository datasetRepo, AccessChecker accessChecker) {
        this.savedRepo = savedRepo;
        this.audit = audit;
        this.datasetRepo = datasetRepo;
        this.accessChecker = accessChecker;
    }

    @PostMapping("/query/preview")
    public ApiResponse<Map<String, Object>> preview(@RequestBody Map<String, Object> body) {
        // Simulate result set
        // Access check based on datasetId if provided
        Object dsId = body.get("datasetId");
        if (dsId != null) {
            try {
                java.util.UUID id = java.util.UUID.fromString(String.valueOf(dsId));
                CatalogDataset ds = datasetRepo.findById(id).orElse(null);
                if (ds == null || !accessChecker.canRead(ds)) {
                    audit.audit("DENY", "explore.preview", String.valueOf(dsId));
                    return ApiResponses.error("Access denied for dataset");
                }
            } catch (Exception ignored) {}
        }
        int cols = 5;
        int rows = 10;
        List<String> headers = new ArrayList<>();
        for (int i = 0; i < cols; i++) headers.add("col_" + (i + 1));
        List<Map<String, Object>> data = new ArrayList<>();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < rows; i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (String h : headers) row.put(h, r.nextInt(1, 1000));
            data.add(row);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("headers", headers);
        result.put("rows", data);
        result.put("durationMs", r.nextInt(50, 400));
        audit.audit("EXECUTE", "explore.preview", Objects.toString(body.get("datasetId"), "unknown"));
        return ApiResponses.ok(result);
    }

    @GetMapping("/saved-queries")
    public ApiResponse<List<Map<String, Object>>> listSaved() {
        var list = savedRepo
            .findAll()
            .stream()
            .map(
                it -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", it.getId());
                    m.put("name", it.getName());
                    m.put("datasetId", it.getDatasetId());
                    return m;
                }
            )
            .collect(Collectors.toList());
        audit.audit("READ", "explore.savedQuery", "list");
        return ApiResponses.ok(list);
    }

    @PostMapping("/saved-queries")
    public ApiResponse<ExploreSavedQuery> createSaved(@RequestBody ExploreSavedQuery item) {
        ExploreSavedQuery saved = savedRepo.save(item);
        audit.audit("CREATE", "explore.savedQuery", saved.getId().toString());
        return ApiResponses.ok(saved);
    }

    @DeleteMapping("/saved-queries/{id}")
    public ApiResponse<Boolean> deleteSaved(@PathVariable UUID id) {
        savedRepo.deleteById(id);
        audit.audit("DELETE", "explore.savedQuery", id.toString());
        return ApiResponses.ok(Boolean.TRUE);
    }

    @GetMapping("/saved-queries/{id}")
    public ApiResponse<ExploreSavedQuery> getSaved(@PathVariable UUID id) {
        var q = savedRepo.findById(id).orElse(null);
        if (q != null) {
            audit.audit("READ", "explore.savedQuery", id.toString());
        }
        return ApiResponses.ok(q);
    }

    @PostMapping("/saved-queries/{id}/run")
    public ApiResponse<Map<String, Object>> runSaved(@PathVariable UUID id) {
        var q = savedRepo.findById(id).orElseThrow();
        if (q.getDatasetId() != null) {
            CatalogDataset ds = datasetRepo.findById(q.getDatasetId()).orElse(null);
            if (ds == null || !accessChecker.canRead(ds)) {
                audit.audit("DENY", "explore.savedQuery.run", String.valueOf(id));
                return ApiResponses.error("Access denied for dataset");
            }
        }
        Map<String, Object> body = new HashMap<>();
        body.put("datasetId", q.getDatasetId());
        body.put("sql", q.getSqlText());
        audit.audit("EXECUTE", "explore.savedQuery.run", id.toString());
        return preview(body);
    }
}
