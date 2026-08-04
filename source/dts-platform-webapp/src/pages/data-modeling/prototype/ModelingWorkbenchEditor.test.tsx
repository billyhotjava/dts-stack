// @vitest-environment jsdom

import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { DimensionDefinitionView } from "@/features/modeling/contracts/dimensionDefinitionContract";
import type { ModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
import { ModelingWorkbenchEditor, type ModelingWorkbenchEditorProps } from "./ModelingWorkbenchEditor";
import type { ModelDraft } from "./services/modelWorkbenchService";

let container: HTMLDivElement;
let root: Root;

const makeDraft = (patch: Partial<ModelDraft> = {}): ModelDraft => ({
	createKind: "dimension-table",
	base: null,
	planId: "plan-1",
	domainId: "finance",
	name: "预算科目表",
	description: "统一维护预算科目",
	physicalName: "dim_budget_account",
	materialization: "table",
	grainStatement: "",
	fields: [
		{
			name: "subject_code",
			displayName: "科目编码",
			dataType: "STRING",
			nullable: false,
			role: "KEY",
			dimensionAttributeCode: "SUBJECT_CODE",
		},
	],
	partitionFields: "",
	loadStrategy: "FULL",
	scdType: "TYPE1",
	reuseScope: "DOMAIN",
	dimensionDefinitionId: "dimension-1",
	standardBindings: [],
	...patch,
});

const definition = {
	id: "dimension-1",
	systemCode: "DIM000001",
	domainId: "finance",
	name: "预算科目",
	ownerId: "owner-1",
	revision: 2,
} as DimensionDefinitionView;

const makeProps = (patch: Partial<ModelingWorkbenchEditorProps> = {}): ModelingWorkbenchEditorProps => ({
	draft: makeDraft(),
	context: {
		domains: [
			{ code: "business", name: "财务业务" },
			{ code: "finance", name: "财务域", parentCode: "business" },
		],
		models: [],
		standards: [],
	},
	dimensionDefinitions: [definition],
	dimensionDefinitionFailure: "",
	currentOwnerId: "current-owner",
	selectedModel: null,
	representation: null,
	representationFailure: "",
	canMaintain: true,
	readOnly: false,
	saving: false,
	dirty: true,
	validationErrors: {},
	failureMessage: "",
	fieldRowIds: ["field-1"],
	onChange: vi.fn(),
	onSave: vi.fn(),
	onRefresh: vi.fn(),
	onDialog: vi.fn(),
	onAddFields: vi.fn(),
	onRemoveBlankFields: vi.fn(),
	onUpdateField: vi.fn(),
	onDeleteField: vi.fn(),
	onStandardChange: vi.fn(),
	...patch,
});

async function render(props = makeProps()) {
	await act(async () => root.render(<ModelingWorkbenchEditor {...props} />));
	return props;
}

function button(label: string) {
	const match = Array.from(container.querySelectorAll("button")).find((item) => item.textContent?.trim() === label);
	expect(match).toBeDefined();
	return match as HTMLButtonElement;
}

beforeEach(() => {
	(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
	container = document.createElement("div");
	document.body.appendChild(container);
	root = createRoot(container);
});

afterEach(async () => {
	await act(async () => root.unmount());
	container.remove();
});

describe("ModelingWorkbenchEditor", () => {
	it("renders the approved dimension form and toolbar without compatibility-only controls", async () => {
		await render();
		for (const label of [
			"数仓分层",
			"业务分类",
			"数据域",
			"存储策略",
			"维度",
			"表名规则",
			"表名",
			"表中文名",
			"生命周期",
			"负责人",
			"描述",
		])
			expect(container.textContent).toContain(label);

		for (const label of [
			"模型类型",
			"目标分层",
			"业务定义",
			"模型粒度",
			"物化方式",
			"加载策略",
			"分区字段",
			"SCD 策略",
			"复用范围",
		])
			expect(container.textContent).not.toContain(label);

		for (const label of ["保存", "提交", "刷新", "关联关系", "发布", "日志", "质量规则", "模型开发", "导出"])
			expect(button(label)).toBeDefined();
		expect(container.textContent).not.toContain("物理预览");
	});

	it("disables the fieldset in read-only mode and shows table-name validation beside its field", async () => {
		await render(
			makeProps({
				readOnly: true,
				validationErrors: { physicalName: "表名只能使用小写字母、数字和下划线" },
			}),
		);

		expect(container.querySelector("fieldset")).toHaveProperty("disabled", true);
		const tableName = container.querySelector<HTMLInputElement>('input[aria-label="表名"]');
		expect(tableName?.closest("label")?.textContent).toContain("表名只能使用小写字母、数字和下划线");
	});

	it("keeps authority fields disabled while dimension draft values remain editable", async () => {
		await render();

		for (const label of ["业务分类", "表名规则", "生命周期", "负责人"])
			expect(container.querySelector<HTMLInputElement>(`input[aria-label="${label}"]`)).toHaveProperty(
				"disabled",
				true,
			);
		for (const label of ["数据域", "存储策略", "维度"])
			expect(container.querySelector<HTMLSelectElement>(`select[aria-label="${label}"]`)).toHaveProperty(
				"disabled",
				false,
			);
		for (const label of ["表名", "表中文名"])
			expect(container.querySelector<HTMLInputElement>(`input[aria-label="${label}"]`)).toHaveProperty(
				"disabled",
				false,
			);
		expect(container.querySelector<HTMLTextAreaElement>('textarea[aria-label="描述"]')).toHaveProperty(
			"disabled",
			false,
		);
	});

	it("preserves a persisted missing dimension reference as a disabled fallback", async () => {
		const draft = makeDraft({
			base: { id: "model-1" } as ModelSpecView,
			dimensionDefinitionId: "retired-dimension",
		});
		await render(makeProps({ draft, dimensionDefinitions: [] }));

		const select = container.querySelector<HTMLSelectElement>('select[aria-label="维度"]');
		expect(select?.value).toBe("retired-dimension");
		expect(select?.querySelector('option[value="retired-dimension"]')).toHaveProperty("disabled", true);
	});

	it("disables unpublished actions and maps every supported toolbar dialog", async () => {
		const unpublished = await render();
		for (const label of ["提交", "关联关系", "发布", "日志", "质量规则", "模型开发", "导出"]) {
			expect(button(label)).toHaveProperty("disabled", true);
			act(() => button(label).click());
		}
		expect(unpublished.onDialog).not.toHaveBeenCalled();

		const selectedModel = { id: "model-1", compatibilityMode: "CANONICAL" } as ModelSpecView;
		const published = makeProps({ selectedModel });
		await render(published);
		for (const [label, dialog] of [
			["提交", "gates"],
			["关联关系", "association"],
			["发布", "publish"],
			["日志", "logs"],
			["质量规则", "quality"],
			["模型开发", "advanced"],
		] as const) {
			act(() => button(label).click());
			expect(published.onDialog).toHaveBeenLastCalledWith(dialog);
		}
		expect(button("导出").title).toBe("尚无模型导出服务端契约");
	});

	it("keeps fact drafts on the explicit compatibility form", async () => {
		await render(makeProps({ draft: makeDraft({ createKind: "fact" }) }));

		expect(container.textContent).toContain("模型粒度");
		expect(container.textContent).toContain("加载策略");
	});

	it("locks and preserves a persisted compatibility draft whose domain is missing", async () => {
		const draft = makeDraft({
			createKind: "fact",
			base: { id: "model-1" } as ModelSpecView,
			domainId: "retired-domain",
		});
		await render(
			makeProps({
				draft,
				context: { domains: [], models: [], standards: [] },
			}),
		);

		const domainLabel = Array.from(container.querySelectorAll("label")).find((item) =>
			item.querySelector("span")?.textContent?.includes("数据域"),
		);
		const select = domainLabel?.querySelector("select");
		expect(select).toHaveProperty("disabled", true);
		expect(select?.value).toBe("retired-domain");
		expect(select?.querySelector('option[value="retired-domain"]')).toHaveProperty("disabled", true);
	});
});
