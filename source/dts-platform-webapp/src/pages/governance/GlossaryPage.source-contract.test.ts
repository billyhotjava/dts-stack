import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const MODELING_AUX_RESOURCE = readFileSync(
	new URL("../../../../dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/ModelingAuxResource.java", import.meta.url),
	"utf8",
);
const PLATFORM_API = readFileSync(new URL("../../api/platformApi.ts", import.meta.url), "utf8");
const PAGE = readFileSync(new URL("./GlossaryPage.tsx", import.meta.url), "utf8");

test("glossary backend exposes detail support endpoints", () => {
	assert.match(MODELING_AUX_RESOURCE, /@GetMapping\("\/glossary\/terms\/\{id\}\/versions"\)/);
	assert.match(MODELING_AUX_RESOURCE, /@GetMapping\("\/glossary\/terms\/\{id\}\/reviews"\)/);
	assert.match(MODELING_AUX_RESOURCE, /@GetMapping\("\/glossary\/terms\/\{id\}\/references"\)/);
	assert.match(MODELING_AUX_RESOURCE, /MODELING_GLOSSARY_VERSION_VIEW/);
	assert.match(MODELING_AUX_RESOURCE, /MODELING_GLOSSARY_REVIEW_VIEW/);
	assert.match(MODELING_AUX_RESOURCE, /MODELING_GLOSSARY_REFERENCE_VIEW/);
});

test("platformApi wires glossary versions, reviews, and references", () => {
	assert.match(PLATFORM_API, /listGlossaryTermVersions/);
	assert.match(PLATFORM_API, /listGlossaryTermReviews/);
	assert.match(PLATFORM_API, /getGlossaryTermReferences/);
	assert.match(PLATFORM_API, /\/modeling\/glossary\/terms\/\$\{id\}\/versions/);
	assert.match(PLATFORM_API, /\/modeling\/glossary\/terms\/\$\{id\}\/reviews/);
	assert.match(PLATFORM_API, /\/modeling\/glossary\/terms\/\$\{id\}\/references/);
});

test("glossary page detail drawer includes version and review context", () => {
	assert.match(PAGE, /listGlossaryTermVersions/);
	assert.match(PAGE, /listGlossaryTermReviews/);
	assert.match(PAGE, /loadDetailContext/);
	assert.match(PAGE, /当前版本/);
	assert.match(PAGE, /版本说明/);
	assert.match(PAGE, /所属部门/);
	assert.match(PAGE, /版本记录/);
	assert.match(PAGE, /评审记录/);
});

test("glossary page exports the current filter as package-compatible csv", () => {
	assert.match(PAGE, /GLOSSARY_EXPORT_HEADERS/);
	assert.match(PAGE, /term_code,term_name,aliases,definition,domain,owner_dept,owner,tags,version,status,version_notes/);
	assert.match(PAGE, /exportGlossaryCsv/);
	assert.match(PAGE, /text\/csv;charset=utf-8/);
	assert.match(PAGE, /01-business-terms\.csv/);
	assert.match(PAGE, /governance-glossary-export/);
	assert.match(PAGE, /导出CSV/);
	assert.match(PAGE, /DownloadOutlined/);
});
