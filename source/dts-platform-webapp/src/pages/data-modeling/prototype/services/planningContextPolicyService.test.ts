// @vitest-environment jsdom

import { describe, expect, it } from "vitest";
import type { Sprint64BusinessProcess } from "@/api/sprint64GovernanceApi";
import { resolveBusinessProcessBinding } from "./planningContextPolicyService";

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

describe("business process selection", () => {
	it("auto-selects and hides the only active confirmed process", () => {
		const result = resolveBusinessProcessBinding(null, [process("one"), process("old", "RETIRED")]);

		expect(result.processes.map((item) => item.id)).toEqual(["one"]);
		expect(result.selectedId).toBe("one");
		expect(result.showSelector).toBe(false);
		expect(result.message).toContain("自动绑定");
	});

	it("requires an explicit choice when multiple processes are active", () => {
		const multiple = resolveBusinessProcessBinding(null, [process("one"), process("two")]);

		expect(multiple.selectedId).toBeNull();
		expect(multiple.showSelector).toBe(true);
	});

	it("guides fact and atomic modeling to define a real process when none is active", () => {
		const result = resolveBusinessProcessBinding(null, [process("old", "RETIRED")]);

		expect(result.selectedId).toBeNull();
		expect(result.showSelector).toBe(false);
		expect(result.message).toContain("先定义");
	});
});
