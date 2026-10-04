import { describe, expect, it } from "vitest";
import { DATA_ARCHITECTURE_VIEWS, dataArchitecturePath } from "@/pages/data-architecture/navigation";
import { isNavItemActive } from "./is-active";

describe("S10DC-95 planning menu selection", () => {
	it.each(DATA_ARCHITECTURE_VIEWS)("selects only %s while keeping the parent active", (view) => {
		const search = `?active=record&view=${view}&returnPlanId=plan`;
		const selected = DATA_ARCHITECTURE_VIEWS.filter((candidate) =>
			isNavItemActive("/data-architecture", dataArchitecturePath(candidate), false, search),
		);
		expect(selected).toEqual([view]);
		expect(isNavItemActive("/data-architecture", "/data-architecture", true, search)).toBe(true);
	});
	it.each(["", "?view=unknown", "?active=record"])("selects the default view for %s", (search) => {
		expect(isNavItemActive("/data-architecture/", dataArchitecturePath("business-domains"), false, search)).toBe(true);
		expect(isNavItemActive("/data-architecture", dataArchitecturePath("processes"), false, search)).toBe(false);
	});
	it("preserves exact leaf matching and path-segment parent matching", () => {
		expect(isNavItemActive("/workbench/todo", "/workbench", false)).toBe(false);
		expect(isNavItemActive("/workbench/todo", "/workbench", true)).toBe(true);
		expect(isNavItemActive("/workbench/todo/", "/workbench/todo", false, "?filter=mine")).toBe(true);
		expect(isNavItemActive("/workbench-other", "/workbench", true)).toBe(false);
		expect(isNavItemActive("/workbench", "", false)).toBe(false);
	});
});
