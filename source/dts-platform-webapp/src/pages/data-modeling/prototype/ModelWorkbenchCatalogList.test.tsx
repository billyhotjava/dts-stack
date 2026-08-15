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
	getMaterializationStatuses: vi.fn(),
	listWorkbenchCatalogPage: vi.fn(),
}));

vi.mock("@/api/modelSpecApi", async (importOriginal) => ({
	...(await importOriginal<typeof import("@/api/modelSpecApi")>()),
	getModelMaterializationStatuses: apiMocks.getMaterializationStatuses,
	listModelWorkbenchCatalogPage: apiMocks.listWorkbenchCatalogPage,
}));

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
const currentDimension = {
	id: "dimension-current",
	name: "日期",
	systemCode: "DIM_DATE",
	domainId: "domain-1",
	status: "CURRENT",
	revision: 3,
} as DimensionDefinitionView;

beforeEach(() => {
	(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
	container = document.createElement("div");
	document.body.appendChild(container);
	root = createRoot(container);
	apiMocks.getMaterializationStatuses.mockReset();
	apiMocks.getMaterializationStatuses.mockResolvedValue([]);
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
	it("shows searchable records with explicit edit and view actions", async () => {
		const onChooseModel = vi.fn();
		const onChooseDimension = vi.fn();
		const onCreate = vi.fn();
		await act(async () =>
			root.render(
				<ModelWorkbenchCatalogList
					busy={false}
					canMaintain
					dimensions={[currentDimension]}
					domains={[{ id: "domain-1", code: "finance", name: "财务域" }] as never}
					failureMessage=""
					models={[draftModel, publishedModel]}
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
					onRemoveModel={vi.fn()}
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
		const edit = Array.from(container.querySelectorAll("button")).find((button) => label(button) === "编辑");
		const views = Array.from(container.querySelectorAll("button")).filter((button) => label(button) === "查看");
		expect(edit).toBeDefined();
		expect(views).toHaveLength(2);
		await act(async () => edit?.click());
		expect(onChooseModel).toHaveBeenCalledWith(draftModel);

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
		apiMocks.getMaterializationStatuses.mockResolvedValue([
			{
				modelSpecId: draftModel.id,
				candidateId: "candidate-1",
				candidateVersion: 5,
				environment: "dev",
				candidateStatus: "BUILT",
				candidateUpdatedAt: "2026-08-09T02:47:00Z",
				currentImplementationRevision: 2,
				evidence: {
					modelSpecId: draftModel.id,
					modelName: draftModel.name,
					modelRevision: draftModel.revision,
					implementationRevision: 2,
					targetRelation: "public.it_demo_dwd_dim_date",
					runStatus: "BUILT",
					relationState: "VERIFIED",
					attempt: 1,
					finishedAt: "2026-08-09T02:47:00Z",
				},
			},
		]);

		await act(async () =>
			root.render(
				<ModelWorkbenchCatalogList
					busy={false}
					canMaintain
					dimensions={[currentDimension]}
					domains={[{ id: "domain-1", code: "finance", name: "财务域" }] as never}
					failureMessage=""
					models={[draftModel, publishedModel]}
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

		expect(container.textContent).toContain("已物化");
		const dateSelection = container.querySelector<HTMLInputElement>('input[aria-label="选择 日期维度表"]');
		const orderSelection = container.querySelector<HTMLInputElement>('input[aria-label="选择 订单明细表"]');
		await act(async () => dateSelection?.click());
		await act(async () => orderSelection?.click());
		const materialize = Array.from(container.querySelectorAll("button")).find((item) =>
			item.textContent?.includes("生成物化候选（2）"),
		);
		await act(async () => materialize?.click());

		expect(onMaterialize).toHaveBeenCalledWith([draftModel, publishedModel]);
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
			button.textContent?.includes("生成物化候选（11）"),
		);
		await act(async () => materialize?.click());

		expect(onMaterialize).toHaveBeenCalledWith(models);
	});
});
