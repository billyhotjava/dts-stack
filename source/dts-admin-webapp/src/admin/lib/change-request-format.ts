import { PERSON_SECURITY_LEVELS } from "@/constants/governance";

export interface ChangeRequestFormatContext {
	roleDisplay?: Record<string, string>;
	userDisplay?: Record<string, string>;
}

const FIELD_LABELS: Record<string, string> = {
	// 通用字段
	username: "用户名",
	name: "名称",
	displayName: "显示名称",
	fullName: "姓名",
	fullname: "姓名",
	description: "描述",
	reason: "审批备注",
	operations: "操作权限",
	action: "操作",
	actionDisplay: "操作",
	allowRoles: "允许角色",
	allowedPermissions: "允许权限",
	allowedRoles: "允许角色",
	allowedRules: "允许规则",
	allowRules: "允许规则",
	enabled: "状态",
	status: "状态",
	email: "邮箱",
	mobile: "手机号",
	phone: "联系电话",
	groupPaths: "所属组织",
	orgPath: "所属组织",
	orgPaths: "所属组织",
	orgName: "组织名称",
	orgId: "组织标识",
	securityLevel: "安全级别",
	personSecurityLevel: "人员密级",
	personLevel: "人员密级",
	personnelSecurityLevel: "人员密级",
	personnelLevel: "人员密级",
	deptCode: "部门编码",
	dept_code: "部门编码",
	department: "所属部门",
	fullNameZh: "中文姓名",
	title: "标题",
	allowDesensitize: "允许脱敏",
	allowDesensitizeJson: "允许脱敏",
	allowDesensitizeFlag: "允许脱敏",
	allowDesensitizeData: "允许脱敏",
	datasetName: "数据集名称",
	targetRef: "目标引用",
	targetReference: "目标引用",
	// 数据级别 / 作用域
	dataLevel: "数据密级",
	dataLevels: "数据密级",
	maxDataLevel: "最大数据密级",
	maxDataLevels: "最大数据密级",
	scope: "作用域",
	shareScope: "共享范围",
	// 角色 / 菜单
	roles: "角色",
	resultRoles: "角色",
	realmRoles: "角色",
	clientRoles: "客户端角色",
	members: "角色成员",
	memberCount: "成员数量",
	memberAdds: "新增成员",
	memberRemoves: "移除成员",
	memberAddsRequested: "申请新增成员",
	memberRemovesRequested: "申请移除成员",
	menuIds: "菜单绑定",
	menuBindings: "菜单绑定",
	deleted: "禁用状态",
	allowedrules: "允许规则",
};

const DATA_LEVEL_LABELS: Record<string, string> = {
	DATA_PUBLIC: "公开",
	DATA_INTERNAL: "内部",
	DATA_SECRET: "秘密",
	DATA_CONFIDENTIAL: "机密",
};

const PERSON_LEVEL_LABELS: Record<string, string> = Object.fromEntries(
	PERSON_SECURITY_LEVELS.map((it) => [String(it.value).toUpperCase(), it.label]),
);

const SCOPE_LABELS: Record<string, string> = {
	DEPARTMENT: "部门",
	INSTITUTE: "研究所共享区",
	DEPT: "部门",
};

const SHARE_SCOPE_LABELS: Record<string, string> = {
	SHARE_INST: "所内共享",
	PUBLIC_INST: "所内公开",
};

const OP_LABELS: Record<string, string> = {
	read: "读取",
	write: "写入",
	export: "导出",
};

const STATUS_VALUE_LABELS: Record<string, string> = {
	PENDING: "待审批",
	PROCESSING: "处理中",
	APPROVED: "已通过",
	APPLIED: "已应用",
	FAILED: "失败",
	REJECTED: "已驳回",
	ON_HOLD: "待定",
	COMPLETED: "已完成",
	ENABLED: "启用",
	DISABLED: "禁用",
	ENABLE: "启用",
	DISABLE: "禁用",
};

const OPERATION_TYPE_LABELS: Record<string, string> = {
	CREATE: "新增",
	UPDATE: "更新",
	DELETE: "删除",
	GRANT: "授予",
	REVOKE: "撤销",
	ENABLE: "启用",
	DISABLE: "禁用",
	BATCH_CREATE: "批量新增",
	BATCH_UPDATE: "批量更新",
	BATCH_DELETE: "批量删除",
	BATCH_ENABLE: "批量启用",
	BATCH_DISABLE: "批量禁用",
};

function toCamelCase(input: string): string {
	if (!input) return "";
	return input
		.replace(/^[_.\s-]+/, "")
		.toLowerCase()
		.replace(/[-_.\s]+(.)?/g, (_, c) => (c ? c.toUpperCase() : ""));
}

function stripDecorators(key: string): string {
	return key.replace(/\[[^\]]*]/g, "");
}

function collectKeyCandidates(rawKey: string): string[] {
	const candidates: string[] = [];
	const seen = new Set<string>();
	const push = (value: string | null | undefined) => {
		if (!value) return;
		if (seen.has(value)) return;
		seen.add(value);
		candidates.push(value);
	};

	const stripped = stripDecorators(rawKey || "");
	push(stripped);
	push(stripped.toLowerCase());

	const tail = stripped.includes(".") ? stripped.substring(stripped.lastIndexOf(".") + 1) : stripped;
	push(tail);
	push(tail.toLowerCase());

	const camel = toCamelCase(tail);
	if (camel) {
		push(camel);
		push(camel.charAt(0).toLowerCase() + camel.slice(1));
		push(camel.charAt(0).toUpperCase() + camel.slice(1));
	}

	const snake = tail.replace(/[-.\s]/g, "_");
	push(snake);
	push(snake.toLowerCase());

	return candidates;
}

export function labelForChangeField(key: string): string {
	const candidates = collectKeyCandidates(key);
	for (const candidate of candidates) {
		if (FIELD_LABELS[candidate]) {
			return FIELD_LABELS[candidate];
		}
	}
	// 默认返回尾部字段，避免出现 attributes.person_security_level 全量路径
	const stripped = stripDecorators(key || "");
	if (stripped.includes(".")) {
		return stripped.substring(stripped.lastIndexOf(".") + 1);
	}
	return stripped || key;
}

function mapArray(value: unknown, mapper: (entry: any) => string): string {
	const list = Array.isArray(value) ? value : value == null ? [] : [value];
	if (list.length === 0) return "—";
	return list.map(mapper).join("，");
}

function isPersonLevelKey(key: string): boolean {
	const normalized = stripDecorators(key || "").toLowerCase();
	return (
		normalized.endsWith("personsecuritylevel") ||
		normalized.endsWith("person_level") ||
		normalized.endsWith("person_security_level") ||
		normalized.endsWith("personlevel") ||
		normalized.endsWith("personnelsecuritylevel") ||
		normalized.endsWith("personnel_security_level")
	);
}

function normalizeKey(key: string): string {
	const stripped = stripDecorators(key || "");
	const tail = stripped.includes(".") ? stripped.substring(stripped.lastIndexOf(".") + 1) : stripped;
	const camel = toCamelCase(tail);
	if (!camel) return tail.toLowerCase();
	return camel.charAt(0).toLowerCase() + camel.slice(1);
}

function truthy(value: unknown): boolean {
	if (typeof value === "boolean") return value;
	if (typeof value === "number") return value !== 0;
	if (typeof value === "string") {
		const trimmed = value.trim().toLowerCase();
		return ["true", "yes", "y", "1", "启用", "是"].includes(trimmed);
	}
	return Boolean(value);
}

export function formatChangeValue(
	key: string,
	value: unknown,
	ctx?: ChangeRequestFormatContext,
): string {
	if (value == null || value === "") return "—";

	if (isPersonLevelKey(key)) {
		return mapArray(value, (item) => {
			const raw = String(item || "");
			const mapped = PERSON_LEVEL_LABELS[raw.toUpperCase()];
			return mapped || raw;
		});
	}

	const normalized = normalizeKey(key);

	switch (normalized) {
		case "dataLevel":
		case "dataLevels":
		case "maxDataLevel":
		case "maxDataLevels":
			return mapArray(value, (item) => DATA_LEVEL_LABELS[String(item)] || String(item));
		case "operations":
		case "dataOperations":
			return mapArray(value, (item) => OP_LABELS[String(item)] || String(item));
		case "role":
		case "roles":
		case "resultRoles":
		case "realmRoles":
		case "clientRoles":
		case "allowRoles": {
			return mapArray(value, (item) => {
				const raw = String(item || "").trim();
				if (!raw) return "—";
				const upper = raw.toUpperCase();
				const mapped = ctx?.roleDisplay?.[upper] || ctx?.roleDisplay?.[raw] || ctx?.roleDisplay?.[raw.toLowerCase()];
				if (mapped) return mapped;
				if (upper.startsWith("DEFAULT-ROLES-")) return "默认角色";
				return raw;
			});
		}
		case "username": {
			const raw = String(value || "").trim();
			if (!raw) return "—";
			const mapped = ctx?.userDisplay?.[raw] || ctx?.userDisplay?.[raw.toLowerCase()] || ctx?.userDisplay?.[raw.toUpperCase()];
			return mapped || raw;
		}
		case "scope":
			return SCOPE_LABELS[String(value)] || String(value);
		case "shareScope":
			return SHARE_SCOPE_LABELS[String(value)] || String(value);
		case "keycloakId":
			return "***";
		case "enabled":
		case "active":
		case "isEnabled":
		case "available":
		case "statusEnabled":
			return truthy(value) ? "启用" : "禁用";
		case "allowDesensitize":
		case "allowDesensitizeJson":
		case "allowDesensitizeFlag":
		case "allowDesensitizeData":
			return truthy(value) ? "是" : "否";
		case "status": {
			const raw = String(value || "").toUpperCase();
			return STATUS_VALUE_LABELS[raw] || String(value);
		}
		case "operationType": {
			const raw = String(value || "").toUpperCase();
			return OPERATION_TYPE_LABELS[raw] || String(value);
		}
		case "allowedRules":
		case "allowRules":
		case "allowedrules":
			return mapArray(value, (item) => {
				if (item == null) return "—";
				if (typeof item === "string") return item;
				if (typeof item === "object") {
					const display = (item as any).label ?? (item as any).name ?? JSON.stringify(item);
					return String(display);
				}
				return String(item);
			});
		default:
			break;
	}

	if (Array.isArray(value)) {
		return mapArray(value, (item) => (typeof item === "string" ? item : JSON.stringify(item)));
	}
	if (typeof value === "boolean") {
		return value ? "是" : "否";
	}
	if (typeof value === "number") {
		return String(value);
	}
	if (typeof value === "string") {
		return value;
	}
	return JSON.stringify(value);
}

export function beautifyBatchItemLabel(label: string): string {
	const raw = label?.trim();
	if (!raw) return label;
	const [head, ...rest] = raw.split("·").map((part) => part.trim());
	const translatedHead = OPERATION_TYPE_LABELS[head.toUpperCase?.() || head] || head.replace(/^BATCH_/i, "批量");
	return [translatedHead, ...rest].filter(Boolean).join(" · ");
}
