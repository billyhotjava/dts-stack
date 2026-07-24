import assert from "node:assert/strict";
import test from "node:test";
import {
	canRetireDimensionDefinition,
	createDimensionDefinitionIdempotencyKey,
	dimensionCatalogEmptyText,
	dimensionDefinitionErrorMessage,
	dimensionDefinitionStatusLabel,
	isDimensionDefinitionVersionConflict,
	shouldApplyDimensionDefinitionReload,
} from "./dimensionCatalogViewState.ts";

test("dimension catalog distinguishes first-use, read-only and search empty states", () => {
	assert.equal(
		dimensionCatalogEmptyText({ totalCount: 0, visibleCount: 0, search: "", canEdit: true }),
		"还没有维度，点击“登记维度”开始",
	);
	assert.equal(
		dimensionCatalogEmptyText({ totalCount: 0, visibleCount: 0, search: "", canEdit: false }),
		"还没有可浏览的维度",
	);
	assert.equal(
		dimensionCatalogEmptyText({ totalCount: 2, visibleCount: 0, search: "组织", canEdit: true }),
		"未找到匹配维度，请调整搜索条件",
	);
});

test("dimension catalog maps definition lifecycle and recoverable write failures to customer copy", () => {
	assert.equal(dimensionDefinitionStatusLabel("DRAFT"), "草稿");
	assert.equal(dimensionDefinitionStatusLabel("CURRENT"), "现行");
	assert.equal(dimensionDefinitionStatusLabel("RETIRED"), "已退役");
	assert.equal(canRetireDimensionDefinition("DRAFT", true), false);
	assert.equal(canRetireDimensionDefinition("CURRENT", true), true);
	assert.equal(canRetireDimensionDefinition("CURRENT", false), false);
	assert.match(
		dimensionDefinitionErrorMessage({
			response: { status: 409, data: { code: "DIMENSION_DEFINITION_REVISION_CONFLICT" } },
		}),
		/当前输入已保留/,
	);
	assert.match(
		dimensionDefinitionErrorMessage({
			response: { status: 409, data: { code: "DIMENSION_DEFINITION_NAME_CONFLICT" } },
		}),
		/同名维度/,
	);
	assert.match(dimensionDefinitionErrorMessage({ response: { status: 403 } }), /没有维护维度的权限/);
	assert.equal(
		isDimensionDefinitionVersionConflict({
			response: { status: 409, data: { code: "DIMENSION_DEFINITION_REVISION_CONFLICT" } },
		}),
		true,
	);
	assert.equal(
		isDimensionDefinitionVersionConflict({
			response: { status: 409, data: { code: "DIMENSION_DEFINITION_NAME_CONFLICT" } },
		}),
		false,
	);
	assert.equal(isDimensionDefinitionVersionConflict({ response: { status: 422 } }), false);
});

test("dimension catalog creates a browser-safe idempotency key", () => {
	assert.equal(
		createDimensionDefinitionIdempotencyKey({ randomUUID: () => "10000000-0000-4000-8000-000000000002" }),
		"10000000-0000-4000-8000-000000000002",
	);
	assert.equal(
		createDimensionDefinitionIdempotencyKey({
			getRandomValues: (bytes) => {
				bytes.fill(0);
				return bytes;
			},
		}),
		"00000000-0000-4000-8000-000000000000",
	);
});

test("dimension reload response applies only to the still-open definition request", () => {
	assert.equal(shouldApplyDimensionDefinitionReload(3, 3, "dimension-a", "dimension-a"), true);
	assert.equal(shouldApplyDimensionDefinitionReload(2, 3, "dimension-a", "dimension-a"), false);
	assert.equal(shouldApplyDimensionDefinitionReload(3, 3, "dimension-a", "dimension-b"), false);
	assert.equal(shouldApplyDimensionDefinitionReload(3, 3, "dimension-a", undefined), false);
});
