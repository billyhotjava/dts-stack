// @vitest-environment jsdom

import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { MemoryRouter } from "react-router";
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({
	listCatalogAssetsV2: vi.fn(),
	listDomains: vi.fn(),
}));

vi.mock("@/api/platformApi", () => ({
	listCatalogAssetsV2: mocks.listCatalogAssetsV2,
	listDomains: mocks.listDomains,
}));
vi.mock("@/components/catalog/tags/AssetTagChips", () => ({ AssetTagChips: () => null }));
vi.mock("@/components/catalog/tags/AssetTagFilter", () => ({
	AssetTagFilter: () => <div data-testid="asset-tag-filter" />,
}));
vi.mock("@/components/empty-state", () => ({
	EmptyState: ({ title }: { title: string }) => <div>{title}</div>,
}));
vi.mock("@/components/page-header", () => ({
	PageHeader: ({ title }: { title: string }) => <h1>{title}</h1>,
}));
vi.mock("./assets/AssetLedgerView", () => ({ AssetLedgerView: () => null }));
vi.mock("./assets/AssetTagsWorkspace", () => ({ AssetTagsWorkspace: () => null }));

import DataSearchPage from "./DataSearchPage";

let container: HTMLDivElement;
let root: Root;

beforeAll(() => {
	Object.defineProperty(window, "matchMedia", {
		writable: true,
		value: (query: string) => ({
			matches: false,
			media: query,
			onchange: null,
			addListener: () => {},
			removeListener: () => {},
			addEventListener: () => {},
			removeEventListener: () => {},
			dispatchEvent: () => false,
		}),
	});
});

beforeEach(() => {
	(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
	localStorage.clear();
	mocks.listDomains.mockResolvedValue({ content: [] });
	mocks.listCatalogAssetsV2.mockResolvedValue({ content: [], page: 0, size: 10, total: 0 });
	container = document.createElement("div");
	document.body.appendChild(container);
	root = createRoot(container);
});

afterEach(async () => {
	await act(async () => root.unmount());
	container.remove();
	vi.clearAllMocks();
});

async function renderPage() {
	await act(async () => {
		root.render(
			<MemoryRouter initialEntries={["/catalog/search"]}>
				<DataSearchPage />
			</MemoryRouter>,
		);
	});
	await act(async () => Promise.resolve());
}

const EMPTY_ASSET_QUERY = {
	page: 0,
	size: 10,
	keyword: undefined,
	domainId: undefined,
	domainUnassigned: undefined,
	type: undefined,
	classification: undefined,
	warehouseLayer: undefined,
	governanceStatus: undefined,
	matchStatus: undefined,
	unclassified: undefined,
	stale: undefined,
	tagIds: undefined,
};

describe("DataSearchPage", () => {
	it("loads the first page of all visible assets on entry", async () => {
		await renderPage();

		expect(mocks.listCatalogAssetsV2).toHaveBeenCalledWith(EMPTY_ASSET_QUERY);
	});

	it("starts empty instead of automatically applying a previously saved query", async () => {
		const savedQuery = JSON.stringify({
			keyword: "历史条件",
			domain: "domain-1",
			assetType: "TABLE",
			datasetType: "HIVE",
			classification: "SECRET",
			warehouseLayer: "DWD",
		});
		localStorage.setItem("catalog.search.form.v1", savedQuery);

		await renderPage();

		expect(mocks.listCatalogAssetsV2).toHaveBeenCalledWith(EMPTY_ASSET_QUERY);
		expect(localStorage.getItem("catalog.search.form.v1")).toBe(savedQuery);
	});

	it("presents each result as a governed data asset instead of a technical search hit", async () => {
		mocks.listDomains.mockResolvedValue({ content: [{ id: "domain-1", name: "项目管理域" }] });
		mocks.listCatalogAssetsV2.mockResolvedValue({
			content: [
				{
					id: "asset-1",
					displayName: "项目任务快照",
					description: "项目任务每日状态快照",
					fqn: "source:finance/schema:public/table:project_task_snapshot",
					type: "POSTGRESQL",
					service: "项目管理库",
					assetType: "DATASET",
					domainId: "domain-1",
					classification: "CONFIDENTIAL",
					warehouseLayer: "DWD",
					owner: "张三",
					ownerDept: "D01",
					governanceStatus: "GOVERNED",
					lifecycleStatus: "ACTIVE",
					columnCount: 12,
					lastSyncedAt: "2026-08-12T01:02:03Z",
					metadataSource: "dts-catalog",
				},
			],
			page: 0,
			size: 10,
			total: 1,
		});

		await renderPage();
		await act(async () => Promise.resolve());

		const visibleText = container.textContent || "";
		expect(visibleText).toContain("数据资产目录");
		expect(visibleText).toContain("项目任务每日状态快照");
		expect(visibleText).toContain("项目管理域");
		expect(visibleText).toContain("张三（D01）");
		expect(visibleText).toContain("明细层（DWD）");
		expect(visibleText).toContain("机密");
		expect(visibleText).toContain("已治理");
		expect(visibleText).toContain("平台登记");
		expect(visibleText).toContain("12 个字段");
		expect(visibleText).not.toContain("CONFIDENTIAL");
		expect(visibleText).not.toContain("GOVERNED");
		expect(visibleText).not.toContain("dts-catalog");
	});
});
