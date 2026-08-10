// @vitest-environment jsdom

import { describe, expect, it } from "vitest";
import { isPathInMenuTree } from "./main";

describe("dashboard route reachability", () => {
	it("lets a granted modeling-space menu host its internal data-architecture views", () => {
		const menuPaths = new Set(["/data-modeling/planning/spaces"]);

		expect(isPathInMenuTree(menuPaths, "/data-architecture")).toBe(true);
	});

	it("does not make unrelated data-modeling routes globally reachable", () => {
		const menuPaths = new Set(["/data-modeling/planning/spaces"]);

		expect(isPathInMenuTree(menuPaths, "/data-modeling/dimensions/workbench")).toBe(false);
	});
});
