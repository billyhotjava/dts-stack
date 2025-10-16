package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.query.QueryGateway;
import com.yuzhi.dts.platform.service.security.AccessChecker;
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
    private final AccessChecker accessChecker;
    private final QueryGateway queryGateway;
    private final AuditService audit;

    public SqlResource(
        CatalogDatasetRepository datasetRepo,
        AccessChecker accessChecker,
        QueryGateway queryGateway,
        AuditService audit
    ) {
        this.datasetRepo = datasetRepo;
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

        // 行级过滤逻辑已迁移至统一查询服务，此处直接使用用户输入 SQL。
        String effectiveSql = sql;
        try {
            Map<String, Object> result = queryGateway.execute(effectiveSql);
            audit.audit("EXECUTE", "sql.query", String.valueOf(datasetId));
            return ApiResponses.ok(result);
        } catch (IllegalStateException ex) {
            audit.audit("ERROR", "sql.query", String.valueOf(datasetId));
            return ApiResponses.error(ex.getMessage());
        }
    }

    private UUID parseUuid(Object value) {
        try {
            return value == null ? null : UUID.fromString(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

}
