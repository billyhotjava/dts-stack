package com.yuzhi.dts.platform.service.permission.dto;

/**
 * 大屏共享请求。granteeId 是被共享给的用户 username（非 UUID）。
 *
 * @param granteeId      被授权用户名（必填）
 * @param permission     VIEW 或 MANAGE（大小写不敏感）
 * @param levelOverride  仅在 permission=VIEW 时生效；true 表示授予越级访问
 *                       （即 grantee 的人员密级低于大屏密级时仍可查看）
 * @param reason         授权原因（用于审计 / 回溯）
 */
public record DashboardShareRequest(String granteeId, String permission, boolean levelOverride, String reason) {}
