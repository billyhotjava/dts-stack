package com.yuzhi.dts.platform.web.rest.catalog;

import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.service.catalog.CatalogConsumerClassificationService;
import com.yuzhi.dts.platform.service.catalog.CatalogConsumerClassificationService.AccessBindingView;
import com.yuzhi.dts.platform.service.catalog.CatalogConsumerClassificationService.BindAccessCommand;
import com.yuzhi.dts.platform.service.catalog.CatalogConsumerClassificationService.DerivationResult;
import com.yuzhi.dts.platform.service.catalog.CatalogConsumerClassificationService.DeriveCommand;
import com.yuzhi.dts.platform.service.catalog.CatalogConsumerClassificationService.ExportSeal;
import com.yuzhi.dts.platform.service.catalog.CatalogConsumerClassificationService.SubjectRef;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ApiResponses;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog/classifications/consumers")
public class CatalogConsumerClassificationResource {

    private static final String WRITE =
        "hasAnyAuthority('ROLE_ADMIN','ROLE_OP_ADMIN','ROLE_GOV_ADMIN','ROLE_DATA_STEWARD','ROLE_INFRA_ADMIN','" +
        AuthoritiesConstants.SERVICE_INTERNAL +
        "')";

    private final CatalogConsumerClassificationService service;

    public CatalogConsumerClassificationResource(CatalogConsumerClassificationService service) {
        this.service = service;
    }

    @PostMapping("/derive")
    @PreAuthorize(WRITE)
    public ApiResponse<DerivationResult> derive(@RequestBody DeriveCommand command) {
        return ApiResponses.ok(service.derive(command));
    }

    /** Resolve read-only SQL to physical assets; never execute the query or infer a warehouse-wide level. */
    @PostMapping("/sql-sources")
    @PreAuthorize(WRITE)
    public ApiResponse<List<SubjectRef>> sqlSources(@RequestBody SqlSourcesRequest request) {
        if (request.sources() == null || request.sources().isEmpty() || request.sources().size() > 256) {
            throw new IllegalArgumentException("SQL 数据来源数量必须在 1 到 256 之间");
        }
        var subjects = new java.util.LinkedHashSet<SubjectRef>();
        for (SqlSource source : request.sources()) {
            if (source.sourceId() == null || source.sql() == null || source.sql().length() > 100000) {
                throw new IllegalArgumentException("SQL 数据来源缺少连接身份或查询过长");
            }
            String sql = source.sql().replaceAll("\\{\\{\\s*[A-Za-z_][A-Za-z0-9_]*\\s*}}", "NULL");
            var tables = com.yuzhi.dts.platform.service.governance.QualitySqlScopeValidator.modelingReadTables(sql);
            for (String table : tables) {
                // Require explicit schema identity. Unknown or dynamic names fail closed.
                String name = table.replace("\"", "").replace("`", "");
                if (!name.matches("[A-Za-z_][A-Za-z0-9_$]*\\.[A-Za-z_][A-Za-z0-9_$]*")) {
                    throw new IllegalArgumentException("SQL 数据来源需要明确的库表名称：" + table);
                }
                String[] parts = name.split("\\.");
                subjects.add(new SubjectRef("ASSET",
                    com.yuzhi.dts.platform.service.catalog.CatalogAssetKey.dataset(
                        source.sourceId(), null, parts[0], parts[1], null)));
            }
        }
        return ApiResponses.ok(List.copyOf(subjects));
    }

    public record SqlSource(java.util.UUID sourceId, String sql) {}
    public record SqlSourcesRequest(List<SqlSource> sources) {}

    @GetMapping("/explain")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<DerivationResult> explain(
        @RequestParam String consumerType,
        @RequestParam String consumerKey
    ) {
        return ApiResponses.ok(service.explain(consumerType, consumerKey));
    }

    @GetMapping("/guard")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<DerivationResult> guardConsumer(
        @RequestParam String consumerType,
        @RequestParam String consumerKey
    ) {
        return ApiResponses.ok(service.requireCurrentConsumer(consumerType, consumerKey));
    }

    @PostMapping("/access-bindings")
    @PreAuthorize(WRITE)
    public ApiResponse<AccessBindingView> bind(@RequestBody BindAccessCommand command) {
        return ApiResponses.ok(service.bindAccess(command, actor()));
    }

    @GetMapping("/access-bindings/guard")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<AccessBindingView> guard(
        @RequestParam String bindingType,
        @RequestParam String bindingKey
    ) {
        return ApiResponses.ok(service.requireCurrentAccessBinding(bindingType, bindingKey));
    }

    @PostMapping("/exports/seal")
    @PreAuthorize(WRITE)
    public ApiResponse<ExportSeal> sealExport(@RequestBody ExportSealRequest request) {
        return ApiResponses.ok(
            service.sealExport(
                request.fileSubjectKey(),
                request.upstreams(),
                request.originRef()
            )
        );
    }

    private String actor() {
        return SecurityUtils.getCurrentUserLogin().orElse("system");
    }

    public record ExportSealRequest(
        String fileSubjectKey,
        List<SubjectRef> upstreams,
        String originRef
    ) {}
}
