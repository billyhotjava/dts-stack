import assert from "node:assert/strict";
import test from "node:test";
import { getCategoryAxisLabelLayout } from "./chartAxisLabelLayout";

test("getCategoryAxisLabelLayout keeps dense labels visible when rotation is enabled", () => {
	const layout = getCategoryAxisLabelLayout(11, 36);
	assert.equal(layout.rotate, 36);
	assert.equal(layout.step, 1);
	assert.equal(layout.textAnchor, "end");
	assert.ok(layout.bottomPadding >= 72);
});

test("getCategoryAxisLabelLayout samples labels when rotation is not enabled", () => {
	const layout = getCategoryAxisLabelLayout(14, 0);
	assert.equal(layout.rotate, 0);
	assert.ok(layout.step > 1);
	assert.equal(layout.textAnchor, "middle");
	assert.equal(layout.bottomPadding, 40);
});
