import { describe, expect, it } from "vitest";
import type { MenuTree } from "#/entity";
import { hasDataModelingMenuGrant } from "./useDataModelingMenuGrant";

const menus = [
	{
		path: "/data-modeling",
		children: [{ path: "/data-modeling/planning/business-categories" }, { path: "/data-modeling/planning/layers" }],
	},
] as MenuTree[];

describe("hasDataModelingMenuGrant", () => {
	it("opens actions only for the exact leaf menu returned by dts-admin", () => {
		expect(hasDataModelingMenuGrant(menus, "/data-modeling/planning/business-categories")).toBe(true);
		expect(hasDataModelingMenuGrant(menus, "/data-modeling/planning/domains")).toBe(false);
	});

	it("does not treat the visible parent menu as a grant for every child page", () => {
		expect(hasDataModelingMenuGrant([{ path: "/data-modeling" }] as MenuTree[], "/data-modeling/planning/layers")).toBe(
			false,
		);
	});

	it("honors the externalLink metadata shape returned by the dts-admin menu tree", () => {
		expect(
			hasDataModelingMenuGrant(
				[
					{
						path: "data-modeling",
						children: [
							{
								path: "planning/business-categories",
								metadata: JSON.stringify({ externalLink: "/data-modeling/planning/business-categories" }),
							},
						],
					},
				] as MenuTree[],
				"/data-modeling/planning/business-categories",
			),
		).toBe(true);
	});
});
