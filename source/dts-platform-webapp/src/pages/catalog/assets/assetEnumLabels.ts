// 资产域枚举的唯一中文字典。界面不得直接显示枚举原值——
// 未收录时降级为"未知（原值）"，使漏译在界面上可见且可被测试断言。

const normalizeKey = (value?: string | null) =>
	String(value ?? "")
		.trim()
		.toUpperCase();

/**
 * 把枚举原值解析为中文标签。
 * @param dict 枚举字典
 * @param value 枚举原值，允许空
 * @param fallback 空值时的显示，默认"未设定"
 */
export function resolveEnumLabel(
	dict: Record<string, string>,
	value?: string | null,
	fallback = "未设定",
): string {
	const key = normalizeKey(value);
	if (!key) return fallback;
	return dict[key] ?? `未知（${key}）`;
}

/** 治理状态：后端散落于 service/catalog 的字符串常量，全集见 sprint-75 spec F5/2.1 */
export const GOVERNANCE_STATUS_DICT: Record<string, string> = {
	GOVERNED: "已治理",
	PENDING_CLAIM: "待认领",
	PENDING_CLASSIFICATION: "待定级",
	PENDING_DOMAIN: "待归域",
	PENDING_GOVERNANCE: "待治理",
	PENDING_APPROVAL: "待审批",
	PENDING_LINEAGE: "待补血缘",
	PENDING_REVIEW: "待复核",
	DISABLED: "已停用",
};

/** 生命周期：对应 Java 枚举 CatalogAssetLifecycleStatus */
export const LIFECYCLE_STATUS_DICT: Record<string, string> = {
	DISCOVERED: "已发现",
	PENDING_GOVERNANCE: "待治理",
	DRAFT_GOVERNANCE: "治理草稿",
	TESTING: "测试中",
	ACTIVE: "生效",
	DEPRECATED: "已弃用",
	ARCHIVED: "已归档",
	BLOCKED: "已阻断",
	PENDING_REVIEW: "待复核",
};

export const CLASSIFICATION_DICT: Record<string, string> = {
	PUBLIC: "公开",
	INTERNAL: "内部",
	SECRET: "秘密",
	CONFIDENTIAL: "机密",
};

export const MATCH_STATUS_DICT: Record<string, string> = {
	MATCHED: "已映射",
	UNMATCHED: "未匹配",
	MANUAL_REVIEW: "人工确认",
};

/** 资产类型：Hive / JDBC 是产品名与技术标准名，按项目语言规范保持原形 */
export const ASSET_TYPE_DICT: Record<string, string> = {
	HIVE: "Hive",
	JDBC: "JDBC",
	FILE: "文件",
};
