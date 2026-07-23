import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const editor = readFileSync(new URL("./components/ModelSpecEditorFields.tsx", import.meta.url), "utf8");

test("the fact model form uses formal property names and explains every visible input", () => {
	assert.match(editor, /if \(modelType === "FACT"\) return "粒度声明"/);
	assert.match(editor, /placeholder="填写便于业务人员识别的模型名称，例如：客户事件明细"/);
	assert.match(editor, /placeholder="说明模型服务的分析主题、报表或业务问题"/);
	assert.match(editor, /每条记录对应的业务事实，例如：每行记录一次客户事件/);
	assert.match(editor, /唯一标识记录的字段，多个用逗号分隔/);
	assert.match(editor, /name="factShape" label="事实形态">[\s\S]{0,300}placeholder="选择数据随业务变化的记录方式"/);
	assert.match(
		editor,
		/name="timeSemanticsType" label="业务时间">[\s\S]{0,300}placeholder="选择记录对应的业务时间含义"/,
	);
	assert.doesNotMatch(
		editor,
		/name="generationStrategyType" label="生成策略">[\s\S]{0,300}placeholder="选择数据随业务变化的记录方式"/,
	);
	assert.match(editor, /承载业务时间的字段，多个用逗号分隔/);
	assert.match(editor, /placeholder="选择用于分类、筛选和汇总分析的维度（可选）"/);
});
