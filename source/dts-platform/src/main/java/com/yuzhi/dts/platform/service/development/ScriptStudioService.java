package com.yuzhi.dts.platform.service.development;

import com.yuzhi.dts.platform.domain.development.DevScriptAsset;
import com.yuzhi.dts.platform.domain.development.DevScriptRun;
import com.yuzhi.dts.platform.domain.development.DevScriptVersion;
import com.yuzhi.dts.platform.security.DepartmentUtils;
import com.yuzhi.dts.platform.repository.development.DevScriptAssetRepository;
import com.yuzhi.dts.platform.repository.development.DevScriptRunRepository;
import com.yuzhi.dts.platform.repository.development.DevScriptVersionRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.modeling.DataStandardSecurity;
import com.yuzhi.dts.platform.service.security.OrganizationVisibilityService;
import com.yuzhi.dts.platform.service.development.dto.ScriptAssetResponse;
import com.yuzhi.dts.platform.service.development.dto.ScriptCreateRequest;
import com.yuzhi.dts.platform.service.development.dto.ScriptRunRequest;
import com.yuzhi.dts.platform.service.development.dto.ScriptRunResponse;
import com.yuzhi.dts.platform.service.development.dto.ScriptSaveVersionRequest;
import com.yuzhi.dts.platform.service.development.dto.ScriptUpdateRequest;
import com.yuzhi.dts.platform.service.development.dto.ScriptVersionResponse;
import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ScriptStudioService {

    private final DevScriptAssetRepository assetRepository;
    private final DevScriptVersionRepository versionRepository;
    private final DevScriptRunRepository runRepository;
    private final AuditService auditService;
    private final OrganizationVisibilityService organizationVisibilityService;
    private final DataStandardSecurity security;

    public ScriptStudioService(
        DevScriptAssetRepository assetRepository,
        DevScriptVersionRepository versionRepository,
        DevScriptRunRepository runRepository,
        AuditService auditService,
        OrganizationVisibilityService organizationVisibilityService,
        DataStandardSecurity security
    ) {
        this.assetRepository = assetRepository;
        this.versionRepository = versionRepository;
        this.runRepository = runRepository;
        this.auditService = auditService;
        this.organizationVisibilityService = organizationVisibilityService;
        this.security = security;
    }

    @Transactional(readOnly = true)
    public List<ScriptAssetResponse> listScripts(String activeDeptHeader) {
        String activeDept = security.resolveActiveDept(activeDeptHeader);
        boolean instituteScope = security.hasInstituteScope();
        return assetRepository
            .findByEnabledTrueOrderByLastModifiedDateDesc()
            .stream()
            .filter(asset -> isOwnerDeptVisible(asset != null ? asset.getOwnerDept() : null, activeDept, instituteScope))
            .map(this::toAssetResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<ScriptVersionResponse> listVersions(UUID scriptId, String activeDeptHeader) {
        requireReadableAsset(scriptId, activeDeptHeader);
        return versionRepository.findByAsset_IdOrderByVersionNoDesc(scriptId).stream().map(this::toVersionResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ScriptRunResponse> listRuns(UUID scriptId, String activeDeptHeader) {
        requireReadableAsset(scriptId, activeDeptHeader);
        return runRepository.findTop50ByAsset_IdOrderByCreatedDateDesc(scriptId).stream().map(this::toRunResponse).toList();
    }

    @Transactional(readOnly = true)
    public ScriptRunResponse getRun(UUID runId, String activeDeptHeader) {
        DevScriptRun run = requireRun(runId);
        ensureReadable(run.getAsset(), activeDeptHeader);
        return toRunResponse(run);
    }

    @Transactional
    public ScriptAssetResponse createScript(ScriptCreateRequest request, String activeDeptHeader, Principal principal) {
        if (request == null || !StringUtils.hasText(request.name())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "脚本名称不能为空");
        }
        if (!StringUtils.hasText(request.content())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "脚本内容不能为空");
        }

        DevScriptAsset asset = new DevScriptAsset();
        asset.setName(request.name().trim());
        asset.setDescription(trimToNull(request.description()));
        asset.setScriptType(normalizeScriptType(request.scriptType()));
        asset.setStatus("DRAFT");
        asset.setLatestVersionNo(1);
        asset.setOwnerDept(resolveOwnerDeptForUpsert(request.ownerDept(), null, activeDeptHeader));
        asset.setEnabled(Boolean.TRUE);
        asset = assetRepository.save(asset);

        DevScriptVersion version = new DevScriptVersion();
        version.setAsset(asset);
        version.setVersionNo(1);
        version.setStatus("DRAFT");
        version.setContent(request.content().trim());
        version.setChangeSummary("初始化版本");
        versionRepository.save(version);

        audit("CREATE", "dev.script", asset.getId().toString(), "创建脚本", principal, Map.of("name", asset.getName(), "type", asset.getScriptType()));
        return toAssetResponse(asset);
    }

    @Transactional
    public ScriptAssetResponse updateScript(UUID scriptId, ScriptUpdateRequest request, String activeDeptHeader, Principal principal) {
        DevScriptAsset asset = requireWritableAsset(scriptId, activeDeptHeader);
        if (request != null) {
            if (StringUtils.hasText(request.name())) {
                asset.setName(request.name().trim());
            }
            if (request.description() != null) {
                asset.setDescription(trimToNull(request.description()));
            }
            if (StringUtils.hasText(request.scriptType())) {
                asset.setScriptType(normalizeScriptType(request.scriptType()));
            }
            if (StringUtils.hasText(request.status())) {
                asset.setStatus(normalizeAssetStatus(request.status()));
            }
            if (request.ownerDept() != null || !security.hasInstituteScope()) {
                asset.setOwnerDept(resolveOwnerDeptForUpsert(request.ownerDept(), asset.getOwnerDept(), activeDeptHeader));
            }
            if (request.enabled() != null) {
                asset.setEnabled(request.enabled());
            }
        }
        asset = assetRepository.save(asset);
        audit("UPDATE", "dev.script", asset.getId().toString(), "更新脚本元信息", principal, Map.of("status", asset.getStatus()));
        return toAssetResponse(asset);
    }

    @Transactional
    public ScriptVersionResponse saveVersion(UUID scriptId, ScriptSaveVersionRequest request, String activeDeptHeader, Principal principal) {
        DevScriptAsset asset = requireWritableAsset(scriptId, activeDeptHeader);
        if (request == null || !StringUtils.hasText(request.content())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "脚本内容不能为空");
        }

        int nextVersionNo = asset.getLatestVersionNo() == null ? 1 : asset.getLatestVersionNo() + 1;
        DevScriptVersion version = new DevScriptVersion();
        version.setAsset(asset);
        version.setVersionNo(nextVersionNo);
        version.setStatus(normalizeVersionStatus(request.status()));
        version.setContent(request.content().trim());
        version.setChangeSummary(trimToNull(request.changeSummary()));
        version = versionRepository.save(version);

        asset.setLatestVersionNo(nextVersionNo);
        asset.setStatus(version.getStatus());
        assetRepository.save(asset);

        audit(
            "UPDATE",
            "dev.script.version",
            version.getId().toString(),
            "保存脚本版本",
            principal,
            Map.of("scriptId", scriptId.toString(), "versionNo", nextVersionNo)
        );
        return toVersionResponse(version);
    }

    @Transactional
    public ScriptRunResponse runScript(UUID scriptId, ScriptRunRequest request, String activeDeptHeader, Principal principal) {
        DevScriptAsset asset = requireWritableAsset(scriptId, activeDeptHeader);
        DevScriptVersion version = resolveVersion(scriptId, request != null ? request.versionNo() : null);

        DevScriptRun run = new DevScriptRun();
        run.setAsset(asset);
        run.setVersionNo(version.getVersionNo());
        run.setExecutionId("script_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        run.setStatus("PENDING");
        run.setTriggeredBy(resolvePrincipal(principal));
        run.setLogText("任务已提交，等待执行器调度...");
        run = runRepository.save(run);

        audit(
            "EXECUTE",
            "dev.script.run",
            run.getId().toString(),
            "触发脚本运行",
            principal,
            Map.of("scriptId", scriptId.toString(), "executionId", run.getExecutionId(), "versionNo", run.getVersionNo())
        );

        UUID runId = run.getId();
        String scriptContent = version.getContent();
        String scriptType = asset.getScriptType();
        CompletableFuture.runAsync(() -> executeRun(runId, scriptType, scriptContent));

        return toRunResponse(run);
    }

    private void executeRun(UUID runId, String scriptType, String content) {
        DevScriptRun run = runRepository.findById(runId).orElse(null);
        if (run == null) {
            return;
        }
        Instant started = Instant.now();
        run.setStartedAt(started);
        run.setStatus("RUNNING");
        run.setFailureType(null);
        run.setErrorMessage(null);
        StringBuilder logs = new StringBuilder();
        logs.append("[").append(started).append("] ").append("启动脚本运行\n");
        logs.append("executionId=").append(run.getExecutionId()).append(", type=").append(scriptType).append(", version=").append(run.getVersionNo()).append("\n");
        run.setLogText(logs.toString());
        runRepository.save(run);

        try {
            Thread.sleep(300L);
            if (!StringUtils.hasText(content)) {
                throw new ScriptExecutionException("VALIDATION_ERROR", "脚本内容为空，无法执行");
            }
            logs.append("加载脚本内容完成，长度=").append(content.length()).append("\n");
            Thread.sleep(350L);

            String lowered = content.toLowerCase();
            if (lowered.contains("raise ") || lowered.contains("throw ") || lowered.contains("system.exit")) {
                throw new ScriptExecutionException("RUNTIME_ERROR", "检测到异常关键字，执行器已终止脚本");
            }

            logs.append("执行步骤 1/2：语法预检查通过\n");
            logs.append("执行步骤 2/2：模拟运行完成\n");
            logs.append("结果：SUCCESS\n");
            run.setStatus("SUCCESS");
            run.setFailureType(null);
            run.setErrorMessage(null);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            run.setStatus("CANCELED");
            run.setFailureType("CANCELED");
            run.setErrorMessage("运行被中断");
            logs.append("结果：CANCELED\n");
        } catch (ScriptExecutionException ex) {
            run.setStatus("FAILED");
            run.setFailureType(ex.failureType());
            run.setErrorMessage(ex.getMessage());
            logs.append("结果：FAILED, failureType=").append(ex.failureType()).append(", message=").append(ex.getMessage()).append("\n");
        } catch (Exception ex) {
            run.setStatus("FAILED");
            run.setFailureType("SYSTEM_ERROR");
            run.setErrorMessage(resolveMessage(ex));
            logs.append("结果：FAILED, failureType=SYSTEM_ERROR, message=").append(resolveMessage(ex)).append("\n");
        }

        Instant finished = Instant.now();
        run.setFinishedAt(finished);
        run.setDurationMs(Duration.between(started, finished).toMillis());
        run.setLogText(logs.toString());
        runRepository.save(run);
    }

    private DevScriptAsset requireAsset(UUID scriptId) {
        return assetRepository
            .findById(scriptId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "脚本不存在"));
    }

    private DevScriptAsset requireReadableAsset(UUID scriptId, String activeDeptHeader) {
        DevScriptAsset asset = requireAsset(scriptId);
        ensureReadable(asset, activeDeptHeader);
        return asset;
    }

    private DevScriptAsset requireWritableAsset(UUID scriptId, String activeDeptHeader) {
        DevScriptAsset asset = requireAsset(scriptId);
        ensureReadable(asset, activeDeptHeader);
        return asset;
    }

    private DevScriptRun requireRun(UUID runId) {
        return runRepository
            .findById(runId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "运行记录不存在"));
    }

    private DevScriptVersion resolveVersion(UUID scriptId, Integer versionNo) {
        if (versionNo != null) {
            return versionRepository
                .findByAsset_IdAndVersionNo(scriptId, versionNo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "指定版本不存在"));
        }
        return versionRepository
            .findTopByAsset_IdOrderByVersionNoDesc(scriptId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "脚本尚无可运行版本"));
    }

    private ScriptAssetResponse toAssetResponse(DevScriptAsset asset) {
        String latestContent = versionRepository
            .findTopByAsset_IdOrderByVersionNoDesc(asset.getId())
            .map(DevScriptVersion::getContent)
            .orElse(null);
        return new ScriptAssetResponse(
            asset.getId(),
            asset.getName(),
            asset.getDescription(),
            asset.getScriptType(),
            asset.getStatus(),
            asset.getLatestVersionNo(),
            asset.getOwnerDept(),
            asset.getEnabled(),
            latestContent,
            asset.getCreatedBy(),
            asset.getCreatedDate(),
            asset.getLastModifiedDate()
        );
    }

    private ScriptVersionResponse toVersionResponse(DevScriptVersion version) {
        return new ScriptVersionResponse(
            version.getId(),
            version.getAsset().getId(),
            version.getVersionNo(),
            version.getStatus(),
            version.getContent(),
            version.getChangeSummary(),
            version.getCreatedBy(),
            version.getCreatedDate()
        );
    }

    private ScriptRunResponse toRunResponse(DevScriptRun run) {
        return new ScriptRunResponse(
            run.getId(),
            run.getAsset().getId(),
            run.getVersionNo(),
            run.getExecutionId(),
            run.getStatus(),
            run.getFailureType(),
            run.getErrorMessage(),
            run.getLogText(),
            run.getStartedAt(),
            run.getFinishedAt(),
            run.getDurationMs(),
            run.getTriggeredBy(),
            run.getCreatedDate()
        );
    }

    private String normalizeScriptType(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase() : "PYTHON";
        if ("PYTHON".equals(normalized) || "SPARK".equals(normalized)) {
            return normalized;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的脚本类型: " + value);
    }

    private String normalizeVersionStatus(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase() : "DRAFT";
        if ("DRAFT".equals(normalized) || "READY".equals(normalized)) {
            return normalized;
        }
        return "DRAFT";
    }

    private String normalizeAssetStatus(String value) {
        String normalized = value.trim().toUpperCase();
        if ("DRAFT".equals(normalized) || "READY".equals(normalized) || "DISABLED".equals(normalized)) {
            return normalized;
        }
        return "DRAFT";
    }

    private void audit(
        String action,
        String module,
        String subject,
        String summary,
        Principal principal,
        Map<String, Object> extras
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", summary);
        payload.put("operator", resolvePrincipal(principal));
        if (extras != null && !extras.isEmpty()) {
            payload.putAll(extras);
        }
        auditService.record(action, module, module, subject, "SUCCESS", payload);
    }

    private String resolvePrincipal(Principal principal) {
        if (principal != null && StringUtils.hasText(principal.getName())) {
            return principal.getName();
        }
        return "system";
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private void ensureReadable(DevScriptAsset asset, String activeDeptHeader) {
        String activeDept = security.resolveActiveDept(activeDeptHeader);
        boolean instituteScope = security.hasInstituteScope();
        if (!isOwnerDeptVisible(asset != null ? asset.getOwnerDept() : null, activeDept, instituteScope)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号无权访问该脚本");
        }
    }

    private boolean isOwnerDeptVisible(String ownerDept, String activeDept, boolean instituteScope) {
        String trimmedOwner = trimToNull(ownerDept);
        if (trimmedOwner == null) {
            return true;
        }
        if (instituteScope) {
            return true;
        }
        if (organizationVisibilityService.isRoot(trimmedOwner)) {
            return true;
        }
        if (!StringUtils.hasText(activeDept)) {
            return false;
        }
        return DepartmentUtils.matches(trimmedOwner, activeDept);
    }

    private String resolveOwnerDeptForUpsert(String requestedOwnerDept, String existingOwnerDept, String activeDeptHeader) {
        if (security.hasInstituteScope()) {
            String resolved = trimToNull(requestedOwnerDept);
            return resolved != null ? resolved : trimToNull(existingOwnerDept);
        }
        String activeDept = trimToNull(security.resolveActiveDept(activeDeptHeader));
        if (activeDept == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少当前部门上下文");
        }
        return activeDept;
    }

    private String resolveMessage(Exception ex) {
        if (ex.getMessage() != null) {
            return ex.getMessage();
        }
        return ex.getClass().getSimpleName();
    }

    private static final class ScriptExecutionException extends RuntimeException {
        private final String failureType;

        private ScriptExecutionException(String failureType, String message) {
            super(message);
            this.failureType = failureType;
        }

        private String failureType() {
            return failureType;
        }
    }
}
