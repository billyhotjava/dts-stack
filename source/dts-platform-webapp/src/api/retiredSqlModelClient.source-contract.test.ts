import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const platformApi = readFileSync(new URL("./platformApi.ts", import.meta.url), "utf8");
const transformBootstrap = readFileSync(
	new URL("../pages/explore/etl/hooks/useTransformBootstrap.ts", import.meta.url),
	"utf8",
);

test("retired SQL-model client has no live consumer and ETL uses canonical ModelSpec identity", () => {
	assert.doesNotMatch(platformApi, /\/modeling\/sql-models/);
	assert.doesNotMatch(platformApi, /(?:list|get|create|update|delete|import)SqlModel/);
	assert.match(transformBootstrap, /listModelSpecs/);
	assert.match(transformBootstrap, /implementationPolicy\?\.physicalName/);
	assert.doesNotMatch(transformBootstrap, /listSqlModels/);
});
