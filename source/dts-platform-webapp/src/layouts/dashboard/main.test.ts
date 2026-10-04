// @vitest-environment jsdom

import { describe, expect, it } from "vitest";
import { isPathInMenuTree } from "./main";

describe("dashboard route reachability", () => {
	it("matches query-backed menu entries by their routed pathname", () => {
		const menuPaths = new Set(["/data-architecture?view=business-domains"]);

		expect(isPathInMenuTree(menuPaths, "/data-architecture")).toBe(true);
	});

	it("lets a granted modeling-space menu host its internal data-architecture views", () => {
		const menuPaths = new Set(["/data-modeling/planning/spaces"]);

		expect(isPathInMenuTree(menuPaths, "/data-architecture")).toBe(true);
	});

	it("does not make unrelated data-modeling routes globally reachable", () => {
		const menuPaths = new Set(["/data-modeling/planning/spaces"]);

		expect(isPathInMenuTree(menuPaths, "/data-modeling/dimensions/workbench")).toBe(false);
	});

	it("lets the canonical governed-indicator menu host the retired BI metrics route", () => {
		const menuPaths = new Set(["/data-modeling/metrics/atomic"]);

		expect(isPathInMenuTree(menuPaths, "/bi/metrics")).toBe(true);
	});

	it("does not expose the retired BI metrics route without indicator menu access", () => {
		const menuPaths = new Set(["/bi/questions"]);

		expect(isPathInMenuTree(menuPaths, "/bi/metrics")).toBe(false);
	});

	it("lets the analysis-card menu host its semantic create and edit routes", () => {
		const menuPaths = new Set(["/bi/questions"]);

		expect(isPathInMenuTree(menuPaths, "/bi/card/new")).toBe(true);
		expect(isPathInMenuTree(menuPaths, "/bi/card/card-1/edit")).toBe(true);
	});

	it("does not expose semantic card editors without analysis-card menu access", () => {
		const menuPaths = new Set(["/bi/dashboards"]);

		expect(isPathInMenuTree(menuPaths, "/bi/card/new")).toBe(false);
	});
});
