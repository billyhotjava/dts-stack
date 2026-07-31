import { describe, expect, it } from "vitest";
import { legacyDataModelingTarget } from "./LegacyDataModelingRedirect";

describe("legacy data-modeling redirects", () => {
	it("maps the old workspace module to the corresponding prototype page", () => {
		expect(legacyDataModelingTarget("/modeling/workbench", "?module=metrics")).toBe("/data-modeling/metrics/atomic");
		expect(legacyDataModelingTarget("/modeling/workbench", "?module=graph")).toBe("/data-modeling/graphs/models");
	});

	it("maps legacy specialist pages without rendering the retired UI", () => {
		expect(legacyDataModelingTarget("/studio/sql-modeling", "")).toBe("/data-modeling/dimensions/workbench");
		expect(legacyDataModelingTarget("/modeling/plans/example", "")).toBe("/data-modeling/planning/spaces");
		expect(legacyDataModelingTarget("/studio/modeling/warehouse-planning", "")).toBe(
			"/data-modeling/planning/business-categories",
		);
		expect(legacyDataModelingTarget("/studio/modeling/standards/glossary", "")).toBe("/data-modeling/standards/fields");
		expect(legacyDataModelingTarget("/modeling/metric-workbench", "")).toBe("/data-modeling/metrics/atomic");
		expect(legacyDataModelingTarget("/modeling/semantic-center", "")).toBe("/data-modeling/dimensions/workbench");
		expect(legacyDataModelingTarget("/modeling/semantic-center/objects", "")).toBe(
			"/data-modeling/dimensions/workbench",
		);
		expect(legacyDataModelingTarget("/modeling/semantic-center/objects", "?model=demo", "#fields")).toBe(
			"/data-modeling/dimensions/workbench?model=demo#fields",
		);
		expect(legacyDataModelingTarget("/modeling/semantic-center/metrics", "")).toBe("/data-modeling/metrics/atomic");
		expect(legacyDataModelingTarget("/bi/semantic-modeling", "")).toBe("/data-modeling/dimensions/workbench");
	});

	it("merges legacy publish tasks into the modeling overview", () => {
		expect(legacyDataModelingTarget("/modeling/semantic/publish", "")).toBe("/data-modeling/home/workspace");
		expect(legacyDataModelingTarget("/modeling/semantic/runs", "")).toBe("/data-modeling/home/workspace");
	});
});
