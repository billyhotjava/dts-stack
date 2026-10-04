package com.yuzhi.dts.platform.service.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CatalogTagRequest(
    @NotNull(message = "标签分类不能为空")
    UUID categoryId,
    @NotBlank(message = "标签编码不能为空")
    @Size(max = 64, message = "标签编码长度不能超过 64")
    @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_-]*", message = "标签编码必须使用 ASCII 字母、数字、下划线或连字符")
    String code,
    @NotBlank(message = "标签名称不能为空") @Size(max = 128, message = "标签名称长度不能超过 128")
    String name,
    @Size(max = 16, message = "标签颜色长度不能超过 16")
    String color,
    Boolean builtin,
    Boolean enabled,
    @Size(max = 512, message = "标签说明长度不能超过 512")
    String description
) {}
