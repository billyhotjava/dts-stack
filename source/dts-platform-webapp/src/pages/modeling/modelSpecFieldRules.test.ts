import assert from "node:assert/strict";
import { existsSync } from "node:fs";
import test from "node:test";

const helperUrl = new URL("./modelSpecFieldRules.ts", import.meta.url);

test("duplicate field names are detected after trimming without rejecting distinct or blank drafts", async () => {
	assert.equal(existsSync(helperUrl), true, "model field rules helper is missing");
	const { hasDuplicateModelFieldNames } = await import(helperUrl.href);

	assert.equal(hasDuplicateModelFieldNames([{ name: "order_id" }, { name: " order_id " }]), true);
	assert.equal(hasDuplicateModelFieldNames([{ name: "order_id" }, { name: "amount" }]), false);
	assert.equal(hasDuplicateModelFieldNames([{ name: "" }, { name: "   " }]), false);
	assert.equal(hasDuplicateModelFieldNames(null), false);
});

test("field-name parsing accepts Chinese commas and newlines while trimming and deduplicating", async () => {
	assert.equal(existsSync(helperUrl), true, "model field rules helper is missing");
	const { parseModelFieldNames } = await import(helperUrl.href);

	assert.deepEqual(parseModelFieldNames?.(" order_id， event_id\norder_id,\n amount "), [
		"order_id",
		"event_id",
		"amount",
	]);
});

test("saved fields and current grain keys are protected from rename or deletion", async () => {
	assert.equal(existsSync(helperUrl), true, "model field rules helper is missing");
	const { isProtectedModelFieldName } = await import(helperUrl.href);

	assert.equal(isProtectedModelFieldName("order_id", ["order_id"], ""), true);
	assert.equal(isProtectedModelFieldName("event_id", [], " order_id, event_id "), true);
	assert.equal(isProtectedModelFieldName("event_id", [], " order_id， event_id "), true);
	assert.equal(isProtectedModelFieldName("snapshot_date", [], "order_id\nsnapshot_date"), true);
	assert.equal(isProtectedModelFieldName("new_attribute", ["order_id"], "order_id"), false);
	assert.equal(isProtectedModelFieldName("  ", ["order_id"], "order_id"), false);
});
