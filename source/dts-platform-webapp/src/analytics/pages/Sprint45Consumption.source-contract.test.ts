import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const STATIC_ROUTES_SOURCE = readFileSync(
	new URL("../../routes/sections/dashboard/static-routes.tsx", import.meta.url),
	"utf8",
);
const DATA_SOURCE = readFileSync(new URL("./DataPage.tsx", import.meta.url), "utf8");
const QUESTIONS_SOURCE = readFileSync(new URL("./CardsPage.tsx", import.meta.url), "utf8");
const DASHBOARDS_SOURCE = readFileSync(new URL("./DashboardsPage.tsx", import.meta.url), "utf8");
const SCREENS_SOURCE = readFileSync(new URL("./screens/ScreensPage.tsx", import.meta.url), "utf8");
const METRIC_LENS_SOURCE = readFileSync(new URL("./MetricLensPage.tsx", import.meta.url), "utf8");
const NL2SQL_SOURCE = readFileSync(new URL("./Nl2SqlEvalPage.tsx", import.meta.url), "utf8");

test("Sprint-45 metrics entry is owned by the prototype modeling surface", () => {
	assert.match(STATIC_ROUTES_SOURCE, /path: "data-modeling\/\*"/);
	assert.match(STATIC_ROUTES_SOURCE, /<DataModelingPage \/>/);
	for (const legacy of ["modeling/metric-workbench", "modeling/semantic/metrics", "modeling/semantic/models"]) {
		assert.match(STATIC_ROUTES_SOURCE, new RegExp(`path: "${legacy}"[\\s\\S]*LegacyDataModelingRedirect`));
	}
	assert.doesNotMatch(STATIC_ROUTES_SOURCE, /MetricsServiceFrame/);
	assert.doesNotMatch(STATIC_ROUTES_SOURCE, /<iframe/);
});

test("BI data catalog connects governed dataset selection to analysis creation", () => {
	for (const label of ["选择数据集", "已发布分析数据集", "查看契约", "创建分析", "契约校验值"]) {
		assert.match(DATA_SOURCE, new RegExp(label));
	}
	assert.doesNotMatch(DATA_SOURCE, /jdbcUrl|dbId=/);
	for (const label of ["新建问题", "运行", "保存", "生成图表"]) {
		assert.match(QUESTIONS_SOURCE, new RegExp(label));
	}
});

test("Sprint-45 dashboards and screens expose complete delivery lifecycle actions", () => {
	for (const label of ["新建看板", "添加图表", "发布", "分享", "删除"]) {
		assert.match(DASHBOARDS_SOURCE, new RegExp(label));
	}
	for (const label of ["新建大屏", "编辑", "预览", "发布", "复制", "导出", "删除"]) {
		assert.match(SCREENS_SOURCE, new RegExp(label));
	}
});

test("Sprint-45 enhanced analysis tools are clearly auxiliary and not the default consumption path", () => {
	for (const label of ["增强分析工具", "辅助工具"]) {
		assert.match(METRIC_LENS_SOURCE, new RegExp(label));
		assert.match(NL2SQL_SOURCE, new RegExp(label));
	}
	assert.match(STATIC_ROUTES_SOURCE, /bi\/metric-lens/);
	assert.match(STATIC_ROUTES_SOURCE, /bi\/nl2sql-eval/);
});
