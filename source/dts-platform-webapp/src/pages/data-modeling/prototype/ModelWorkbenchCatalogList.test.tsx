// @vitest-environment jsdom

import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { DimensionDefinitionView } from "@/features/modeling/contracts/dimensionDefinitionContract";
import type { ModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
import { ModelWorkbenchCatalogList } from "./ModelWorkbenchCatalogList";

const apiMocks = vi.hoisted(() => ({
	getMaterializationStatuses: vi.fn(),
}));

vi.mock("@/api/modelSpecApi", async (importOriginal) => ({
	...(await importOriginal<typeof import("@/api/modelSpecApi")>()),
	getModelMaterializationStatuses: apiMocks.getMaterializationStatuses,
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
});

afterEach(async () => {
	await act(async () => root.unmount());
	container.remove();
});

describe("ModelWorkbenchCatalogList", () => {
	it("shows searchable records with explicit edit and view actions", async () => {
		const onChooseModel = vi.fn();
		const onChooseDimension = vi.fn();
		await act(async () =>
			root.render(
				<ModelWorkbenchCatalogList
					canMaintain
					dimensions={[currentDimension]}
					domains={[{ id: "domain-1", code: "finance", name: "财务域" }] as never}
					models={[draftModel, publishedModel]}
					onBack={vi.fn()}
					onChooseDimension={onChooseDimension}
					onChooseModel={onChooseModel}
					onMaterialize={vi.fn()}
				/>,
			),
		);

		expect(container.textContent).toContain("日期维度表");
		expect(container.textContent).toContain("订单明细表");
		expect(container.textContent).toContain("日期");
		const edit = Array.from(container.querySelectorAll("button")).find((button) => button.textContent === "编辑");
		const views = Array.from(container.querySelectorAll("button")).filter((button) => button.textContent === "查看");
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
					canMaintain
					dimensions={[currentDimension]}
					domains={[{ id: "domain-1", code: "finance", name: "财务域" }] as never}
					models={[draftModel, publishedModel]}
					onBack={vi.fn()}
					onChooseDimension={vi.fn()}
					onChooseModel={vi.fn()}
					onMaterialize={onMaterialize}
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
			item.textContent?.includes("物化所选（2）"),
		);
		await act(async () => materialize?.click());

		expect(onMaterialize).toHaveBeenCalledWith([draftModel, publishedModel]);
	});
});
