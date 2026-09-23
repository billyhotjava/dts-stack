import type { AnalysisQuerySpec } from "../api/analysisApi";

export type AnalysisFieldKind = "dimension" | "metric" | "derived";
export type AnalysisShelf = "x" | "y" | "color" | "label" | "tooltip";

export type AnalysisWorkspaceField = {
	kind: AnalysisFieldKind;
	code: string;
};

export const CANONICAL_VISUALIZATIONS = [
	{ value: "table", label: "明细表" },
	{ value: "bar", label: "柱状图" },
	{ value: "line", label: "折线图" },
	{ value: "area", label: "面积图" },
	{ value: "pie", label: "饼图" },
	{ value: "number", label: "指标卡" },
] as const;

export function analysisQueryFingerprint(spec: AnalysisQuerySpec): string {
	return JSON.stringify({
		apiVersion: spec.apiVersion,
		dataset: spec.dataset,
		dimensions: spec.dimensions,
		metrics: spec.metrics,
		derivedMetrics: spec.derivedMetrics,
		filters: spec.filters,
		timeRange: spec.timeRange,
		orderBy: spec.orderBy,
		limit: spec.limit,
	});
}

function unique(values: string[]): string[] {
	return [...new Set(values.filter(Boolean))];
}

function withSelectedField(spec: AnalysisQuerySpec, field: AnalysisWorkspaceField): AnalysisQuerySpec {
	if (field.kind === "dimension") {
		return spec.dimensions.some((item) => item.field === field.code)
			? spec
			: { ...spec, dimensions: [...spec.dimensions, { field: field.code, alias: null }] };
	}
	if (field.kind === "metric") {
		return spec.metrics.some((item) => item.code === field.code)
			? spec
			: { ...spec, metrics: [...spec.metrics, { code: field.code, alias: null }] };
	}
	return spec;
}

export function placeFieldOnShelf(
	spec: AnalysisQuerySpec,
	field: AnalysisWorkspaceField,
	shelf: AnalysisShelf,
): AnalysisQuerySpec {
	const selected = withSelectedField(spec, field);
	const settings = { ...selected.visualization.settings };
	if (shelf === "x") {
		settings["graph.dimensions"] = unique([
			...((settings["graph.dimensions"] as string[] | undefined) ?? []),
			field.code,
		]);
	} else if (shelf === "y") {
		settings["graph.metrics"] = unique([
			...((settings["graph.metrics"] as string[] | undefined) ?? []),
			field.code,
		]);
	} else if (shelf === "color") {
		settings["dts.encoding.color"] = field.code;
	} else if (shelf === "label") {
		settings["dts.encoding.label"] = field.code;
	} else {
		settings["dts.tooltip.fields"] = unique([
			...((settings["dts.tooltip.fields"] as string[] | undefined) ?? []),
			field.code,
		]);
	}
	return { ...selected, visualization: { ...selected.visualization, settings } };
}

/** S10DC-115: one action for "select all dimensions"; same effect as clicking each one in order. */
export function placeAllDimensionsOnShelf(spec: AnalysisQuerySpec, dimensionCodes: readonly string[]): AnalysisQuerySpec {
	return dimensionCodes.reduce((current, code) => placeFieldOnShelf(current, { kind: "dimension", code }, "x"), spec);
}

export function removeFieldFromAnalysis(spec: AnalysisQuerySpec, code: string): AnalysisQuerySpec {
	const settings = { ...spec.visualization.settings };
	for (const key of ["graph.dimensions", "graph.metrics", "dts.tooltip.fields"]) {
		const values = settings[key];
		if (Array.isArray(values)) settings[key] = values.filter((value) => value !== code);
	}
	for (const key of ["dts.encoding.color", "dts.encoding.label"]) {
		if (settings[key] === code) delete settings[key];
	}
	return {
		...spec,
		dimensions: spec.dimensions.filter((item) => item.field !== code),
		metrics: spec.metrics.filter((item) => item.code !== code),
		derivedMetrics: spec.derivedMetrics.filter((item) => item.code !== code),
		filters: spec.filters.filter((item) => item.field !== code),
		timeRange: spec.timeRange?.field === code ? null : spec.timeRange,
		orderBy: spec.orderBy.filter((item) => item.field !== code),
		visualization: { ...spec.visualization, settings },
	};
}

export function setVisualizationSetting(
	spec: AnalysisQuerySpec,
	key: string,
	value: unknown,
): AnalysisQuerySpec {
	return {
		...spec,
		visualization: {
			...spec.visualization,
			settings: { ...spec.visualization.settings, [key]: value },
		},
	};
}
