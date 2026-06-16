import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const PAGE_SOURCE = readFileSync(new URL("./DataManagementWorkbenchPage.tsx", import.meta.url), "utf8");

test("Sprint-45 workbench exposes product journey actions from source to operations", () => {
	const expectedActions = [
		"配置数据源",
		"处理阻断项",
		"治理检查",
		"查看资产",
		"创建报表",
		"发布数据 API",
		"查看运行",
	];

	for (const action of expectedActions) {
		assert.match(PAGE_SOURCE, new RegExp(action));
	}

	const expectedRoutes = [
		"/foundation/data-sources",
		"/workbench/todo",
		"/governance/quality",
		"/catalog/assets",
		"/bi/dashboards",
		"/services/apis",
		"/ops/overview",
	];

	for (const route of expectedRoutes) {
		assert.match(PAGE_SOURCE, new RegExp(route.replace(/\//g, "\\/")));
	}
});
