package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.domain.iam.IamDept;
import com.yuzhi.dts.platform.repository.iam.IamDeptRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/departments")
public class DeptResource {

    private final IamDeptRepository repository;

    public DeptResource(IamDeptRepository repository) {
        this.repository = repository;
    }

    public record DeptDto(String code, String nameZh, String nameEn) {}

    @GetMapping
    public ApiResponse<List<DeptDto>> list(@RequestParam(name = "keyword", required = false) String keyword) {
        List<IamDept> items;
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            items = repository.findTop100ByCodeIgnoreCaseContainingOrNameZhIgnoreCaseContainingOrderByCodeAsc(kw, kw);
        } else {
            items = repository.findTop100ByOrderByCodeAsc();
        }
        List<DeptDto> data = items
            .stream()
            .map(it -> new DeptDto(it.getCode(), safe(it.getNameZh()), safe(it.getNameEn())))
            .collect(Collectors.toList());
        return ApiResponses.ok(data);
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}

