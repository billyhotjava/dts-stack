import assert from "node:assert/strict";
import test from "node:test";
import { handleModelSpecFormValidationError, modelSpecIssueFieldPath } from "./modelSpecIssueFieldPath.ts";

const fields = [{ name: "account_code" }, { name: "account_name" }];

test("model issue locations resolve exact field rows and preserve top-level contract mappings", () => {
	assert.equal(modelSpecIssueFieldPath("grain", fields), "grainStatement");
	assert.equal(modelSpecIssueFieldPath("fields", fields), "fields");
	assert.deepEqual(modelSpecIssueFieldPath("fields.account_code.dimensionAttributeCode", fields), [
		"fields",
		0,
		"dimensionAttributeCode",
	]);
	assert.deepEqual(modelSpecIssueFieldPath("fields[1].role", fields), ["fields", 1, "role"]);
	assert.deepEqual(modelSpecIssueFieldPath("fields[account_name].displayName", fields), ["fields", 1, "displayName"]);
});

test("unresolvable field names fail visibly at the field collection instead of another row", () => {
	assert.equal(modelSpecIssueFieldPath("fields.unknown.dimensionAttributeCode", fields), "fields");
});

test("client validation exposes recovery copy and targets the exact first field", () => {
	const messages: string[] = [];
	const paths: Array<Array<string | number>> = [];
	const handled = handleModelSpecFormValidationError(
		{ errorFields: [{ name: ["fields", 1, "redundancySourceRef"] }] },
		{ scrollToField: (name) => paths.push(name) },
		(message) => messages.push(message),
	);

	assert.equal(handled, true);
	assert.deepEqual(messages, ["请补齐标红字段后再保存"]);
	assert.deepEqual(paths, [["fields", 1, "redundancySourceRef"]]);
	assert.equal(
		handleModelSpecFormValidationError(new Error("network"), { scrollToField: () => undefined }, () => undefined),
		false,
	);
});
