export type SemanticDatasetOption = {
	id: string;
	name: string;
	table?: string;
	layer?: string;
	database?: string;
	schema?: string;
};

export type GovernanceDomainOption = {
	id: string;
	code?: string;
	name: string;
	label: string;
};

export type SemanticFieldLike = {
	name: string;
	dataType?: string;
};

export type DefaultMetricFormula = {
	formulaType: MetricFormulaType;
	format: "number" | "integer" | "percent";
	formulaJson: string;
};

export type MetricFormulaType = "sum" | "count" | "count_distinct" | "avg" | "max" | "min" | "count_if" | "sum_if" | "ratio";

export type MetricFormulaAggregation = "sum" | "count" | "count_distinct" | "avg" | "max" | "min";

export type MetricFormulaTemplateInput = {
	formulaType?: MetricFormulaType | string;
	field?: string;
	dataType?: string;
	conditionField?: string;
	conditionOperator?: string;
	conditionValue?: string;
	distinct?: boolean;
	numeratorField?: string;
	numeratorAggregation?: MetricFormulaAggregation;
	denominatorField?: string;
	denominatorAggregation?: MetricFormulaAggregation;
	multiply?: number;
};

export function normalizeSemanticDataset(item: any): SemanticDatasetOption {
	return {
		id: String(item?.id || item?.key || item?.name || item?.tableName || item?.hiveTable || ""),
		name: String(item?.name || item?.displayName || item?.hiveTable || item?.tableName || item?.id || ""),
		table: String(item?.hiveTable || item?.tableName || item?.name || ""),
		layer: String(item?.warehouseLayer || item?.layer || ""),
		database: String(item?.databaseName || item?.database || ""),
		schema: String(item?.schemaName || item?.schema || ""),
	};
}

export function flattenGovernanceDomains(
	nodes: any[],
	path: string[] = [],
	out: GovernanceDomainOption[] = [],
): GovernanceDomainOption[] {
	nodes.forEach((node) => {
		if (!node || typeof node !== "object") return;
		const id = String(node.id || node.key || "");
		const name = String(node.name || "");
		if (!id || !name) return;
		const code = node.code ? String(node.code) : undefined;
		const nextPath = [...path, name];
		out.push({
			id,
			code,
			name,
			label: nextPath.join(" / "),
		});
		if (Array.isArray(node.children) && node.children.length) {
			flattenGovernanceDomains(node.children, nextPath, out);
		}
	});
	return out;
}

export function isDwdSemanticDataset(item: { layer?: string; warehouseLayer?: string; table?: string; name?: string }) {
	const layer = String(item.warehouseLayer || item.layer || "").toUpperCase();
	const table = String(item.table || item.name || "").toLowerCase();
	return layer === "DWD" || table.startsWith("dwd_");
}

export function safeSemanticCode(value: string): string {
	return value
		.trim()
		.replace(/([a-z0-9])([A-Z])/g, "$1_$2")
		.replace(/[^\p{L}\p{N}_]+/gu, "_")
		.replace(/^_+|_+$/g, "")
		.toLowerCase();
}

export function isNumericSemanticField(field: SemanticFieldLike): boolean {
	const dataType = (field.dataType || "").toLowerCase();
	return ["int", "integer", "bigint", "smallint", "decimal", "numeric", "number", "double", "float", "real"].some((item) =>
		dataType.includes(item),
	);
}

export function buildDefaultMetricFormula(field: SemanticFieldLike): DefaultMetricFormula {
	const aggregation = isNumericSemanticField(field) ? "sum" : "count_distinct";
	return buildMetricFormulaTemplate({
		formulaType: aggregation,
		field: field.name,
		dataType: field.dataType,
	});
}

export function buildMetricFormulaTemplate(input: MetricFormulaTemplateInput): DefaultMetricFormula {
	const formulaType = normalizeFormulaType(input.formulaType);
	if (formulaType === "ratio") {
		const multiply = input.multiply ?? 1;
		return {
			formulaType,
			format: "percent",
			formulaJson: JSON.stringify({
				type: "ratio",
				numerator: buildAggregationNode(
					input.numeratorAggregation || "sum",
					requiredFormulaField(input.numeratorField, "numeratorField"),
				),
				denominator: buildAggregationNode(
					input.denominatorAggregation || "sum",
					requiredFormulaField(input.denominatorField, "denominatorField"),
				),
				multiply,
				zero_division: "null",
			}),
		};
	}
	if (formulaType === "count_if") {
		return {
			formulaType,
			format: "integer",
			formulaJson: JSON.stringify({
				type: "conditional_count",
				field: requiredFormulaField(input.field, "field"),
				condition: buildConditionNode(input),
				distinct: Boolean(input.distinct),
			}),
		};
	}
	if (formulaType === "sum_if") {
		return {
			formulaType,
			format: "number",
			formulaJson: JSON.stringify({
				type: "conditional_sum",
				field: requiredFormulaField(input.field, "field"),
				condition: buildConditionNode(input),
			}),
		};
	}
	const aggregation = formulaType as MetricFormulaAggregation;
	return {
		formulaType: aggregation,
		format: ["count", "count_distinct"].includes(aggregation) ? "integer" : "number",
		formulaJson: JSON.stringify({
			type: "aggregation",
			aggregation,
			field: requiredFormulaField(input.field, "field"),
		}),
	};
}

function normalizeFormulaType(value?: string): MetricFormulaType {
	const normalized = String(value || "sum").toLowerCase();
	if (["sum", "count", "count_distinct", "avg", "max", "min", "count_if", "sum_if", "ratio"].includes(normalized)) {
		return normalized as MetricFormulaType;
	}
	return "sum";
}

function buildAggregationNode(aggregation: MetricFormulaAggregation, field: string) {
	return {
		type: "aggregation",
		aggregation,
		field,
	};
}

function buildConditionNode(input: MetricFormulaTemplateInput) {
	return {
		field: requiredFormulaField(input.conditionField, "conditionField"),
		operator: input.conditionOperator || "=",
		value: parseFormulaValue(input.conditionValue),
	};
}

function parseFormulaValue(value?: string) {
	const normalized = String(value ?? "").trim();
	if (normalized === "true") return true;
	if (normalized === "false") return false;
	if (/^-?\d+(\.\d+)?$/.test(normalized)) return Number(normalized);
	return normalized;
}

function requiredFormulaField(value: string | undefined, name: string): string {
	const normalized = String(value || "").trim();
	if (!normalized) throw new Error(`${name} is required`);
	return normalized;
}

export function splitQualifiedField(value?: string): { table?: string; field?: string } {
	const parts = String(value || "").split(".");
	if (parts.length < 2) return { table: undefined, field: value };
	return { table: parts.slice(0, -1).join("."), field: parts[parts.length - 1] };
}
