package com.yuzhi.dts.platform.web.rest.sql;

import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.sql.QueryDatasetService;
import com.yuzhi.dts.platform.service.sql.SavedQueryService;
import com.yuzhi.dts.platform.service.sql.SqlCatalogService;
import com.yuzhi.dts.platform.service.sql.SqlExecutionService;
import com.yuzhi.dts.platform.service.sql.SqlMetadataService;
import com.yuzhi.dts.platform.service.sql.SqlValidationService;
import com.yuzhi.dts.platform.service.sql.dto.CreateQueryDatasetFromExecutionRequest;
import com.yuzhi.dts.platform.service.sql.dto.CreateQueryDatasetVersionRequest;
import com.yuzhi.dts.platform.service.sql.dto.PublishQueryDatasetRequest;
import com.yuzhi.dts.platform.service.sql.dto.QueryDatasetResponse;
import com.yuzhi.dts.platform.service.sql.dto.QueryDatasetVersionResponse;
import com.yuzhi.dts.platform.service.sql.dto.SavedQueryRequest;
import com.yuzhi.dts.platform.service.sql.dto.SavedQueryResponse;
import com.yuzhi.dts.platform.service.sql.dto.SqlCatalogNode;
import com.yuzhi.dts.platform.service.sql.dto.SqlCatalogRequest;
import com.yuzhi.dts.platform.service.sql.dto.SqlResultPageResponse;
import com.yuzhi.dts.platform.service.sql.dto.SqlStatusResponse;
import com.yuzhi.dts.platform.service.sql.dto.SqlSubmitRequest;
import com.yuzhi.dts.platform.service.sql.dto.SqlSubmitResponse;
import com.yuzhi.dts.platform.service.sql.dto.SqlValidateRequest;
import com.yuzhi.dts.platform.service.sql.dto.SqlValidateResponse;
import com.yuzhi.dts.platform.service.sql.dto.TableInfo;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ApiResponses;
import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sql")
public class SqlWorkbenchResource {

    private final SqlCatalogService catalogService;
    private final SqlValidationService validationService;
    private final SqlExecutionService executionService;
    private final SqlMetadataService metadataService;
    private final SavedQueryService savedQueryService;
    private final QueryDatasetService queryDatasetService;
    private final AuditService auditService;

    public SqlWorkbenchResource(
        SqlCatalogService catalogService,
        SqlValidationService validationService,
        SqlExecutionService executionService,
        SqlMetadataService metadataService,
        SavedQueryService savedQueryService,
        QueryDatasetService queryDatasetService,
        AuditService auditService
    ) {
        this.catalogService = catalogService;
        this.validationService = validationService;
        this.executionService = executionService;
        this.metadataService = metadataService;
        this.savedQueryService = savedQueryService;
        this.queryDatasetService = queryDatasetService;
        this.auditService = auditService;
    }

    @PostMapping("/catalog")
    public ApiResponse<SqlCatalogNode> catalog(@RequestBody SqlCatalogRequest request, Principal principal) {
        ApiResponse<SqlCatalogNode> response = ApiResponses.ok(catalogService.fetchTree(request, principal));
        auditService.audit("READ", "sql.workbench.catalog", principal != null ? principal.getName() : "anonymous");
        return response;
    }

    @PostMapping("/validate")
    public ApiResponse<SqlValidateResponse> validate(@RequestBody SqlValidateRequest request, Principal principal) {
        ApiResponse<SqlValidateResponse> response = ApiResponses.ok(validationService.validate(request, principal));
        auditService.audit("READ", "sql.workbench.validate", principal != null ? principal.getName() : "anonymous");
        return response;
    }

    @PostMapping("/submit")
    public ApiResponse<SqlSubmitResponse> submit(
        @RequestBody SqlSubmitRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept,
        Principal principal
    ) {
        ApiResponse<SqlSubmitResponse> response = ApiResponses.ok(executionService.submit(request, principal, activeDept));
        auditService.audit("EXECUTE", "sql.workbench.submit", principal != null ? principal.getName() : "anonymous");
        return response;
    }

    @GetMapping("/status/{id}")
    public ApiResponse<SqlStatusResponse> status(@PathVariable UUID id, Principal principal) {
        ApiResponse<SqlStatusResponse> response = ApiResponses.ok(executionService.status(id, principal));
        auditService.audit("READ", "sql.workbench.status", principal != null ? principal.getName() : "anonymous");
        return response;
    }

    @GetMapping("/result-page/{id}")
    public ApiResponse<SqlResultPageResponse> resultPage(
        @PathVariable UUID id,
        @RequestParam(name = "page", required = false) Integer page,
        @RequestParam(name = "pageSize", required = false) Integer pageSize,
        Principal principal
    ) {
        ApiResponse<SqlResultPageResponse> response = ApiResponses.ok(
            executionService.resultPage(id, page, pageSize, principal)
        );
        auditService.audit("READ", "sql.workbench.result-page", principal != null ? principal.getName() : "anonymous");
        return response;
    }

    @PostMapping("/cancel/{id}")
    public ApiResponse<Boolean> cancel(@PathVariable UUID id, Principal principal) {
        executionService.cancel(id, principal);
        auditService.audit("CANCEL", "sql.workbench.cancel", id.toString());
        return ApiResponses.ok(Boolean.TRUE);
    }

    @GetMapping("/tables/{datasourceId}")
    public ApiResponse<List<TableInfo>> listTables(
        @PathVariable UUID datasourceId,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept,
        Principal principal
    ) {
        List<TableInfo> tables = metadataService.listTables(datasourceId, activeDept);
        auditService.audit("READ", "sql.workbench.tables", principal != null ? principal.getName() : "anonymous");
        return ApiResponses.ok(tables);
    }

    @GetMapping("/columns/{datasourceId}")
    public ApiResponse<List<Map<String, String>>> listColumns(
        @PathVariable UUID datasourceId,
        @RequestParam String schema,
        @RequestParam String table,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept,
        Principal principal
    ) {
        List<Map<String, String>> columns = metadataService.listColumns(datasourceId, schema, table, activeDept);
        auditService.audit("READ", "sql.workbench.columns", principal != null ? principal.getName() : "anonymous");
        return ApiResponses.ok(columns);
    }

    @PostMapping("/saved-queries")
    public ApiResponse<SavedQueryResponse> saveQuery(@RequestBody SavedQueryRequest request, Principal principal) {
        SavedQueryResponse response = savedQueryService.save(request, principal);
        auditService.audit("CREATE", "sql.workbench.saved-query", principal != null ? principal.getName() : "anonymous");
        return ApiResponses.ok(response);
    }

    @PutMapping("/saved-queries/{id}")
    public ApiResponse<SavedQueryResponse> updateQuery(
        @PathVariable UUID id,
        @RequestBody SavedQueryRequest request,
        Principal principal
    ) {
        SavedQueryResponse response = savedQueryService.update(id, request, principal);
        auditService.audit("UPDATE", "sql.workbench.saved-query", principal != null ? principal.getName() : "anonymous");
        return ApiResponses.ok(response);
    }

    @GetMapping("/saved-queries")
    public ApiResponse<List<SavedQueryResponse>> listSavedQueries(Principal principal) {
        List<SavedQueryResponse> queries = savedQueryService.list(principal);
        return ApiResponses.ok(queries);
    }

    @GetMapping("/saved-queries/{id}")
    public ApiResponse<SavedQueryResponse> getSavedQuery(@PathVariable UUID id) {
        SavedQueryResponse response = savedQueryService.get(id);
        return ApiResponses.ok(response);
    }

    @DeleteMapping("/saved-queries/{id}")
    public ApiResponse<Boolean> deleteSavedQuery(@PathVariable UUID id, Principal principal) {
        savedQueryService.delete(id, principal);
        auditService.audit("DELETE", "sql.workbench.saved-query", principal != null ? principal.getName() : "anonymous");
        return ApiResponses.ok(Boolean.TRUE);
    }

    @GetMapping("/query-datasets")
    public ApiResponse<List<QueryDatasetResponse>> listQueryDatasets(
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        return ApiResponses.ok(queryDatasetService.list(activeDept));
    }

    @GetMapping("/query-datasets/{id}/versions")
    public ApiResponse<List<QueryDatasetVersionResponse>> listQueryDatasetVersions(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        return ApiResponses.ok(queryDatasetService.listVersions(id, activeDept));
    }

    @PostMapping("/query-datasets/from-execution/{executionId}")
    public ApiResponse<QueryDatasetResponse> createQueryDatasetFromExecution(
        @PathVariable UUID executionId,
        @RequestBody(required = false) CreateQueryDatasetFromExecutionRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        QueryDatasetResponse response = queryDatasetService.createFromExecution(executionId, request, activeDept);
        auditService.audit("CREATE", "sql.workbench.query-dataset", response.id().toString());
        return ApiResponses.ok(response);
    }

    @PostMapping("/query-datasets/{id}/versions")
    public ApiResponse<QueryDatasetVersionResponse> createQueryDatasetVersion(
        @PathVariable UUID id,
        @RequestBody CreateQueryDatasetVersionRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        QueryDatasetVersionResponse response = queryDatasetService.createVersion(id, request, activeDept);
        auditService.audit("UPDATE", "sql.workbench.query-dataset.version", response.id().toString());
        return ApiResponses.ok(response);
    }

    @PostMapping("/query-datasets/{id}/publish")
    public ApiResponse<QueryDatasetVersionResponse> publishQueryDatasetVersion(
        @PathVariable UUID id,
        @RequestBody(required = false) PublishQueryDatasetRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        QueryDatasetVersionResponse response = queryDatasetService.publish(id, request, activeDept);
        auditService.audit("PUBLISH", "sql.workbench.query-dataset", response.id().toString());
        return ApiResponses.ok(response);
    }

    @PostMapping("/query-datasets/{id}/archive")
    public ApiResponse<QueryDatasetResponse> archiveQueryDataset(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        QueryDatasetResponse response = queryDatasetService.archive(id, activeDept);
        auditService.audit("ARCHIVE", "sql.workbench.query-dataset", response.id().toString());
        return ApiResponses.ok(response);
    }

    @PostMapping("/audit/copy")
    public ApiResponse<Boolean> auditCopy(@RequestBody Map<String, Object> payload, Principal principal) {
        String username = principal != null ? principal.getName() : "anonymous";
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "复制查询结果");
        auditPayload.put("rowCount", payload.get("rowCount"));
        auditPayload.put("columnCount", payload.get("columnCount"));
        if (payload.get("executionId") != null) {
            auditPayload.put("executionId", payload.get("executionId"));
        }
        auditService.record("EXPORT", "sql.workbench", "sql.result.copy", username, "SUCCESS", auditPayload);
        return ApiResponses.ok(Boolean.TRUE);
    }
}
