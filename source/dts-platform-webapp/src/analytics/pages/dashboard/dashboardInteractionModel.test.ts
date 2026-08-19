import assert from "node:assert/strict";
import test from "node:test";
import { buildTargetedCrossFilterParams, mapWithConcurrency } from "./dashboardInteractionModel.ts";

test("cross-filter applies only to configured targets and never to its source", () => {
	const active = {
		sourceCardId: 10,
		column: "department",
		value: "D1",
		targetCardIds: [20],
	};

	assert.deepEqual(buildTargetedCrossFilterParams(active, 10, []), []);
	assert.deepEqual(buildTargetedCrossFilterParams(active, 30, []), []);
	assert.deepEqual(buildTargetedCrossFilterParams(active, 20, []), [{
		type: "category",
		value: "D1",
		target: ["dimension", ["field", "department", null]],
	}]);
});

test("dashboard query helper never exceeds its concurrency budget", async () => {
	let active = 0;
	let peak = 0;
	const values = await mapWithConcurrency([1, 2, 3, 4, 5, 6], 2, async (value) => {
		active += 1;
		peak = Math.max(peak, active);
		await new Promise((resolve) => setTimeout(resolve, 2));
		active -= 1;
		return value * 2;
	});

	assert.equal(peak, 2);
	assert.deepEqual(values, [2, 4, 6, 8, 10, 12]);
});
