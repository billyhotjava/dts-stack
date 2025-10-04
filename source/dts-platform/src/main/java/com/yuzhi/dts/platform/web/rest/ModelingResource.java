package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.domain.modeling.DataSecurityLevel;
import com.yuzhi.dts.platform.domain.modeling.DataStandardStatus;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.modeling.DataStandardAttachmentService;
import com.yuzhi.dts.platform.service.modeling.DataStandardFilter;
import com.yuzhi.dts.platform.service.modeling.DataStandardService;
import com.yuzhi.dts.platform.service.modeling.DataStandardUpsertRequest;
import com.yuzhi.dts.platform.service.modeling.dto.DataStandardAttachmentContent;
import com.yuzhi.dts.platform.service.modeling.dto.DataStandardAttachmentDto;
import com.yuzhi.dts.platform.service.modeling.dto.DataStandardDto;
import com.yuzhi.dts.platform.service.modeling.dto.DataStandardVersionDto;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import jakarta.validation.Valid;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/modeling")
@Transactional
public class ModelingResource {

    private final DataStandardService standards;
    private final DataStandardAttachmentService attachments;
    private final AuditService audit;

    public ModelingResource(DataStandardService standards, DataStandardAttachmentService attachments, AuditService audit) {
        this.standards = standards;
        this.attachments = attachments;
        this.audit = audit;
    }

    @GetMapping("/standards")
    public ApiResponse<Map<String, Object>> list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String domain,
        @RequestParam(required = false) String securityLevel,
        @RequestParam(required = false) String keyword
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdDate").descending());
        DataStandardFilter filter = new DataStandardFilter();
        filter.setDomain(domain);
        filter.setKeyword(keyword);
        filter.setStatus(parseStatus(status));
        filter.setSecurityLevel(parseSecurityLevel(securityLevel));

        Page<DataStandardDto> result = standards.list(filter, pageable);
        Map<String, Object> payload = Map.of(
            "content",
            result.getContent(),
            "total",
            result.getTotalElements(),
            "page",
            result.getNumber(),
            "size",
            result.getSize()
        );
        audit.audit("READ", "modeling.standard", "page=" + page);
        return ApiResponses.ok(payload);
    }

    @GetMapping("/standards/{id}")
    public ApiResponse<DataStandardDto> get(@PathVariable UUID id) {
        DataStandardDto dto = standards.get(id);
        audit.audit("READ", "modeling.standard", id.toString());
        return ApiResponses.ok(dto);
    }

    @PostMapping("/standards")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.CATALOG_ADMIN + "','" + AuthoritiesConstants.ADMIN + "','" + AuthoritiesConstants.OP_ADMIN + "')")
    public ApiResponse<DataStandardDto> create(@Valid @RequestBody DataStandardUpsertRequest request) {
        try {
            DataStandardDto saved = standards.create(request);
            audit.audit("CREATE", "modeling.standard", saved.getId().toString());
            return ApiResponses.ok(saved);
        } catch (RuntimeException e) {
            audit.auditFailure("CREATE", "modeling.standard", request.getCode(), e.getMessage());
            throw e;
        }
    }

    @PutMapping("/standards/{id}")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.CATALOG_ADMIN + "','" + AuthoritiesConstants.ADMIN + "','" + AuthoritiesConstants.OP_ADMIN + "')")
    public ApiResponse<DataStandardDto> update(@PathVariable UUID id, @Valid @RequestBody DataStandardUpsertRequest request) {
        try {
            DataStandardDto saved = standards.update(id, request);
            audit.audit("UPDATE", "modeling.standard", id.toString());
            return ApiResponses.ok(saved);
        } catch (RuntimeException e) {
            audit.auditFailure("UPDATE", "modeling.standard", id.toString(), e.getMessage());
            throw e;
        }
    }

    @DeleteMapping("/standards/{id}")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.CATALOG_ADMIN + "','" + AuthoritiesConstants.ADMIN + "','" + AuthoritiesConstants.OP_ADMIN + "')")
    public ApiResponse<Boolean> delete(@PathVariable UUID id) {
        standards.delete(id);
        audit.audit("DELETE", "modeling.standard", id.toString());
        return ApiResponses.ok(Boolean.TRUE);
    }

    @GetMapping("/standards/{id}/versions")
    public ApiResponse<List<DataStandardVersionDto>> listVersions(@PathVariable UUID id) {
        List<DataStandardVersionDto> versions = standards.listVersions(id);
        audit.audit("READ", "modeling.standard.version", id.toString());
        return ApiResponses.ok(versions);
    }

    @GetMapping("/standards/{id}/attachments")
    public ApiResponse<List<DataStandardAttachmentDto>> listAttachments(@PathVariable UUID id) {
        List<DataStandardAttachmentDto> data = attachments.list(id);
        audit.audit("READ", "modeling.standard.attachment", id.toString());
        return ApiResponses.ok(data);
    }

    @PostMapping(value = "/standards/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.CATALOG_ADMIN + "','" + AuthoritiesConstants.ADMIN + "','" + AuthoritiesConstants.OP_ADMIN + "')")
    public ApiResponse<DataStandardAttachmentDto> uploadAttachment(
        @PathVariable UUID id,
        @RequestPart("file") MultipartFile file,
        @RequestParam(required = false) String version
    ) {
        try {
            DataStandardAttachmentDto dto = attachments.upload(id, file, version);
            audit.audit("CREATE", "modeling.standard.attachment", id + ":" + dto.getId());
            return ApiResponses.ok(dto);
        } catch (RuntimeException e) {
            audit.auditFailure("CREATE", "modeling.standard.attachment", id.toString(), e.getMessage());
            throw e;
        }
    }

    @GetMapping("/standards/{id}/attachments/{attachmentId}/download")
    public ResponseEntity<byte[]> download(
        @PathVariable UUID id,
        @PathVariable UUID attachmentId
    ) {
        DataStandardAttachmentContent content = attachments.download(id, attachmentId);
        audit.audit("READ", "modeling.standard.attachment", id + ":" + attachmentId);
        MediaType mediaType = resolveMediaType(content.getContentType());
        String encodedFileName = URLEncoder.encode(content.getFileName(), StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        ContentDisposition disposition = ContentDisposition.attachment().filename(encodedFileName).build();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(disposition);
        headers.setContentType(mediaType);
        return ResponseEntity.ok().headers(headers).body(content.getData());
    }

    @DeleteMapping("/standards/{id}/attachments/{attachmentId}")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.CATALOG_ADMIN + "','" + AuthoritiesConstants.ADMIN + "','" + AuthoritiesConstants.OP_ADMIN + "')")
    public ApiResponse<Boolean> deleteAttachment(@PathVariable UUID id, @PathVariable UUID attachmentId) {
        attachments.delete(id, attachmentId);
        audit.audit("DELETE", "modeling.standard.attachment", id + ":" + attachmentId);
        return ApiResponses.ok(Boolean.TRUE);
    }

    private DataStandardStatus parseStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        try {
            return DataStandardStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private DataSecurityLevel parseSecurityLevel(String level) {
        if (!StringUtils.hasText(level)) {
            return null;
        }
        try {
            return DataSecurityLevel.valueOf(level.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private MediaType resolveMediaType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (Exception ignored) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
