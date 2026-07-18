import type { ComponentInteractionMapping, DataSourceConfig, DrillLevel } from "./types";

const DRILL_TARGET_SOURCE_TYPES = new Set(["api", "card", "sql", "dataset", "metric", "database"]);

export interface NormalizedDrillLevel {
	label: string;
	dataSource: DataSourceConfig;
	mappings: ComponentInteractionMapping[];
	inheritContext: boolean;
	legacyParamName?: string;
}

export interface DrillEntry {
	label: string;
	parameters: Record<string, string>;
}

export interface DrillBreadcrumb {
	label: string;
	depth: number;
}

export interface GenericDrillSnapshot {
	depth: number;
	effectiveDataSource: DataSourceConfig | undefined;
	queryParameters: Array<{ name: string; value: string }>;
	breadcrumbs: DrillBreadcrumb[];
}

function readScalarValue(payload: Record<string, unknown>, sourcePath: string): string | undefined {
	const path = sourcePath.trim();
	if (!path) return undefined;

	let value: unknown;
	if (Object.hasOwn(payload, path)) {
		value = payload[path];
	} else {
		const segments = path.match(/[^.[\]]+/g) ?? [];
		value = payload;
		for (const segment of segments) {
			if (value === null || typeof value !== "object") {
				return undefined;
			}
			value = (value as Record<string, unknown>)[segment];
		}
	}

	if (typeof value === "string") return value;
	if (typeof value === "number" || typeof value === "boolean") return String(value);
	return undefined;
}

function transformMappedValue(rawValue: string | undefined, mapping: ComponentInteractionMapping): string | undefined {
	const fallback = String(mapping.fallbackValue ?? "").trim();
	const source = rawValue == null ? "" : String(rawValue);
	let next = source;
	if (mapping.transform === "lowercase") {
		next = source.toLowerCase();
	} else if (mapping.transform === "uppercase") {
		next = source.toUpperCase();
	} else if (mapping.transform === "number") {
		const parsed = Number(source);
		if (!Number.isFinite(parsed)) return fallback || undefined;
		next = String(parsed);
	} else if (mapping.transform === "string") {
		next = String(source);
	}
	return next.trim().length > 0 ? next : fallback || undefined;
}

function resolveLegacyValue(clickPayload: Record<string, unknown>): string | undefined {
	for (const sourcePath of ["name", "data.name", "row[0]"]) {
		const value = readScalarValue(clickPayload, sourcePath);
		if (value?.trim()) return value;
	}
	return undefined;
}

export function normalizeDrillLevel(level: DrillLevel): NormalizedDrillLevel | null {
	const label = String(level?.label ?? "").trim();
	if (!label) return null;

	const dataSource = level.dataSource;
	const sourceType = String(dataSource?.sourceType ?? dataSource?.type ?? "")
		.trim()
		.toLowerCase();
	if (dataSource && DRILL_TARGET_SOURCE_TYPES.has(sourceType)) {
		const mappings = Array.isArray(level.mappings) ? level.mappings : [];
		if (
			mappings.length === 0 ||
			mappings.some(
				(mapping) => !String(mapping?.variableKey ?? "").trim() || !String(mapping?.sourcePath ?? "").trim(),
			)
		) {
			return null;
		}
		return {
			label,
			dataSource,
			mappings,
			inheritContext: level.inheritContext !== false,
		};
	}

	const cardId = Number(level.cardId);
	const paramName = String(level.paramName ?? "").trim();
	if (!Number.isFinite(cardId) || cardId <= 0 || !paramName) return null;
	return {
		label,
		dataSource: {
			type: "card",
			sourceType: "card",
			cardConfig: { cardId },
		},
		mappings: [{ variableKey: paramName, sourcePath: "name", transform: "string" }],
		inheritContext: level.inheritContext === true,
		legacyParamName: paramName,
	};
}

export function resolveNextDrillEntry(
	level: NormalizedDrillLevel,
	clickPayload: Record<string, unknown>,
): DrillEntry | null {
	if (level.legacyParamName) {
		const value = resolveLegacyValue(clickPayload);
		if (!value) return null;
		return {
			label: `${level.label}: ${value}`,
			parameters: { [level.legacyParamName]: value },
		};
	}

	const parameters: Record<string, string> = {};
	let breadcrumbValue: string | undefined;
	for (const mapping of level.mappings) {
		const variableKey = String(mapping.variableKey ?? "").trim();
		const sourcePath = String(mapping.sourcePath ?? "").trim();
		const value = transformMappedValue(readScalarValue(clickPayload, sourcePath), mapping);
		if (!variableKey || !sourcePath || value === undefined) return null;
		parameters[variableKey] = value;
		breadcrumbValue ??= value;
	}
	if (!breadcrumbValue) return null;
	return {
		label: `${level.label}: ${breadcrumbValue}`,
		parameters,
	};
}

export function buildDrillSnapshot(
	rootDataSource: DataSourceConfig | undefined,
	levels: NormalizedDrillLevel[],
	stack: DrillEntry[],
): GenericDrillSnapshot {
	const depth = Math.min(levels.length, stack.length);
	const parameters = new Map<string, string>();
	for (let index = 0; index < depth; index += 1) {
		if (levels[index].inheritContext === false) {
			parameters.clear();
		}
		for (const [name, value] of Object.entries(stack[index].parameters)) {
			parameters.set(name, value);
		}
	}

	return {
		depth,
		effectiveDataSource: depth > 0 ? levels[depth - 1].dataSource : rootDataSource,
		queryParameters: Array.from(parameters, ([name, value]) => ({ name, value })),
		breadcrumbs:
			depth === 0
				? []
				: [
						{ label: "全部", depth: 0 },
						...stack.slice(0, depth).map((entry, index) => ({ label: entry.label, depth: index + 1 })),
					],
	};
}
