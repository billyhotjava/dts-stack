import { Fragment, useMemo } from "react";
import { Text } from "@/ui/typography";
import { cn } from "@/utils";

type SnapshotRecord = Record<string, unknown>;

type SnapshotChange = {
	field?: string;
	label?: string;
	before?: unknown;
	after?: unknown;
};

type ChangeSnapshotLike = {
	before?: SnapshotRecord | null;
	after?: SnapshotRecord | null;
	changes?: SnapshotChange[] | null;
	items?: Array<{
		label?: string;
		name?: string;
		displayName?: string;
		before?: SnapshotRecord | null;
		after?: SnapshotRecord | null;
		changes?: SnapshotChange[] | null;
	}>;
};

type ChangeSummaryEntry = {
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
	resourceType?: string | null;
	className?: string;
}

const DEFAULT_DELETE_MESSAGE = "记录已删除";

export function ChangeDiffViewer({
	snapshot,
	summary,
	action,
	operationTypeCode,
	resourceType,
	className,
}: ChangeDiffViewerProps) {
	const mode = normalizeMode(action, operationTypeCode);
	const sections = useMemo(() => buildSections({ snapshot, summary, mode, resourceType }), [snapshot, summary, mode, resourceType]);

	if (mode === "delete") {
		return (
			<div className={cn("rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-xs text-destructive", className)}>
				{DEFAULT_DELETE_MESSAGE}
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
									<div className="mt-1 text-muted-foreground">{formatValue(entry.after)}</div>
								) : (
									<Fragment>
										<div className="mt-1 text-muted-foreground">变更前：{formatValue(entry.before)}</div>
										<div className="mt-1 text-destructive">变更后：{formatValue(entry.after)}</div>
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

function buildSections({ snapshot, summary, mode }: SectionInput): Section[] {
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
		const after = sanitizeRecord(effectiveSnapshot.after);
		const entries = Object.entries(after).map(([field, value]) => ({
			key: field,
			label: formatFieldLabel(field),
			after: value,
		}));
		if (entries.length > 0) {
			sections.push({
				key: "create",
				title: "新增内容",
				entries,
			});
		}
		return sections;
	}

	const sourceEntries = pickChangeEntries(effectiveSnapshot, summary);
	if (sourceEntries.length > 0) {
		sections.push({
			key: "update",
			title: "变更字段",
			entries: sourceEntries,
		});
	}

	return sections;
}

function pickChangeEntries(snapshot: ChangeSnapshotLike, summary?: ChangeSummaryEntry[] | null): SectionEntry[] {
	if (summary && summary.length > 0) {
		return summary
			.map((row, idx) => ({
				key: `${row.field ?? row.label ?? idx}`,
				label: formatFieldLabel(row.label ?? row.field ?? `字段${idx + 1}`),
				before: row.before,
				after: row.after,
			}))
			.filter((entry) => !valuesEqual(entry.before, entry.after));
	}

	if (snapshot && Array.isArray(snapshot.changes)) {
		return snapshot.changes
			.map((change, idx) => ({
				key: `${change.field ?? idx}`,
				label: formatFieldLabel(change.label ?? change.field ?? `字段${idx + 1}`),
				before: change.before,
				after: change.after,
			}))
			.filter((entry) => !valuesEqual(entry.before, entry.after));
	}

	const before = sanitizeRecord(snapshot.before);
	const after = sanitizeRecord(snapshot.after);
	const fields = new Set<string>([...Object.keys(before), ...Object.keys(after)]);
	return Array.from(fields)
		.map((field) => ({
			key: field,
			label: formatFieldLabel(field),
			before: before[field],
			after: after[field],
		}))
		.filter((entry) => !valuesEqual(entry.before, entry.after));
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

function formatValue(value: unknown): string {
	if (value === null || value === undefined) {
		return "—";
	}
	if (Array.isArray(value)) {
		if (value.length === 0) {
			return "[]";
		}
		return `[${value.map(formatPrimitive).join("，")}]`;
	}
	if (typeof value === "object") {
		try {
			return JSON.stringify(value, null, 2);
		} catch (err) {
			console.warn("Failed to stringify value", err, value);
			return String(value);
		}
	}
	return formatPrimitive(value);
}

function formatPrimitive(value: unknown): string {
	if (typeof value === "string") {
		return value || "—";
	}
	if (typeof value === "number" || typeof value === "boolean") {
		return String(value);
	}
	return value === null || value === undefined ? "—" : String(value);
}

function formatFieldLabel(field: string): string {
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

function beautifyLabel(label: string): string {
	return label.replace(/[_\-.]+/g, " ").trim();
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
