package com.yuzhi.dts.common.security;

import java.util.Objects;
import java.util.Set;

/**
 * F11-T05 带范围的单项授权绑定。
 *
 * <p>动作权限与其组织/资源范围必须绑定在同一项 grant 上；禁止把“所有权限”与“所有范围”
 * 分别合并后再交叉授权（笛卡尔放行）。
 *
 * @param tenant 租户；必须精确匹配
 * @param permission 权限码（{@link PermissionCodes}），必须精确匹配，不做动作推导
 * @param orgScope 组织范围编码；{@code null} 表示未声明——按 UT-015 不得解释为全所；
 *     所级显式范围使用 {@link #INSTITUTE_SCOPE}
 * @param datasetIds 数据集范围；空表示未授权任何数据集
 * @param operations 允许的操作；空表示未授权任何操作
 */
public record ScopedGrant(
    String tenant,
    String permission,
    String orgScope,
    Set<String> datasetIds,
    Set<String> operations
) {
    /** 显式所级范围哨兵；只有持有该值的绑定才匹配任意部门（仍须满足其余约束）。 */
    public static final String INSTITUTE_SCOPE = "*";

    public ScopedGrant {
        if (tenant == null || tenant.isBlank()) {
            throw new IllegalArgumentException("tenant 不能为空");
        }
        if (!PermissionCodes.isKnown(permission)) {
            throw new IllegalArgumentException("permission 非法: " + permission);
        }
        datasetIds = datasetIds == null ? Set.of() : Set.copyOf(datasetIds);
        operations = operations == null ? Set.of() : Set.copyOf(operations);
    }

    /**
     * 单次授权请求。
     *
     * @param deptCode 目标部门编码；精确匹配，后缀/父子/名称不产生授权
     * @param datasetId 目标数据集；为 null 表示本次不校验数据集维度
     * @param operation 目标操作；为 null 表示本次不校验操作维度
     */
    public record AccessRequest(String tenant, String permission, String deptCode, String datasetId, String operation) {}

    /**
     * 同一绑定内匹配（纯函数）。
     *
     * <ul>
     *   <li>租户/权限码任一不符 → false；</li>
     *   <li>orgScope 为 null → false（不解释成全所）；{@code "*"} 匹配任意部门；其余精确相等；</li>
     *   <li>请求带 datasetId 而绑定集合不包含 → false；</li>
     *   <li>请求带 operation 而绑定集合不包含 → false。</li>
     * </ul>
     */
    public static boolean matches(ScopedGrant grant, AccessRequest request) {
        if (grant == null || request == null) {
            return false;
        }
        if (!grant.tenant().equals(request.tenant())) {
            return false;
        }
        if (!PermissionCodes.isKnown(request.permission()) || !grant.permission().equals(request.permission())) {
            return false;
        }
        if (grant.orgScope() == null) {
            return false;
        }
        if (!INSTITUTE_SCOPE.equals(grant.orgScope()) && !grant.orgScope().equals(request.deptCode())) {
            return false;
        }
        if (request.datasetId() != null && !grant.datasetIds().contains(request.datasetId())) {
            return false;
        }
        if (request.operation() != null && !grant.operations().contains(request.operation())) {
            return false;
        }
        return true;
    }
}
