package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.service.etl.DbtFileService;
import com.yuzhi.dts.platform.service.etl.DbtFileService.DbtArchiveUploadResult;
import com.yuzhi.dts.platform.service.etl.DbtFileService.DbtFileContent;
import com.yuzhi.dts.platform.service.etl.DbtFileService.DbtFileCreateRequest;
import com.yuzhi.dts.platform.service.etl.DbtFileService.DbtFileNode;
import com.yuzhi.dts.platform.service.etl.DbtFileService.DbtFileRenameRequest;
import com.yuzhi.dts.platform.service.etl.DbtFileService.DbtFileSaveRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/etl/dbt/files")
public class DbtFileResource {

    private final DbtFileService fileService;
    private final AuditService auditService;

    public DbtFileResource(DbtFileService fileService, AuditService auditService) {
        this.fileService = fileService;
        this.auditService = auditService;
    }

    @GetMapping("/tree")
    public ApiResponse<DbtFileNode> getTree() {
        ApiResponse<DbtFileNode> response = ApiResponses.ok(fileService.getTree());
        auditService.auditAction("ETL_DBT_FILES_READ", AuditStage.SUCCESS, "tree", null);
        return response;
    }

    @GetMapping("/content")
    public ApiResponse<DbtFileContent> getContent(@RequestParam String path) {
        ApiResponse<DbtFileContent> response = ApiResponses.ok(fileService.readFile(path));
        auditService.auditAction("ETL_DBT_FILES_READ", AuditStage.SUCCESS, path, null);
        return response;
    }

    @PutMapping("/content")
    public ApiResponse<Void> saveContent(@RequestBody DbtFileSaveRequest request) {
        fileService.saveFile(request.path(), request.content());
        auditService.auditAction("ETL_DBT_FILES_UPDATE", AuditStage.SUCCESS, request.path(), null);
        return ApiResponses.ok(null);
    }

    @PostMapping
    public ApiResponse<Void> createFile(@RequestBody DbtFileCreateRequest request) {
        fileService.createFile(request.path(), request.type(), request.content());
        auditService.auditAction("ETL_DBT_FILES_CREATE", AuditStage.SUCCESS, request.path(), null);
        return ApiResponses.ok(null);
    }

    @DeleteMapping
    public ApiResponse<Void> deleteFile(@RequestParam String path) {
        fileService.deleteFile(path);
        auditService.auditAction("ETL_DBT_FILES_DELETE", AuditStage.SUCCESS, path, null);
        return ApiResponses.ok(null);
    }

    @PutMapping("/rename")
    public ApiResponse<Void> renameFile(@RequestBody DbtFileRenameRequest request) {
        fileService.renameFile(request.oldPath(), request.newPath());
        auditService.auditAction("ETL_DBT_FILES_UPDATE", AuditStage.SUCCESS, request.oldPath() + " -> " + request.newPath(), null);
        return ApiResponses.ok(null);
    }

    @PostMapping("/upload-archive")
    public ApiResponse<DbtArchiveUploadResult> uploadArchive(
        @RequestPart("archive") MultipartFile archive,
        @RequestParam(name = "clean", required = false, defaultValue = "false") boolean clean
    ) {
        DbtArchiveUploadResult result = fileService.uploadArchive(archive, clean);
        auditService.auditAction("ETL_DBT_FILES_UPLOAD", AuditStage.SUCCESS, "archive: extracted=" + result.extracted().size()
                + " skipped=" + result.skipped().size()
                + " cleaned=" + result.cleaned().size()
                + " clean=" + clean, null);
        return ApiResponses.ok(result);
    }

}
