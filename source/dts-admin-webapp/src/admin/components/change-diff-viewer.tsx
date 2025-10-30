import { Fragment, useMemo } from "react";
import { Text } from "@/ui/typography";
import { cn } from "@/utils";
import { formatDisplayValue } from "@/admin/utils/value-localization";
import { isFieldHiddenForResource } from "@/admin/utils/change-sanitizer";

type SnapshotRecord = Record<string, unknown>;

type SnapshotChange = {
	field?: string;
	label?: string;
	before?: unknown;
	after?: unknown;
};

type SnapshotNode = {
	label?: string;
	name?: string;
	displayName?: string;
	before?: SnapshotRecord | null;
	after?: SnapshotRecord | null;
	changes?: SnapshotChange[] | null;
	items?: SnapshotNode[] | null;
};

export type ChangeSnapshotLike = {
	before?: SnapshotRecord | null;
	after?: SnapshotRecord | null;
	changes?: SnapshotChange[] | null;
	items?: SnapshotNode[] | null;
};

export type ChangeSummaryEntry = {
	field?: string;
	label?: string;
	before?: unknown;
	after?: unknown;
};

type ChangeDiffMode = "create" | "update" | "delete";

export interface ChangeDiffViewerProps {
	snapshot?: ChangeSnapshotLike | null;
	summary?: ChangeSummaryEntry[] | null;
	action?: string | null;
	operationTypeCode?: string | null;
	status?: string | null;
	resourceType?: string | null;
	className?: string;
}

const DEFAULT_DELETE_MESSAGE = "记录待删除";
const FINAL_DELETE_MESSAGE = "记录已删除";
const FINAL_DELETE_STATUSES = new Set(["APPROVED", "APPLIED", "SUCCESS", "COMPLETED"]);
const HIDDEN_FIELD_KEYS = new Set([
	"attributes",
	"target",
	"payload",
	"targetid",
	"targetlabel",
	"payloadjson",
	"actiondisplay",
]);
const HIDDEN_FIELD_LABELS = new Set(["attributes", "target", "详细信息", "目标信息", "actiondisplay", "操作概述"]);
const RESOURCE_FIELD_WHITELIST: Record<
	string,
	{
		fields: Set<string>;
		labels: Set<string>;
	}
> = {
	PORTAL_MENU: {
		fields: new Set([
			"allowedroles",
			"allowedrolesbefore",
			"allowedrolesafter",
			"addedroles",
			"removedroles",
			"deletedbefore",
			"deletedafter",
			"statusbeforelabel",
			"statusafterlabel",
			"menutitle",
			"menuname",
			"menupath",
			"menuid",
		]),
		labels: new Set(["绑定角色", "新增角色", "移除角色", "禁用状态", "菜单名称", "菜单路径", "菜单标识"]),
	},
	MENU: {
		fields: new Set([
			"allowedroles",
			"allowedrolesbefore",
			"allowedrolesafter",
			"addedroles",
			"removedroles",
			"deletedbefore",
			"deletedafter",
			"statusbeforelabel",
			"statusafterlabel",
			"menutitle",
			"menuname",
			"menupath",
			"menuid",
		]),
		labels: new Set(["绑定角色", "新增角色", "移除角色", "禁用状态", "菜单名称", "菜单路径", "菜单标识"]),
	},
	MENU_MANAGEMENT: {
		fields: new Set([
			"allowedroles",
			"allowedrolesbefore",
			"allowedrolesafter",
			"addedroles",
			"removedroles",
			"deletedbefore",
			"deletedafter",
			"statusbeforelabel",
			"statusafterlabel",
			"menutitle",
			"menuname",
			"menupath",
			"menuid",
		]),
		labels: new Set(["绑定角色", "新增角色", "移除角色", "禁用状态", "菜单名称", "菜单路径", "菜单标识"]),
	},
};
const RESOURCE_FIELD_LABELS: Record<string, Record<string, string>> = {
	ADMIN_KEYCLOAK_USER: {
		username: "用户名",
		user_name: "用户名",
		fullname: "姓名",
		full_name: "姓名",
		displayname: "显示名称",
		display_name: "显示名称",
		nickname: "昵称",
		email: "邮箱",
		phone: "手机号",
		mobile: "手机号",
		personsecuritylevel: "人员密级",
		person_security_level: "人员密级",
		securitylevel: "访问密级",
		security_level: "访问密级",
		maxdatalevel: "最大数据密级",
		max_data_level: "最大数据密级",
		enabled: "启用状态",
		grouppaths: "所属组织",
		group_paths: "所属组织",
		realmroles: "角色",
		realm_roles: "角色",
		allowedroles: "角色",
		allowed_roles: "角色",
		allowedpermissions: "允许权限",
		allowed_permissions: "允许权限",
		visibilityrules: "可见性规则",
		visibility_rules: "可见性规则",
		attributes: "扩展属性",
		reason: "审批备注",
		action: "操作类型",
		actiondisplay: "操作类型",
		action_display: "操作类型",
		emailverified: "邮箱已验证",
		email_verified: "邮箱已验证",
		passwordreset: "重置密码",
		password_reset: "重置密码",
	},
	USER: {
		username: "用户名",
		user_name: "用户名",
		fullname: "姓名",
		full_name: "姓名",
		displayname: "显示名称",
		display_name: "显示名称",
		nickname: "昵称",
		email: "邮箱",
		phone: "手机号",
		mobile: "手机号",
		personsecuritylevel: "人员密级",
		person_security_level: "人员密级",
		securitylevel: "访问密级",
		security_level: "访问密级",
		maxdatalevel: "最大数据密级",
		max_data_level: "最大数据密级",
		enabled: "启用状态",
		grouppaths: "所属组织",
		group_paths: "所属组织",
		realmroles: "角色",
		realm_roles: "角色",
		allowedroles: "角色",
		allowed_roles: "角色",
		allowedpermissions: "允许权限",
		allowed_permissions: "允许权限",
		visibilityrules: "可见性规则",
		visibility_rules: "可见性规则",
		attributes: "扩展属性",
		reason: "审批备注",
		action: "操作类型",
		actiondisplay: "操作类型",
		action_display: "操作类型",
		emailverified: "邮箱已验证",
		email_verified: "邮箱已验证",
		passwordreset: "重置密码",
		password_reset: "重置密码",
	},
	ROLE: {
		name: "角色标识",
		role: "角色标识",
		roleid: "角色标识",
		role_id: "角色标识",
		displayname: "显示名称",
		display_name: "显示名称",
		titlecn: "中文名称",
		title_cn: "中文名称",
		namezh: "中文名称",
		name_zh: "中文名称",
		titleen: "英文名称",
		title_en: "英文名称",
		scope: "作用域",
		description: "角色描述",
		enabled: "启用状态",
		allowedroles: "绑定角色",
		allowed_roles: "绑定角色",
		permissions: "权限列表",
		allowedpermissions: "允许权限",
		allowed_permissions: "允许权限",
		visibilityrules: "可见性规则",
		visibility_rules: "可见性规则",
		deleted: "是否禁用",
		reason: "审批备注",
		action: "操作类型",
		actiondisplay: "操作类型",
		action_display: "操作类型",
	},
	CUSTOM_ROLE: {
		name: "角色标识",
		displayname: "显示名称",
		display_name: "显示名称",
		titlecn: "中文名称",
		title_cn: "中文名称",
		namezh: "中文名称",
		name_zh: "中文名称",
		titleen: "英文名称",
		title_en: "英文名称",
		scope: "作用域",
		description: "角色描述",
		enabled: "启用状态",
		allowedroles: "绑定角色",
		allowed_roles: "绑定角色",
		permissions: "权限列表",
		allowedpermissions: "允许权限",
		allowed_permissions: "允许权限",
		visibilityrules: "可见性规则",
		visibility_rules: "可见性规则",
		deleted: "是否禁用",
		reason: "审批备注",
		action: "操作类型",
		actiondisplay: "操作类型",
		action_display: "操作类型",
	},
	ADMIN_CUSTOM_ROLE: {
		name: "角色标识",
		displayname: "显示名称",
		display_name: "显示名称",
		titlecn: "中文名称",
		namezh: "中文名称",
		titleen: "英文名称",
		scope: "作用域",
		description: "角色描述",
		enabled: "启用状态",
		allowedroles: "绑定角色",
		allowed_roles: "绑定角色",
		permissions: "权限列表",
		allowedpermissions: "允许权限",
		allowed_permissions: "允许权限",
		visibilityrules: "可见性规则",
		visibility_rules: "可见性规则",
		deleted: "是否禁用",
		reason: "审批备注",
		action: "操作类型",
		actiondisplay: "操作类型",
		action_display: "操作类型",
	},
	PORTAL_MENU: {
		menuid: "菜单标识",
		menuname: "菜单名称",
		menutitle: "菜单标题",
		menupath: "菜单路径",
		allowedroles: "绑定角色",
		allowedrolesbefore: "变更前角色",
		allowedrolesafter: "变更后角色",
		addedroles: "新增角色",
		removedroles: "移除角色",
		deletedbefore: "变更前禁用状态",
		deletedafter: "变更后禁用状态",
		statusbeforelabel: "变更前状态",
		statusafterlabel: "变更后状态",
	},
	MENU: {
		menuid: "菜单标识",
		menuname: "菜单名称",
		menutitle: "菜单标题",
		menupath: "菜单路径",
		allowedroles: "绑定角色",
		allowedrolesbefore: "变更前角色",
		allowedrolesafter: "变更后角色",
		addedroles: "新增角色",
		removedroles: "移除角色",
		deletedbefore: "变更前禁用状态",
		deletedafter: "变更后禁用状态",
		statusbeforelabel: "变更前状态",
		statusafterlabel: "变更后状态",
	},
	MENU_MANAGEMENT: {
		menuid: "菜单标识",
		menuname: "菜单名称",
		menutitle: "菜单标题",
		menupath: "菜单路径",
		allowedroles: "绑定角色",
		allowedrolesbefore: "变更前角色",
		allowedrolesafter: "变更后角色",
		addedroles: "新增角色",
		removedroles: "移除角色",
		deletedbefore: "变更前禁用状态",
		deletedafter: "变更后禁用状态",
		statusbeforelabel: "变更前状态",
		statusafterlabel: "变更后状态",
	},
};
const MENU_RESOURCE_TYPES = new Set(["PORTAL_MENU", "MENU", "MENU_MANAGEMENT"]);

export function ChangeDiffViewer({
	snapshot,
	summary,
	action,
	operationTypeCode,
	status,
	resourceType,
	className,
}: ChangeDiffViewerProps) {
	const mode = normalizeMode(action, operationTypeCode);
	const sections = useMemo(
		() => buildSections({ snapshot, summary, mode, resourceType }),
		[snapshot, summary, mode, resourceType],
	);

	if (mode === "delete") {
		return (
			<div className={cn("rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-xs text-destructive", className)}>
				{resolveDeleteMessage(status)}
			</div>
		);
	}

	if (!sections.length) {
		return (
			<div className={cn("rounded-md border border-border/60 bg-muted/30 px-3 py-2 text-xs text-muted-foreground", className)}>
				无变更内容
			</div>
		);
	}

	return (
		<div className={cn("space-y-3", className)}>
			{sections.map((section, index) => (
				<div key={`${section.key}-${index}`} className="rounded-md border border-border/60">
					<div className="border-b border-border/60 bg-muted/40 px-3 py-2">
						<Text variant="body3" className="font-medium text-foreground">
							{section.title}
						</Text>
						{section.subtitle ? <Text variant="caption" className="text-muted-foreground">{section.subtitle}</Text> : null}
					</div>
					<div className="divide-y divide-border/60">
					{section.entries.map((entry) => (
						<div key={entry.key} className="px-3 py-2 text-xs leading-5">
							<Text variant="body3" className="font-medium text-foreground">
								{entry.label}
							</Text>
							{mode === "create" ? (
								<div className="mt-1 text-muted-foreground">
									{formatValue(entry.after, entry.field, entry.label)}
								</div>
							) : (
								<Fragment>
									<div className="mt-1 text-muted-foreground">
										变更前：{formatValue(entry.before, entry.field, entry.label)}
									</div>
									<div className="mt-1 text-destructive">
										变更后：{formatValue(entry.after, entry.field, entry.label)}
									</div>
								</Fragment>
							)}
						</div>
					))}
					</div>
				</div>
			))}
		</div>
	);
}

interface SectionInput {
	snapshot?: ChangeSnapshotLike | null;
	summary?: ChangeSummaryEntry[] | null;
	mode: ChangeDiffMode;
	resourceType?: string | null;
}

type SectionEntry = {
	key: string;
	field?: string;
	label: string;
	before?: unknown;
	after?: unknown;
};

type Section = {
	key: string;
	title: string;
	subtitle?: string;
	entries: SectionEntry[];
};

function buildSections({ snapshot, summary, mode, resourceType }: SectionInput): Section[] {
	if (!snapshot && (!summary || summary.length === 0)) {
		return [];
	}

	const sections: Section[] = [];
	const effectiveSnapshot = snapshot ?? {};

	if (Array.isArray(effectiveSnapshot.items) && effectiveSnapshot.items.length > 0) {
		effectiveSnapshot.items.forEach((item, idx) => {
			const nested = buildSections({
				snapshot: {
					before: sanitizeRecord(item.before),
					after: sanitizeRecord(item.after),
					changes: item.changes,
				},
				summary,
				mode,
				resourceType,
			});
			nested.forEach((section) => {
				sections.push({
					key: `${idx}-${section.key}`,
					title: section.title,
					subtitle: itemLabel(item, idx),
					entries: section.entries,
				});
			});
		});
		return sections;
	}

	if (mode === "create") {
		const summaryEntries = buildCreateEntriesFromSummary(summary, resourceType);
		if (summaryEntries.length > 0) {
			sections.push({
				key: "create",
				title: "新增内容",
				entries: summaryEntries,
			});
			return sections;
		}

		const after = sanitizeRecord(effectiveSnapshot.after);
		const entries = Object.entries(after)
			.filter(([field]) => !shouldSkipInitialField(field))
			.map(([field, value], idx) => ({
				key: field || String(idx),
				label: formatFieldLabel(field, resourceType),
				field,
				after: value,
			}))
			.filter((entry) => !shouldHideField(entry.field, entry.label, resourceType));
		if (entries.length > 0) {
			sections.push({
				key: "create",
				title: "新增内容",
				entries,
			});
		}
		return sections;
	}

	const sourceEntries = pickChangeEntries(effectiveSnapshot, summary, resourceType);
	if (sourceEntries.length > 0) {
		sections.push({
			key: "update",
			title: "变更字段",
			entries: sourceEntries,
		});
	}

	return sections;
}

function pickChangeEntries(snapshot: ChangeSnapshotLike, summary?: ChangeSummaryEntry[] | null, resourceType?: string | null): SectionEntry[] {
	if (summary && summary.length > 0) {
		return summary
			.map((row, idx) => ({
				key: `${row.field ?? row.label ?? idx}`,
				label: formatSummaryLabel(row, idx, resourceType),
				before: row.before,
				after: row.after,
				field: typeof row.field === "string" ? row.field : undefined,
			}))
			.filter((entry) => !valuesEqual(entry.before, entry.after))
			.filter((entry) => !shouldHideField(entry.field, entry.label, resourceType));
	}

	if (snapshot && Array.isArray(snapshot.changes)) {
		return snapshot.changes
			.map((change, idx) => ({
				key: `${change.field ?? idx}`,
				label: formatDisplayLabel(change.field, change.label, resourceType) ?? formatFieldLabel(
					change.label ?? change.field ?? `字段${idx + 1}`,
					resourceType,
				),
				before: change.before,
				after: change.after,
				field: typeof change.field === "string" ? change.field : undefined,
			}))
			.filter((entry) => !valuesEqual(entry.before, entry.after))
			.filter((entry) => !shouldHideField(entry.field, entry.label, resourceType));
	}

	const before = sanitizeRecord(snapshot.before);
	const after = sanitizeRecord(snapshot.after);
	const fields = new Set<string>([...Object.keys(before), ...Object.keys(after)]);
	const normalizedType = normalizeResourceType(resourceType);
	return Array.from(fields)
		.map((field) => {
			const beforeHas = Object.prototype.hasOwnProperty.call(before, field);
			const afterHas = Object.prototype.hasOwnProperty.call(after, field);
			if (normalizedType && MENU_RESOURCE_TYPES.has(normalizedType)) {
				if (beforeHas && !afterHas) {
					return null;
				}
			}
			return {
				key: field,
				label: formatFieldLabel(field, resourceType),
				before: before[field],
				after: after[field],
				field,
			};
		})
		.filter((entry): entry is SectionEntry => entry !== null)
		.filter((entry) => !valuesEqual(entry.before, entry.after))
		.filter((entry) => !shouldHideField(entry.field, entry.label, resourceType));
}

function sanitizeRecord(record?: SnapshotRecord | null): SnapshotRecord {
	if (!record) {
		return {};
	}
	const result: SnapshotRecord = {};
	Object.entries(record).forEach(([key, value]) => {
		if (value && typeof value === "object" && !Array.isArray(value)) {
			result[key] = sanitizeRecord(value as SnapshotRecord);
		} else if (Array.isArray(value)) {
			result[key] = value.map((item) => (typeof item === "object" ? sanitizeRecord(item as SnapshotRecord) : item));
		} else {
			result[key] = value;
		}
	});
	return result;
}

function itemLabel(item: { label?: unknown; name?: unknown; displayName?: unknown; title?: unknown } | null, index: number): string {
	const candidates = [item?.label, item?.name, item?.displayName, item?.title];
	for (const candidate of candidates) {
		if (typeof candidate === "string" && candidate.trim().length > 0) {
			return beautifyLabel(candidate.trim());
		}
	}
	return `第 ${index + 1} 项`;
}

function normalizeMode(action?: string | null, operationTypeCode?: string | null): ChangeDiffMode {
	const candidates = [action, operationTypeCode].filter(Boolean).map((token) => token!.toUpperCase());
	if (candidates.some((token) => token.includes("DELETE"))) {
		return "delete";
	}
	if (candidates.some((token) => token.includes("CREATE") || token.includes("ADD") || token === "INSERT")) {
		return "create";
	}
	return "update";
}

function formatValue(value: unknown, field?: string, label?: string): string {
	return formatDisplayValue(value, field, label);
}

function beautifyLabel(label: string): string {
	return label.replace(/[_\-.]+/g, " ").trim();
}

function resolveDeleteMessage(status?: string | null): string {
	if (!status) {
		return DEFAULT_DELETE_MESSAGE;
	}
	const normalized = status.trim().toUpperCase();
	if (FINAL_DELETE_STATUSES.has(normalized)) {
		return FINAL_DELETE_MESSAGE;
	}
	return DEFAULT_DELETE_MESSAGE;
}

function valuesEqual(a: unknown, b: unknown): boolean {
	if (a === b) {
		return true;
	}
	try {
		return JSON.stringify(a) === JSON.stringify(b);
	} catch {
		return false;
	}
}

export function buildChangeSnapshotFromDiff(diff?: Record<string, unknown> | null): ChangeSnapshotLike | null {
	if (!diff) {
		return null;
	}
	const before = toSnapshotRecord(diff.before);
	const after = toSnapshotRecord(diff.after);
	const changes = normalizeSnapshotChanges(diff.changes);
	const items = Array.isArray(diff.items)
		? (diff.items as Array<Record<string, unknown>>).map((item) => ({
				label: typeof item?.label === "string" ? item.label : undefined,
				name: typeof item?.name === "string" ? item.name : undefined,
				displayName: typeof item?.displayName === "string" ? item.displayName : undefined,
				before: toSnapshotRecord(item?.before),
				after: toSnapshotRecord(item?.after),
				changes: normalizeSnapshotChanges(item?.changes),
			}))
		: undefined;

	const snapshot: ChangeSnapshotLike = {};
	if (before && Object.keys(before).length > 0) {
		snapshot.before = before;
	}
	if (after && Object.keys(after).length > 0) {
		snapshot.after = after;
	}
	if (changes && changes.length > 0) {
		snapshot.changes = changes;
	}
	if (items && items.length > 0) {
		snapshot.items = items;
	}

	if (!snapshot.before && !snapshot.after && !snapshot.changes && !snapshot.items) {
		return null;
	}
	return snapshot;
}

export function normalizeSnapshotChanges(source: unknown): ChangeSnapshotLike["changes"] | undefined {
	if (!Array.isArray(source)) {
		return undefined;
	}
	const mapped: SnapshotChange[] = [];
	source.forEach((item, idx) => {
		if (!item || typeof item !== "object") {
			return;
		}
		const record = item as Record<string, unknown>;
		const field = record.field != null ? String(record.field) : undefined;
		const label = typeof record.label === "string" ? record.label : undefined;
		mapped.push({
			field: field ?? `field_${idx}`,
			label,
			before: record.before,
			after: record.after,
		});
	});
	return mapped.length > 0 ? mapped : undefined;
}

function buildCreateEntriesFromSummary(summary?: ChangeSummaryEntry[] | null, resourceType?: string | null): SectionEntry[] {
	if (!summary || summary.length === 0) {
		return [];
	}
	return summary
		.map((row, idx) => {
			const label = formatSummaryLabel(row, idx, resourceType);
			const field = typeof row.field === "string" ? row.field : undefined;
			return {
				key: `${row.field ?? row.label ?? idx}`,
				label,
				field,
				after: row.after ?? row.before,
			};
		})
		.filter((entry) => !shouldHideField(entry.field, entry.label, resourceType));
}

function shouldSkipInitialField(field?: string): boolean {
	if (!field) {
		return false;
	}
	const normalized = field.trim().toLowerCase();
	return normalized === "target";
}

function normalizeResourceType(resourceType?: string | null): string | null {
	if (!resourceType) {
		return null;
	}
	const normalized = resourceType.trim().toUpperCase();
	return normalized.length > 0 ? normalized : null;
}

function isAllowedFieldForResource(resourceType?: string | null, field?: string, label?: string): boolean {
	const normalizedType = normalizeResourceType(resourceType);
	if (!normalizedType) {
		return true;
	}
	const config = RESOURCE_FIELD_WHITELIST[normalizedType];
	if (!config) {
		return true;
	}
	const normalizedField = field ? field.trim().toLowerCase() : "";
	if (normalizedField && config.fields.has(normalizedField)) {
		return true;
	}
	const normalizedLabel = label ? label.trim().toLowerCase() : "";
	if (normalizedLabel && config.labels.has(normalizedLabel)) {
		return true;
	}
	return false;
}

function resolveResourceFieldLabel(field?: string, resourceType?: string | null): string | undefined {
	const normalizedType = normalizeResourceType(resourceType);
	if (!normalizedType) {
		return undefined;
	}
	if (!field) {
		return undefined;
	}
	const normalizedField = field.trim().toLowerCase();
	if (!normalizedField) {
		return undefined;
	}
	const dictionary = RESOURCE_FIELD_LABELS[normalizedType];
	return dictionary?.[normalizedField];
}

function formatDisplayLabel(field?: string, label?: string, resourceType?: string | null): string | undefined {
	const labelFromDictionary = field ? resolveResourceFieldLabel(field, resourceType) : undefined;
	if (labelFromDictionary) {
		return labelFromDictionary;
	}
	const normalizedLabel = label?.trim();
	if (normalizedLabel) {
		// 如果前端已经得到中文或非纯英文，直接使用
		if (!/^[A-Za-z0-9_\-]+$/.test(normalizedLabel)) {
			return normalizedLabel;
		}
		const dictionaryMatch = resolveResourceFieldLabel(normalizedLabel, resourceType);
		if (dictionaryMatch) {
			return dictionaryMatch;
		}
	}
	if (field) {
		const fallback = resolveResourceFieldLabel(field, resourceType);
		if (fallback) {
			return fallback;
		}
		return beautifyLabel(field);
	}
	if (normalizedLabel) {
		return beautifyLabel(normalizedLabel);
	}
	return undefined;
}

function formatFieldLabel(field: string, resourceType?: string | null): string {
	const display = formatDisplayLabel(field, undefined, resourceType);
	if (display) {
		return display;
	}
	if (!field) {
		return "字段";
	}
	const replaced = field.replace(/[_\-.]+/g, " ").trim();
	if (!replaced) {
		return field;
	}
	return replaced
		.split(" ")
		.filter(Boolean)
		.map((token) => token.charAt(0).toUpperCase() + token.slice(1))
		.join(" ");
}

function formatSummaryLabel(row: ChangeSummaryEntry, index: number, resourceType?: string | null): string {
	const display = formatDisplayLabel(
		typeof row.field === "string" ? row.field : undefined,
		typeof row.label === "string" ? row.label : undefined,
		resourceType,
	);
	if (display) {
		return display;
	}
	const rawLabel = typeof row.label === "string" ? row.label.trim() : "";
	if (rawLabel.length > 0) {
		return beautifyLabel(rawLabel);
	}
	const rawField = typeof row.field === "string" ? row.field.trim() : "";
	if (rawField.length > 0) {
		return formatFieldLabel(rawField, resourceType);
	}
	return `字段${index + 1}`;
}

function shouldHideField(field?: string, label?: string, resourceType?: string | null): boolean {
	if (isFieldHiddenForResource(field, resourceType)) {
		return true;
	}
	if (!isAllowedFieldForResource(resourceType, field, label)) {
		return true;
	}
	const normalizedField = field ? field.trim().toLowerCase() : "";
	if (normalizedField && HIDDEN_FIELD_KEYS.has(normalizedField)) {
		return true;
	}
	const normalizedLabel = label ? label.trim().toLowerCase() : "";
	if (normalizedLabel && HIDDEN_FIELD_LABELS.has(normalizedLabel)) {
		return true;
	}
	return false;
}

function toSnapshotRecord(value: unknown): SnapshotRecord | null {
	if (!value || typeof value !== "object" || Array.isArray(value)) {
		return null;
	}
	const result: SnapshotRecord = {};
	Object.entries(value as Record<string, unknown>).forEach(([key, val]) => {
		result[key] = val;
	});
	return result;
}
