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
		new URL("../../../../dts-platform/src/main/resources/standard-packages/manifest.json", import.meta.url),
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

test("standard package page returns to the standard owner that launched it", () => {
	assert.match(PAGE, /useSearchParams/);
	assert.match(PAGE, /useNavigate/);
	assert.match(PAGE, /getStandardPackageSourceMeta/);
	assert.match(PAGE, /buildStandardPackageReturnRoute/);
	assert.match(PAGE, /sourceMeta\.label/);
	assert.match(PAGE, /standard-package-return-\$\{sourceMeta\.source\}/);
});

test("standard package success returns to the global owner without creating a session draft", () => {
	assert.match(PAGE, /buildStandardPackageReturnRoute/);
	assert.match(PAGE, /applied:\s*true/);
	assert.match(PAGE, /appliedSourceReturnRoute/);
	assert.doesNotMatch(PAGE, /JourneyContextBar/);
	assert.doesNotMatch(PAGE, /bindingDraft=1|查看数据元并生成落标草稿/);
});

test("standard package preview and apply report measurement units as the sixth object type", () => {
	assert.match(PAGE, /06-measurement-units\.csv/);
	assert.match(PAGE, /MEASUREMENT_UNIT:\s*"计量单位"/);
	assert.match(PAGE, /共 6 个 CSV/);
});
