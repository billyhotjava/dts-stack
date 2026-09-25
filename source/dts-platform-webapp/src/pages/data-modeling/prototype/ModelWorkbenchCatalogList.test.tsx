// @vitest-environment jsdom

import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
import type { DimensionDefinitionView } from "@/features/modeling/contracts/dimensionDefinitionContract";
import type { ModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
import { ModelWorkbenchCatalogList } from "./ModelWorkbenchCatalogList";

// 整树渲染在并行跑批下会超过 vitest 默认的 5s（单跑 <2s），放宽文件级超时避免假红。
vi.setConfig({ testTimeout: 20_000 });

beforeAll(() => {
	if (!window.matchMedia) {
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
	}
});

const apiMocks = vi.hoisted(() => ({
	getDeliveryStatus: vi.fn(),
	getSummaries: vi.fn(),
	listWorkbenchCatalogPage: vi.fn(),
}));
const accessState = vi.hoisted(() => ({ denied: false, unavailable: false }));
vi.mock("./useModelingAccess", () => ({
	useModelAccess: (ids: string[]) => ({
		isError: accessState.unavailable,
		data: Object.fromEntries(ids.map((id) => [id, { canEdit: !accessState.denied, canManage: !accessState.denied }])),
	}),
}));
const routerPush = vi.hoisted(() => vi.fn());

vi.mock("@/api/modelSpecApi", async (importOriginal) => ({
	...(await importOriginal<typeof import("@/api/modelSpecApi")>()),
	listModelWorkbenchCatalogPage: apiMocks.listWorkbenchCatalogPage,
}));
vi.mock("@/api/modelDeliveryStatusApi", async (importOriginal) => ({
	...(await importOriginal<typeof import("@/api/modelDeliveryStatusApi")>()),
	getModelDeliveryStatus: apiMocks.getDeliveryStatus,
	getModelWorkbenchSummaries: apiMocks.getSummaries,
}));

vi.mock("@/routes/hooks", () => ({ useRouter: () => ({ push: routerPush }) }));

let container: HTMLDivElement;
let root: Root;

const draftModel = {
	id: "model-draft",
	name: "日期维度表",
	modelType: "DIMENSION",
	layer: "DWD",
	status: "DRAFT",
	revision: 2,
	compatibilityMode: "CANONICAL",
	domainId: "domain-1",
	planId: "plan-1",
} as ModelSpecView;
const publishedModel = {
	...draftModel,
	id: "model-published",
	name: "订单明细表",
	modelType: "FACT",
	status: "PUBLISHED",
} as ModelSpecView;
const emptyDeliveryStatus = {
	modelRevision: 2,
	candidate: null,
	steps: [
		{ key: "materialization", state: "NOT_STARTED", matchesCurrentTarget: false, resourceId: null },
		{ key: "quality", state: "NOT_STARTED", matchesCurrentTarget: false, resourceId: null },
		{ key: "publication", state: "NOT_STARTED", matchesCurrentTarget: false, resourceId: null },
		{ key: "catalog", state: "UNKNOWN", matchesCurrentTarget: false, resourceId: null },
		{ key: "analysis", state: "UNKNOWN", matchesCurrentTarget: false, resourceId: null },
	],
} as never;

// F15 K1 summary row: the latest build of the current revision and the latest published version.
const summaryFor = (id: string, buildState?: string, publishedRevision?: number) => ({
	modelSpecId: id,
	modelRevision: 2,
	modelChecksum: null,
	readState: "OK",
	reasonCode: null,
	build: buildState
		? {
				state: buildState,
				reasonCode: null,
				modelSpecId: id,
				modelRevision: 2,
				modelChecksum: "checksum",
				implementationRevision: 1,
				implementationChecksum: null,
				buildMode: "DATA_BUILD",
				environment: "dev",
				candidateId: null,
				runGroupId: null,
				targetRelation: "public.orders",
				matchesCurrentTarget: true,
				observedAt: null,
			}
		: null,
	publishedReadState: "OK",
	published: publishedRevision
		? { releaseId: `release-${id}`, modelRevision: publishedRevision, environment: "prod", publishedAt: "2026-09-20T02:00:00Z" }
		: null,
});

const currentDimension = {
	id: "dimension-current",
	name: "日期",
	systemCode: "DIM_DATE",
	domainId: "domain-1",
	status: "CURRENT",
	revision: 3,
} as DimensionDefinitionView;

beforeEach(() => {
	accessState.denied = false;
	accessState.unavailable = false;
	(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
	container = document.createElement("div");
	document.body.appendChild(container);
	root = createRoot(container);
	apiMocks.getDeliveryStatus.mockReset();
	apiMocks.getDeliveryStatus.mockResolvedValue(emptyDeliveryStatus);
	apiMocks.getSummaries.mockReset();
	apiMocks.getSummaries.mockImplementation(async (ids: string[]) => ids.map((id) => summaryFor(id)));
	routerPush.mockReset();
	apiMocks.listWorkbenchCatalogPage.mockReset();
	const content = [
		{
			kind: "DIMENSION_DEFINITION",
			id: currentDimension.id,
			name: currentDimension.name,
			code: currentDimension.systemCode,
			planId: null,
			domainId: currentDimension.domainId,
			objectType: "DIMENSION_DEFINITION",
			layer: null,
			status: currentDimension.status,
			revision: currentDimension.revision,
		},
		...[draftModel, publishedModel].map((model) => ({
			kind: "MODEL_SPEC" as const,
			id: model.id,
			name: model.name,
			code: model.implementationPolicy?.physicalName || "—",
			planId: model.planId,
			domainId: model.domainId,
			objectType: model.modelType,
			layer: model.layer,
			status: model.status,
			revision: model.revision,
		})),
	];
	apiMocks.listWorkbenchCatalogPage.mockImplementation(
		async (params: { query?: string; page: number; size: number }) => {
			const query = params.query?.toLowerCase();
			const filtered = query
				? content.filter((entry) =>
						[entry.name, entry.code, entry.objectType, entry.status].join(" ").toLowerCase().includes(query),
					)
				: content;
			return {
				content: filtered,
				totalElements: filtered.length,
				page: params.page,
				size: params.size,
				totalPages: filtered.length ? Math.ceil(filtered.length / params.size) : 0,
			};
		},
	);
});

afterEach(async () => {
	await act(async () => root.unmount());
	container.remove();
});

describe("ModelWorkbenchCatalogList", () => {
	it.each(["selected-plan", null])("keeps department catalog queries within %s", async (scopePlanId) => {
		await act(async () =>
			root.render(
				<ModelWorkbenchCatalogList
					busy={false}
					scopePlanId={scopePlanId}
					canMaintain
					dimensions={[]}
					domains={[]}
					failureMessage=""
					models={[]}
					onArchiveModel={vi.fn()}
					onCloneDimension={vi.fn()}
					onChooseDimension={vi.fn()}
					onChooseModel={vi.fn()}
					onCreate={vi.fn()}
					onGoToGraphDimension={vi.fn()}
					onGoToGraphModel={vi.fn()}
					onImport={vi.fn()}
					onMaterialize={vi.fn()}
					onRefresh={vi.fn()}
					onRemoveDimension={vi.fn()}
					onRemoveModel={vi.fn()}
				/>,
			),
		);
		if (scopePlanId)
			expect(apiMocks.listWorkbenchCatalogPage).toHaveBeenCalledWith(expect.objectContaining({ planId: scopePlanId }));
		else expect(apiMocks.listWorkbenchCatalogPage).not.toHaveBeenCalled();
		expect(container.querySelector('[aria-label="按规划筛选"]')).toBeNull();
	});

	it("shows searchable records with explicit edit and view actions", async () => {
		const onChooseModel = vi.fn();
		const onChooseDimension = vi.fn();
		const onCreate = vi.fn();
		const onArchiveModel = vi.fn();
		const onRemoveModel = vi.fn();
		await act(async () =>
			root.render(
				<ModelWorkbenchCatalogList
					busy={false}
					canMaintain
					dimensions={[currentDimension]}
					domains={[{ id: "domain-1", code: "finance", name: "财务域" }] as never}
					failureMessage=""
					models={[draftModel, publishedModel]}
					onArchiveModel={onArchiveModel}
					onCloneDimension={vi.fn()}
					onChooseDimension={onChooseDimension}
					onChooseModel={onChooseModel}
					onCreate={onCreate}
					onGoToGraphDimension={vi.fn()}
					onGoToGraphModel={vi.fn()}
					onImport={vi.fn()}
					onMaterialize={vi.fn()}
					onRefresh={vi.fn()}
					onRemoveDimension={vi.fn()}
					onRemoveModel={onRemoveModel}
				/>,
			),
		);

		expect(container.textContent).not.toContain("进入目录编辑器");
		const create = Array.from(container.querySelectorAll("button")).find((button) => button.textContent === "新建模型");
		expect(create).toBeDefined();
		await act(async () => create?.click());
		const createDimensionTable = Array.from(container.querySelectorAll("button")).find(
			(button) => button.textContent === "创建维度表",
		);
		await act(async () => createDimensionTable?.click());
		expect(onCreate).toHaveBeenCalledWith("dimension-table", "");

		expect(container.textContent).toContain("日期维度表");
		expect(container.textContent).toContain("订单明细表");
		expect(container.textContent).toContain("日期");
		// antd 给“恰好两个汉字”的带边框按钮自动插空格（编辑 → 编 辑），比对前先去掉空白。
		const label = (button: Element) => (button.textContent ?? "").replace(/\s/g, "");
		const edits = Array.from(container.querySelectorAll("button")).filter((button) => label(button) === "编辑");
		const views = Array.from(container.querySelectorAll("button")).filter((button) => label(button) === "查看");
		expect(edits).toHaveLength(2);
		expect(views).toHaveLength(1);
		const dimensionRow = Array.from(container.querySelectorAll("tr")).find((row) =>
			row.textContent?.includes("DIM_DATE"),
		);
		const dimensionEdit = Array.from(dimensionRow?.querySelectorAll("button") || []).find(
			(button) => label(button) === "编辑",
		);
		await act(async () => dimensionEdit?.click());
		expect(onChooseDimension).toHaveBeenCalledWith(currentDimension);
		const draftModelRow = Array.from(container.querySelectorAll("tr")).find((row) =>
			row.textContent?.includes("日期维度表"),
		);
		const modelEdit = Array.from(draftModelRow?.querySelectorAll("button") || []).find(
			(button) => label(button) === "编辑",
		);
		await act(async () => modelEdit?.click());
		expect(onChooseModel).toHaveBeenCalledWith(draftModel);
		const publishedModelRow = Array.from(container.querySelectorAll("tr")).find((row) =>
			row.textContent?.includes("订单明细表"),
		);
		expect(Array.from(draftModelRow?.querySelectorAll("button") || []).map(label)).toContain("删除");
		expect(Array.from(draftModelRow?.querySelectorAll("button") || []).map(label)).not.toContain("归档");
		expect(Array.from(publishedModelRow?.querySelectorAll("button") || []).map(label)).toContain("归档");
		expect(Array.from(publishedModelRow?.querySelectorAll("button") || []).map(label)).not.toContain("删除");
		const archive = Array.from(publishedModelRow?.querySelectorAll("button") || []).find(
			(button) => label(button) === "归档",
		);
		await act(async () => archive?.click());
		expect(onArchiveModel).toHaveBeenCalledWith(publishedModel);
		const statusFilter = container.querySelector<HTMLSelectElement>('select[aria-label="按状态筛选"]');
		expect(statusFilter?.options[0]?.textContent).toBe("在用状态（不含已归档）");
		expect(Array.from(statusFilter?.options || []).map((option) => option.textContent)).toContain("已归档");

		const search = container.querySelector<HTMLInputElement>('input[aria-label="搜索模型列表"]');
		await act(async () => {
			if (!search) return;
			const valueSetter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value")?.set;
			valueSetter?.call(search, "订单");
			search.dispatchEvent(new Event("input", { bubbles: true }));
		});
		expect(container.textContent).not.toContain("日期维度表");
		expect(container.textContent).toContain("订单明细表");
	});

	it("multi-selects physical models from one plan and opens one batch materialization", async () => {
		const onMaterialize = vi.fn();
		apiMocks.getSummaries.mockImplementation(async (ids: string[]) => ids.map((id) => summaryFor(id, "SUCCEEDED")));

		await act(async () =>
			root.render(
				<ModelWorkbenchCatalogList
					busy={false}
					canMaintain
					dimensions={[currentDimension]}
					domains={[{ id: "domain-1", code: "finance", name: "财务域" }] as never}
					failureMessage=""
					models={[draftModel, publishedModel]}
					onArchiveModel={vi.fn()}
					onCloneDimension={vi.fn()}
					onChooseDimension={vi.fn()}
					onChooseModel={vi.fn()}
					onCreate={vi.fn()}
					onGoToGraphDimension={vi.fn()}
					onGoToGraphModel={vi.fn()}
					onImport={vi.fn()}
					onMaterialize={onMaterialize}
					onRefresh={vi.fn()}
					onRemoveDimension={vi.fn()}
					onRemoveModel={vi.fn()}
				/>,
			),
		);
		await act(async () => Promise.resolve());

		expect(container.textContent).toContain("构建完成");
		const dateSelection = container.querySelector<HTMLInputElement>('input[aria-label="选择 日期维度表"]');
		const orderSelection = container.querySelector<HTMLInputElement>('input[aria-label="选择 订单明细表"]');
		await act(async () => dateSelection?.click());
		await act(async () => orderSelection?.click());
		const materialize = Array.from(container.querySelectorAll("button")).find((item) =>
			item.textContent?.includes("生成构建发布单（2）"),
		);
		await act(async () => materialize?.click());

		expect(onMaterialize).toHaveBeenCalledWith([draftModel, publishedModel]);
	});

	it("reads build and published summaries for the page in one request without other modules", async () => {
		apiMocks.getSummaries.mockImplementation(async (ids: string[]) =>
			ids.map((id) => (id === publishedModel.id ? summaryFor(id, "SUCCEEDED", 1) : summaryFor(id))),
		);

		await act(async () =>
			root.render(
				<ModelWorkbenchCatalogList
					busy={false}
					canMaintain
					dimensions={[currentDimension]}
					domains={[{ id: "domain-1", code: "finance", name: "财务域" }] as never}
					failureMessage=""
					models={[draftModel, publishedModel]}
					onArchiveModel={vi.fn()}
					onCloneDimension={vi.fn()}
					onChooseDimension={vi.fn()}
					onChooseModel={vi.fn()}
					onCreate={vi.fn()}
					onGoToGraphDimension={vi.fn()}
					onGoToGraphModel={vi.fn()}
					onImport={vi.fn()}
					onMaterialize={vi.fn()}
					onRefresh={vi.fn()}
					onRemoveDimension={vi.fn()}
					onRemoveModel={vi.fn()}
				/>,
			),
		);
		await act(async () => Promise.resolve());

		expect(apiMocks.getSummaries).toHaveBeenCalledTimes(1);
		expect(apiMocks.getSummaries).toHaveBeenCalledWith(
			[draftModel.id, publishedModel.id],
			undefined,
			expect.any(AbortSignal),
		);
		expect(apiMocks.getDeliveryStatus).not.toHaveBeenCalled();
		const row = (name: string) => Array.from(container.querySelectorAll("tr")).find((item) => item.textContent?.includes(name));
		expect(row("订单明细表")?.textContent).toContain("构建完成");
		expect(row("订单明细表")?.textContent).toContain("r1");
		expect(row("日期维度表")?.textContent).toContain("未构建");
		expect(row("日期维度表")?.textContent).toContain("未发布");
		expect(container.textContent).not.toContain("资产已登记");
		expect(container.textContent).not.toContain("分析准备");
	});

	it("requests a server page and preserves model selection while paging", async () => {
		const models = Array.from({ length: 11 }, (_, index) => ({
			...draftModel,
			id: `model-${index + 1}`,
			name: `模型 ${index + 1}`,
		})) as ModelSpecView[];
		const entry = (model: ModelSpecView) => ({
			kind: "MODEL_SPEC" as const,
			id: model.id,
			name: model.name,
			code: "—",
			planId: model.planId,
			domainId: model.domainId,
			objectType: model.modelType,
			layer: model.layer,
			status: model.status,
			revision: model.revision,
		});
		apiMocks.listWorkbenchCatalogPage
			.mockResolvedValueOnce({
				content: models.slice(0, 10).map(entry),
				totalElements: 11,
				page: 0,
				size: 10,
				totalPages: 2,
			})
			.mockResolvedValueOnce({
				content: [entry(models[10])],
				totalElements: 11,
				page: 1,
				size: 10,
				totalPages: 2,
			});
		const onMaterialize = vi.fn();

		await act(async () =>
			root.render(
				<ModelWorkbenchCatalogList
					busy={false}
					canMaintain
					dimensions={[]}
					domains={[{ id: "domain-1", code: "finance", name: "财务域" }] as never}
					failureMessage=""
					models={models}
					onArchiveModel={vi.fn()}
					onCloneDimension={vi.fn()}
					onChooseDimension={vi.fn()}
					onChooseModel={vi.fn()}
					onCreate={vi.fn()}
					onGoToGraphDimension={vi.fn()}
					onGoToGraphModel={vi.fn()}
					onImport={vi.fn()}
					onMaterialize={onMaterialize}
					onRefresh={vi.fn()}
					onRemoveDimension={vi.fn()}
					onRemoveModel={vi.fn()}
				/>,
			),
		);
		await act(async () => Promise.resolve());

		const selectPage = Array.from(container.querySelectorAll("button")).find(
			(button) => button.textContent === "选择当前页",
		);
		await act(async () => selectPage?.click());
		expect(container.textContent).toContain("已选 10 个模型");
		const next = container.querySelector<HTMLElement>(".ant-pagination-next");
		await act(async () => next?.dispatchEvent(new MouseEvent("click", { bubbles: true })));
		await act(async () => Promise.resolve());

		expect(apiMocks.listWorkbenchCatalogPage).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1, size: 10 }));
		const last = container.querySelector<HTMLInputElement>('input[aria-label="选择 模型 11"]');
		await act(async () => last?.click());
		const materialize = Array.from(container.querySelectorAll("button")).find((button) =>
			button.textContent?.includes("生成构建发布单（11）"),
		);
		await act(async () => materialize?.click());

		expect(onMaterialize).toHaveBeenCalledWith(models);
	});
});

// F4: one unreadable model must not discard the other rows.
it("keeps successful summary rows when another model cannot be read", async () => {
	apiMocks.getSummaries.mockImplementation(async (ids: string[]) =>
		ids.map((id) =>
			id === draftModel.id ? { ...summaryFor(id), readState: "FAILED", reasonCode: "MODEL_NOT_VISIBLE" } : summaryFor(id),
		),
	);
	await act(async () =>
		root.render(
			<ModelWorkbenchCatalogList
				busy={false}
				canMaintain
				dimensions={[]}
				domains={[]}
				failureMessage=""
				models={[draftModel, publishedModel]}
				onArchiveModel={vi.fn()}
				onCloneDimension={vi.fn()}
				onChooseDimension={vi.fn()}
				onChooseModel={vi.fn()}
				onCreate={vi.fn()}
				onGoToGraphDimension={vi.fn()}
				onGoToGraphModel={vi.fn()}
				onImport={vi.fn()}
				onMaterialize={vi.fn()}
				onRefresh={vi.fn()}
				onRemoveDimension={vi.fn()}
				onRemoveModel={vi.fn()}
			/>,
		),
	);
	await act(async () => Promise.resolve());
	const rows = Array.from(container.querySelectorAll("tr"));
	expect(rows.find((row) => row.textContent?.includes(draftModel.name))?.textContent).toContain("状态读取失败");
	expect(rows.find((row) => row.textContent?.includes(publishedModel.name))?.textContent).not.toContain(
		"状态读取失败",
	);
});

async function renderPerformanceList(models: ModelSpecView[], detailsReady = true) {
	await act(async () =>
		root.render(
			<ModelWorkbenchCatalogList
				busy={false}
				detailsReady={detailsReady}
				canMaintain
				dimensions={[]}
				domains={[]}
				failureMessage=""
				models={models}
				onArchiveModel={vi.fn()}
				onCloneDimension={vi.fn()}
				onChooseDimension={vi.fn()}
				onChooseModel={vi.fn()}
				onCreate={vi.fn()}
				onGoToGraphDimension={vi.fn()}
				onGoToGraphModel={vi.fn()}
				onImport={vi.fn()}
				onMaterialize={vi.fn()}
				onRefresh={vi.fn()}
				onRemoveDimension={vi.fn()}
				onRemoveModel={vi.fn()}
			/>,
		),
	);
}

it("marks every row unreadable when the summary request fails", async () => {
	apiMocks.getSummaries.mockRejectedValue(new Error("timeout"));
	await renderPerformanceList([draftModel, publishedModel]);
	await act(async () => Promise.resolve());
	const rows = Array.from(container.querySelectorAll("tr")).filter((row) =>
		[draftModel.name, publishedModel.name].some((name) => row.textContent?.includes(name)),
	);
	expect(rows).toHaveLength(2);
	for (const row of rows) expect(row.textContent).toContain("状态读取失败");
});

it("aborts a stale summary request on refresh and ignores its late answer", async () => {
	const signals: AbortSignal[] = [];
	const completions: Array<() => void> = [];
	apiMocks.getSummaries.mockImplementation(
		(ids: string[], _environment: string | undefined, signal: AbortSignal) =>
			new Promise((resolve) => {
				signals.push(signal);
				completions.push(() => resolve(ids.map((id) => summaryFor(id, "SUCCEEDED"))));
			}),
	);
	await renderPerformanceList([draftModel, publishedModel]);
	await act(async () => Promise.resolve());
	expect(signals).toHaveLength(1);
	await renderPerformanceList([draftModel, publishedModel]);
	await act(async () => Promise.resolve());
	expect(signals[0].aborted).toBe(true);
	expect(signals).toHaveLength(2);
	await act(async () => completions[0]());
	// The old request cannot complete the new generation.
	expect(container.textContent).toContain("读取中…");
	await act(async () => completions[1]());
	expect(container.textContent).not.toContain("读取中…");
	expect(container.textContent).toContain("构建完成");
});

it("keeps refresh available while editor options are unavailable", async () => {
	await renderPerformanceList([draftModel, publishedModel], false);
	const buttons = Array.from(container.querySelectorAll("button"));
	const label = (button: HTMLButtonElement) => button.textContent?.replace(/\s/g, "");
	expect(buttons.find((button) => label(button) === "新建模型")?.disabled).toBe(true);
	expect(buttons.find((button) => label(button) === "刷新")?.disabled).toBe(false);
	expect(buttons.find((button) => label(button) === "编辑")?.disabled).toBe(true);
});

it.each(["denied", "unavailable"] as const)("disables model edits when object authorization is %s", async (failure) => {
	accessState[failure] = true;
	await renderPerformanceList([draftModel]);
	const edits = Array.from(container.querySelectorAll("button")).filter(
		(button) => button.textContent?.replace(/\s/g, "") === "编辑",
	);
	expect(edits).toHaveLength(0);
	const buttons = Array.from(container.querySelectorAll("button"));
	expect(buttons.some((button) => button.textContent?.replace(/\s/g, "") === "查看")).toBe(true);
	const writes = buttons.filter((button) =>
		["删除", "归档", "生成构建发布单"].some((label) => button.textContent?.includes(label)),
	);
	expect(writes.length).toBeGreaterThan(0);
	expect(writes.every((button) => button.disabled)).toBe(true);
});

it("re-reads summaries while a build is running and stops once it settles", async () => {
	vi.useFakeTimers();
	try {
		let reads = 0;
		apiMocks.getSummaries.mockImplementation(async (ids: string[]) => {
			reads += 1;
			return ids.map((id) => (id === draftModel.id ? summaryFor(id, reads === 1 ? "RUNNING" : "FAILED") : summaryFor(id)));
		});
		await renderPerformanceList([draftModel, publishedModel]);
		await act(async () => {
			await vi.advanceTimersByTimeAsync(0);
		});
		expect(apiMocks.getSummaries).toHaveBeenCalledTimes(1);
		expect(container.textContent).toContain("构建中");

		await act(async () => {
			await vi.advanceTimersByTimeAsync(20000);
		});
		expect(apiMocks.getSummaries).toHaveBeenCalledTimes(2);
		expect(container.textContent).toContain("构建失败");

		await act(async () => {
			await vi.advanceTimersByTimeAsync(60000);
		});
		expect(apiMocks.getSummaries).toHaveBeenCalledTimes(2);
	} finally {
		vi.useRealTimers();
	}
});
