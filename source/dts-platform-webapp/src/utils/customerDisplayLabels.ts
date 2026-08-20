const normalize = (value: unknown): string => String(value ?? "").trim().toUpperCase();

const STATUS_LABELS: Readonly<Record<string, string>> = {
	ACTIVE: "生效",
	APPROVED: "已通过",
	ARCHIVED: "已归档",
	BUILDING: "构建中",
	BUILD_FAILED: "构建失败",
	BUILT: "构建完成",
	CANCELED: "已取消",
	CANCELLED: "已取消",
	COMPLETED: "已完成",
	CURRENT: "当前有效",
	DISABLED: "已停用",
	DRAFT: "草稿",
	ENABLED: "已启用",
	EFFECTIVE: "已生效",
	EXPIRED: "已过期",
	FAILED: "失败",
	GRANT_MISSING: "授权未生效",
	INACTIVE: "未启用",
	IN_PROGRESS: "进行中",
	ONLINE: "已上线",
	PARTIAL: "部分完成",
	PENDING: "待处理",
	PUBLISHED: "已发布",
	PUBLISHING: "发布中",
	QUALITY_FAILED: "质量校验失败",
	QUALITY_PASSED: "质量校验通过",
	QUALITY_RUNNING: "质量校验中",
	QUEUED: "排队中",
	REJECTED: "已拒绝",
	REVIEW_PENDING: "待审核",
	ROLLED_BACK: "已回滚",
	RETIRED: "已退役",
	RUNNING: "运行中",
	SHARED: "已共享",
	STALE: "已过期",
	SUCCESS: "成功",
	SUCCEEDED: "成功",
	SYNCED: "已同步",
	SYNC_FAILED: "同步失败",
	TIMED_OUT: "已超时",
	TODO: "待处理",
	WAITING_EFFECTIVE: "等待生效",
};

export const MATERIALIZATION_LABELS: Readonly<Record<string, string>> = {
	table: "表",
	incremental: "增量表",
	view: "视图",
	ephemeral: "临时模型",
};

export const ASSET_TYPE_LABELS: Readonly<Record<string, string>> = {
	TABLE: "数据表",
	CARD: "分析卡片",
	DASHBOARD: "分析看板",
	SCREEN: "数据大屏",
	MODEL: "数据模型",
	DATASET: "数据集",
	QUERY_DATASET: "查询数据集",
};

export const REPORT_TYPE_LABELS: Readonly<Record<string, string>> = {
	COCKPIT: "驾驶舱",
	DASHBOARD: "主题看板",
	REPORT: "分析报表",
	APP: "业务应用",
};

export const PERMISSION_LABELS: Readonly<Record<string, string>> = {
	READ: "查看",
	EDIT: "编辑",
	MANAGE: "管理",
	OWNER: "所有者",
};

export const SUBJECT_TYPE_LABELS: Readonly<Record<string, string>> = {
	USER: "用户",
	ROLE: "角色",
	DEPARTMENT: "部门",
	DEPT: "部门",
};

export const CLASSIFICATION_LABELS: Readonly<Record<string, string>> = {
	PUBLIC: "公开",
	INTERNAL: "内部",
	SECRET: "秘密",
	CONFIDENTIAL: "机密",
	RESTRICTED: "受限",
};

export const AUDIT_ACTION_LABELS: Readonly<Record<string, string>> = {
	CHECK_ALLOW: "权限校验通过",
	CHECK_DENY: "权限校验拒绝",
	GRANT: "授权",
	REVOKE: "撤销授权",
	CHANGE_OWNERSHIP: "变更所有者",
};

const resolveLabel = (labels: Readonly<Record<string, string>>, value: unknown, fallback: string): string => {
	const key = normalize(value);
	return key ? labels[key] ?? fallback : fallback;
};

export function statusLabel(value: unknown, fallback = "未知状态"): string {
	const key = normalize(value);
	return key ? STATUS_LABELS[key] ?? fallback : fallback;
}

export function materializationLabel(value: unknown): string {
	const key = String(value ?? "").trim().toLowerCase();
	return MATERIALIZATION_LABELS[key] ?? "未知方式";
}

export const assetTypeLabel = (value: unknown, fallback = "未知资产类型") =>
	resolveLabel(ASSET_TYPE_LABELS, value, fallback);

export const permissionLabel = (value: unknown, fallback = "未知权限") =>
	resolveLabel(PERMISSION_LABELS, value, fallback);

export const subjectTypeLabel = (value: unknown, fallback = "未知主体类型") =>
	resolveLabel(SUBJECT_TYPE_LABELS, value, fallback);

export const classificationLabel = (value: unknown, fallback = "未设置密级") =>
	resolveLabel(CLASSIFICATION_LABELS, value, fallback);

export const auditActionLabel = (value: unknown, fallback = "未知审计动作") =>
	resolveLabel(AUDIT_ACTION_LABELS, value, fallback);

export const reportTypeLabel = (value: unknown, fallback = "未知报表类型") =>
	resolveLabel(REPORT_TYPE_LABELS, value, fallback);
