package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.common.security.SecurityLevelCatalog;
import com.yuzhi.dts.platform.domain.modeling.DataSecurityLevel;
import com.yuzhi.dts.platform.domain.modeling.DataStandardStatus;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.modeling.DataStandardAttachmentService;
import com.yuzhi.dts.platform.service.modeling.DataStandardFilter;
import com.yuzhi.dts.platform.service.modeling.DataStandardImportService;
import com.yuzhi.dts.platform.service.modeling.DataStandardService;
import com.yuzhi.dts.platform.service.modeling.DataStandardUpsertRequest;
import com.yuzhi.dts.platform.service.modeling.dto.DataStandardAttachmentContent;
import com.yuzhi.dts.platform.service.modeling.dto.DataStandardAttachmentDto;
import com.yuzhi.dts.platform.service.modeling.dto.DataStandardDto;
import com.yuzhi.dts.platform.service.modeling.dto.DataStandardImportResultDto;
import com.yuzhi.dts.platform.service.modeling.dto.DataStandardVersionDto;
import jakarta.validation.Valid;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
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
    private static final String BUSINESS_TERM_TEMPLATE =
        "term_code,term_name,aliases,definition,domain,owner_dept,owner,tags,version,status,version_notes\n" +
        "BT_EXAMPLE_PROJECT,示例业务对象,示例对象|业务对象,描述业务对象的统一口径和边界,示例域,数据治理部,biadmin,示例|业务口径,v1,DRAFT,首次梳理\n";
    private static final String DATA_ELEMENT_TEMPLATE =
        "field_name_cn,field_name_en,data_type,data_length,data_precision,data_scale,nullable,domain,description,source_system,code_set,default_value,is_pk,security_level\n" +
        "示例编号,example_id,VARCHAR,64,,,N,示例域,业务对象唯一标识,示例系统,,,Y,INTERNAL\n" +
        "示例状态,example_status,VARCHAR,32,,,N,示例域,业务状态编码，枚举值来自 EXAMPLE_STATUS,示例系统,EXAMPLE_STATUS,,N,INTERNAL\n";
    private static final String REFERENCE_CODE_DIRECTORY_TEMPLATE =
        "code_type_id,code_type_code,code_type_name,std_level,biz_catalog,data_type,status,owner_dept,version\n" +
        ",EXAMPLE_STATUS,示例状态,企业级,示例域,VARCHAR,1,数据治理部,v1\n";
    private static final String REFERENCE_CODE_ITEM_TEMPLATE =
        "code_type_code,code_value,code_name,description,sort_num,parent_code,is_default\n" +
        "EXAMPLE_STATUS,ACTIVE,有效,可用于生产口径,10,,Y\n" +
        "EXAMPLE_STATUS,INACTIVE,无效,不再用于生产口径,20,,N\n";
    private static final String REFERENCE_CODE_MAPPING_TEMPLATE =
        "code_type_code,source_system,source_code,standard_code\n" +
        "EXAMPLE_STATUS,示例系统,1,ACTIVE\n" +
        "EXAMPLE_STATUS,示例系统,0,INACTIVE\n";
    private static final String MEASUREMENT_UNIT_TEMPLATE =
        "code,name,symbol,quantity_kind,conversion_factor,base_unit_code,precision,status\n" +
        "M,米,m,LENGTH,1,,3,ACTIVE\n" +
        "CM,厘米,cm,LENGTH,0.01,M,3,ACTIVE\n";
    private static final String DATA_STANDARD_PACKAGE_RULES =
        """
        数据标准包模板说明

        这个模板用于把“业务术语 -> 数据元 -> 公共码表 -> 计量单位 -> SQL/dbt 字段落标”串成一套可验收的标准包。
        示例行只表示填写格式，不代表客户现场业务真值；现场落地时请替换为客户确认后的口径。

        文件清单
        1) 01-business-terms.csv：业务术语。用于定义业务对象、指标口径、别名和归属域。
        2) 02-data-elements.csv：数据元。用于定义字段中文名、英文名、类型、可空、主题域、来源系统、码表编码和安全等级。
        3) 03-reference-code-directories.csv：公共码表目录。用于定义码表编码、名称、层级、业务分类、数据类型、状态和版本。
        4) 04-reference-code-items.csv：公共码表取值。用于定义标准码值、标准码名、排序、父级和值默认标识。
        5) 05-reference-code-mappings.csv：系统码值映射。用于把来源系统码值映射到标准码值。
        6) 06-measurement-units.csv：计量单位。使用稳定 code 描述名称、符号、量纲、换算因子和基准单位。

        建议维护顺序
        1) 先在“数据域/主题域”确认 domain / biz_catalog 的归属。
        2) 维护业务术语，统一业务名词和口径边界。
        3) 维护公共码表目录、码值和来源系统映射。
        4) 维护计量单位；base_unit_code 只填写同包或库中已存在的稳定单位 code，不填写数据库 UUID。
        5) 维护数据元；当字段使用枚举时，code_set 填公共码表的 code_type_code。
        6) 在 SQL/dbt 模型中绑定数据元，执行标准闸口；schema.yml 可继承字段说明和标准绑定。

        数据元导入校验规则
        1) 必填字段：field_name_cn, field_name_en, data_type, nullable, domain, description, source_system。
        2) 唯一键：field_name_en + domain。
        3) nullable 仅允许 Y/N（大小写不敏感）。
        4) data_type 建议使用：VARCHAR/INT/BIGINT/DECIMAL/DATE/TIMESTAMP/BOOLEAN/DOUBLE。
        5) VARCHAR 必须填写 data_length；DECIMAL 建议填写 data_precision/data_scale。
        6) security_level 可选：INTERNAL/CONFIDENTIAL/SECRET/TOP_SECRET。
        7) 空行或全部为空的记录会被跳过。

        常见错误说明
        - 缺少必填字段：请补齐必填列后再导入
        - 唯一键重复：同一 domain 下 field_name_en 重复
        - 类型不匹配：data_type 未在建议集合内
        - 长度/精度缺失：VARCHAR 未填 length 或 DECIMAL 未填 precision/scale
        """;

    private final DataStandardService standards;
    private final DataStandardAttachmentService attachments;
    private final DataStandardImportService standardImport;
    private final AuditService audit;

    public ModelingResource(
        DataStandardService standards,
        DataStandardAttachmentService attachments,
        DataStandardImportService standardImport,
        AuditService audit
    ) {
        this.standards = standards;
        this.attachments = attachments;
        this.standardImport = standardImport;
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

    @GetMapping("/standards/import-template")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ResponseEntity<byte[]> downloadStandardsImportTemplate() {
        byte[] content = standardImport.buildTemplateCsv();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDisposition(ContentDisposition.attachment().filename("数据标准导入模板.csv", StandardCharsets.UTF_8).build());
        audit.auditAction(
            "MODELING_STANDARD_IMPORT",
            AuditStage.SUCCESS,
            "template",
            Map.of("summary", "下载数据标准导入模板")
        );
        return ResponseEntity.ok().headers(headers).body(content);
    }

    @GetMapping("/metadata-standards/template")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ResponseEntity<byte[]> downloadMetadataStandardsTemplate() {
        byte[] zip = buildMetadataTemplateZip();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDisposition(
            ContentDisposition.attachment()
                .filename(URLEncoder.encode("data-standard-package-template.zip", StandardCharsets.UTF_8), StandardCharsets.UTF_8)
                .build()
        );
        audit.auditAction(
            "MODELING_METADATA_STANDARD_TEMPLATE_DOWNLOAD",
            AuditStage.SUCCESS,
            "template",
            Map.of("summary", "下载数据标准包模板")
        );
        return ResponseEntity.ok().headers(headers).body(zip);
    }

    @PostMapping(value = "/standards/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<DataStandardImportResultDto> importStandards(@RequestPart("file") MultipartFile file) {
        DataStandardImportResultDto result = standardImport.importCsv(file);
        Map<String, Object> auditPayload = new java.util.LinkedHashMap<>();
        auditPayload.put("summary", "导入数据标准");
        auditPayload.put("totalRows", result.getTotalRows());
        auditPayload.put("created", result.getCreated());
        auditPayload.put("updated", result.getUpdated());
        auditPayload.put("skipped", result.getSkipped());
        auditPayload.put("errorCount", result.getErrors() != null ? result.getErrors().size() : 0);
        audit.auditAction("MODELING_STANDARD_IMPORT", AuditStage.SUCCESS, "import", auditPayload);
        return ApiResponses.ok(result);
    }

    @GetMapping("/standards/template")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ResponseEntity<byte[]> downloadTemplate() {
        // Excel-friendly: prefix UTF-8 BOM so that Chinese headers and sample values render correctly.
        byte[] csv = standardImport.buildTemplateCsv();
        byte[] bom = "\uFEFF".getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[bom.length + csv.length];
        System.arraycopy(bom, 0, out, 0, bom.length);
        System.arraycopy(csv, 0, out, bom.length, csv.length);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        headers.setContentDisposition(
            ContentDisposition.attachment()
                .filename(URLEncoder.encode("data-standards-template.csv", StandardCharsets.UTF_8), StandardCharsets.UTF_8)
                .build()
        );
        audit.auditAction("MODELING_STANDARD_TEMPLATE_DOWNLOAD", AuditStage.SUCCESS, "template", Map.of("summary", "下载数据标准导入模板"));
        return ResponseEntity.ok().headers(headers).body(out);
    }

    private byte[] buildMetadataTemplateZip() {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            writeZipEntry(zos, "01-business-terms.csv", BUSINESS_TERM_TEMPLATE);
            writeZipEntry(zos, "02-data-elements.csv", DATA_ELEMENT_TEMPLATE);
            writeZipEntry(zos, "03-reference-code-directories.csv", REFERENCE_CODE_DIRECTORY_TEMPLATE);
            writeZipEntry(zos, "04-reference-code-items.csv", REFERENCE_CODE_ITEM_TEMPLATE);
            writeZipEntry(zos, "05-reference-code-mappings.csv", REFERENCE_CODE_MAPPING_TEMPLATE);
            writeZipEntry(zos, "06-measurement-units.csv", MEASUREMENT_UNIT_TEMPLATE);
            writeZipEntry(zos, "README-data-standard-package.txt", DATA_STANDARD_PACKAGE_RULES);

            zos.finish();
            return baos.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("数据标准包模板构建失败", ex);
        }
    }

    private static void writeZipEntry(ZipOutputStream zos, String entryName, String content) throws java.io.IOException {
        zos.putNextEntry(new ZipEntry(entryName));
        // Excel-friendly: prefix UTF-8 BOM so Chinese headers and examples render correctly.
        zos.write(("\uFEFF" + content).getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
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

    @PostMapping("/standards/{id}/archive")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<DataStandardDto> archive(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        try {
            DataStandardDto saved = standards.archive(id, activeDept);
            return ApiResponses.ok(saved);
        } catch (RuntimeException e) {
            Map<String, Object> detail = new java.util.LinkedHashMap<>();
            detail.put("summary", "归档数据标准失败");
            detail.put("error", e.getMessage());
            detail.put("targetId", id.toString());
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
        audit.auditAction("MODELING_STANDARD_VERSION_LIST", AuditStage.SUCCESS, id.toString(), detail);
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
        SecurityLevelCatalog.DataSecurityLevel parsed = SecurityLevelCatalog.DataSecurityLevel.parse(level);
        return parsed == null ? null : DataSecurityLevel.valueOf(parsed.code());
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
