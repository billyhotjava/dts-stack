import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const PANEL_SOURCE = readFileSync(new URL("./AssetActionMatrixPanel.tsx", import.meta.url), "utf8");
const PAGE_SOURCE = readFileSync(new URL("./data-security.tsx", import.meta.url), "utf8");
const API_SOURCE = readFileSync(new URL("../../api/services/iamPolicyService.ts", import.meta.url), "utf8");

test("operation permission matrix exposes the complete protocol action set", () => {
	for (const action of [
		"CREATE",
		"DELETE",
		"UPDATE",
		"COPY",
		"IMPORT",
		"EXPORT",
		"ARCHIVE",
		"DESTROY",
	]) {
		assert.match(PANEL_SOURCE, new RegExp(`"${action}"`));
	}
	assert.match(PAGE_SOURCE, /AssetActionMatrixPanel/);
	assert.match(PAGE_SOURCE, /操作权限矩阵/);
});

test("matrix changes are submitted as pending requests and never directly saved as effective policy", () => {
	assert.match(API_SOURCE, /\/iam\/action-policies\/matrix/);
	assert.match(API_SOURCE, /\/iam\/action-policies\/requests/);
	assert.match(API_SOURCE, /requestAssetActionPolicyChange/);
	assert.match(API_SOURCE, /decideAssetActionPolicyRequest/);
	assert.doesNotMatch(API_SOURCE, /put\(\{ url: "\/iam\/action-policies/);
	assert.match(PANEL_SOURCE, /PENDING|待审批/);
	assert.match(PANEL_SOURCE, /只提交发生变化的动作/);
});

