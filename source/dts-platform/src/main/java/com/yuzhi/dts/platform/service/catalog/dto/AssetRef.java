package com.yuzhi.dts.platform.service.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AssetRef(
    @NotBlank(message = "资产类型不能为空") @Size(max = 32, message = "资产类型长度不能超过 32")
    String assetType,
    @NotBlank(message = "资产标识不能为空") @Size(max = 512, message = "资产标识长度不能超过 512")
    String assetKey
) {}
