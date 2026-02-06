package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.etl.DbtFileService;
import com.yuzhi.dts.platform.service.etl.DbtFileService.DbtFileContent;
import com.yuzhi.dts.platform.service.etl.DbtFileService.DbtFileCreateRequest;
import com.yuzhi.dts.platform.service.etl.DbtFileService.DbtFileNode;
import com.yuzhi.dts.platform.service.etl.DbtFileService.DbtFileRenameRequest;
import com.yuzhi.dts.platform.service.etl.DbtFileService.DbtFileSaveRequest;
import com.yuzhi.dts.platform.service.etl.DbtFileService.DbtImportResult;
import org.springframework.http.MediaType;
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
        auditService.audit("READ", "etl.dbt.files", "tree");
        return response;
    }

    @GetMapping("/content")
    public ApiResponse<DbtFileContent> getContent(@RequestParam String path) {
        ApiResponse<DbtFileContent> response = ApiResponses.ok(fileService.readFile(path));
        auditService.audit("READ", "etl.dbt.files", path);
        return response;
    }

    @PutMapping("/content")
    public ApiResponse<Void> saveContent(@RequestBody DbtFileSaveRequest request) {
        fileService.saveFile(request.path(), request.content());
        auditService.audit("UPDATE", "etl.dbt.files", request.path());
        return ApiResponses.ok(null);
    }

    @PostMapping
    public ApiResponse<Void> createFile(@RequestBody DbtFileCreateRequest request) {
        fileService.createFile(request.path(), request.type(), request.content());
        auditService.audit("CREATE", "etl.dbt.files", request.path());
        return ApiResponses.ok(null);
    }

    @DeleteMapping
    public ApiResponse<Void> deleteFile(@RequestParam String path) {
        fileService.deleteFile(path);
        auditService.audit("DELETE", "etl.dbt.files", path);
        return ApiResponses.ok(null);
    }

    @PutMapping("/rename")
    public ApiResponse<Void> renameFile(@RequestBody DbtFileRenameRequest request) {
        fileService.renameFile(request.oldPath(), request.newPath());
        auditService.audit("UPDATE", "etl.dbt.files", request.oldPath() + " -> " + request.newPath());
        return ApiResponses.ok(null);
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<DbtImportResult> uploadZip(@RequestPart("file") MultipartFile file) {
        DbtImportResult result = fileService.importZip(file);
        auditService.audit("EXECUTE", "etl.dbt.files", "upload:" + file.getOriginalFilename());
        return ApiResponses.ok(result);
    }
}
