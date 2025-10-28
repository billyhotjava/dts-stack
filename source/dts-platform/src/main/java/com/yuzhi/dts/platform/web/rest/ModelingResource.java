package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.common.audit.AuditStage;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/modeling")
@Transactional
public class ModelingResource {

    private static final String MODELING_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).CATALOG_MAINTAINERS)";

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
        @RequestParam(required = false) String keyword,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdDate").descending());
        DataStandardFilter filter = new DataStandardFilter();
        filter.setDomain(domain);
        filter.setKeyword(keyword);
        filter.setStatus(parseStatus(status));
        filter.setSecurityLevel(parseSecurityLevel(securityLevel));

        Page<DataStandardDto> result = standards.list(filter, pageable, activeDept);
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
        Map<String, Object> auditPayload = new java.util.LinkedHashMap<>();
        auditPayload.put("summary", "查看数据标准列表");
        auditPayload.put("page", page);
        auditPayload.put("size", size);
        if (StringUtils.hasText(status)) {
            auditPayload.put("status", status.trim());
        }
        if (StringUtils.hasText(domain)) {
            auditPayload.put("domain", domain.trim());
        }
        if (StringUtils.hasText(securityLevel)) {
            auditPayload.put("securityLevel", securityLevel.trim());
        }
        if (StringUtils.hasText(keyword)) {
            auditPayload.put("keyword", keyword.trim());
        }
        audit.auditAction("MODELING_STANDARD_LIST", AuditStage.SUCCESS, "page=" + page, auditPayload);
        return ApiResponses.ok(payload);
    }

    @GetMapping("/standards/{id}")
    public ApiResponse<DataStandardDto> get(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        DataStandardDto dto = standards.get(id, activeDept);
        Map<String, Object> detail = new java.util.LinkedHashMap<>();
        detail.put("targetId", id.toString());
        if (StringUtils.hasText(dto.getName())) {
            detail.put("targetName", dto.getName());
            detail.put("summary", "查看数据标准：" + dto.getName());
        } else {
            detail.put("summary", "查看数据标准详情");
        }
        audit.auditAction("MODELING_STANDARD_VIEW", AuditStage.SUCCESS, id.toString(), detail);
        return ApiResponses.ok(dto);
    }

    @PostMapping("/standards")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<DataStandardDto> create(
        @Valid @RequestBody DataStandardUpsertRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        try {
            DataStandardDto saved = standards.create(request, activeDept);
            return ApiResponses.ok(saved);
        } catch (RuntimeException e) {
            Map<String, Object> detail = new java.util.LinkedHashMap<>();
            detail.put("summary", "新建数据标准失败：" + request.getName());
            detail.put("error", e.getMessage());
            detail.put("requestDomain", request.getDomain());
            detail.put("requestCode", request.getCode());
            detail.put("requestName", request.getName());
            String resourceRef = resolveResourceRef(request.getCode(), request.getName(), request.getDomain());
            audit.auditAction("MODELING_STANDARD_EDIT", AuditStage.FAIL, resourceRef, detail);
            throw e;
        }
    }

    @PutMapping("/standards/{id}")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<DataStandardDto> update(
        @PathVariable UUID id,
        @Valid @RequestBody DataStandardUpsertRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        try {
            DataStandardDto saved = standards.update(id, request, activeDept);
            return ApiResponses.ok(saved);
        } catch (RuntimeException e) {
            Map<String, Object> detail = new java.util.LinkedHashMap<>();
            detail.put("summary", "更新数据标准失败：" + request.getName());
            detail.put("error", e.getMessage());
            detail.put("targetId", id.toString());
            detail.put("requestDomain", request.getDomain());
            detail.put("requestCode", request.getCode());
            audit.auditAction("MODELING_STANDARD_EDIT", AuditStage.FAIL, id.toString(), detail);
            throw e;
        }
    }

    @DeleteMapping("/standards/{id}")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<Boolean> delete(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        try {
            standards.delete(id, activeDept);
            return ApiResponses.ok(Boolean.TRUE);
        } catch (RuntimeException e) {
            Map<String, Object> detail = new java.util.LinkedHashMap<>();
            detail.put("summary", "删除数据标准失败");
            detail.put("error", e.getMessage());
            detail.put("targetId", id.toString());
            audit.auditAction("MODELING_STANDARD_EDIT", AuditStage.FAIL, id.toString(), detail);
            throw e;
        }
    }

    @GetMapping("/standards/{id}/versions")
    public ApiResponse<List<DataStandardVersionDto>> listVersions(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        List<DataStandardVersionDto> versions = standards.listVersions(id, activeDept);
        Map<String, Object> detail = new java.util.LinkedHashMap<>();
        detail.put("targetId", id.toString());
        detail.put("summary", "查看数据标准版本列表");
        audit.recordAuxiliary(
            "READ",
            "modeling.standard.version",
            "modeling.standard.version",
            id.toString(),
            "SUCCESS",
            detail
        );
        return ApiResponses.ok(versions);
    }

    @GetMapping("/standards/{id}/attachments")
    public ApiResponse<List<DataStandardAttachmentDto>> listAttachments(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        List<DataStandardAttachmentDto> data = attachments.list(id, activeDept);
        return ApiResponses.ok(data);
    }

    @PostMapping(value = "/standards/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<DataStandardAttachmentDto> uploadAttachment(
        @PathVariable UUID id,
        @RequestPart("file") MultipartFile file,
        @RequestParam(required = false) String version,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        try {
            DataStandardAttachmentDto dto = attachments.upload(id, file, version, activeDept);
            Map<String, Object> detail = new java.util.LinkedHashMap<>();
            detail.put("targetId", dto.getId().toString());
            detail.put("targetName", dto.getFileName());
            detail.put("standardId", id.toString());
            detail.put("summary", "上传数据标准附件：" + dto.getFileName());
            detail.put("operationType", "UPLOAD");
            audit.auditAction("MODELING_STANDARD_EDIT", AuditStage.SUCCESS, dto.getId().toString(), detail);
            return ApiResponses.ok(dto);
        } catch (RuntimeException e) {
            Map<String, Object> detail = new java.util.LinkedHashMap<>();
            detail.put("summary", "上传数据标准附件失败：" + file.getOriginalFilename());
            detail.put("error", e.getMessage());
            detail.put("standardId", id.toString());
            audit.auditAction("MODELING_STANDARD_EDIT", AuditStage.FAIL, id.toString(), detail);
            throw e;
        }
    }

    @GetMapping("/standards/{id}/attachments/{attachmentId}/download")
    public ResponseEntity<byte[]> download(
        @PathVariable UUID id,
        @PathVariable UUID attachmentId,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        DataStandardAttachmentContent content = attachments.download(id, attachmentId, activeDept);
        Map<String, Object> detail = new java.util.LinkedHashMap<>();
        detail.put("targetId", attachmentId.toString());
        detail.put("targetName", content.getFileName());
        detail.put("standardId", id.toString());
        detail.put("summary", "下载数据标准附件：" + content.getFileName());
        detail.put("operationType", "DOWNLOAD");
        audit.auditAction("MODELING_STANDARD_VIEW", AuditStage.SUCCESS, attachmentId.toString(), detail);
        MediaType mediaType = resolveMediaType(content.getContentType());
        String encodedFileName = URLEncoder.encode(content.getFileName(), StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        ContentDisposition disposition = ContentDisposition.attachment().filename(encodedFileName).build();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(disposition);
        headers.setContentType(mediaType);
        return ResponseEntity.ok().headers(headers).body(content.getData());
    }

    @DeleteMapping("/standards/{id}/attachments/{attachmentId}")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<Boolean> deleteAttachment(
        @PathVariable UUID id,
        @PathVariable UUID attachmentId,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        DataStandardAttachmentDto attachment = attachments.getMetadata(id, attachmentId, activeDept);
        attachments.delete(id, attachmentId, activeDept);
        Map<String, Object> detail = new java.util.LinkedHashMap<>();
        detail.put("targetId", attachmentId.toString());
        detail.put("targetName", attachment.getFileName());
        detail.put("standardId", id.toString());
        detail.put("summary", "删除数据标准附件：" + attachment.getFileName());
        detail.put("operationType", "DELETE");
        audit.auditAction("MODELING_STANDARD_EDIT", AuditStage.SUCCESS, attachmentId.toString(), detail);
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

    private String resolveResourceRef(String... candidates) {
        if (candidates == null) {
            return "unknown";
        }
        for (String candidate : candidates) {
            if (StringUtils.hasText(candidate)) {
                return candidate.trim();
            }
        }
        return "unknown";
    }
}
