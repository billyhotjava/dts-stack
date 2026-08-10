// @vitest-environment jsdom

import { describe, expect, it } from "vitest";
import type { Sprint64BusinessProcess } from "@/api/sprint64GovernanceApi";
import { resolveBusinessProcessBinding, resolveDefaultBusinessCategoryId } from "./planningContextPolicyService";

const process = (id: string, lifecycleStatus: "ACTIVE" | "RETIRED" = "ACTIVE"): Sprint64BusinessProcess => ({
	id,
	version: 1,
	processId: `process_${id}`,
	domainId: "00000000-0000-0000-0000-000000000001",
	name: `过程 ${id}`,
	sourceType: "MANUAL",
	confirmed: lifecycleStatus === "ACTIVE",
	lifecycleStatus,
});

describe("planning context simplification", () => {
	it("infers a default category only when the confirmed plan scope is unique", () => {
		const one = [{ domainId: "category-1", confirmationStatus: "CONFIRMED", resolutionStatus: "AVAILABLE" }] as const;
		const two = [
			...one,
			{ domainId: "category-2", confirmationStatus: "CONFIRMED", resolutionStatus: "AVAILABLE" },
		] as const;

		expect(resolveDefaultBusinessCategoryId(null, one)).toBe("category-1");
		expect(resolveDefaultBusinessCategoryId(null, two)).toBeNull();
		expect(resolveDefaultBusinessCategoryId("category-2", two)).toBe("category-2");
	});

	it("auto-selects and hides the only active confirmed process", () => {
		const result = resolveBusinessProcessBinding("AUTO_SELECT_SINGLE", null, [
			process("one"),
			process("old", "RETIRED"),
		]);

		expect(result.processes.map((item) => item.id)).toEqual(["one"]);
		expect(result.selectedId).toBe("one");
		expect(result.showSelector).toBe(false);
		expect(result.message).toContain("自动绑定");
	});

	it("requires an explicit choice for multiple processes or managed mode", () => {
		const multiple = resolveBusinessProcessBinding("AUTO_SELECT_SINGLE", null, [process("one"), process("two")]);
		const managed = resolveBusinessProcessBinding("MANAGED", null, [process("one")]);

		expect(multiple.selectedId).toBeNull();
		expect(multiple.showSelector).toBe(true);
		expect(managed.selectedId).toBeNull();
		expect(managed.showSelector).toBe(true);
	});

	it("guides fact and atomic modeling to define a real process when none is active", () => {
		const result = resolveBusinessProcessBinding("AUTO_SELECT_SINGLE", null, [process("old", "RETIRED")]);

		expect(result.selectedId).toBeNull();
		expect(result.showSelector).toBe(false);
		expect(result.message).toContain("先定义");
	});
});
