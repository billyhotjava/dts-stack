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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/development/scripts")
public class ScriptStudioResource {

    private final ScriptStudioService scriptStudioService;

    public ScriptStudioResource(ScriptStudioService scriptStudioService) {
        this.scriptStudioService = scriptStudioService;
    }

    @GetMapping
    public ApiResponse<List<ScriptAssetResponse>> listScripts() {
        return ApiResponses.ok(scriptStudioService.listScripts());
    }

    @PostMapping
    public ApiResponse<ScriptAssetResponse> createScript(@RequestBody ScriptCreateRequest request, Principal principal) {
        return ApiResponses.ok(scriptStudioService.createScript(request, principal));
    }

    @PutMapping("/{scriptId}")
    public ApiResponse<ScriptAssetResponse> updateScript(
        @PathVariable UUID scriptId,
        @RequestBody ScriptUpdateRequest request,
        Principal principal
    ) {
        return ApiResponses.ok(scriptStudioService.updateScript(scriptId, request, principal));
    }

    @GetMapping("/{scriptId}/versions")
    public ApiResponse<List<ScriptVersionResponse>> listVersions(@PathVariable UUID scriptId) {
        return ApiResponses.ok(scriptStudioService.listVersions(scriptId));
    }

    @PostMapping("/{scriptId}/versions")
    public ApiResponse<ScriptVersionResponse> saveVersion(
        @PathVariable UUID scriptId,
        @RequestBody ScriptSaveVersionRequest request,
        Principal principal
    ) {
        return ApiResponses.ok(scriptStudioService.saveVersion(scriptId, request, principal));
    }

    @GetMapping("/{scriptId}/runs")
    public ApiResponse<List<ScriptRunResponse>> listRuns(@PathVariable UUID scriptId) {
        return ApiResponses.ok(scriptStudioService.listRuns(scriptId));
    }

    @PostMapping("/{scriptId}/run")
    public ApiResponse<ScriptRunResponse> runScript(
        @PathVariable UUID scriptId,
        @RequestBody(required = false) ScriptRunRequest request,
        Principal principal
    ) {
        return ApiResponses.ok(scriptStudioService.runScript(scriptId, request, principal));
    }

    @GetMapping("/runs/{runId}")
    public ApiResponse<ScriptRunResponse> getRun(@PathVariable UUID runId) {
        return ApiResponses.ok(scriptStudioService.getRun(runId));
    }
}
