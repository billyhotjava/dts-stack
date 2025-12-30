const FIELD_ENUM_MAP: Record<string, Record<string, string>> = {
	personsecuritylevel: {
		"0": "一般",
		"1": "重要",
		"2": "核心",
		GENERAL: "一般",
		IMPORTANT: "重要",
		CORE: "核心",
		NON_SECRET: "一般",
		NONE_SECRET: "一般",
	},
	enabled: {
		TRUE: "启用",
		FALSE: "停用",
	},
	securitylevel: {
		"0": "一般",
		"1": "重要",
		"2": "核心",
		GENERAL: "一般",
		IMPORTANT: "重要",
		CORE: "核心",
		NON_SECRET: "一般",
		NONE_SECRET: "一般",
		// 兼容误用/旧值：数据密级映射到人员密级展示
		PUBLIC: "一般",
		INTERNAL: "一般",
		SECRET: "重要",
		CONFIDENTIAL: "核心",
		TOP_SECRET: "核心",
	},
	maxdatalevel: {
		"0": "公开",
		"1": "内部",
		"2": "秘密",
		"3": "机密",
		// 数据密级：PUBLIC/INTERNAL/SECRET/CONFIDENTIAL
		PUBLIC: "公开",
		INTERNAL: "内部",
		SECRET: "秘密",
		CONFIDENTIAL: "机密",
		TOP_SECRET: "机密",
		// 兼容历史口径：人员密级映射为数据密级
		NON_SECRET: "内部",
		NONE_SECRET: "内部",
		GENERAL: "内部",
		IMPORTANT: "秘密",
		CORE: "机密",
	},
	scope: {
		INSTITUTE: "全所",
		DEPARTMENT: "部门",
	},
	operations: {
		read: "读取",
		write: "写入",
		export: "导出",
		READ: "读取",
		WRITE: "写入",
		EXPORT: "导出",
	},
	allowdesensitizejson: {
		TRUE: "是",
		FALSE: "否",
	},
};

const LABEL_ENUM_MAP: Record<string, Record<string, string>> = {
	"人员密级": FIELD_ENUM_MAP.personsecuritylevel,
	"启用状态": FIELD_ENUM_MAP.enabled,
	"访问密级": FIELD_ENUM_MAP.securitylevel,
	"最大数据密级": FIELD_ENUM_MAP.maxdatalevel,
	"作用域": FIELD_ENUM_MAP.scope,
	"操作权限": FIELD_ENUM_MAP.operations,
	"允许脱敏 JSON": FIELD_ENUM_MAP.allowdesensitizejson,
};

function resolveEnumMapping(field?: string, label?: string): Record<string, string> | undefined {
	const normalizedField = field?.trim().toLowerCase();
	if (normalizedField && FIELD_ENUM_MAP[normalizedField]) {
		return FIELD_ENUM_MAP[normalizedField];
	}
	const normalizedLabel = label?.trim().toLowerCase();
	if (normalizedLabel && LABEL_ENUM_MAP[normalizedLabel]) {
		return LABEL_ENUM_MAP[normalizedLabel];
	}
	return undefined;
}

function translateScalar(field?: string, label?: string, raw?: unknown): string | undefined {
	if (raw === null || raw === undefined) {
		return undefined;
}
	if (typeof raw === "object") {
		return undefined;
	}
	const mapping = resolveEnumMapping(field, label);
	if (!mapping) {
		return undefined;
	}
	let candidate: string;
	if (typeof raw === "boolean") {
		candidate = raw ? "TRUE" : "FALSE";
	} else {
		candidate = raw.toString().trim();
	}
	if (candidate.length === 0) {
		return undefined;
	}
	const upper = candidate.toUpperCase();
	return mapping[upper] ?? mapping[candidate] ?? mapping[candidate.toLowerCase()];
}

function formatArrayItem(item: unknown, field?: string, label?: string): string {
	const translated = translateScalar(field, label, item);
	if (translated !== undefined) {
		return translated;
	}
	if (item === null || item === undefined || item === "") {
		return "—";
	}
	if (Array.isArray(item)) {
		return formatDisplayValue(item, field, label);
	}
	if (typeof item === "object") {
		try {
			return JSON.stringify(item, null, 2);
		} catch {
			return String(item);
		}
	}
	return String(item);
}

export function formatDisplayValue(value: unknown, field?: string, label?: string): string {
	if (value === null || value === undefined || value === "") {
		return "—";
	}
	if (Array.isArray(value)) {
		if (value.length === 0) {
			return "[]";
		}
		const formatted = value.map((item) => formatArrayItem(item, field, label));
		return `[${formatted.join("，")}]`;
	}
	if (typeof value === "object") {
		try {
			return JSON.stringify(value, null, 2);
		} catch (err) {
			console.warn("Failed to stringify value", err, value);
			return String(value);
		}
	}
	const translated = translateScalar(field, label, value);
	if (translated !== undefined) {
		return translated;
	}
	if (typeof value === "string") {
		return value || "—";
	}
	if (typeof value === "number" || typeof value === "boolean") {
		return String(value);
	}
	return String(value);
}
