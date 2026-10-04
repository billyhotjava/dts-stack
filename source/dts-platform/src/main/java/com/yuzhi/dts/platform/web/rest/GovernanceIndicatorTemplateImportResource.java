package com.yuzhi.dts.platform.web.rest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.governance.GovIndicatorTemplate;
import com.yuzhi.dts.platform.service.governance.IndicatorTemplateService;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/governance/indicator-templates")
public class GovernanceIndicatorTemplateImportResource {

    private static final String GOVERNANCE_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).GOVERNANCE_MAINTAINERS)";

    private final IndicatorTemplateService templateService;
    private final ObjectMapper objectMapper;

    public GovernanceIndicatorTemplateImportResource(
        IndicatorTemplateService templateService,
        ObjectMapper objectMapper
    ) {
        this.templateService = templateService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/import")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Integer>> importTemplates(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件不能为空");
        }
        if (file.getSize() > 10 * 1024 * 1024L) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "文件不能超过 10MB");
        }

        List<GovIndicatorTemplate> templates;
        try {
            templates = objectMapper.readValue(
                file.getInputStream(),
                new TypeReference<List<GovIndicatorTemplate>>() {}
            );
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "JSON 格式错误");
        }

        if (templates == null || templates.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件内容为空");
        }
        if (templates.size() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "单次导入不能超过 1000 个模板");
        }
        for (int i = 0; i < templates.size(); i++) {
            GovIndicatorTemplate t = templates.get(i);
            if (t == null || t.getCode() == null || t.getCode().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "第 " + (i + 1) + " 个模板缺少 code 字段");
            }
            if (t.getName() == null || t.getName().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "第 " + (i + 1) + " 个模板缺少 name 字段");
            }
        }

        Map<String, Integer> result = templateService.batchImport(templates);
        return ApiResponses.ok(result);
    }
}
