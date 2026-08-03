import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const read = (relativePath: string) =>
	readFileSync(new URL(relativePath, import.meta.url), "utf8");

const HEADER = read("../../layouts/dashboard/header.tsx");
const ROUTES = read("../../routes/sections/dashboard/static-routes.tsx");
const LAUNCHER = read("./HelpCenter.tsx");
const PAGE = read("./HelpCenterPage.tsx");
const TOPICS = read("./helpTopics.ts");
const JOURNEY_BAR = read("../../components/journey/JourneyContextBar.tsx");
const DATA_MODELING = read("../../pages/data-modeling/DataModelingPage.tsx");
const DIMENSIONAL_MODELING = read("../../pages/data-modeling/pages/DimensionalModelingWorkspace.tsx");

test("dashboard header exposes one accessible help launcher without a floating gear", () => {
	assert.match(HEADER, /import HelpCenter/);
	assert.match(HEADER, /<SearchBar \/>[\s\S]*<HelpCenter \/>[\s\S]*<AccountDropdown \/>/);
	assert.match(LAUNCHER, /aria-label="打开帮助"/);
	assert.match(LAUNCHER, /<SheetTitle/);
	assert.match(LAUNCHER, /<SheetDescription/);
	assert.doesNotMatch(LAUNCHER, /fixed\s+bottom-|Settings|onOpenAutoFocus/);
});

test("full help center is a static settings route rather than a business menu", () => {
	assert.match(ROUTES, /HelpCenterPage = lazy/);
	assert.match(ROUTES, /path: "settings\/help"[\s\S]*<HelpCenterPage \/>/);
	assert.match(PAGE, /data-testid="help-center-page"/);
	assert.match(PAGE, /aria-label="搜索帮助主题"/);
	assert.match(PAGE, /searchParams\.get\("q"\)/);
});

test("prototype modeling guidance lives in typed local topics", () => {
	for (const topicId of [
		"construction-planning",
		"model-center",
		"sql-modeling",
		"metric-workbench",
	]) {
		assert.match(TOPICS, new RegExp(`id: "${topicId}"`));
	}
	assert.match(TOPICS, /DAG 未就绪/);
	assert.match(TOPICS, /质量或发布门禁阻断/);
	assert.match(TOPICS, /数据输出 → 重建/);
	assert.match(TOPICS, /"\/data-modeling"/);
	assert.match(TOPICS, /"\/data-modeling\/planning"/);
	assert.match(TOPICS, /"\/data-modeling\/dimensions"/);
	assert.match(TOPICS, /"\/data-modeling\/metrics"/);
	assert.match(DATA_MODELING, /DimensionalModelingWorkspace/);
	assert.match(DIMENSIONAL_MODELING, /BackendPendingButton|disabled title="后台阶段接入"/);
	assert.doesNotMatch(`${DATA_MODELING}\n${DIMENSIONAL_MODELING}`, /pages\/modeling|SqlModelingPage/);
	assert.doesNotMatch(JOURNEY_BAR, /此页面是数据产品旅程|从工作台开始可获得完整的上下文与下一步引导/);
	assert.doesNotMatch(JOURNEY_BAR, /journey-join-enter|进入旅程/);
	assert.doesNotMatch(`${LAUNCHER}\n${PAGE}`, /dangerouslySetInnerHTML/);
});
