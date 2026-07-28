import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const SOURCE = readFileSync(new URL("./search-bar.tsx", import.meta.url), "utf8");

test("global search includes searchable DTS help topics", () => {
	assert.match(SOURCE, /HELP_TOPICS, buildHelpTopicHref/);
	assert.match(SOURCE, /heading="帮助主题"/);
	assert.match(SOURCE, /\[topic\.id, topic\.title, topic\.summary, \.\.\.topic\.keywords\]\.join\(" "\)/);
	assert.match(SOURCE, /handleSelect\(buildHelpTopicHref\(topic\.id\)\)/);
});

test("highlighting treats the search query as literal text", () => {
	assert.match(SOURCE, /const escapeRegExp =/);
	assert.match(SOURCE, /escapeRegExp\(query\)/);
});

test("global search controls are presented in Chinese", () => {
	assert.match(SOURCE, /aria-label="搜索页面与帮助"/);
	assert.match(SOURCE, /title="全局搜索"/);
	assert.match(SOURCE, /heading="页面导航"/);
	assert.match(SOURCE, />切换</);
	assert.match(SOURCE, />选择</);
	assert.match(SOURCE, />关闭</);
});
