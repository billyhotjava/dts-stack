import assert from "node:assert/strict";
import test from "node:test";
import { dimensionCatalogEmptyText } from "./dimensionCatalogViewState.ts";

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
