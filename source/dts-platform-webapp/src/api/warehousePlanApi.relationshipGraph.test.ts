import { afterEach, describe, expect, it, vi } from "vitest";

const { get } = vi.hoisted(() => ({
	get: vi.fn(),
}));

vi.mock("./apiClient", () => ({ default: { get } }));

import { getWarehousePlanRelationshipGraph } from "./warehousePlanApi";

describe("warehouse plan relationship graph API", () => {
	afterEach(() => vi.clearAllMocks());

	it("passes the opaque keyset cursor with the active filter", async () => {
		get.mockResolvedValue({
			planId: "plan-1",
			nodes: [],
			edges: [],
			truncated: false,
			nextCursor: null,
		});

		await getWarehousePlanRelationshipGraph("plan-1", {
			kind: "MODEL",
			query: "customer",
			limit: 500,
			cursor: "opaque-composite-cursor-v1",
		});

		expect(get).toHaveBeenCalledWith(
			expect.objectContaining({
				url: "/modeling/warehouse-plans/plan-1/relationship-graph",
				params: {
					kind: "MODEL",
					query: "customer",
					limit: 500,
					cursor: "opaque-composite-cursor-v1",
				},
			}),
		);
	});
});
