package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.repository.catalog.CatalogAccessPolicyRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.query.QueryGateway;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sql")
@Transactional
public class SqlResource {

    private final CatalogDatasetRepository datasetRepo;
    private final CatalogAccessPolicyRepository policyRepo;
    private final AccessChecker accessChecker;
    private final QueryGateway queryGateway;
    private final AuditService audit;

    public SqlResource(
        CatalogDatasetRepository datasetRepo,
        CatalogAccessPolicyRepository policyRepo,
        AccessChecker accessChecker,
        QueryGateway queryGateway,
        AuditService audit
    ) {
        this.datasetRepo = datasetRepo;
        this.policyRepo = policyRepo;
        this.accessChecker = accessChecker;
        this.queryGateway = queryGateway;
        this.audit = audit;
    }

    @PostMapping("/query")
    public ApiResponse<Map<String, Object>> query(@RequestBody Map<String, Object> body) {
        UUID datasetId = parseUuid(body.get("datasetId"));
        String sql = Objects.toString(body.get("sql"), null);
        if (datasetId == null || sql == null || sql.isBlank()) {
            return ApiResponses.error("datasetId and sql are required");
        }
        CatalogDataset ds = datasetRepo.findById(datasetId).orElse(null);
        if (ds == null || !accessChecker.canRead(ds)) {
            audit.audit("DENY", "sql.query", String.valueOf(datasetId));
            return ApiResponses.error("Access denied for dataset");
        }

        // If AccessPolicy has rowFilter and we are not using Ranger, push down as a WHERE on outer query
        String effectiveSql = sql;
        var policy = policyRepo.findByDataset(ds).orElse(null);
        boolean usingRanger = "RANGER".equalsIgnoreCase(Objects.toString(ds.getExposedBy(), ""));
        if (!usingRanger && policy != null && policy.getRowFilter() != null && !policy.getRowFilter().isBlank()) {
            effectiveSql = "SELECT * FROM (" + sql + ") t WHERE (" + policy.getRowFilter() + ")";
        }

        Map<String, Object> result = queryGateway.execute(effectiveSql);
        audit.audit("EXECUTE", "sql.query", String.valueOf(datasetId));
        return ApiResponses.ok(result);
    }

    private UUID parseUuid(Object value) {
        try {
            return value == null ? null : UUID.fromString(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }
}

