import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const PAGE_SOURCE = readFileSync(new URL("./CardsPage.tsx", import.meta.url), "utf8");
const ROUTES_SOURCE = readFileSync(
	new URL("../../routes/sections/dashboard/static-routes.tsx", import.meta.url),
	"utf8",
);

test("analysis workspace lists and archives governed analyses only", () => {
	assert.match(PAGE_SOURCE, /listAnalyses/);
	assert.match(PAGE_SOURCE, /archiveAnalysis/);
	assert.match(PAGE_SOURCE, /lifecycleStatus/);
	assert.doesNotMatch(PAGE_SOURCE, /listCards|createCard|deleteCard/);
	assert.doesNotMatch(PAGE_SOURCE, /BatchImportCardsModal|CollectionTree|MoveToCollectionModal/);
});

test("analysis creation starts from the published dataset catalog", () => {
	assert.match(PAGE_SOURCE, /to="\/bi\/data"/);
	assert.match(PAGE_SOURCE, /从已发布数据集创建分析/);
	assert.doesNotMatch(PAGE_SOURCE, /to="\/bi\/card\/new"|批量导入 SQL/);
});

test("canonical question view and edit routes both use the governed analysis editor", () => {
	assert.match(ROUTES_SOURCE, /path: "bi\/questions\/:id"[\s\S]*?<AnalysisEditorPage/);
	assert.match(ROUTES_SOURCE, /path: "bi\/questions\/:id\/edit"[\s\S]*?<AnalysisEditorPage/);
	assert.doesNotMatch(ROUTES_SOURCE, /path: "bi\/questions\/:id"[\s\S]*?<CardDetailPage/);
});
