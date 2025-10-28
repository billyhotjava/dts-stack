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

type PlainRecord = Record<string, unknown>;

const SUMMARY_KEYS = ["changeSummary", "change_summary", "summary"];
const SNAPSHOT_KEYS = ["changeSnapshot", "change_snapshot", "snapshot"];

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
}

export function summarizeChangeDisplayContext(
	context: ChangeDisplayContext,
	{ maxEntries = 3 }: SummarizeContextOptions = {},
): string {
	const { summary, menuChanges } = context;
	if (summary.length > 0) {
		return summary
			.slice(0, maxEntries)
			.map((entry, index) => {
				const label = entry.label || entry.field || `字段${index + 1}`;
				const beforeDefined = entry.before !== undefined && entry.before !== null && entry.before !== "";
				const beforeText = formatSummaryValue(entry.before);
				const afterText = formatSummaryValue(entry.after);
				return beforeDefined ? `${label}: ${beforeText} → ${afterText}` : `${label}: ${afterText}`;
			})
			.join("；");
	}

	if (menuChanges.length > 0) {
		const parts: string[] = [];
		menuChanges.slice(0, maxEntries).forEach((entry) => {
			const label = entry.menuTitle || entry.menuName || entry.menuPath || entry.id || "菜单";
			const changeSegments: string[] = [];
			if (entry.addedRoles?.length) {
				changeSegments.push(`新增角色 ${entry.addedRoles.join("、")}`);
			}
			if (entry.removedRoles?.length) {
				changeSegments.push(`移除角色 ${entry.removedRoles.join("、")}`);
			}
			if (entry.allowedRolesAfter && entry.allowedRolesBefore) {
				const before = entry.allowedRolesBefore.join("、");
				const after = entry.allowedRolesAfter.join("、");
				if (before !== after && !entry.addedRoles?.length && !entry.removedRoles?.length) {
					changeSegments.push(`角色 ${before || "无"} → ${after || "无"}`);
				}
			}
			if (entry.addedPermissions?.length) {
				changeSegments.push(`新增权限 ${entry.addedPermissions.join("、")}`);
			}
			if (entry.removedPermissions?.length) {
				changeSegments.push(`移除权限 ${entry.removedPermissions.join("、")}`);
			}
			const statusChanged =
				entry.statusBeforeLabel !== undefined &&
				entry.statusAfterLabel !== undefined &&
				entry.statusBeforeLabel !== entry.statusAfterLabel;
			if (statusChanged) {
				changeSegments.push(`状态 ${entry.statusBeforeLabel} → ${entry.statusAfterLabel}`);
			}
			if (
				entry.maxDataLevelBeforeLabel &&
				entry.maxDataLevelAfterLabel &&
				entry.maxDataLevelBeforeLabel !== entry.maxDataLevelAfterLabel
			) {
				changeSegments.push(`最大数据密级 ${entry.maxDataLevelBeforeLabel} → ${entry.maxDataLevelAfterLabel}`);
			}
			if (
				entry.securityLevelBeforeLabel &&
				entry.securityLevelAfterLabel &&
				entry.securityLevelBeforeLabel !== entry.securityLevelAfterLabel
			) {
				changeSegments.push(`访问密级 ${entry.securityLevelBeforeLabel} → ${entry.securityLevelAfterLabel}`);
			}
			if (entry.addedRules?.length) {
				changeSegments.push(
					`新增可见性规则 ${entry.addedRules.map((rule) => formatVisibilityRule(rule)).join("、")}`,
				);
			}
			if (entry.removedRules?.length) {
				changeSegments.push(
					`移除可见性规则 ${entry.removedRules.map((rule) => formatVisibilityRule(rule)).join("、")}`,
				);
			}
			if (changeSegments.length > 0) {
				parts.push(`菜单「${label}」${changeSegments.join("；")}`);
			}
		});
		return parts.join("；") || "—";
	}

	return "—";
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

function formatSummaryValue(value: unknown): string {
	if (value === null || value === undefined || value === "") {
		return "—";
	}
	if (Array.isArray(value)) {
		if (value.length === 0) {
			return "[]";
		}
		return value
			.map((item) => formatSummaryValue(item))
			.filter((item) => item !== "—")
			.join("、");
	}
	if (typeof value === "object") {
		try {
			return JSON.stringify(value, null, 0);
		} catch {
			return String(value);
		}
	}
	return String(value);
}

function formatVisibilityRule(rule: { role?: string; permission?: string; dataLevelLabel?: string }): string {
	const parts: string[] = [];
	if (rule.role) {
		parts.push(rule.role);
	}
	if (rule.permission) {
		parts.push(`权限:${rule.permission}`);
	}
	if (rule.dataLevelLabel) {
		parts.push(`密级:${rule.dataLevelLabel}`);
	}
	return parts.join(" ");
}
