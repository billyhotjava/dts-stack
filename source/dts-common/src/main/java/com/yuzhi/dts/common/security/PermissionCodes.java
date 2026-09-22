package com.yuzhi.dts.common.security;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * F11-T03 权限点字典（首批建模/目录/治理/管理动作）。
 *
 * <p>格式 {@code 模块:资源[:动作]} 全小写；两段式为既有门户码兼容，新码一律三段。
 * 不按按钮数量倒推字典，编辑/审核/执行/导出/授权分别表达。
 * 纯自定义角色不得获得建模码（由 T03 审批校验执行，本字典只定义合法集合）。
 */
public final class PermissionCodes {

    private PermissionCodes() {}

    public static final String MODELING_MODEL_READ = "modeling:model:read";
    public static final String MODELING_MODEL_UPDATE = "modeling:model:update";
    public static final String MODELING_MODEL_SHARE = "modeling:model:share";
    public static final String MODELING_MODEL_PUBLISH = "modeling:model:publish";

    public static final String CATALOG_DATASET_READ = "catalog:dataset:read";
    public static final String CATALOG_DATASET_EXPORT = "catalog:dataset:export";

    public static final String GOVERNANCE_RULE_MANAGE = "governance:rule:manage";

    public static final String ADMIN_USER_MANAGE = "admin:user:manage";
    public static final String ADMIN_ROLE_MANAGE = "admin:role:manage";
    public static final String ADMIN_ORG_MANAGE = "admin:org:manage";

    public static final String PORTAL_READ = "portal:read";
    public static final String PORTAL_DATASET_READ = "portal:dataset:read";

    public static final Set<String> ALL = Set.of(
        MODELING_MODEL_READ,
        MODELING_MODEL_UPDATE,
        MODELING_MODEL_SHARE,
        MODELING_MODEL_PUBLISH,
        CATALOG_DATASET_READ,
        CATALOG_DATASET_EXPORT,
        GOVERNANCE_RULE_MANAGE,
        ADMIN_USER_MANAGE,
        ADMIN_ROLE_MANAGE,
        ADMIN_ORG_MANAGE,
        PORTAL_READ,
        PORTAL_DATASET_READ
    );

    private static final Pattern CODE_PATTERN = Pattern.compile("^[a-z0-9]+:[a-z0-9_\\-]+(:[a-z]+)?$");

    /** 格式合法且在字典内。未知/禁用码一律 false，调用方按拒绝处理。 */
    public static boolean isKnown(String code) {
        return code != null && ALL.contains(code);
    }

    /** 仅格式校验（模块:资源:动作），不判断是否在字典内。 */
    public static boolean isValid(String code) {
        return code != null && CODE_PATTERN.matcher(code).matches();
    }
}
