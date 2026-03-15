import assert from "node:assert/strict";
import test from "node:test";
import { getGanttOwnerLabelPlacement } from "./projectGanttBoard.helpers";

test("getGanttOwnerLabelPlacement keeps wide tasks inside the bar", () => {
	assert.equal(getGanttOwnerLabelPlacement(8, 18), "inside");
});

test("getGanttOwnerLabelPlacement moves narrow tasks to the right when there is room", () => {
	assert.equal(getGanttOwnerLabelPlacement(12, 4), "outside-right");
});

test("getGanttOwnerLabelPlacement moves narrow tasks to the left near the end of the track", () => {
	assert.equal(getGanttOwnerLabelPlacement(88, 4), "outside-left");
});
