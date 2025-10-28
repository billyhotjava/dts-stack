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

