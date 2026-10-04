import type {
	ChangeSnapshotLike,
	ChangeSummaryEntry,
} from "@/admin/components/change-diff-viewer";
import type {
	MenuChangeDisplayEntry,
	VisibilityRuleDisplay,
} from "@/admin/components/menu-change-viewer";

type PlainRecord = Record<string, unknown>;

const MENU_DIFF_FIELD_SET = new Set([
	"allowedroles",
	"allowed_permissions",
	"allowedpermissions",
	"visibilityrules",
	"allowedorgcodes",
	"deleted",
	"enabled",
	"securitylevel",
	"maxdatalevel",
]);

/**
 * 从原始记录集合中提取菜单变更列表。支持读取 menuChanges 或 menu_changes 字段，并兼容 JSON 字符串。
 */
export function extractMenuChanges(records: Array<PlainRecord | null | undefined>): MenuChangeDisplayEntry[] {
	for (const record of records) {
		if (!record) continue;
		const raw = record["menuChanges"] ?? record["menu_changes"];
		const parsed = parseMenuChangeList(raw);
		if (parsed.length > 0) {
			return parsed;
		}
	}
	return [];
}

/**
 * 过滤与菜单字段相关的摘要行，避免与 MenuChangeViewer 重复展示。
 */
export function filterMenuSummaryRows<T extends ChangeSummaryEntry>(rows: T[]): T[] {
	return rows.filter((row) => {
		const field = (row.field || row.label || "").toString().toLowerCase();
		if (field.startsWith("menu#")) {
			return false;
		}
		if (field === "menuchanges") {
			return false;
		}
		return true;
	});
}

/**
 * 在存在 menuChanges 时，剔除快照中菜单相关的大数组，保留非菜单字段。
 */
export function pruneMenuSnapshot(snapshot?: ChangeSnapshotLike | null): ChangeSnapshotLike | null {
	if (!snapshot) {
		return null;
	}
	return pruneSnapshotFields(snapshot, MENU_DIFF_FIELD_SET);
}

/**
 * 判断快照是否仍包含可展示内容。
 */
export function snapshotHasContent(snapshot?: ChangeSnapshotLike | null): boolean {
	if (!snapshot) {
		return false;
	}
	if (snapshot.changes && snapshot.changes.length > 0) {
		return true;
	}
	if (snapshot.items && snapshot.items.length > 0) {
		return snapshot.items.some((item) =>
			snapshotHasContent({
				before: item.before ?? undefined,
				after: item.after ?? undefined,
				changes: item.changes ?? undefined,
				items: item.items ?? undefined,
			}),
		);
	}
	if (snapshot.after && Object.keys(snapshot.after).length > 0) {
		return true;
	}
	if (snapshot.before && Object.keys(snapshot.before).length > 0) {
		return true;
	}
	return false;
}

function pruneSnapshotFields(snapshot: ChangeSnapshotLike, fields: Set<string>): ChangeSnapshotLike | null {
	let hasChanges = false;
	const pruned: ChangeSnapshotLike = {};
	if (snapshot.before) {
		pruned.before = snapshot.before;
	}
	if (snapshot.after) {
		pruned.after = snapshot.after;
	}

	if (snapshot.changes) {
		const filteredChanges = snapshot.changes.filter((change) => {
			const field = (change.field || "").toString().toLowerCase();
			return !fields.has(field);
		});
		if (filteredChanges.length > 0) {
			pruned.changes = filteredChanges;
			hasChanges = true;
		}
	}

	if (snapshot.items) {
		const filteredItems = snapshot.items
			.map((item) => {
				const next = pruneSnapshotFields(
					{
						before: item.before,
						after: item.after,
						changes: item.changes ?? undefined,
						items: item.items ?? undefined,
					},
					fields,
				);
				if (!next) {
					return null;
				}
				return {
					...item,
					before: next.before,
					after: next.after,
					changes: next.changes ?? null,
					items: next.items ?? null,
				};
			})
			.filter(Boolean) as NonNullable<ChangeSnapshotLike["items"]>;
		if (filteredItems.length > 0) {
			pruned.items = filteredItems;
			hasChanges = true;
		}
	}

	if (!hasChanges && !(pruned.before && Object.keys(pruned.before).length) && !(pruned.after && Object.keys(pruned.after).length)) {
		return null;
	}
	return pruned;
}

function parseMenuChangeList(value: unknown): MenuChangeDisplayEntry[] {
	const parsed = toIterable(value);
	if (!parsed) {
		return [];
	}
	const result: MenuChangeDisplayEntry[] = [];
	for (const item of parsed) {
		const record = toRecord(item);
		if (!record) continue;
		const id = readOptionalString(record["menuId"] ?? record["id"]);
		const name = readOptionalString(record["menuName"] ?? record["name"]);
		const title = readOptionalString(record["menuTitle"] ?? record["title"] ?? record["label"]);
		const path = readOptionalString(record["menuPath"] ?? record["path"] ?? record["route"]);
		const entry: MenuChangeDisplayEntry = {
			id,
			name,
			title,
			path,
			menuName: name,
			menuTitle: title,
			menuPath: path,
			allowedRolesBefore: normalizeStringList(record["allowedRolesBefore"]),
			allowedRolesAfter: normalizeStringList(record["allowedRolesAfter"]),
			addedRoles: normalizeStringList(record["addedRoles"]),
			removedRoles: normalizeStringList(record["removedRoles"]),
			allowedPermissionsBefore: normalizeStringList(record["allowedPermissionsBefore"]),
			allowedPermissionsAfter: normalizeStringList(record["allowedPermissionsAfter"]),
			addedPermissions: normalizeStringList(record["addedPermissions"]),
			removedPermissions: normalizeStringList(record["removedPermissions"]),
			addedRules: parseRuleDetails(record["addedRules"]),
			removedRules: parseRuleDetails(record["removedRules"]),
			maxDataLevelBeforeLabel: readOptionalString(record["maxDataLevelBeforeLabel"] ?? record["maxDataLevelBefore"]),
			maxDataLevelAfterLabel: readOptionalString(record["maxDataLevelAfterLabel"] ?? record["maxDataLevelAfter"]),
			securityLevelBeforeLabel: readOptionalString(record["securityLevelBeforeLabel"] ?? record["securityLevelBefore"]),
			securityLevelAfterLabel: readOptionalString(record["securityLevelAfterLabel"] ?? record["securityLevelAfter"]),
			statusBeforeLabel: readOptionalString(record["statusBeforeLabel"] ?? record["deletedBeforeLabel"]),
			statusAfterLabel: readOptionalString(record["statusAfterLabel"] ?? record["deletedAfterLabel"]),
		};
		result.push(entry);
	}
	return result;
}

function parseRuleDetails(value: unknown): VisibilityRuleDisplay[] {
	const list = toIterable(value);
	if (!list) {
		return [];
	}
	const result: VisibilityRuleDisplay[] = [];
	for (const item of list) {
		const record = toRecord(item);
		if (!record) continue;
		const role = readOptionalString(record["role"]);
		const permission = readOptionalString(record["permission"]);
		const level = readOptionalString(record["dataLevelLabel"] ?? record["dataLevel"]);
		if (!role && !permission && !level) {
			continue;
		}
		result.push({ role, permission, dataLevelLabel: level });
	}
	return result;
}

function readOptionalString(value: unknown): string | undefined {
	if (typeof value === "string") {
		const trimmed = value.trim();
		return trimmed.length > 0 ? trimmed : undefined;
	}
	if (typeof value === "number" || typeof value === "boolean") {
		return String(value);
	}
	return undefined;
}

function normalizeStringList(value: unknown): string[] {
	if (!value) {
		return [];
	}
	if (Array.isArray(value)) {
		return value
			.map((item) => (typeof item === "string" ? item.trim() : item != null ? String(item).trim() : ""))
			.filter((item) => item.length > 0);
	}
	if (typeof value === "string") {
		const trimmed = value.trim();
		if (!trimmed) {
			return [];
		}
		try {
			const parsed = JSON.parse(trimmed);
			if (Array.isArray(parsed)) {
				return parsed
					.map((item) => (typeof item === "string" ? item.trim() : item != null ? String(item).trim() : ""))
					.filter((item) => item.length > 0);
			}
		} catch {
			// ignore
		}
		return trimmed
			.split(/[,\uFF0C\u3001;\uFF1B\s]+/)
			.map((token) => token.trim())
			.filter((token) => token.length > 0);
	}
	return [];
}

function toIterable(value: unknown): unknown[] | null {
	if (!value) {
		return null;
}
	if (Array.isArray(value)) {
		return value;
	}
	if (typeof value === "string") {
		const trimmed = value.trim();
		if (!trimmed) {
			return null;
		}
		try {
			const parsed = JSON.parse(trimmed);
			return Array.isArray(parsed) ? parsed : null;
		} catch {
			return null;
		}
	}
	return null;
}

function toRecord(value: unknown): PlainRecord | null {
	if (!value) {
		return null;
	}
	if (typeof value === "object" && !Array.isArray(value)) {
		return value as PlainRecord;
	}
	if (typeof value === "string") {
		const trimmed = value.trim();
		if (!trimmed) {
			return null;
		}
		try {
			const parsed = JSON.parse(trimmed);
			return typeof parsed === "object" && !Array.isArray(parsed) ? (parsed as PlainRecord) : null;
		} catch {
			return null;
		}
	}
	return null;
}
