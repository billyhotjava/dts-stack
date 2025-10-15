package com.yuzhi.dts.admin.web.rest;

import com.yuzhi.dts.admin.security.AuthoritiesConstants;
import com.yuzhi.dts.admin.security.SecurityUtils;
import com.yuzhi.dts.admin.service.infra.InfraAdminService;
import com.yuzhi.dts.admin.service.infra.dto.ConnectionTestLogDto;
import com.yuzhi.dts.admin.service.infra.dto.HiveConnectionPersistRequest;
import com.yuzhi.dts.admin.service.infra.dto.HiveConnectionTestRequest;
import com.yuzhi.dts.admin.service.infra.dto.HiveConnectionTestResult;
import com.yuzhi.dts.admin.service.infra.dto.InfraDataSourceDto;
import com.yuzhi.dts.admin.service.infra.dto.InfraFeatureFlags;
import com.yuzhi.dts.admin.service.infra.dto.UpsertInfraDataSourcePayload;
import com.yuzhi.dts.admin.web.rest.api.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/infra")
public class InfraResource {

    private final InfraAdminService infraAdminService;

    public InfraResource(InfraAdminService infraAdminService) {
        this.infraAdminService = infraAdminService;
    }

    @GetMapping("/data-sources")
    public ResponseEntity<ApiResponse<List<InfraDataSourceDto>>> listDataSources() {
        return ResponseEntity.ok(ApiResponse.ok(infraAdminService.listDataSources()));
    }

    @PostMapping("/data-sources")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "','" + AuthoritiesConstants.SYS_ADMIN + "','" + AuthoritiesConstants.OP_ADMIN + "')")
    public ResponseEntity<ApiResponse<InfraDataSourceDto>> createDataSource(@Valid @RequestBody UpsertInfraDataSourcePayload payload) {
        String actor = SecurityUtils.getCurrentUserLogin().orElse("system");
        InfraDataSourceDto saved = infraAdminService.createDataSource(payload, actor);
        return ResponseEntity.ok(ApiResponse.ok(saved));
    }

    @PutMapping("/data-sources/{id}")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "','" + AuthoritiesConstants.SYS_ADMIN + "','" + AuthoritiesConstants.OP_ADMIN + "')")
    public ResponseEntity<ApiResponse<InfraDataSourceDto>> updateDataSource(
        @PathVariable UUID id,
        @Valid @RequestBody UpsertInfraDataSourcePayload payload
    ) {
        String actor = SecurityUtils.getCurrentUserLogin().orElse("system");
        Optional<InfraDataSourceDto> updated = infraAdminService.updateDataSource(id, payload, actor);
        return updated
            .map(body -> ResponseEntity.ok(ApiResponse.ok(body)))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据源不存在"));
    }

    @DeleteMapping("/data-sources/{id}")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "','" + AuthoritiesConstants.SYS_ADMIN + "','" + AuthoritiesConstants.OP_ADMIN + "')")
    public ResponseEntity<ApiResponse<Boolean>> deleteDataSource(@PathVariable UUID id) {
        boolean removed = infraAdminService.deleteDataSource(id);
        if (!removed) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "数据源不存在");
        }
        return ResponseEntity.ok(ApiResponse.ok(Boolean.TRUE));
    }

    @GetMapping("/data-sources/test-logs")
    public ResponseEntity<ApiResponse<List<ConnectionTestLogDto>>> recentTestLogs(@RequestParam(required = false) UUID dataSourceId) {
        return ResponseEntity.ok(ApiResponse.ok(infraAdminService.recentTestLogs(dataSourceId)));
    }

    @PostMapping("/data-sources/test-connection")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "','" + AuthoritiesConstants.SYS_ADMIN + "','" + AuthoritiesConstants.OP_ADMIN + "')")
    public ResponseEntity<ApiResponse<HiveConnectionTestResult>> testConnection(@Valid @RequestBody HiveConnectionTestRequest request) {
        HiveConnectionTestResult result = infraAdminService.testDataSourceConnection(request, null);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @PostMapping("/data-sources/inceptor/publish")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "','" + AuthoritiesConstants.SYS_ADMIN + "','" + AuthoritiesConstants.OP_ADMIN + "')")
    public ResponseEntity<ApiResponse<InfraDataSourceDto>> publishInceptor(@Valid @RequestBody HiveConnectionPersistRequest request) {
        String actor = SecurityUtils.getCurrentUserLogin().orElse("system");
        InfraDataSourceDto saved = infraAdminService.publishInceptor(request, actor);
        return ResponseEntity.ok(ApiResponse.ok(saved));
    }

    @PostMapping("/data-sources/inceptor/refresh")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "','" + AuthoritiesConstants.SYS_ADMIN + "','" + AuthoritiesConstants.OP_ADMIN + "')")
    public ResponseEntity<ApiResponse<InfraFeatureFlags>> refreshInceptor() {
        String actor = SecurityUtils.getCurrentUserLogin().orElse("system");
        InfraFeatureFlags flags = infraAdminService.refreshInceptorRegistry(actor);
        return ResponseEntity.ok(ApiResponse.ok(flags));
    }

    @GetMapping("/features")
    public ResponseEntity<ApiResponse<InfraFeatureFlags>> features() {
        return ResponseEntity.ok(ApiResponse.ok(infraAdminService.computeFeatureFlags()));
    }
}
