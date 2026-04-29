package com.yuzhi.dts.platform.service.permission.dto;

import com.yuzhi.dts.platform.domain.permission.AssetGrant;
import java.time.Instant;

/**
 * 共享 grant 的对外视图。隐藏内部字段（asset_type/asset_id 等冗余信息），
 * 只暴露调用方决策需要的部分。
 */
public record AssetGrantDto(
    Long id,
    String granteeType,
    String granteeId,
    String permission,
    boolean levelOverride,
    String grantedBy,
    String grantReason,
    Instant grantedAt
) {
    public static AssetGrantDto from(AssetGrant g) {
        return new AssetGrantDto(
            g.getId(),
            g.getGranteeType(),
            g.getGranteeId(),
            g.getPermission(),
            g.isLevelOverride(),
            g.getGrantedBy(),
            g.getGrantReason(),
            g.getCreatedDate()
        );
    }
}
