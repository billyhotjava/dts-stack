package com.yuzhi.dts.ingestion.web.rest;

import com.yuzhi.dts.ingestion.service.etl.FileUploadService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/ingestion/files")
public class FileUploadResource {

    private static final Logger LOG = LoggerFactory.getLogger(FileUploadResource.class);

    private static final String INFRA_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.ingestion.security.AuthoritiesConstants).INFRA_MAINTAINERS)";
    private static final String UPLOAD_TRACE_HEADER = "X-DTS-Upload-Trace";
    private static final String UPLOAD_FAILED = "FILE_UPLOAD_FAILED";
    private static final String UPLOAD_AND_PARSE_FAILED = "FILE_UPLOAD_AND_PARSE_FAILED";
    private static final String PARSE_BY_ID_FAILED = "FILE_PARSE_BY_ID_FAILED";

    private final FileUploadService fileUploadService;

    public FileUploadResource(FileUploadService fileUploadService) {
        this.fileUploadService = fileUploadService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public FileUploadService.FileUploadResult uploadFile(
        @RequestPart("file") MultipartFile file,
        @RequestHeader(value = UPLOAD_TRACE_HEADER, required = false) String uploadTraceId
    ) {
        String traceId = resolveTraceId(uploadTraceId);
        LOG.info(
            "Ingestion upload request received: traceId={}, operation=upload, size={}",
            traceId,
            file == null ? null : file.getSize()
        );
        try {
            FileUploadService.FileUploadResult result = fileUploadService.handleUpload(file, traceId);
            LOG.info(
                "Ingestion upload request completed: traceId={}, operation=upload, fileId={}, fileType={}, columns={}, rows={}",
                traceId,
                safeIdentifier(result.fileId()),
                result.fileType(),
                result.columns() == null ? 0 : result.columns().size(),
                result.rowCount()
            );
            return result;
        } catch (RuntimeException ex) {
            LOG.info(
                "Ingestion upload request failed: traceId={}, operation=upload, errorCode={}",
                traceId,
                UPLOAD_FAILED
            );
            throw ex;
        }
    }

    @PostMapping(value = "/upload-and-parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<FileUploadService.FileUploadResult> uploadAndParse(
        @RequestPart("file") MultipartFile file,
        @RequestParam(value = "previewLimit", required = false) Integer previewLimit,
        @RequestParam(value = "sheetIndex", required = false) Integer sheetIndex,
        @RequestParam(value = "sheetName", required = false) String sheetName,
        @RequestHeader(value = UPLOAD_TRACE_HEADER, required = false) String uploadTraceId
    ) {
        String traceId = resolveTraceId(uploadTraceId);
        LOG.info(
            "Ingestion upload request received: traceId={}, operation=upload-and-parse, size={}, previewLimit={}",
            traceId,
            file == null ? null : file.getSize(),
            previewLimit
        );
        try {
            FileUploadService.FileUploadResult result = fileUploadService.handleUploadAndParse(
                file,
                previewLimit,
                sheetIndex,
                sheetName,
                traceId
            );
            LOG.info(
                "Ingestion upload request completed: traceId={}, operation=upload-and-parse, fileId={}, fileType={}, columns={}, rows={}, errors={}",
                traceId,
                safeIdentifier(result.fileId()),
                result.fileType(),
                result.columns() == null ? 0 : result.columns().size(),
                result.rowCount(),
                result.errorCount()
            );
            return ResponseEntity.ok(result);
        } catch (Exception ex) {
            LOG.info(
                "Ingestion upload request failed: traceId={}, operation=upload-and-parse, errorCode={}",
                traceId,
                UPLOAD_AND_PARSE_FAILED
            );
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
    public ResponseEntity<FileUploadService.FileUploadResult> parseUploadedFile(
        @RequestBody FileParseByIdRequest request,
        @RequestHeader(value = UPLOAD_TRACE_HEADER, required = false) String uploadTraceId
    ) {
        String traceId = resolveTraceId(uploadTraceId);
        if (request == null || !StringUtils.hasText(request.fileId())) {
            LOG.info("Ingestion upload parse request rejected: traceId={}, reason=missing_fileId", traceId);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
        LOG.info(
            "Ingestion upload parse request received: traceId={}, fileId={}, previewLimit={}",
            traceId,
            safeIdentifier(request.fileId()),
            request.previewLimit()
        );
        try {
            FileUploadService.FileUploadResult result = fileUploadService.parseById(
                request.fileId(),
                request.previewLimit(),
                request.sheetIndex(),
                request.sheetName(),
                request.originalName()
            );
            LOG.info(
                "Ingestion upload parse request completed: traceId={}, fileId={}, fileType={}, columns={}, rows={}, errors={}",
                traceId,
                safeIdentifier(result.fileId()),
                result.fileType(),
                result.columns() == null ? 0 : result.columns().size(),
                result.rowCount(),
                result.errorCount()
            );
            return ResponseEntity.ok(result);
        } catch (RuntimeException ex) {
            LOG.info(
                "Ingestion upload parse request failed: traceId={}, fileId={}, errorCode={}",
                traceId,
                safeIdentifier(request.fileId()),
                PARSE_BY_ID_FAILED
            );
            throw ex;
        }
    }

    private String resolveTraceId(String uploadTraceId) {
        if (StringUtils.hasText(uploadTraceId)) {
            String candidate = uploadTraceId.trim();
            if (candidate.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) {
                return candidate;
            }
        }
        return UUID.randomUUID().toString();
    }

    private String safeIdentifier(String value) {
        if (!StringUtils.hasText(value)) {
            return "missing";
        }
        String candidate = value.trim();
        return candidate.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}") ? candidate : "invalid";
    }
}
