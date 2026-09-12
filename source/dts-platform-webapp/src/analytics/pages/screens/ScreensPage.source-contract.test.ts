import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const screensPagePath = new URL("./ScreensPage.tsx", import.meta.url);
const globalCssPath = new URL("../../../global.css", import.meta.url);
const analyticsApiPath = new URL("../../api/analyticsApi.ts", import.meta.url);

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

test("ScreensPage previews screen names in one shared side panel from the management list", async () => {
	const source = await readFile(screensPagePath, "utf8");
	assert.match(source, /<ScreenListPreview>/);
	assert.match(source, /<ScreenPreviewName screen=\{screen\}/);
	assert.doesNotMatch(source, /href=\{resolveRouteForOpen\(`\/bi\/screens\/\$\{screen\.id\}\/preview`\)\}/);
});

test("ScreensPage hides management actions when row permissions do not allow them", async () => {
	const source = await readFile(screensPagePath, "utf8");

	assert.match(source, /rowPermissions\.canEdit/);
	assert.match(source, /rowPermissions\.canManage/);
	assert.match(source, /rowPermissions\.canDelete/);
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
// 由共享 CompactTable 接管分页，不再维护页面私有切片和 Pagination。
test("ScreensPage paginates the list at 10 rows per page by default", async () => {
	const source = await readFile(screensPagePath, "utf8");

	assert.match(source, /useState\(10\)/);
	assert.match(source, /setCurrentPage\(1\)[\s\S]*?\[searchKeyword,\s*publishFilter\]/);
	assert.match(source, /Math\.min\(currentPage,\s*totalPages\)/);
	assert.match(source, /pagination=\{\{[\s\S]*current:\s*safePage,[\s\S]*pageSize,[\s\S]*total:\s*totalCount/);
	assert.match(source, /dataSource=\{visibleScreens\}/);
	assert.doesNotMatch(source, /<Pagination/);
	assert.doesNotMatch(source, /pagedScreens|sortedScreens\.slice/);
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

test("ScreensPage uses the shared responsive table instead of a page-private pixel grid", async () => {
	const [source, globalCss] = await Promise.all([readFile(screensPagePath, "utf8"), readFile(globalCssPath, "utf8")]);

	assert.match(source, /import\s*\{\s*actionColumn,\s*CompactTable\s*\}\s*from\s*["']@\/components\/table["']/);
	assert.match(source, /<CompactTable<ScreenListItem>/);
	assert.match(source, /tableLayout="fixed"/);
	assert.match(source, /scroll=\{\{\s*x:\s*"100%"\s*\}\}/);
	assert.match(source, /className="analytics-screen-table"/);
	assert.doesNotMatch(source, /<table|<colgroup|SortableHeader|useTableSort/);
	assert.doesNotMatch(
		globalCss,
		/analytics-screen-management-table|analytics-screen-col-|analytics-screen-action-cell/,
	);
});

test("ScreensPage keeps the domain filter available without consuming table width on common monitors", async () => {
	const source = await readFile(screensPagePath, "utf8");

	assert.match(source, /data-testid="analytics-screen-domain-select"/);
	assert.match(source, /className="[^"]*2xl:hidden[^"]*"/);
	assert.match(source, /<aside className="hidden [^"]*2xl:block[^"]*"/);
	assert.match(source, /responsive:\s*\["xl"\]/);
});

test("ScreensPage limits the fixed action area and moves secondary actions into More", async () => {
	const source = await readFile(screensPagePath, "utf8");

	assert.match(source, /actionColumn<ScreenListItem>/);
	assert.match(source, /\{\s*maxActions:\s*2\s*\}/);
	assert.match(source, /<Dropdown[\s\S]*trigger=\{\["click"\]\}[\s\S]*placement="bottomRight"/);
	assert.match(source, /修改所属域/);
	assert.match(source, /导出大屏/);
	assert.match(source, /保存为模板/);
	assert.match(source, /删除/);
	assert.doesNotMatch(source, /width:\s*280|activeCardMenuId|cardMenuAnchor|createPortal/);
});

test("ScreensPage shows the screen creator returned by the list contract", async () => {
	const [source, apiSource] = await Promise.all([
		readFile(screensPagePath, "utf8"),
		readFile(analyticsApiPath, "utf8"),
	]);

	assert.match(apiSource, /creatorId\?: number \| string \| null/);
	assert.match(apiSource, /creatorName\?: string \| null/);
	assert.match(source, /title:\s*"创建者"/);
	assert.match(
		source,
		/screen\.creatorName \|\| \(screen\.creatorId == null \? "—" : `用户 \$\{screen\.creatorId\}`\)/,
	);
});

test("ScreensPage prioritizes long screen names and descriptions in the management table", async () => {
	const source = await readFile(screensPagePath, "utf8");

	assert.equal(source.includes("{screen.width || 1920} × {screen.height || 1080}"), false);
	assert.match(source, /title:\s*"名称"[\s\S]*dataIndex:\s*"name"[\s\S]*ellipsis:/);
	assert.match(source, /title:\s*"描述"[\s\S]*dataIndex:\s*"description"[\s\S]*ellipsis:/);
	const preview = await readFile(new URL("./components/ScreenListPreview.tsx", import.meta.url), "utf8");
	assert.match(preview, /title=\{screen\.name \|\| "未命名大屏"\}/);
	assert.match(source, /title=\{screen\.description \|\| "无描述"\}/);
	assert.doesNotMatch(source, /min-width:\s*1200px|width:\s*280px/);
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

	assert.match(source, /<Dropdown/);
	assert.match(source, /danger:\s*true/);
	assert.match(source, /disabled:\s*true[\s\S]*请进入编辑器完成发布门禁/);
	assert.doesNotMatch(source, /SCREEN_CARD_MENU_ITEM_CLASS|SCREEN_CARD_MENU_DANGER_ITEM_CLASS|screen-card-menu fixed/);
});
