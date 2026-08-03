import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const METADATA_PAGE = readFileSync(new URL("./MetadataPage.tsx", import.meta.url), "utf8");
const DATASETS_PAGE = readFileSync(new URL("./DatasetsPage.tsx", import.meta.url), "utf8");
const ZH_LOCALE = readFileSync(new URL("../../locales/lang/zh_CN/sys.json", import.meta.url), "utf8");
const EN_LOCALE = readFileSync(new URL("../../locales/lang/en_US/sys.json", import.meta.url), "utf8");

test("metadata collection surface is named as source structure collection", () => {
	assert.match(METADATA_PAGE, /title="数据源结构采集"/);
	assert.match(METADATA_PAGE, /请先完成数据源结构采集或检查采集服务连接。/);
	assert.match(DATASETS_PAGE, /可能还未完成数据源结构采集/);
	assert.match(ZH_LOCALE, /"catalogMetadata":\s*"数据源结构采集"/);
	assert.match(EN_LOCALE, /"catalogMetadata":\s*"Source structure collection"/);
	assert.doesNotMatch(METADATA_PAGE, /元数据采集/);
	assert.doesNotMatch(DATASETS_PAGE, /元数据采集/);
});
