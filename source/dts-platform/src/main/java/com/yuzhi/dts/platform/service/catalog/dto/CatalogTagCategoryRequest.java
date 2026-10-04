package com.yuzhi.dts.platform.service.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CatalogTagCategoryRequest(
    @NotBlank(message = "分类编码不能为空")
    @Size(max = 64, message = "分类编码长度不能超过 64")
    @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_-]*", message = "分类编码必须使用 ASCII 字母、数字、下划线或连字符")
    String code,
    @NotBlank(message = "分类名称不能为空") @Size(max = 128, message = "分类名称长度不能超过 128")
    String name,
    UUID parentId,
    Integer sortOrder,
    Boolean builtin,
    Boolean enabled,
    @Size(max = 512, message = "分类说明长度不能超过 512")
    String description
) {}
