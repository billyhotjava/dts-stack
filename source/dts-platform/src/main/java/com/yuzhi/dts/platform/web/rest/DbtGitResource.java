package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.domain.modeling.ModelingSqlModel;
import com.yuzhi.dts.platform.repository.modeling.ModelingSqlModelRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.etl.DbtGitService;
import com.yuzhi.dts.platform.service.etl.DbtGitService.GitCommitRequest;
import com.yuzhi.dts.platform.service.etl.DbtGitService.GitCommitResult;
import com.yuzhi.dts.platform.service.etl.DbtGitService.GitLogEntry;
import com.yuzhi.dts.platform.service.etl.DbtGitService.GitRevertRequest;
import com.yuzhi.dts.platform.service.etl.DbtGitService.GitStatusResult;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/etl/dbt/git")
public class DbtGitResource {

    private static final Logger LOG = LoggerFactory.getLogger(DbtGitResource.class);

    private final DbtGitService gitService;
    private final AuditService auditService;
    private final ModelingSqlModelRepository sqlModelRepository;

    public DbtGitResource(DbtGitService gitService, AuditService auditService, ModelingSqlModelRepository sqlModelRepository) {
        this.gitService = gitService;
        this.auditService = auditService;
        this.sqlModelRepository = sqlModelRepository;
    }

    @GetMapping("/status")
    public ApiResponse<GitStatusResult> getStatus() {
        GitStatusResult result = gitService.getStatus();
        auditService.audit("READ", "etl.dbt.git", "status");
        return ApiResponses.ok(result);
    }

    @PostMapping("/commit")
    public ApiResponse<GitCommitResult> commit(@RequestBody GitCommitRequest request) {
        // Capture changed model paths before commit
        GitStatusResult status = gitService.getStatus();
        List<String> changedModelPaths = status.modified().stream()
            .filter(p -> p.startsWith("models/") && p.endsWith(".sql"))
            .toList();

        GitCommitResult result = gitService.commit(
            request.message(), request.authorName(), request.authorEmail()
        );

        // Upgrade status to COMMITTED for all changed models
        if (!changedModelPaths.isEmpty()) {
            try {
                List<ModelingSqlModel> models = sqlModelRepository.findByModelPathIn(changedModelPaths);
                for (ModelingSqlModel model : models) {
                    String currentStatus = model.getStatus();
                    if (currentStatus == null || "DRAFT".equals(currentStatus)) {
                        model.setStatus("COMMITTED");
                    }
                }
                if (!models.isEmpty()) {
                    sqlModelRepository.saveAll(models);
                    LOG.info("[dbt-git] Updated {} model(s) to COMMITTED after commit {}", models.size(), result.commitHash());
                }
            } catch (RuntimeException ex) {
                LOG.warn("[dbt-git] Failed to update model status after commit: {}", ex.getMessage());
            }
        }

        auditService.audit("CREATE", "etl.dbt.git", "commit: " + result.commitHash());
        return ApiResponses.ok(result);
    }

    @GetMapping("/log")
    public ApiResponse<List<GitLogEntry>> log(@RequestParam(defaultValue = "20") int limit) {
        List<GitLogEntry> entries = gitService.log(limit);
        auditService.audit("READ", "etl.dbt.git", "log");
        return ApiResponses.ok(entries);
    }

    @GetMapping("/diff")
    public ApiResponse<String> diff(@RequestParam(required = false) String path) {
        String result;
        if (path != null && !path.isBlank()) {
            result = gitService.diff(path);
            auditService.audit("READ", "etl.dbt.git", "diff: " + path);
        } else {
            result = gitService.diffAll();
            auditService.audit("READ", "etl.dbt.git", "diff-all");
        }
        return ApiResponses.ok(result);
    }

    @PostMapping("/revert")
    public ApiResponse<Void> revert(@RequestBody GitRevertRequest request) {
        gitService.revertFile(request.path());
        auditService.audit("UPDATE", "etl.dbt.git", "revert: " + request.path());
        return ApiResponses.ok(null);
    }

    @GetMapping("/file-at-commit")
    public ApiResponse<String> fileAtCommit(
            @RequestParam String path, @RequestParam String commitHash) {
        String content = gitService.getFileAtCommit(path, commitHash);
        auditService.audit("READ", "etl.dbt.git", "file-at-commit: " + path + "@" + commitHash);
        return ApiResponses.ok(content);
    }
}
