package com.yuzhi.dts.ingestion.web.rest;

import com.yuzhi.dts.ingestion.service.etl.FileUploadService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/ingestion/files")
public class FileUploadResource {

    private static final String INFRA_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.ingestion.security.AuthoritiesConstants).INFRA_MAINTAINERS)";

    private final FileUploadService fileUploadService;

    public FileUploadResource(FileUploadService fileUploadService) {
        this.fileUploadService = fileUploadService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public FileUploadService.FileUploadResult uploadFile(@RequestPart("file") MultipartFile file) {
        return fileUploadService.handleUpload(file);
    }

    @PostMapping(value = "/upload-and-parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<FileUploadService.FileUploadResult> uploadAndParse(
        @RequestPart("file") MultipartFile file,
        @RequestParam(value = "previewLimit", required = false) Integer previewLimit,
        @RequestParam(value = "sheetIndex", required = false) Integer sheetIndex,
        @RequestParam(value = "sheetName", required = false) String sheetName
    ) {
        try {
            return ResponseEntity.ok(
                fileUploadService.handleUploadAndParse(file, previewLimit, sheetIndex, sheetName)
            );
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }

    public record FileParseByIdRequest(
        String fileId,
        Integer previewLimit,
        Integer sheetIndex,
        String sheetName,
        String originalName
    ) {}

    @PostMapping(value = "/parse")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<FileUploadService.FileUploadResult> parseUploadedFile(@RequestBody FileParseByIdRequest request) {
        if (request == null || !StringUtils.hasText(request.fileId())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
        return ResponseEntity.ok(
            fileUploadService.parseById(
                request.fileId(),
                request.previewLimit(),
                request.sheetIndex(),
                request.sheetName(),
                request.originalName()
            )
        );
    }
}
