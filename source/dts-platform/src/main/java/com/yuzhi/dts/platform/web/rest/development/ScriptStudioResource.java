package com.yuzhi.dts.platform.web.rest.development;

import com.yuzhi.dts.platform.service.development.ScriptStudioService;
import com.yuzhi.dts.platform.service.development.dto.ScriptAssetResponse;
import com.yuzhi.dts.platform.service.development.dto.ScriptCreateRequest;
import com.yuzhi.dts.platform.service.development.dto.ScriptRunRequest;
import com.yuzhi.dts.platform.service.development.dto.ScriptRunResponse;
import com.yuzhi.dts.platform.service.development.dto.ScriptSaveVersionRequest;
import com.yuzhi.dts.platform.service.development.dto.ScriptUpdateRequest;
import com.yuzhi.dts.platform.service.development.dto.ScriptVersionResponse;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ApiResponses;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/development/scripts")
@PreAuthorize("hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).CATALOG_MAINTAINERS)")
public class ScriptStudioResource {

    private final ScriptStudioService scriptStudioService;

    public ScriptStudioResource(ScriptStudioService scriptStudioService) {
        this.scriptStudioService = scriptStudioService;
    }

    @GetMapping
    public ApiResponse<List<ScriptAssetResponse>> listScripts(
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        return ApiResponses.ok(scriptStudioService.listScripts(activeDept));
    }

    @PostMapping
    public ApiResponse<ScriptAssetResponse> createScript(
        @RequestBody ScriptCreateRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept,
        Principal principal
    ) {
        return ApiResponses.ok(scriptStudioService.createScript(request, activeDept, principal));
    }

    @PutMapping("/{scriptId}")
    public ApiResponse<ScriptAssetResponse> updateScript(
        @PathVariable UUID scriptId,
        @RequestBody ScriptUpdateRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept,
        Principal principal
    ) {
        return ApiResponses.ok(scriptStudioService.updateScript(scriptId, request, activeDept, principal));
    }

    @GetMapping("/{scriptId}/versions")
    public ApiResponse<List<ScriptVersionResponse>> listVersions(
        @PathVariable UUID scriptId,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        return ApiResponses.ok(scriptStudioService.listVersions(scriptId, activeDept));
    }

    @PostMapping("/{scriptId}/versions")
    public ApiResponse<ScriptVersionResponse> saveVersion(
        @PathVariable UUID scriptId,
        @RequestBody ScriptSaveVersionRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept,
        Principal principal
    ) {
        return ApiResponses.ok(scriptStudioService.saveVersion(scriptId, request, activeDept, principal));
    }

    @GetMapping("/{scriptId}/runs")
    public ApiResponse<List<ScriptRunResponse>> listRuns(
        @PathVariable UUID scriptId,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        return ApiResponses.ok(scriptStudioService.listRuns(scriptId, activeDept));
    }

    @PostMapping("/{scriptId}/run")
    public ApiResponse<ScriptRunResponse> runScript(
        @PathVariable UUID scriptId,
        @RequestBody(required = false) ScriptRunRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept,
        Principal principal
    ) {
        return ApiResponses.ok(scriptStudioService.runScript(scriptId, request, activeDept, principal));
    }

    @GetMapping("/runs/{runId}")
    public ApiResponse<ScriptRunResponse> getRun(
        @PathVariable UUID runId,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        return ApiResponses.ok(scriptStudioService.getRun(runId, activeDept));
    }
}
