import { describe, expect, it } from "vitest";
import type { PortalMenuItem } from "@/admin/types";
import {
	collectSelectableMenuIds,
	mergeMenuRoles,
	resolveMenuSelectionState,
} from "./portal-menu-bulk";

describe("portal menu bulk helpers", () => {
	const tree: PortalMenuItem[] = [
		{
			id: 1,
			name: "root",
			path: "root",
			children: [
				{ id: 11, name: "enabled-leaf", path: "root/enabled", allowedRoles: ["ROLE_A"] },
				{ id: 12, name: "disabled-leaf", path: "root/disabled", deleted: true, allowedRoles: ["ROLE_A"] },
				{
					id: 13,
					name: "folder",
					path: "root/folder",
					children: [{ id: 131, name: "nested-leaf", path: "root/folder/nested", allowedRoles: [] }],
				},
			],
		},
	];

	it("collects only enabled leaf menus for bulk selection", () => {
		expect(collectSelectableMenuIds(tree)).toEqual([11, 131]);
	});

	it("resolves folder selection state from descendant leaves", () => {
		expect(resolveMenuSelectionState(tree[0], new Set([11]))).toBe("indeterminate");
		expect(resolveMenuSelectionState(tree[0], new Set([11, 131]))).toBe("checked");
		expect(resolveMenuSelectionState(tree[0], new Set())).toBe("unchecked");
	});

	it("merges roles by append remove and replace modes", () => {
		expect(mergeMenuRoles(["ROLE_A"], ["ROLE_B"], "APPEND")).toEqual(["ROLE_A", "ROLE_B"]);
		expect(mergeMenuRoles(["ROLE_A", "ROLE_B"], ["ROLE_A"], "REMOVE")).toEqual(["ROLE_B"]);
		expect(mergeMenuRoles(["ROLE_A", "ROLE_B"], ["ROLE_C"], "REPLACE")).toEqual(["ROLE_C"]);
	});
});
