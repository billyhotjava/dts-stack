package com.yuzhi.dts.platform.web.rest.infra;

import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.infra.ExcelImportService;
import com.yuzhi.dts.platform.service.infra.dto.ExcelImportErrorPreviewResponse;
import com.yuzhi.dts.platform.service.infra.dto.ExcelImportParseRequest;
import com.yuzhi.dts.platform.service.infra.dto.ExcelImportParseResponse;
import com.yuzhi.dts.platform.service.infra.dto.ExcelImportPrepareResponse;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ApiResponses;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/infra/excel-import")
public class ExcelImportResource {

    private static final String INFRA_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).INFRA_MAINTAINERS)";

    private final ExcelImportService excelImportService;

    public ExcelImportResource(ExcelImportService excelImportService) {
        this.excelImportService = excelImportService;
    }

    @PostMapping(value = "/prepare", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ApiResponse<ExcelImportPrepareResponse> prepare(
        @RequestPart("file") MultipartFile file,
        @RequestParam("classification") String classification,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        String operator = SecurityUtils.getCurrentUserLogin().orElse("system");
        ExcelImportPrepareResponse response = excelImportService.prepare(file, operator, activeDept, classification);
        return ApiResponses.ok(response);
    }

    @PostMapping("/parse")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ApiResponse<ExcelImportParseResponse> parse(
        @RequestBody ExcelImportParseRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        String operator = SecurityUtils.getCurrentUserLogin().orElse("system");
        boolean privileged = SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.INSTITUTE_PRIVILEGED_ROLES);
        ExcelImportParseResponse response = excelImportService.parse(request, operator, activeDept, privileged);
        return ApiResponses.ok(response);
    }

    @GetMapping("/errors")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ApiResponse<ExcelImportErrorPreviewResponse> errors(
        @RequestParam("fileId") java.util.UUID fileId,
        @RequestParam(value = "limit", required = false) Integer limit,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        String operator = SecurityUtils.getCurrentUserLogin().orElse("system");
        boolean privileged = SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.INSTITUTE_PRIVILEGED_ROLES);
        ExcelImportErrorPreviewResponse response = excelImportService.errorPreview(fileId, limit, operator, activeDept, privileged);
        return ApiResponses.ok(response);
    }
}
