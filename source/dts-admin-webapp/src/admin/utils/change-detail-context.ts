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
	actionLabel?: string | null;
	request?: ChangeRequest;
	subjectName?: string | null;
}

export function summarizeChangeDisplayContext(
	context: ChangeDisplayContext,
	{ maxEntries = 3, actionLabel, request, subjectName }: SummarizeContextOptions = {},
): string {
	const filteredSummary = context.summary.filter((entry) => !isSummaryHidden(entry));
	const primaryFieldLabel = resolvePrimaryFieldLabel(filteredSummary, context.menuChanges, context.snapshot, maxEntries);
	const resolvedSubjectName = normalizeToString(subjectName) ?? resolveSubjectName(context, request);
	const actionText = normalizeToString(actionLabel) ?? normalizeToString(request?.action);

	if (!actionText && !resolvedSubjectName && !primaryFieldLabel) {
		return "—";
	}

	const finalAction = actionText ?? "未知操作";
	const finalSubject = resolvedSubjectName ?? "未知用户";
	const finalField = primaryFieldLabel ?? "无字段变更";

	return `操作类型「${finalAction}」 ${finalSubject} ${finalField}`;
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

function formatSummaryValue(value: unknown, field?: string, label?: string): string {
	return formatDisplayValue(value, field, label);
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

function summarizeSnapshotChanges(snapshot: ChangeSnapshotLike | null | undefined, maxEntries: number): string | null {
	if (!snapshot) {
		return null;
	}
	const collected = collectSnapshotChanges(snapshot, maxEntries);
	if (collected.length === 0) {
		return null;
	}
	return collected
		.slice(0, maxEntries)
		.map((entry, index) => {
			const label = entry.label || entry.field || `字段${index + 1}`;
			const beforeDefined = entry.before !== undefined && entry.before !== null && entry.before !== "";
			const beforeText = formatSummaryValue(entry.before, entry.field, label);
			const afterText = formatSummaryValue(entry.after, entry.field, label);
			return beforeDefined ? `${label}: ${beforeText} → ${afterText}` : `${label}: ${afterText}`;
		})
		.join("；");
}

type SnapshotChangeEntry = {
	field?: string;
	label?: string;
	before?: unknown;
	after?: unknown;
};

function collectSnapshotChanges(snapshot: ChangeSnapshotLike, maxEntries: number): SnapshotChangeEntry[] {
	const result: SnapshotChangeEntry[] = [];
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
