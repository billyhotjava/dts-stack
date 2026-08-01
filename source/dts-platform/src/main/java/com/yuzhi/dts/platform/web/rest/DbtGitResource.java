package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.etl.DbtGitService;
import com.yuzhi.dts.platform.service.etl.DbtGitService.GitCommitRequest;
import com.yuzhi.dts.platform.service.etl.DbtGitService.GitCommitResult;
import com.yuzhi.dts.platform.service.etl.DbtGitService.GitLogEntry;
import com.yuzhi.dts.platform.service.etl.DbtGitService.GitRevertRequest;
import com.yuzhi.dts.platform.service.etl.DbtGitService.GitStatusResult;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/etl/dbt/git")
public class DbtGitResource {

    private final DbtGitService gitService;
    private final AuditService auditService;

    public DbtGitResource(DbtGitService gitService, AuditService auditService) {
        this.gitService = gitService;
        this.auditService = auditService;
    }

    @GetMapping("/status")
    public ApiResponse<GitStatusResult> getStatus() {
        GitStatusResult result = gitService.getStatus();
        auditService.auditAction("ETL_DBT_GIT_READ", AuditStage.SUCCESS, "status", null);
        return ApiResponses.ok(result);
    }

    @PostMapping("/commit")
    public ApiResponse<GitCommitResult> commit(@RequestBody GitCommitRequest request) {
        GitCommitResult result = gitService.commit(
            request.message(), request.authorName(), request.authorEmail()
        );

        auditService.auditAction("ETL_DBT_GIT_CREATE", AuditStage.SUCCESS, "commit: " + result.commitHash(), null);
        return ApiResponses.ok(result);
    }

    @GetMapping("/log")
    public ApiResponse<List<GitLogEntry>> log(@RequestParam(defaultValue = "20") int limit) {
        List<GitLogEntry> entries = gitService.log(limit);
        auditService.auditAction("ETL_DBT_GIT_READ", AuditStage.SUCCESS, "log", null);
        return ApiResponses.ok(entries);
    }

    @GetMapping("/diff")
    public ApiResponse<String> diff(@RequestParam(required = false) String path) {
        String result;
        if (path != null && !path.isBlank()) {
            result = gitService.diff(path);
            auditService.auditAction("ETL_DBT_GIT_READ", AuditStage.SUCCESS, "diff: " + path, null);
        } else {
            result = gitService.diffAll();
            auditService.auditAction("ETL_DBT_GIT_READ", AuditStage.SUCCESS, "diff-all", null);
        }
        return ApiResponses.ok(result);
    }

    @PostMapping("/revert")
    public ApiResponse<Void> revert(@RequestBody GitRevertRequest request) {
        gitService.revertFile(request.path());
        auditService.auditAction("ETL_DBT_GIT_UPDATE", AuditStage.SUCCESS, "revert: " + request.path(), null);
        return ApiResponses.ok(null);
    }

    @GetMapping("/file-at-commit")
    public ApiResponse<String> fileAtCommit(
            @RequestParam String path, @RequestParam String commitHash) {
        String content = gitService.getFileAtCommit(path, commitHash);
        auditService.auditAction("ETL_DBT_GIT_READ", AuditStage.SUCCESS, "file-at-commit: " + path + "@" + commitHash, null);
        return ApiResponses.ok(content);
    }
}
