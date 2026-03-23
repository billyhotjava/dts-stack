import assert from "node:assert/strict";
import test from "node:test";
import { resolveTemplateApplyOutcome } from "./transformCreateTemplate.helpers";

test("resolveTemplateApplyOutcome prefers rendered defaults and surfaces warnings/errors", () => {
	const outcome = resolveTemplateApplyOutcome({
		template: {
			id: "tpl-1",
			name: "Excel 模板",
			sourceCategory: "database",
			defaults: { sourceCategory: "database", syncMode: "full_refresh" },
			warnings: ["模板默认警告"],
		},
		currentSourceCategory: "",
		renderResult: {
			id: "tpl-1",
			name: "Excel 模板",
			renderedDefaults: { sourceCategory: "file", syncMode: "incremental" },
			warnings: ["渲染警告"],
			errors: ["缺少 fileName", "缺少 sourceDataSourceId", "ignored"],
		},
	});

	assert.deepEqual(outcome.defaults, { sourceCategory: "file", syncMode: "incremental" });
	assert.equal(outcome.infoMessage, "渲染警告");
	assert.equal(outcome.warningMessage, "模板参数待补：缺少 fileName；缺少 sourceDataSourceId");
	assert.equal(outcome.errorMessage, undefined);
	assert.equal(outcome.sourceCategory, "file");
	assert.equal(outcome.successMessage, "已应用模板：Excel 模板");
});

test("resolveTemplateApplyOutcome falls back to template defaults on render failure and keeps valid source category", () => {
	const outcome = resolveTemplateApplyOutcome({
		template: {
			id: "tpl-2",
			name: "数据库模板",
			sourceCategory: "database",
			defaults: { sourceCategory: "database", writerType: "postgresqlwriter" },
		},
		currentSourceCategory: "database",
		errorMessage: "模板服务超时",
	});

	assert.deepEqual(outcome.defaults, { sourceCategory: "database", writerType: "postgresqlwriter" });
	assert.equal(outcome.infoMessage, undefined);
	assert.equal(outcome.warningMessage, undefined);
	assert.equal(outcome.errorMessage, "模板服务超时");
	assert.equal(outcome.sourceCategory, "database");
	assert.equal(outcome.successMessage, "已应用模板：数据库模板");
});
