package com.yuzhi.dts.platform.service.catalog.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record BatchAssetTagRequest(
    @NotEmpty(message = "批量打标资产不能为空")
    @Size(max = 500, message = "单次批量打标最多支持 500 个资产")
    List<@Valid AssetRef> assets,
    @NotEmpty(message = "标签不能为空")
    @Size(max = AssetTagRequest.MAX_TAG_IDS, message = "单次资产标签操作最多支持 100 个标签")
    List<@NotNull(message = "标签不能为空") UUID> tagIds
) {}
