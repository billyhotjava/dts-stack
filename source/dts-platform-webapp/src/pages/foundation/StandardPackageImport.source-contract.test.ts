import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const STANDARD_PACKAGE_RESOURCE = readFileSync(
	new URL(
		"../../../../dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/StandardPackageResource.java",
		import.meta.url,
	),
	"utf8",
);
const PLATFORM_API = readFileSync(new URL("../../api/platformApi.ts", import.meta.url), "utf8");
const PAGE = readFileSync(new URL("./StandardPackagePage.tsx", import.meta.url), "utf8");
const STATIC_ROUTES = readFileSync(
	new URL("../../routes/sections/dashboard/static-routes.tsx", import.meta.url),
	"utf8",
);
const DYNAMIC_RESOLVER = readFileSync(
	new URL("../../routes/sections/dashboard/dynamic-resolver.tsx", import.meta.url),
	"utf8",
);

test("standard package pipeline exposes preview/apply/rollback/runs endpoints", () => {
	assert.match(STANDARD_PACKAGE_RESOURCE, /\/api\/modeling\/standard-packages/);
	assert.match(STANDARD_PACKAGE_RESOURCE, /import\/preview/);
	assert.match(STANDARD_PACKAGE_RESOURCE, /import\/apply/);
	assert.match(STANDARD_PACKAGE_RESOURCE, /runs\/\{runId\}\/rollback/);
});

test("platformApi wires the standard package pipeline", () => {
	assert.match(PLATFORM_API, /previewStandardPackageImport/);
	assert.match(PLATFORM_API, /applyStandardPackageImport/);
	assert.match(PLATFORM_API, /listStandardPackageRuns/);
	assert.match(PLATFORM_API, /rollbackStandardPackageRun/);
	assert.match(PLATFORM_API, /\/modeling\/standard-packages\/import\/preview/);
});

test("standard package page implements the three-step wizard with history rollback", () => {
	assert.match(PAGE, /previewStandardPackageImport/);
	assert.match(PAGE, /applyStandardPackageImport/);
	assert.match(PAGE, /rollbackStandardPackageRun/);
	assert.match(PAGE, /downloadDataStandardPackageTemplate/);
	assert.match(PAGE, /下载标准包模板/);
	assert.match(PAGE, /standard-package-wizard-upload/);
	assert.match(PAGE, /standard-package-error-report-download/);
	assert.match(PAGE, /导入历史/);
});

test("builtin GB standard packs install through the same pipeline", () => {
	assert.match(STANDARD_PACKAGE_RESOURCE, /builtin\/\{code\}\/install/);
	assert.match(PLATFORM_API, /listBuiltinStandardPackages/);
	assert.match(PLATFORM_API, /installBuiltinStandardPackage/);
	assert.match(PAGE, /内置标准包/);
	assert.match(PAGE, /installBuiltinStandardPackage/);
	assert.match(PAGE, /builtin-package-/);
});

test("builtin pack resources ship the four GB packages", () => {
	const manifest = readFileSync(
		new URL(
			"../../../../dts-platform/src/main/resources/standard-packages/manifest.json",
			import.meta.url,
		),
		"utf8",
	);
	assert.match(manifest, /gbt-2261-gender/);
	assert.match(manifest, /gbt-4658-education/);
	assert.match(manifest, /gbt-2260-region/);
	assert.match(manifest, /common-data-elements/);
});

test("standard package page is routable statically and via menu resolver", () => {
	assert.match(STATIC_ROUTES, /foundation\/standard-package/);
	assert.match(DYNAMIC_RESOLVER, /"\/foundation\/standard-package": "\/pages\/foundation\/StandardPackagePage"/);
});
