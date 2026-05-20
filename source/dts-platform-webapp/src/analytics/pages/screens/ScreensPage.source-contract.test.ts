import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const screensPagePath = new URL("./ScreensPage.tsx", import.meta.url);

test("ScreensPage does not depend on removed legacy page shell classes", async () => {
	const source = await readFile(screensPagePath, "utf8");

	assert.equal(source.includes('className="page-container"'), false);
	assert.equal(source.includes('className="page-content"'), false);
});

test("ScreensPage opens editor in a new window from the management list", async () => {
	const source = await readFile(screensPagePath, "utf8");

	assert.match(
		source,
		/window\.open\(resolveRouteForOpen\(`\/bi\/screens\/\$\{id\}\/edit`\), ["']_blank["'], ["']noopener,noreferrer["']\)/,
	);
});

test("ScreensPage hides management actions when row permissions do not allow them", async () => {
	const source = await readFile(screensPagePath, "utf8");

	assert.match(source, /rowPermissions\.canEdit \? \(/);
	assert.match(source, /rowPermissions\.canManage \? \(/);
	assert.match(source, /rowPermissions\.canDelete \? \(/);
});

// Sprint-24 F3 回归：导入 JSON 缺失合法 classification 时必须先弹 IntakeModal，
// 否则后端会以 400 拒绝（must be one of PUBLIC/INTERNAL/SECRET/CONFIDENTIAL）。
test("ScreensPage import flow gates on classification before calling createScreen", async () => {
	const source = await readFile(screensPagePath, "utf8");

	// 必须读取并校验 importPreview.parsedSpec.classification
	assert.match(source, /importPreview\.parsedSpec[^)]*\)\.classification/);
	// 合法集合必须覆盖后端要求的四个枚举值
	assert.match(source, /["']PUBLIC["'],\s*["']INTERNAL["'],\s*["']SECRET["'],\s*["']CONFIDENTIAL["']/);
	// 缺失合法密级时必须打开 importIntakeOpen，而不是直接调用 createScreen
	assert.match(source, /setImportIntakeOpen\(true\)/);
	// import-flow 专用的 IntakeModal 必须存在
	assert.match(source, /open=\{importIntakeOpen\}/);
});

// 大屏列表分页：默认 10 条/页，过滤变化重置到第 1 页，越界自动 clamp，
// 渲染时切片为 pagedScreens 而不是直接渲染 sortedScreens。
test("ScreensPage paginates the list at 10 rows per page by default", async () => {
	const source = await readFile(screensPagePath, "utf8");

	// 默认页大小是 10
	assert.match(source, /useState\(10\)/);
	// 切片后用 pagedScreens 渲染，而不是 sortedScreens 直出
	assert.match(source, /pagedScreens\.map\(/);
	assert.match(source, /sortedScreens\.slice\(start,\s*start\s*\+\s*pageSize\)/);
	// 过滤条件变化时回到第 1 页
	assert.match(source, /setCurrentPage\(1\)[\s\S]*?\[searchKeyword,\s*publishFilter\]/);
	// 越界自动 clamp
	assert.match(source, /Math\.min\(currentPage,\s*totalPages\)/);
	// 分页器 UI 存在且 total > 0 时显示
	assert.match(source, /totalCount\s*>\s*0\s*\?[\s\S]*?<Pagination/);
	assert.match(source, /data-testid="analytics-screen-pagination"/);
});

// Sprint-24 F4 回归：大屏密级合规盘点入口必须对治理角色开放，
// 与后端 MetabaseAuth.SCREEN_AUDITOR_ROLES 保持一致。
test("ScreensPage unclassified audit gate accepts all governance roles", async () => {
	const source = await readFile(screensPagePath, "utf8");

	// 五个非 superuser 治理角色必须全部出现在前端守卫里
	assert.match(source, /["']OP_ADMIN["']/);
	assert.match(source, /["']INST_DATA_OWNER["']/);
	assert.match(source, /["']DEPT_DATA_OWNER["']/);
	assert.match(source, /["']INST_LEADER["']/);
	assert.match(source, /["']DEPT_LEADER["']/);
	// superuser 也必须放行
	assert.match(source, /["']SUPERUSER["']/);
	// 按钮文案改成「大屏盘点」（不是「裸屏盘点」）
	assert.match(source, /大屏盘点/);
	assert.equal(source.includes("裸屏"), false);
});

test("ScreensPage classifies screens by governance domain tree with an uncategorized bucket", async () => {
	const source = await readFile(screensPagePath, "utf8");

	assert.match(source, /getDomainTree/);
	assert.match(source, /UNASSIGNED_DOMAIN_KEY\s*=\s*['"]__UNASSIGNED__['"]/);
	assert.match(source, /selectedDomain/);
	assert.match(source, /domainId/);
	assert.match(source, /全部大屏/);
	assert.match(source, /未归类/);
	assert.match(source, /Tree/);
	assert.match(source, /collectDomainIds/);
});

test("ScreensPage uses a single intake modal for create and screen package import metadata", async () => {
	const source = await readFile(screensPagePath, "utf8");

	assert.match(source, /mode=\{['"]create['"]\}/);
	assert.match(source, /mode=\{['"]import['"]\}/);
	assert.match(source, /domainId:\s*payload\.domainId/);
	assert.match(source, /file:\s*payload\.file/);
	assert.match(source, /defaultDomainId/);
	assert.equal(source.includes('type="file"\n'), false);
	assert.equal(source.includes("importInputRef"), false);
});

test("ScreensPage exports and imports portable screen zip packages", async () => {
	const source = await readFile(screensPagePath, "utf8");

	assert.match(source, /buildScreenPackageZip/);
	assert.match(source, /parseScreenImportFile/);
	assert.match(source, /handleExportScreenPackage/);
	assert.match(source, /\.zip`/);
	assert.match(source, /导出大屏/);
	assert.match(source, /大屏包/);
	assert.match(source, /restoredResourceCount/);
	assert.equal(source.includes("handleExportJson"), false);
	assert.equal(source.includes("导出 JSON"), false);
});

test("ScreensPage keeps the domain classifier and table inside the page frame", async () => {
	const source = await readFile(screensPagePath, "utf8");

	assert.match(source, /className="flex min-h-\[620px\] gap-4 overflow-hidden"/);
	assert.match(source, /className="min-w-0 flex-1 space-y-4 overflow-hidden"/);
	assert.match(source, /className="max-w-full overflow-x-auto rounded-lg border border-border-default"/);
	assert.match(source, /<colgroup>/);
	assert.match(source, /className="w-full min-w-\[1080px\] table-fixed border-collapse text-sm"/);
	// 操作列固定在右侧(sticky) + 按钮横排不换行(flex-nowrap)
	assert.match(source, /flex flex-nowrap items-center justify-end gap-1\.5/);
	assert.match(source, /sticky right-0 z-10 bg-surface-card/);
});

test("ScreensPage lets managers change screen domain from the more menu", async () => {
	const source = await readFile(screensPagePath, "utf8");

	assert.match(source, /domainEditorScreen/);
	assert.match(source, /修改所属域/);
	assert.match(source, /analyticsApi\.updateScreenDomain/);
	assert.match(source, /setScreens\(\(prev\) =>/);
	assert.match(source, /title="修改所属业务域"/);
	assert.match(source, /数据域来自主题域管理；不选择时归入未归类。/);
});

test("ScreensPage more menu keeps page typography and visible hover states", async () => {
	const source = await readFile(screensPagePath, "utf8");

	assert.match(source, /SCREEN_CARD_MENU_ITEM_CLASS/);
	assert.match(source, /SCREEN_CARD_MENU_DANGER_ITEM_CLASS/);
	assert.match(source, /screen-card-menu fixed[\s\S]*text-sm/);
	assert.match(source, /SCREEN_CARD_MENU_ITEM_CLASS[\s\S]*text-sm[\s\S]*hover:bg-\[rgba\(37,99,235,0\.10\)\]/);
	assert.match(source, /SCREEN_CARD_MENU_DANGER_ITEM_CLASS[\s\S]*text-sm[\s\S]*hover:bg-\[rgba\(220,38,38,0\.10\)\]/);
	assert.equal(source.includes("text-xs text-left cursor-pointer hover:border-brand hover:bg-brand/10"), false);
	assert.equal(source.includes("hover:bg-error/10 text-error"), false);
});
