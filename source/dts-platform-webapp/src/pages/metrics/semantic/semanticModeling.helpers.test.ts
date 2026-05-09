import { describe, expect, it } from "vitest";
import {
	buildDefaultMetricFormula,
	buildMetricFormulaTemplate,
	flattenGovernanceDomains,
	isNumericSemanticField,
	normalizeSemanticDataset,
	safeSemanticCode,
	splitQualifiedField,
} from "./semanticModeling.helpers";

describe("semanticModeling.helpers", () => {
	it("normalizes catalog dataset payloads and preserves DWD layer metadata", () => {
		const dataset = normalizeSemanticDataset({
			id: "ds-1",
			displayName: "项目明细",
			hiveTable: "dwd_project_detail",
			warehouseLayer: "DWD",
			databaseName: "dts",
			schemaName: "public",
		});

		expect(dataset).toEqual({
			id: "ds-1",
			name: "项目明细",
			table: "dwd_project_detail",
			layer: "DWD",
			database: "dts",
			schema: "public",
		});
	});

	it("flattens governance domain trees with readable path labels", () => {
		const domains = flattenGovernanceDomains([
			{
				id: "root",
				code: "project",
				name: "项目管理",
				children: [{ id: "risk", code: "risk", name: "风险管理" }],
			},
		]);

		expect(domains).toEqual([
			{ id: "root", code: "project", name: "项目管理", label: "项目管理" },
			{ id: "risk", code: "risk", name: "风险管理", label: "项目管理 / 风险管理" },
		]);
	});

	it("generates stable lower snake case codes from field names", () => {
		expect(safeSemanticCode("DirectCostAmount")).toBe("direct_cost_amount");
		expect(safeSemanticCode("直接成本-执行率 %")).toBe("直接成本_执行率");
	});

	it("detects numeric fields across common warehouse types", () => {
		expect(isNumericSemanticField({ name: "amount", dataType: "DECIMAL(18,2)" })).toBe(true);
		expect(isNumericSemanticField({ name: "project_id", dataType: "varchar" })).toBe(false);
	});

	it("builds skill-compatible formula JSON for numeric and non-numeric fields", () => {
		expect(buildDefaultMetricFormula({ name: "direct_cost_amount", dataType: "decimal" })).toEqual({
			formulaType: "sum",
			format: "number",
			formulaJson: JSON.stringify({
				type: "aggregation",
				aggregation: "sum",
				field: "direct_cost_amount",
			}),
		});
		expect(buildDefaultMetricFormula({ name: "project_id", dataType: "varchar" })).toEqual({
			formulaType: "count_distinct",
			format: "integer",
			formulaJson: JSON.stringify({
				type: "aggregation",
				aggregation: "count_distinct",
				field: "project_id",
			}),
		});
	});

	it("builds ratio formulas with nested aggregation nodes", () => {
		expect(buildMetricFormulaTemplate({
			formulaType: "ratio",
			numeratorField: "direct_cost_amount",
			denominatorField: "direct_cost_control_amount",
			numeratorAggregation: "sum",
			denominatorAggregation: "sum",
			multiply: 100,
		})).toEqual({
			formulaType: "ratio",
			format: "percent",
			formulaJson: JSON.stringify({
				type: "ratio",
				numerator: {
					type: "aggregation",
					aggregation: "sum",
					field: "direct_cost_amount",
				},
				denominator: {
					type: "aggregation",
					aggregation: "sum",
					field: "direct_cost_control_amount",
				},
				multiply: 100,
				zero_division: "null",
			}),
		});
	});

	it("builds conditional count formulas with object conditions", () => {
		expect(buildMetricFormulaTemplate({
			formulaType: "count_if",
			field: "project_id",
			conditionField: "project_status",
			conditionOperator: "=",
			conditionValue: "在研",
			distinct: true,
		})).toEqual({
			formulaType: "count_if",
			format: "integer",
			formulaJson: JSON.stringify({
				type: "conditional_count",
				field: "project_id",
				condition: {
					field: "project_status",
					operator: "=",
					value: "在研",
				},
				distinct: true,
			}),
		});
	});

	it("splits qualified join fields while keeping raw fields intact", () => {
		expect(splitQualifiedField("dwd_project_detail.project_id")).toEqual({
			table: "dwd_project_detail",
			field: "project_id",
		});
		expect(splitQualifiedField("project_id")).toEqual({ table: undefined, field: "project_id" });
	});
});
