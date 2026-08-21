const normalize = (value: unknown): string =>
	String(value ?? "")
		.trim()
		.toUpperCase();

const STATUS_LABELS: Readonly<Record<string, string>> = {
	ACTIVE: "生效",
	APPROVED: "已通过",
	APPLIED: "已应用",
	APPLY_COMPLETE: "应用完成",
	APPLY_RUNNING: "正在应用",
	ARCHIVED: "已归档",
	BUILDING: "构建中",
	BUILD_FAILED: "构建失败",
	BLOCKED: "已阻断",
	BUILT: "构建完成",
	ABORTED: "已终止",
	CANCELED: "已取消",
	CANCELLED: "已取消",
	COMPLETED: "已完成",
	CURRENT: "当前有效",
	DEPRECATED: "已弃用",
	DISABLED: "已停用",
	DISCOVERED: "已发现",
	DRAFT: "草稿",
	DRAFT_GOVERNANCE: "治理草稿",
	DRY_RUN_COMPLETE: "试跑完成",
	DRY_RUN_RUNNING: "试跑中",
	ENABLED: "已启用",
	ERROR: "异常",
	EFFECTIVE: "已生效",
	EXPIRED: "已过期",
	FAILED: "失败",
	GRANT_MISSING: "授权未生效",
	INACTIVE: "未启用",
	IN_PROGRESS: "进行中",
	HEALTHY: "正常",
	GOVERNED: "已治理",
	MANUAL_REVIEW: "人工确认",
	MATCHED: "已映射",
	ONLINE: "已上线",
	NOT_APPLICABLE: "不适用",
	PARTIAL: "部分完成",
	PASSED: "通过",
	PAUSED: "已暂停",
	PENDING: "待处理",
	PENDING_APPROVAL: "待审批",
	PENDING_CLAIM: "待认领",
	PENDING_CLASSIFICATION: "待定级",
	PENDING_DOMAIN: "待归域",
	PENDING_GOVERNANCE: "待治理",
	PENDING_LINEAGE: "待补血缘",
	PENDING_REVIEW: "待复核",
	PUBLISHED: "已发布",
	PUBLISHING: "发布中",
	PROPAGATED: "已传播",
	PROPAGATION_PENDING: "待传播",
	QUALITY_FAILED: "质量校验失败",
	QUALITY_PASSED: "质量校验通过",
	QUALITY_RUNNING: "质量校验中",
	QUEUED: "排队中",
	REJECTED: "已拒绝",
	RECONCILIATION_FAILED: "对账失败",
	REVIEW_PENDING: "待审核",
	RETRY: "待重试",
	ROLLED_BACK: "已回滚",
	RETIRED: "已退役",
	READY: "就绪",
	RUNNING: "运行中",
	SHARED: "已共享",
	SKIPPED: "已跳过",
	STALE: "已过期",
	SUCCESS: "成功",
	SUCCEEDED: "成功",
	SYNCED: "已同步",
	SYNC_FAILED: "同步失败",
	SYNC_PENDING: "待同步",
	TESTING: "测试中",
	TIMED_OUT: "已超时",
	TODO: "待处理",
	UNKNOWN: "待识别",
	UNMATCHED: "未匹配",
	WARNING: "警告",
	WAITING_EFFECTIVE: "等待生效",
};

const QUALITY_LABELS: Readonly<Record<string, string>> = {
	ACCURACY: "准确性",
	API: "接口触发",
	BLOCK: "阻断",
	BLOCKING: "阻断",
	COMPLETENESS: "完整性",
	CONSISTENCY: "一致性",
	CRITICAL: "严重",
	CRON: "定时调度",
	CUSTOM: "自定义",
	DATA_SOURCE_ERROR: "数据源异常",
	FILE_DECLARATION: "文件声明",
	DRY_RUN: "试跑",
	HIGH: "高",
	HIVE: "数据湖执行器",
	INCEPTOR: "默认数据湖执行器",
	JDBC: "数据库执行器",
	LOW: "低",
	MANUAL: "手动执行",
	MANUAL_FLOOR: "人工下限",
	MEDIUM: "中",
	MIGRATION: "存量迁移",
	QUALITY_VIOLATION: "质量规则未通过",
	READY: "就绪",
	REGEX_MATCH: "格式匹配",
	RETRY: "重新执行",
	SCHEDULED: "调度执行",
	SENSITIVE_DETECTION: "敏感识别",
	SOURCE_DECLARATION: "源端声明",
	SQL: "检测语句",
	SQL_EXECUTION_FAILED: "检测语句执行失败",
	SYSTEM: "系统触发",
	TIMELINESS: "及时性",
	TIMEOUT: "执行超时",
	UPSTREAM_INHERITANCE: "上游继承",
	UNIQUENESS: "唯一性",
	VALIDITY: "有效性",
	WARN: "告警",
	WARNING: "警告",
};

const INDICATOR_DOMAIN_LABELS: Readonly<Record<string, string>> = {
	COMPLIANCE: "合规",
	CUSTOM: "自定义",
	FINANCE: "财务",
	OPERATION: "运营",
	QUALITY: "质量",
};

const INDICATOR_TYPE_LABELS: Readonly<Record<string, string>> = {
	ATOMIC: "原子指标",
	BASE: "基础指标",
	CALCULATED: "计算指标",
	COMPOSITE: "复合指标",
	CORE: "核心指标",
	DERIVED: "派生指标",
};

const AGGREGATION_LABELS: Readonly<Record<string, string>> = {
	AVERAGE: "平均值",
	AVG: "平均值",
	COUNT: "计数",
	DISTINCT_COUNT: "去重计数",
	MAX: "最大值",
	MIN: "最小值",
	SUM: "求和",
};

const GRANULARITY_LABELS: Readonly<Record<string, string>> = {
	DAY: "日",
	HOUR: "小时",
	MINUTE: "分钟",
	MONTH: "月",
	QUARTER: "季度",
	WEEK: "周",
	YEAR: "年",
};

const STATUS_AXIS_LABELS: Readonly<Record<string, string>> = {
	GOVERNANCE: "治理状态",
	LIFECYCLE: "生命周期状态",
	PUBLICATION: "发布状态",
	QUALITY: "质量状态",
	SERVING: "服务状态",
};

const GOVERNANCE_EVENT_LABELS: Readonly<Record<string, string>> = {
	ARCHIVE_APPROVED: "归档申请已批准",
	ARCHIVE_EXECUTED: "归档已执行",
	ARCHIVE_FIRST_APPROVED: "归档申请已完成首次审批",
	ARCHIVE_REJECTED: "归档申请已驳回",
	ARCHIVE_REQUESTED: "已申请归档",
	CONTROL_ACTION: "治理控制操作",
	DETECTION_RAISED: "敏感识别提高密级",
	EXECUTION_TOKEN: "执行凭证",
	LINEAGE_PROPAGATION: "血缘传播",
	MANUAL_FLOOR_RAISED: "人工提高密级下限",
	PERMANENT_DESTROY_APPROVED: "永久销毁申请已批准",
	PERMANENT_DESTROY_EXECUTED: "永久销毁已执行",
	PERMANENT_DESTROY_FIRST_APPROVED: "永久销毁申请已完成首次审批",
	PERMANENT_DESTROY_REJECTED: "永久销毁申请已驳回",
	PERMANENT_DESTROY_REQUESTED: "已申请永久销毁",
	RESTORE_APPROVED: "恢复申请已批准",
	RESTORE_EXECUTED: "恢复已执行",
	RESTORE_FIRST_APPROVED: "恢复申请已完成首次审批",
	RESTORE_REJECTED: "恢复申请已驳回",
	RESTORE_REQUESTED: "已申请恢复",
	SEALED: "已封存",
	SEMANTIC_DELIVERY: "语义交付",
	TRASH_APPROVED: "临时销毁申请已批准",
	TRASH_EXECUTED: "临时销毁已执行",
	TRASH_FIRST_APPROVED: "临时销毁申请已完成首次审批",
	TRASH_REJECTED: "临时销毁申请已驳回",
	TRASH_REQUESTED: "已申请临时销毁",
	UPSTREAM_INHERITED: "已继承上游密级",
};

const MIGRATION_DECISION_LABELS: Readonly<Record<string, string>> = {
	BLOCKED_DOWNGRADE: "禁止降级",
	BLOCKED_INVALID: "无效密级",
	BLOCKED_MISSING: "缺少可信密级",
	CREATE: "新建密级事实",
	RAISE: "提升密级",
	UNCHANGED: "无需变更",
};

const CHANGE_TYPE_LABELS: Readonly<Record<string, string>> = {
	ADDED: "新增",
	REMOVED: "移除",
	UPDATED: "更新",
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
	DATA_ACCESS: "数据访问",
	READ: "查看",
	EDIT: "编辑",
	MANAGE: "管理",
	OWNER: "所有者",
	SHARE: "共享",
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
	return key ? (labels[key] ?? fallback) : fallback;
};

export function statusLabel(value: unknown, fallback = "未知状态"): string {
	const key = normalize(value);
	return key ? (STATUS_LABELS[key] ?? fallback) : fallback;
}

export function materializationLabel(value: unknown): string {
	const key = String(value ?? "")
		.trim()
		.toLowerCase();
	return MATERIALIZATION_LABELS[key] ?? "未知方式";
}

export const qualityLabel = (value: unknown, fallback = "未知质量类型") =>
	resolveLabel(QUALITY_LABELS, value, fallback);

export const indicatorDomainLabel = (value: unknown, fallback = "其他领域") =>
	resolveLabel(INDICATOR_DOMAIN_LABELS, value, fallback);

export const indicatorTypeLabel = (value: unknown, fallback = "其他指标") =>
	resolveLabel(INDICATOR_TYPE_LABELS, value, fallback);

export const aggregationLabel = (value: unknown, fallback = "其他方式") =>
	resolveLabel(AGGREGATION_LABELS, value, fallback);

export const granularityLabel = (value: unknown, fallback = "其他粒度") =>
	resolveLabel(GRANULARITY_LABELS, value, fallback);

export const statusAxisLabel = (value: unknown, fallback = "其他状态") =>
	resolveLabel(STATUS_AXIS_LABELS, value, fallback);

export const governanceEventLabel = (value: unknown, fallback = "治理事件") =>
	resolveLabel(GOVERNANCE_EVENT_LABELS, value, fallback);

export const migrationDecisionLabel = (value: unknown, fallback = "待处理") =>
	resolveLabel(MIGRATION_DECISION_LABELS, value, fallback);

export const changeTypeLabel = (value: unknown, fallback = "其他变更") =>
	resolveLabel(CHANGE_TYPE_LABELS, value, fallback);

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
