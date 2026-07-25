package com.yuzhi.dts.platform.service.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record AssetTagRequest(
    @NotBlank(message = "资产类型不能为空") @Size(max = 32, message = "资产类型长度不能超过 32")
    String assetType,
    @NotBlank(message = "资产标识不能为空") @Size(max = 512, message = "资产标识长度不能超过 512")
    String assetKey,
    @NotEmpty(message = "标签不能为空")
    @Size(max = MAX_TAG_IDS, message = "单次资产标签操作最多支持 100 个标签")
    List<@NotNull(message = "标签不能为空") UUID> tagIds
) {
    public static final int MAX_TAG_IDS = 100;
}
