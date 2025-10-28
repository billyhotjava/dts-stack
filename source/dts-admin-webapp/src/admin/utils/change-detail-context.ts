import {
	buildChangeSnapshotFromDiff,
	type ChangeSnapshotLike,
	type ChangeSummaryEntry,
} from "@/admin/components/change-diff-viewer";
import {
	extractMenuChanges,
	filterMenuSummaryRows,
	pruneMenuSnapshot,
} from "@/admin/utils/menu-change-parser";
import type { MenuChangeDisplayEntry } from "@/admin/components/menu-change-viewer";
import type { ChangeRequest } from "@/admin/types";
import { labelForChangeField } from "@/admin/lib/change-request-format";
type PlainRecord = Record<string, unknown>;

const SUMMARY_KEYS = ["changeSummary", "change_summary", "summary"];
const SNAPSHOT_KEYS = ["changeSnapshot", "change_snapshot", "snapshot"];

const FIELD_LABEL_FALLBACKS: Record<string, string> = {
	FULLNAME: "姓名",
	FULL_NAME: "姓名",
	NAME: "姓名",
	DISPLAYNAME: "显示名",
	DISPLAY_NAME: "显示名",
	NICKNAME: "昵称",
	USERNAME: "用户名",
	USER_NAME: "用户名",
	ACCOUNT: "账号",
	ACCOUNT_NAME: "账号",
	EMAIL: "邮箱",
	MAIL: "邮箱",
	PHONE: "手机号",
	PHONE_NUMBER: "手机号",
	MOBILE: "手机号",
	MOBILE_PHONE: "手机号",
	MOBILEPHONE: "手机号",
	TELEPHONE: "座机",
	SECURITYLEVEL: "安全级别",
	SECURITY_LEVEL: "安全级别",
	PERSONSECURITYLEVEL: "人员安全级别",
	PERSON_SECURITY_LEVEL: "人员安全级别",
	MAXDATALEVEL: "最大数据密级",
	MAX_DATA_LEVEL: "最大数据密级",
	DATALEVEL: "数据密级",
	DATA_LEVEL: "数据密级",
	ROLE: "角色",
	ROLES: "角色",
	ALLOWEDROLES: "角色",
	ALLOWED_ROLES: "角色",
	STATUS: "状态",
	STATE: "状态",
	PASSWORD: "密码",
	PASSWD: "密码",
};

export interface ChangeDisplayContext {
	snapshot: ChangeSnapshotLike | null;
	summary: ChangeSummaryEntry[];
	menuChanges: MenuChangeDisplayEntry[];
}

export interface ChangeDisplayContextOptions {
	layers: Array<PlainRecord | null | undefined>;
	baseSnapshot?: ChangeSnapshotLike | null;
	baseSummary?: ChangeSummaryEntry[];
	fallbackDiff?: Record<string, unknown> | null;
}

export function buildChangeDisplayContext({
	layers,
	baseSnapshot,
	baseSummary,
	fallbackDiff,
}: ChangeDisplayContextOptions): ChangeDisplayContext {
	const normalizedLayers = layers ?? [];
	const menuChanges = extractMenuChanges(normalizedLayers);
	const summary = buildSummary(normalizedLayers, baseSummary);
	let snapshot = baseSnapshot ?? findSnapshot(normalizedLayers) ?? null;

	if (!snapshot && fallbackDiff) {
		snapshot = buildChangeSnapshotFromDiff(fallbackDiff);
	}

	if (menuChanges.length > 0) {
		if (summary.length > 0) {
			return {
				snapshot: pruneMenuSnapshot(snapshot),
				summary: filterMenuSummaryRows(summary),
				menuChanges,
			};
		}
		return {
			snapshot: pruneMenuSnapshot(snapshot),
			summary: [],
			menuChanges,
		};
	}

	return {
		snapshot,
		summary,
		menuChanges,
	};
}

export interface ChangeRequestContextOptions {
	includePayloadLayer?: boolean;
	includeOriginalUpdatedValues?: boolean;
}

export function buildContextForChangeRequest(
	request: ChangeRequest,
	options?: ChangeRequestContextOptions,
): ChangeDisplayContext {
	const diffRecord = parseJsonRecord(request.diffJson);
	const payloadRecord = options?.includePayloadLayer === false ? null : parseJsonRecord(request.payloadJson);
	const originalRecord = options?.includeOriginalUpdatedValues === false ? null : toRecord(request.originalValue);
	const updatedRecord = options?.includeOriginalUpdatedValues === false ? null : toRecord(request.updatedValue);

	const layers: Array<PlainRecord | null | undefined> = [
		diffRecord,
		diffRecord ? toRecord(diffRecord["detail"]) : null,
		diffRecord ? toRecord(diffRecord["context"]) : null,
		diffRecord ? toRecord(diffRecord["metadata"]) : null,
		diffRecord ? toRecord(diffRecord["extraAttributes"]) : null,
		payloadRecord,
		originalRecord,
		updatedRecord,
	];

	const baseSnapshot = diffRecord ? buildChangeSnapshotFromDiff(diffRecord) : null;

	return buildChangeDisplayContext({
		layers,
		baseSnapshot,
		fallbackDiff: diffRecord,
	});
}

export interface SummarizeContextOptions {
	maxEntries?: number;
	actionLabel?: string | null;
	request?: ChangeRequest;
	subjectName?: string | null;
}

export function summarizeChangeDisplayContext(
	context: ChangeDisplayContext,
	{ maxEntries = 3, actionLabel, request, subjectName }: SummarizeContextOptions = {},
): string {
	const actionText = resolveActionText(actionLabel, request?.action);
	const subjectDisplay = resolveSubjectDisplay(context, request, subjectName);

	if (!actionText && !subjectDisplay) {
		return "—";
	}

	const action = actionText ?? "变更";
	return subjectDisplay ? `${action}${subjectDisplay}` : action;
}

function buildSummary(layers: Array<PlainRecord | null | undefined>, baseSummary?: ChangeSummaryEntry[]): ChangeSummaryEntry[] {
	const result: ChangeSummaryEntry[] = [];
	const seen = new Set<string>();
	if (Array.isArray(baseSummary)) {
		for (const entry of baseSummary) {
			const key = summaryKey(entry);
			if (seen.has(key)) continue;
			result.push(entry);
			seen.add(key);
		}
	}
	for (const layer of layers) {
		if (!layer) continue;
		for (const key of SUMMARY_KEYS) {
			const entries = parseSummaryList(layer[key]);
			for (const entry of entries) {
				const k = summaryKey(entry);
				if (seen.has(k)) continue;
				result.push(entry);
				seen.add(k);
			}
		}
	}
	return result;
}

const SUMMARY_HIDDEN_FIELDS = new Set(["actiondisplay", "target", "attributes"]);

function isSummaryHidden(entry: ChangeSummaryEntry): boolean {
	const field = typeof entry.field === "string" ? entry.field.trim().toLowerCase() : "";
	if (field && SUMMARY_HIDDEN_FIELDS.has(field)) {
		return true;
	}
	const label = typeof entry.label === "string" ? entry.label.trim().toLowerCase() : "";
	if (label && SUMMARY_HIDDEN_FIELDS.has(label)) {
		return true;
	}
	return false;
}

function summaryKey(entry: ChangeSummaryEntry): string {
	return `${entry.field ?? entry.label ?? ""}|${JSON.stringify(entry.before)}|${JSON.stringify(entry.after)}`;
}

function parseSummaryList(value: unknown): ChangeSummaryEntry[] {
	const array = toArray(value);
	if (!array) {
		return [];
	}
	const rows: ChangeSummaryEntry[] = [];
	array.forEach((item, index) => {
		const record = toRecord(item);
		if (!record) return;
		const fieldRaw = record["field"] ?? record["code"] ?? record["name"] ?? record["key"] ?? `field_${index}`;
		const field = typeof fieldRaw === "string" ? fieldRaw.trim() : fieldRaw != null ? String(fieldRaw) : "";
		const labelRaw = record["label"] ?? record["title"] ?? record["name"];
		const label = typeof labelRaw === "string" && labelRaw.trim().length > 0 ? labelRaw.trim() : deriveFieldLabel(field);
		rows.push({
			field: field || label,
			label,
			before: record["before"],
			after: record["after"],
		});
	});
	return rows;
}

function findSnapshot(layers: Array<PlainRecord | null | undefined>): ChangeSnapshotLike | null {
	for (const layer of layers) {
		if (!layer) continue;
		for (const key of SNAPSHOT_KEYS) {
			const candidate = layer[key];
			const parsed = parseSnapshot(candidate);
			if (parsed) {
				return parsed;
			}
		}
		const snapshotList = toArray(layer["changeSnapshots"]);
		if (snapshotList) {
			for (const entry of snapshotList) {
				const parsed = parseSnapshot(entry);
				if (parsed) {
					return parsed;
				}
			}
		}
	}
	return null;
}

function parseSnapshot(value: unknown): ChangeSnapshotLike | null {
	const record = toRecord(value);
	if (!record) {
		return null;
	}
	return buildChangeSnapshotFromDiff(record);
}

function toRecord(value: unknown): PlainRecord | null {
	if (!value) {
		return null;
	}
	if (typeof value === "object" && !Array.isArray(value)) {
		return value as PlainRecord;
	}
	if (typeof value === "string") {
		try {
			const parsed = JSON.parse(value);
			return typeof parsed === "object" && !Array.isArray(parsed) ? (parsed as PlainRecord) : null;
		} catch {
			return null;
		}
	}
	return null;
}

function toArray(value: unknown): unknown[] | null {
	if (!value) {
		return null;
	}
	if (Array.isArray(value)) {
		return value;
	}
	if (typeof value === "string") {
		try {
			const parsed = JSON.parse(value);
			return Array.isArray(parsed) ? parsed : null;
		} catch {
			return null;
		}
	}
	return null;
}

function deriveFieldLabel(field?: string): string {
	if (!field) {
		return "字段";
	}
	const trimmed = field.trim();
	if (!trimmed) {
		return "字段";
	}
	const camelExpanded = trimmed.replace(/([a-z0-9])([A-Z])/g, "$1_$2");
	const normalizedKey = camelExpanded
		.replace(/[\s\-]+/g, "_")
		.replace(/[_]+/g, "_")
		.replace(/[^A-Za-z0-9_]/g, "")
		.toUpperCase();
	const fallback =
		FIELD_LABEL_FALLBACKS[normalizedKey] ??
		FIELD_LABEL_FALLBACKS[trimmed.toUpperCase()] ??
		FIELD_LABEL_FALLBACKS[camelExpanded.toUpperCase()];
	if (fallback) {
		return fallback;
	}
	const replaced = camelExpanded.replace(/[_\-.]+/g, " ").trim();
	if (!replaced) {
		return trimmed;
	}
	return replaced
		.split(" ")
		.filter(Boolean)
		.map((token) => token.charAt(0).toUpperCase() + token.slice(1))
		.join(" ");
}

function parseJsonRecord(value?: string | null): PlainRecord | null {
	if (!value) {
		return null;
	}
	try {
		const parsed = JSON.parse(value);
		return toRecord(parsed);
	} catch {
		return null;
	}
}

function resolvePrimaryFieldLabel(
	summary: ChangeSummaryEntry[],
	menuChanges: MenuChangeDisplayEntry[],
	snapshot: ChangeSnapshotLike | null | undefined,
	maxEntries: number,
): string | null {
	const menuField = resolveMenuChangeFieldLabel(menuChanges[0]);
	if (menuField) {
		return menuField;
	}

	const summaryLabel = extractSummaryLabel(summary[0]);
	if (summaryLabel) {
		return summaryLabel;
	}

	const firstMenu = menuChanges[0];
	if (firstMenu) {
		return (
			normalizeToString(firstMenu.title) ??
			normalizeToString(firstMenu.name) ??
			normalizeToString(firstMenu.path) ??
			normalizeToString(firstMenu.id)
		);
	}

	const snapshotEntry = collectSnapshotChanges(snapshot, Math.max(1, maxEntries))[0];
	if (snapshotEntry) {
		const labelFromEntry = normalizeToString(snapshotEntry.label);
		const labelFromField =
			typeof snapshotEntry.field === "string" ? labelForChangeField(snapshotEntry.field) : null;
		return labelFromEntry ?? labelFromField ?? (typeof snapshotEntry.field === "string"
			? deriveFieldLabel(snapshotEntry.field)
			: null);
	}

	return null;
}

function extractSummaryLabel(entry?: ChangeSummaryEntry): string | null {
	if (!entry) {
		return null;
	}
	if (typeof entry.label === "string") {
		const label = entry.label.trim();
		if (label.length > 0) {
			const mapped = labelForChangeField(label);
			return mapped || label;
		}
	}
	if (typeof entry.field === "string") {
		const field = entry.field.trim();
		if (field.length > 0) {
			return labelForChangeField(field);
		}
	}
	return null;
}

function resolveMenuChangeFieldLabel(entry?: MenuChangeDisplayEntry): string | null {
	if (!entry) {
		return null;
	}
	const statusChanged =
		entry.statusBeforeLabel !== undefined &&
		entry.statusAfterLabel !== undefined &&
		entry.statusBeforeLabel !== entry.statusAfterLabel;
	if (statusChanged) {
		return "状态";
	}

	if (hasArrayDiff(entry.allowedRolesBefore, entry.allowedRolesAfter) || entry.addedRoles?.length || entry.removedRoles?.length) {
		return "角色";
	}

	if (
		hasArrayDiff(entry.allowedPermissionsBefore, entry.allowedPermissionsAfter) ||
		entry.addedPermissions?.length ||
		entry.removedPermissions?.length
	) {
		return "权限";
	}

	if (entry.securityLevelBeforeLabel && entry.securityLevelAfterLabel && entry.securityLevelBeforeLabel !== entry.securityLevelAfterLabel) {
		return "访问密级";
	}

	if (entry.maxDataLevelBeforeLabel && entry.maxDataLevelAfterLabel && entry.maxDataLevelBeforeLabel !== entry.maxDataLevelAfterLabel) {
		return "最大数据密级";
	}

	if (entry.addedRules?.length || entry.removedRules?.length) {
		return "可见性规则";
	}

	return null;
}

const ACTION_TEXT_FALLBACKS: Record<string, string> = {
	CREATE: "新增",
	UPDATE: "修改",
	MODIFY: "修改",
	EDIT: "修改",
	DELETE: "删除",
	REMOVE: "删除",
	ENABLE: "启用",
	DISABLE: "禁用",
	APPROVE: "审批",
	REJECT: "驳回",
	APPLY: "应用",
	SUBMIT: "提交",
	GRANT: "授予",
	GRANT_ROLE: "授予",
	ASSIGN_ROLE: "授予",
	ADD_ROLE: "授予",
	REMOVE_ROLE: "撤销",
	REVOKE_ROLE: "撤销",
	DELETE_ROLE: "删除",
	UPDATE_ROLE: "修改",
	ENABLE_MENU: "启用",
	DISABLE_MENU: "禁用",
	MENU_ENABLE: "启用",
	MENU_DISABLE: "禁用",
};

function resolveActionText(label?: string | null, raw?: string | null): string | null {
	const normalizedLabel = normalizeToString(label);
	if (normalizedLabel) {
		return normalizedLabel;
	}
	const normalizedRaw = normalizeToString(raw);
	if (!normalizedRaw) {
		return null;
	}
	const code = normalizedRaw.toUpperCase();
	return ACTION_TEXT_FALLBACKS[code] ?? normalizedRaw;
}

const SUBJECT_NAME_KEYS = [
	"fullName",
	"full_name",
	"displayName",
	"display_name",
	"displayTitle",
	"display_title",
	"name",
	"username",
	"userName",
	"user_name",
	"accountName",
	"account",
	"targetName",
	"target_name",
	"title",
	"menuTitle",
	"menu_title",
	"menuName",
	"menu_name",
	"menuLabel",
	"menu_label",
	"path",
	"menuPath",
	"menu_path",
	"resourceName",
	"resource_name",
	"resourceLabel",
	"resource_label",
] as const;

const MAX_NAME_DEPTH = 3;

function resolveSubjectName(context: ChangeDisplayContext, request?: ChangeRequest): string | null {
	const menuEntry = context.menuChanges[0];
	if (menuEntry) {
		const menuLabel =
			normalizeToString(menuEntry.title) ??
			normalizeToString((menuEntry as any).menuTitle) ??
			normalizeToString(menuEntry.name) ??
			normalizeToString((menuEntry as any).menuName) ??
			normalizeToString(menuEntry.path) ??
			normalizeToString((menuEntry as any).menuPath) ??
			normalizeToString(menuEntry.id);
		if (menuLabel) {
			return menuLabel;
		}
	}

	const records = collectCandidateRecords(context, request);
	for (const record of records) {
		const name = extractNameFromRecord(record);
		if (name) {
			return name;
		}
	}
	if (request) {
		const payload = parseJsonRecord(request.payloadJson);
		const payloadName = payload ? extractNameFromRecord(payload) : null;
		if (payloadName) {
			return payloadName;
		}
		const resourceId = normalizeToString(request.resourceId);
		if (resourceId) {
			return resourceId;
		}
	}
	return null;
}

const RESOURCE_TYPE_LABELS: Record<string, string> = {
	USER: "用户",
	ROLE: "角色",
	CUSTOM_ROLE: "角色",
	ROLE_ASSIGNMENT: "角色",
	PORTAL_MENU: "菜单",
	MENU: "菜单",
};

function resolveSubjectDisplay(
	context: ChangeDisplayContext,
	request?: ChangeRequest,
	overrideSubjectName?: string | null,
): string | null {
	const name = normalizeToString(overrideSubjectName) ?? resolveSubjectName(context, request);
	const typeLabel = resolveSubjectTypeLabel(context, request);
	if (name && typeLabel) {
		return `${typeLabel}${name}`;
	}
	if (name) {
		return name;
	}
	if (typeLabel) {
		return typeLabel;
	}
	return null;
}

function resolveSubjectTypeLabel(context: ChangeDisplayContext, request?: ChangeRequest): string | null {
	const resourceType = normalizeToString(request?.resourceType)?.toUpperCase();
	if (resourceType && RESOURCE_TYPE_LABELS[resourceType]) {
		return RESOURCE_TYPE_LABELS[resourceType];
	}
	if (context.menuChanges.length > 0) {
		return "菜单";
	}
	return null;
}

function collectCandidateRecords(context: ChangeDisplayContext, request?: ChangeRequest): PlainRecord[] {
	const records: PlainRecord[] = [];

	if (context.snapshot) {
		const afterRecord = toRecord(context.snapshot.after);
		if (afterRecord) {
			records.push(afterRecord);
		}
		const beforeRecord = toRecord(context.snapshot.before);
		if (beforeRecord) {
			records.push(beforeRecord);
		}
	}

	if (request) {
		const diffRecord = parseJsonRecord(request.diffJson);
		if (diffRecord) {
			records.push(diffRecord);
			const detail = toRecord(diffRecord["detail"]);
			if (detail) records.push(detail);
			const contextRecord = toRecord(diffRecord["context"]);
			if (contextRecord) records.push(contextRecord);
			const metadataRecord = toRecord(diffRecord["metadata"]);
			if (metadataRecord) records.push(metadataRecord);
			const extraAttributesRecord = toRecord(diffRecord["extraAttributes"]);
			if (extraAttributesRecord) records.push(extraAttributesRecord);
		}

		const payloadRecord = parseJsonRecord(request.payloadJson);
		if (payloadRecord) {
			records.push(payloadRecord);
		}

		const originalRecord = toRecord(request.originalValue);
		if (originalRecord) {
			records.push(originalRecord);
		}

		const updatedRecord = toRecord(request.updatedValue);
		if (updatedRecord) {
			records.push(updatedRecord);
		}
	}

	return records;
}

function extractNameFromRecord(record: PlainRecord, depth = 0): string | null {
	for (const key of SUBJECT_NAME_KEYS) {
		if (Object.prototype.hasOwnProperty.call(record, key)) {
			const text = normalizeToString(record[key]);
			if (text) {
				return text;
			}
		}
	}
	if (depth >= MAX_NAME_DEPTH) {
		return null;
	}
	for (const value of Object.values(record)) {
		if (value == null) {
			continue;
		}
		if (Array.isArray(value)) {
			for (const item of value) {
				if (!item) {
					continue;
				}
				if (typeof item === "object") {
					const nested = toRecord(item);
					if (nested) {
						const name = extractNameFromRecord(nested, depth + 1);
						if (name) {
							return name;
						}
					}
				} else {
					const text = normalizeToString(item);
					if (text) {
						return text;
					}
				}
			}
		} else if (typeof value === "object") {
			const nested = toRecord(value);
			if (nested) {
				const name = extractNameFromRecord(nested, depth + 1);
				if (name) {
					return name;
				}
			}
		}
	}
	return null;
}

function normalizeToString(value: unknown): string | null {
	if (typeof value === "string") {
		const trimmed = value.trim();
		return trimmed.length > 0 ? trimmed : null;
	}
	if (typeof value === "number" || typeof value === "boolean") {
		return String(value);
	}
	return null;
}

type SnapshotChangeEntry = {
	field?: string;
	label?: string;
	before?: unknown;
	after?: unknown;
};

function collectSnapshotChanges(
	snapshot: ChangeSnapshotLike | null | undefined,
	maxEntries: number,
): SnapshotChangeEntry[] {
	const result: SnapshotChangeEntry[] = [];
	if (!snapshot) {
		return result;
	}
	const queue: Array<{ node: ChangeSnapshotLike; path: string[] }> = [{ node: snapshot, path: [] }];
	while (queue.length > 0 && result.length < maxEntries) {
		const { node, path } = queue.shift()!;
		const pathLabel = path.filter(Boolean).join(" / ");
		if (node.changes) {
			for (const change of node.changes) {
				if (!change) {
					continue;
				}
				const label = buildSnapshotChangeLabel(change.label, change.field, pathLabel);
				result.push({
					field: typeof change.field === "string" ? change.field : undefined,
					label,
					before: change.before,
					after: change.after,
				});
				if (result.length >= maxEntries) {
					break;
				}
			}
		}
		if (result.length >= maxEntries) {
			break;
		}
		if (node.items) {
			for (const item of node.items) {
				if (!item) {
					continue;
				}
				const nextPathLabel = extractSnapshotItemLabel(item);
				queue.push({
					node: {
						before: item.before ?? undefined,
						after: item.after ?? undefined,
						changes: item.changes ?? undefined,
						items: item.items ?? undefined,
					},
					path: nextPathLabel ? [...path, nextPathLabel] : path,
				});
			}
		}
	}
	return result;
}

function buildSnapshotChangeLabel(rawLabel: unknown, field: unknown, pathLabel: string): string | undefined {
	let base: string | undefined;
	if (typeof rawLabel === "string" && rawLabel.trim().length > 0) {
		base = rawLabel.trim();
	} else if (typeof rawLabel === "number") {
		base = String(rawLabel);
	} else if (typeof field === "string" && field.trim().length > 0) {
		base = deriveFieldLabel(field);
	}
	if (pathLabel && base) {
		return `${pathLabel} · ${base}`;
	}
	if (pathLabel) {
		return pathLabel;
	}
	return base;
}

function hasArrayDiff(before?: string[] | null, after?: string[] | null): boolean {
	const beforeList = before ?? [];
	const afterList = after ?? [];
	if (beforeList.length !== afterList.length) {
		return true;
	}
	const beforeSet = new Set(beforeList);
	const afterSet = new Set(afterList);
	if (beforeSet.size !== afterSet.size) {
		return true;
	}
	for (const item of beforeSet) {
		if (!afterSet.has(item)) {
			return true;
		}
	}
	return false;
}

function extractSnapshotItemLabel(item: {
	label?: string;
	name?: string;
	displayName?: string;
}): string | undefined {
	if (typeof item.label === "string" && item.label.trim().length > 0) {
		return item.label.trim();
	}
	if (typeof item.displayName === "string" && item.displayName.trim().length > 0) {
		return item.displayName.trim();
	}
	if (typeof item.name === "string" && item.name.trim().length > 0) {
		return item.name.trim();
	}
	return undefined;
}
