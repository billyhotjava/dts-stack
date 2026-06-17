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
            "Ingestion upload request received: traceId={}, operation=upload, name={}, size={}, contentType={}",
            traceId,
            file == null ? null : file.getOriginalFilename(),
            file == null ? null : file.getSize(),
            file == null ? null : file.getContentType()
        );
        try {
            FileUploadService.FileUploadResult result = fileUploadService.handleUpload(file, traceId);
            LOG.info(
                "Ingestion upload request completed: traceId={}, operation=upload, fileId={}, fileType={}, columns={}, rows={}, hostPath={}",
                traceId,
                result.fileId(),
                result.fileType(),
                result.columns() == null ? 0 : result.columns().size(),
                result.rowCount(),
                result.hostPath()
            );
            return result;
        } catch (RuntimeException ex) {
            LOG.info(
                "Ingestion upload request failed: traceId={}, operation=upload, name={}, size={}, contentType={}",
                traceId,
                file == null ? null : file.getOriginalFilename(),
                file == null ? null : file.getSize(),
                file == null ? null : file.getContentType(),
                ex
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
            "Ingestion upload request received: traceId={}, operation=upload-and-parse, name={}, size={}, contentType={}, previewLimit={}, sheetIndex={}, sheetName={}",
            traceId,
            file == null ? null : file.getOriginalFilename(),
            file == null ? null : file.getSize(),
            file == null ? null : file.getContentType(),
            previewLimit,
            sheetIndex,
            sheetName
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
                "Ingestion upload request completed: traceId={}, operation=upload-and-parse, fileId={}, fileType={}, columns={}, rows={}, errors={}, sheetIndex={}, sheetName={}, hostPath={}",
                traceId,
                result.fileId(),
                result.fileType(),
                result.columns() == null ? 0 : result.columns().size(),
                result.rowCount(),
                result.errorCount(),
                result.sheetIndex(),
                result.sheetName(),
                result.hostPath()
            );
            return ResponseEntity.ok(result);
        } catch (Exception ex) {
            LOG.info(
                "Upload and parse file failed: traceId={}, name={}, size={}, contentType={}, previewLimit={}, sheetIndex={}, sheetName={}",
                traceId,
                file == null ? null : file.getOriginalFilename(),
                file == null ? null : file.getSize(),
                file == null ? null : file.getContentType(),
                previewLimit,
                sheetIndex,
                sheetName,
                ex
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
            "Ingestion upload parse request received: traceId={}, fileId={}, previewLimit={}, sheetIndex={}, sheetName={}, originalName={}",
            traceId,
            request.fileId(),
            request.previewLimit(),
            request.sheetIndex(),
            request.sheetName(),
            request.originalName()
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
                "Ingestion upload parse request completed: traceId={}, fileId={}, fileType={}, columns={}, rows={}, errors={}, sheetIndex={}, sheetName={}",
                traceId,
                result.fileId(),
                result.fileType(),
                result.columns() == null ? 0 : result.columns().size(),
                result.rowCount(),
                result.errorCount(),
                result.sheetIndex(),
                result.sheetName()
            );
            return ResponseEntity.ok(result);
        } catch (RuntimeException ex) {
            LOG.info("Ingestion upload parse request failed: traceId={}, fileId={}", traceId, request.fileId(), ex);
            throw ex;
        }
    }

    private String resolveTraceId(String uploadTraceId) {
        return StringUtils.hasText(uploadTraceId) ? uploadTraceId.trim() : UUID.randomUUID().toString();
    }
}
