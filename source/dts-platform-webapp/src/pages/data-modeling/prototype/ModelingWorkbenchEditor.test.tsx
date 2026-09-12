// @vitest-environment jsdom

import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
import type { DimensionDefinitionView } from "@/features/modeling/contracts/dimensionDefinitionContract";
import type { ModelImplementationCapabilities } from "@/features/modeling/contracts/modelImplementationContract";
import type { ModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";

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

const mocks = vi.hoisted(() => ({
	listBusinessProcessesApi: vi.fn().mockResolvedValue([
		{
			id: "process-row-1",
			version: 1,
			processId: "budget_execution",
			domainId: "finance",
			name: "预算执行",
			sourceType: "MANUAL",
			confirmed: true,
			lifecycleStatus: "ACTIVE",
		},
	]),
}));

vi.mock("@/api/sprint64GovernanceApi", () => ({
	listBusinessProcessesApi: mocks.listBusinessProcessesApi,
}));

vi.mock("./ModelServingSyncStatus", () => ({
	ModelServingSyncStatus: () => null,
}));

import { ModelingWorkbenchEditor, type ModelingWorkbenchEditorProps } from "./ModelingWorkbenchEditor";
import type { ConceptDimensionDraft, ModelSpecDraft } from "./services/modelWorkbenchService";

let container: HTMLDivElement;
let root: Root;

const makeDraft = (patch: Partial<ModelSpecDraft> = {}): ModelSpecDraft => ({
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
	warehouseLayerCode: "DWD",
	implementationMode: "DESIGNER_GENERATED",
	implementationBase: null,
	implementationInputMode: "",
	generationStrategyType: "",
	implementationIdempotencyKey: "implementation-draft-1",
	creationOperationId: "create-draft-1",
	fieldMappings: [],
	casts: {},
	filters: [],
	deduplicateBy: [],
	joins: [],
	groupBy: [],
	aggregations: [],
	sourceRefs: [],
	dependsOn: [],
	dimensionRefs: [],
	factShape: "",
	timeSemanticsType: "",
	timeSemanticsFields: [],
	consumptionScenario: "",
	...patch,
});

const makeConceptDraft = (patch: Partial<ConceptDimensionDraft> = {}): ConceptDimensionDraft => ({
	createKind: "dimension",
	base: null,
	definitionBase: null,
	idempotencyKey: "concept-draft-1",
	domainId: "finance",
	name: "预算科目",
	description: "统一预算科目定义",
	reuseScope: "DOMAIN",
	attributes: [],
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

const implementationCapabilities: ModelImplementationCapabilities = {
	adapter: "postgres",
	inputModesByModelType: {
		DIMENSION: ["PHYSICAL_ASSET", "GENERATED"],
		FACT: ["PHYSICAL_ASSET", "UPSTREAM_MODEL"],
		SUMMARY: ["UPSTREAM_MODEL"],
		APPLICATION: ["UPSTREAM_MODEL"],
	},
	loadStrategies: ["FULL", "INCREMENTAL"],
	materializationsByLoadStrategy: { FULL: ["table", "view"], INCREMENTAL: ["incremental"] },
	settingKeys: ["casts", "loadStrategy", "partitionFields", "targetPhysicalName"],
	partitionFieldsSupported: false,
	incrementalKeyRequired: true,
};
const partitionCapabilities = { ...implementationCapabilities, partitionFieldsSupported: true };

const makeProps = (patch: Partial<ModelingWorkbenchEditorProps> = {}): ModelingWorkbenchEditorProps => ({
	draft: makeDraft(),
	context: {
		planId: "plan-1",
		domains: [
			{ id: "10000000-0000-0000-0000-000000000001", code: "business", name: "财务业务" },
			{ id: "20000000-0000-0000-0000-000000000001", code: "finance", name: "财务域", parentCode: "business" },
		],
		models: [],
		dimensions: [],
		standards: [],
		dataMarts: [
			{
				id: "mart-1",
				code: "PJM_ANALYTICS",
				name: "项目管理分析集市",
				purpose: "项目管理分析",
				ownerId: "owner-1",
				businessCategoryIds: ["category-1"],
				status: "CURRENT",
				revision: 1,
				checksum: "a".repeat(64),
				usageCount: 0,
				createdAt: "2026-08-12T00:00:00Z",
				updatedAt: "2026-08-12T00:00:00Z",
			},
		],
		subjectDomains: [
			{
				id: "subject-tech",
				code: "TECH_STATE_ANALYSIS",
				name: "技术状态主题",
				purpose: "技术状态分析",
				martId: "mart-1",
				status: "CURRENT",
				revision: 1,
				checksum: "b".repeat(64),
				createdAt: "2026-08-12T00:00:00Z",
				updatedAt: "2026-08-12T00:00:00Z",
			},
		],
		sources: [
			{
				bindingId: "50000000-0000-0000-0000-000000000001",
				sourceType: "CONNECTION_TABLE",
				locator: { namespace: "public", objectName: "ods_budget_execution" },
				sourceId: "ods_budget_execution",
				confirmationStatus: "CONFIRMED",
				displayName: "预算执行 ODS",
				resolvedVersion: "source-v1",
				resolutionStatus: "AVAILABLE",
				freshness: "CURRENT",
			},
		],
		warehouseLayers: [
			{
				code: "DWD",
				name: "明细事实 / 维度层",
				systemLayerCode: "DWD",
				kind: "DETAIL",
				responsibility: "业务明细",
				namingPrefixes: ["dwd_"],
				optional: false,
				builtin: true,
				deletable: false,
				disabledReason: "平台内置分层不可删除",
			},
			{
				code: "FIN_DETAIL",
				name: "财务明细层",
				systemLayerCode: "DWD",
				kind: "DETAIL",
				responsibility: "财务域明细",
				namingPrefixes: ["fin_dwd_"],
				optional: false,
				builtin: false,
				deletable: true,
				disabledReason: null,
			},
			{
				code: "FIN_SUMMARY",
				name: "财务汇总层",
				systemLayerCode: "DWS",
				kind: "SERVICE",
				responsibility: "财务域汇总",
				namingPrefixes: ["fin_dws_"],
				optional: false,
				builtin: false,
				deletable: true,
				disabledReason: null,
			},
		],
		implementationCapabilities,
	},
	dimensionDefinitions: [definition],
	dimensionDefinitionFailure: "",
	currentOwnerId: "current-owner",
	selectedModel: null,
	authoringContext: null,
	authoringFailure: "",
	authoringValidation: null,
	authoringBusy: "",
	authoringConflict: false,
	canMaintain: true,
	readOnly: false,
	saving: false,
	dirty: true,
	validationErrors: {},
	failureMessage: "",
	editorAccessMessage: "",
	fieldRowIds: ["field-1"],
	onChange: vi.fn(),
	onSave: vi.fn(),
	onValidateAuthoring: vi.fn(),
	onCommitAuthoring: vi.fn(),
	onForkPublished: vi.fn(),
	onOpenRawNode: vi.fn(),
	onRefresh: vi.fn(),
	onDialog: vi.fn(),
	onAddFields: vi.fn(),
	onRemoveBlankFields: vi.fn(),
	onUpdateField: vi.fn(),
	onDeleteField: vi.fn(),
	onStandardChange: vi.fn(),
	onSourcesChanged: vi.fn(),
	...patch,
});

async function render(props = makeProps()) {
	await act(async () => root.render(<ModelingWorkbenchEditor {...props} />));
	return props;
}

// antd 会给“恰好两个汉字”的纯文字按钮自动插入空格（保存 → 保 存），比对前先去掉空白。
const squash = (value: string | null | undefined) => (value ?? "").replace(/\s/g, "");

function button(label: string) {
	const match = Array.from(container.querySelectorAll("button")).find(
		(item) => squash(item.textContent) === squash(label),
	);
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
	it("shows the wrongly bound category and lets a draft choose only its child domains", async () => {
		const categoryId = "10000000-0000-0000-0000-000000000001";
		const childId = "20000000-0000-0000-0000-000000000001";
		const props = makeProps({ definitionOnly: true, draft: makeDraft({ createKind: "fact", domainId: categoryId,
			base: { id: "model", domainId: categoryId, status: "DRAFT", compatibilityMode: "CANONICAL" } as ModelSpecView,
		}) });
		await render(props);
		const select = container.querySelector<HTMLSelectElement>('select[aria-label="数据域"]')!;
		expect(select.disabled).toBe(false);
		expect(select.value).toBe(categoryId);
		expect(select.selectedOptions[0].textContent).toContain("误选业务分类");
		expect(Array.from(select.options).filter((option) => !option.disabled && option.value).map((option) => option.value)).toEqual([childId]);
		await act(async () => { select.value = childId; select.dispatchEvent(new Event("change", { bubbles: true })); });
		expect(props.onChange).toHaveBeenLastCalledWith(expect.objectContaining({ domainId: childId, businessProcessId: "" }));
	});

	it("preserves the saved business process when the lookup fails", async () => {
		mocks.listBusinessProcessesApi.mockRejectedValueOnce(new Error("forbidden"));
		const props = makeProps({ definitionOnly: true, draft: makeDraft({ createKind: "fact", businessProcessId: "saved-process" }) });
		await render(props);
		expect(props.onChange).not.toHaveBeenCalled();
		expect(container.textContent).toContain("业务过程读取失败");
		expect(container.textContent).toContain("已保留原选择");
		expect(container.textContent).not.toContain("已自动绑定业务过程");
	});

	it("requires an explicit replacement when a saved business process is absent from the list", async () => {
		const props = makeProps({ definitionOnly: true, draft: makeDraft({ createKind: "fact", businessProcessId: "saved-process" }) });
		await render(props);
		expect(props.onChange).not.toHaveBeenCalled();
		expect(container.querySelector<HTMLSelectElement>('select[aria-label="业务过程"]')?.value).toBe("saved-process");
		expect(container.textContent).toContain("请核对数仓规划或重新选择后保存");
	});

	it("does not auto-bind a business process in read-only mode", async () => {
		const props = makeProps({ definitionOnly: true, readOnly: true, draft: makeDraft({ createKind: "fact", businessProcessId: "" }) });
		await render(props);
		expect(props.onChange).not.toHaveBeenCalled();
	});

	it("waits for the new domain lookup before auto-binding its business process", async () => {
		const props = makeProps({ definitionOnly: true, draft: makeDraft({ createKind: "fact", businessProcessId: "process-row-1" }) });
		await render(props);
		let resolveLookup!: (items: unknown[]) => void;
		mocks.listBusinessProcessesApi.mockImplementationOnce(() => new Promise((resolve) => { resolveLookup = resolve; }));
		await render({ ...props, draft: makeDraft({ createKind: "fact", domainId: "new-domain", businessProcessId: "" }) });
		expect(props.onChange).not.toHaveBeenCalled();
		await act(async () => resolveLookup([{ id: "new-process", name: "新业务过程", confirmed: true, lifecycleStatus: "ACTIVE" }]));
		expect(props.onChange).toHaveBeenLastCalledWith(expect.objectContaining({ domainId: "new-domain", businessProcessId: "new-process" }));
	});

	it("shows imported SQL configuration and allows continuing without an empty visual form", async () => {
		const selectedModel = {
			id: "imported-model",
			status: "DRAFT",
			revision: 1,
			checksum: "a".repeat(64),
			compatibilityMode: "CANONICAL",
		} as ModelSpecView;
		const implementation = {
			id: "impl-1",
			modelSpecId: selectedModel.id,
			planId: "plan-1",
			revision: 1,
			modelChecksum: selectedModel.checksum,
			ownership: "DBT_MANAGED" as const,
			projectKey: "patent",
			dbtUniqueId: "model.patent.dim_status",
			status: "ACTIVE",
			implementationRevision: 1,
			implementationChecksum: "b".repeat(64),
			inputMode: "GENERATED" as const,
			inputs: [{ generatorType: "DBT", config: { resourcePath: "models/dim_status.sql" } }],
			fieldMappings: [],
			settings: { targetPhysicalName: "dim_status", loadStrategy: "FULL", partitionFields: [] },
			materialization: "table",
		};
		const props = makeProps({
			dirty: false,
			onViewChange: vi.fn(),
			onNext: vi.fn(),
			draft: makeDraft({
				base: selectedModel,
				implementationMode: "DBT_MANAGED",
				implementationBase: implementation,
				implementationInputMode: "",
				physicalName: "dim_status",
			}),
			selectedModel,
			authoringContext: {
				model: selectedModel,
				implementation,
				provenance: { origin: "DBT_ZIP_IMPORT", lossless: true },
				projection: {
					coverage: "UNKNOWN",
					lossless: false,
					managedPaths: [],
					rawNodes: [],
					reasons: ["MODEL_AUTHORING_PROJECTION_NOT_PREPARED"],
				},
				openDraft: null,
				allowedActions: ["EDIT_MODEL", "EDIT_IMPLEMENTATION"],
				publishedForkRequired: false,
			},
		});
		await render(props);
		expect(container.querySelector<HTMLInputElement>('[aria-label="产出表英文名"]')?.value).toBe("dim_status");
		expect(container.querySelector<HTMLInputElement>('[aria-label="产出表英文名"]')?.readOnly).toBe(true);
		expect(container.querySelector<HTMLInputElement>('[aria-label="加载策略"]')?.value).toBe("全量");
		expect(container.textContent).toContain("models/dim_status.sql");
		expect(container.textContent).not.toContain("加工配置尚未生成");
		expect(container.querySelector('select[aria-label="数据来源方式"]')).toBeNull();
		expect(button("下一步").disabled).toBe(false);
		await act(async () => button("下一步").click());
		expect(props.onNext).toHaveBeenCalledOnce();
		const edit = Array.from(container.querySelectorAll("button")).find((button) =>
			button.textContent?.includes("编辑 SQL 代码"),
		);
		await act(async () => edit?.click());
		expect(props.onViewChange).toHaveBeenCalledWith("code");
	});
	it("binds a manually created dimension table to a confirmed warehouse source", async () => {
		const initial = await render();
		const mode = container.querySelector<HTMLSelectElement>('select[aria-label="数据来源方式"]');
		expect(mode).not.toBeNull();
		await act(async () => {
			if (!mode) return;
			mode.value = "PHYSICAL_ASSET";
			mode.dispatchEvent(new Event("change", { bubbles: true }));
		});
		expect(initial.onChange).toHaveBeenCalledWith(
			expect.objectContaining({ implementationInputMode: "PHYSICAL_ASSET" }),
		);
		const props = await render(makeProps({ draft: makeDraft({ implementationInputMode: "PHYSICAL_ASSET" }) }));

		const source = container.querySelector<HTMLInputElement>('input[aria-label="选择来源 预算执行 ODS"]');
		expect(source).not.toBeNull();
		await act(async () => {
			source?.click();
		});

		expect(props.onChange).toHaveBeenLastCalledWith(
			expect.objectContaining({
				implementationInputMode: "PHYSICAL_ASSET",
				sourceRefs: [
					expect.objectContaining({
						kind: "TABLE",
						layer: "ODS",
						role: "PRIMARY",
						sourceBindingId: "50000000-0000-0000-0000-000000000001",
						resolvedVersion: "source-v1",
					}),
				],
			}),
		);
	});

	it("explains model inputs with terms familiar to traditional warehouse users", async () => {
		await render(
			makeProps({
				draft: makeDraft({
					createKind: "fact",
					implementationInputMode: "PHYSICAL_ASSET",
					businessProcessId: "process-row-1",
					grainStatement: "一条预算执行明细一行",
				}),
			}),
		);

		const factSourceMode = container.querySelector<HTMLSelectElement>('select[aria-label="数据来源方式"]');
		expect(Array.from(factSourceMode?.options || []).map((option) => option.textContent)).toEqual([
			"请选择数据来源方式",
			"直接选择输入源表",
			"引用已有上游模型",
		]);
		expect(container.textContent).not.toContain("明细表可以直接读取 ODS 或源系统表，也可以基于已有明细模型继续加工。");
		expect(container.textContent).toContain("输入源表");
		expect(container.textContent).toContain("从资产目录登记源表");
		expect(container.textContent).toContain("已确认输入源表 · 版本 source-v1");
		expect(container.textContent).not.toContain("CONNECTION_TABLE");
		expect(container.querySelector<HTMLInputElement>('input[aria-label="产出表英文名"]')).not.toBeNull();

		await render(
			makeProps({
				draft: makeDraft({
					createKind: "summary",
					implementationInputMode: "UPSTREAM_MODEL",
					warehouseLayerCode: "FIN_SUMMARY",
					grainStatement: "一个预算科目一行",
				}),
			}),
		);
		expect(container.textContent).not.toContain("汇总表基于已治理的上游模型进行汇总，无需登记物理源表。");
		expect(container.textContent).not.toContain("从资产目录登记源表");

		await render(makeProps({ draft: makeConceptDraft() }));
		expect(container.textContent).not.toContain("数据来源与加工方式");
	});

	it("hides implementation ownership while keeping source relationships editable", async () => {
		await render(
			makeProps({
				draft: makeDraft({ implementationMode: "DBT_MANAGED", implementationInputMode: "PHYSICAL_ASSET" }),
			}),
		);
		expect(container.querySelector<HTMLSelectElement>('select[aria-label="实现维护方式"]')).toBeNull();
		expect(container.querySelector<HTMLInputElement>('input[aria-label="选择来源 预算执行 ODS"]')).toHaveProperty(
			"disabled",
			false,
		);
	});

	it("uses the same controlled date generator after an imported model selects visual generation", async () => {
		const props = await render(
			makeProps({
				draft: makeDraft({
					implementationMode: "DBT_MANAGED",
					implementationInputMode: "",
					generationStrategyType: "",
				}),
			}),
		);
		const source = container.querySelector<HTMLSelectElement>('select[aria-label="数据来源方式"]');
		expect(source).not.toBeNull();
		expect(Array.from(source?.options || []).map((option) => option.textContent)).toContain("按模型生成");
		expect(container.textContent).not.toContain("维度表可以从输入源表加工，日期维度也可以由系统生成。");

		await act(async () => {
			if (!source) return;
			source.value = "GENERATED";
			source.dispatchEvent(new Event("change", { bubbles: true }));
		});
		expect(props.onChange).toHaveBeenCalledWith(
			expect.objectContaining({ implementationInputMode: "GENERATED", generationStrategyType: "DATE_DIMENSION" }),
		);
	});

	it("shows FACT semantics and APPLICATION consumption scenario in the unified form", async () => {
		await render(
			makeProps({
				definitionOnly: true,
				draft: makeDraft({
					createKind: "fact",
					grainStatement: "一条预算执行明细一行",
					businessProcessId: "process-row-1",
					factShape: "TRANSACTION",
					timeSemanticsType: "EVENT_TIME",
					timeSemanticsFields: ["event_time"],
					fields: [...makeDraft().fields, { name: "event_time", dataType: "TIMESTAMP", nullable: false, role: "TIME" }],
				}),
			}),
		);

		for (const label of ["事实类型", "时间字段", "时间字段"])
			expect(container.textContent).toContain(label);
		expect(container.textContent).not.toContain("数据来源方式");

		await render(
			makeProps({
				definitionOnly: true,
				draft: makeDraft({
					createKind: "application",
					warehouseLayerCode: "ADS",
					grainStatement: "一个应用输出对象一行",
					implementationInputMode: "UPSTREAM_MODEL",
				}),
			}),
		);
		expect(container.textContent).toContain("应用场景");
		expect(container.textContent).not.toContain("应用表基于已治理的上游模型加工，无需登记物理源表。");
	});

	it("selects partition fields from the current model fields instead of accepting arbitrary text", async () => {
		const fields = [
			...makeDraft().fields,
			{
				name: "event_time",
				displayName: "业务时间",
				dataType: "TIMESTAMP",
				nullable: false,
				role: "TIME" as const,
			},
		];
		const props = await render(
			makeProps({
				context: { ...makeProps().context, implementationCapabilities: partitionCapabilities },
				draft: makeDraft({
					createKind: "fact",
					grainStatement: "一条预算执行明细一行",
					businessProcessId: "process-row-1",
					fields,
				}),
			}),
		);
		const selector = container.querySelector<HTMLSelectElement>('select[aria-label="分区字段"]');
		expect(selector).not.toBeNull();
		expect(container.querySelector<HTMLInputElement>('input[aria-label="分区字段"]')).toBeNull();
		expect(Array.from(selector?.options || []).map((option) => option.textContent)).toContain(
			"业务时间（event_time · TIMESTAMP）",
		);

		await act(async () => {
			if (!selector) return;
			selector.value = "event_time";
			selector.dispatchEvent(new Event("change", { bubbles: true }));
		});
		expect(props.onChange).toHaveBeenLastCalledWith(expect.objectContaining({ partitionFields: "event_time" }));

		const selectedProps = await render(
			makeProps({
				context: { ...makeProps().context, implementationCapabilities: partitionCapabilities },
				draft: makeDraft({
					createKind: "fact",
					grainStatement: "一条预算执行明细一行",
					businessProcessId: "process-row-1",
					fields,
					partitionFields: "event_time",
				}),
			}),
		);
		expect(container.querySelector('[aria-label="已选分区字段"]')?.textContent).toContain("业务时间");

		await act(async () => {
			container.querySelector<HTMLButtonElement>('button[aria-label="移除分区字段 event_time"]')?.click();
		});
		expect(selectedProps.onChange).toHaveBeenLastCalledWith(expect.objectContaining({ partitionFields: "" }));
	});

	it("lets an APPLICATION bind a current data mart and subject domain", async () => {
		await render(
			makeProps({
				definitionOnly: true,
				draft: makeDraft({
					createKind: "application",
					warehouseLayerCode: "ADS",
					grainStatement: "一个技术状态月度指标一行",
					implementationInputMode: "UPSTREAM_MODEL",
					consumptionScenario: "技术状态月度看板",
					dataMartId: "mart-1",
					subjectDomainId: "subject-tech",
				}),
			}),
		);

		const dataMart = container.querySelector<HTMLSelectElement>('select[aria-label="数据集市"]');
		const subjectDomain = container.querySelector<HTMLSelectElement>('select[aria-label="主题域"]');
		expect(dataMart?.value).toBe("mart-1");
		expect(subjectDomain?.value).toBe("subject-tech");
		expect(subjectDomain?.textContent).toContain("技术状态主题 · TECH_STATE_ANALYSIS");
	});

	it("lets a FACT bind pinned dimension revisions independently of its physical source", async () => {
		const dimension = {
			id: "dimension-model-1",
			domainId: "finance",
			name: "风险等级维度表",
			modelType: "DIMENSION",
			layer: "DWD",
			revision: 2,
			compatibilityMode: "CANONICAL",
		} as ModelSpecView;
		const draft = makeDraft({
			createKind: "fact",
			implementationInputMode: "PHYSICAL_ASSET",
			sourceRefs: [
				{
					kind: "TABLE",
					layer: "ODS",
					ref: "public.ods_budget_execution",
					role: "PRIMARY",
					sortOrder: 0,
					sourceBindingId: "50000000-0000-0000-0000-000000000001",
					resolvedVersion: "source-v1",
				},
			],
		});
		const baseProps = makeProps();
		const props = await render(
			makeProps({ definitionOnly: true, draft, context: { ...baseProps.context, models: [dimension] } }),
		);

		const checkbox = container.querySelector<HTMLInputElement>('input[aria-label="引用维度模型 风险等级维度表"]');
		expect(checkbox).not.toBeNull();
		expect(container.querySelector('[aria-label="数据来源方式"]')).toBeNull();
		await act(async () => checkbox?.click());

		expect(props.onChange).toHaveBeenLastCalledWith(
			expect.objectContaining({
				sourceRefs: draft.sourceRefs,
				dimensionRefs: [{ modelSpecId: dimension.id, revision: dimension.revision }],
			}),
		);

		await render({ ...props, definitionOnly: false });
		expect(container.querySelector('[aria-label="数据来源方式"]')).not.toBeNull();
		expect(container.querySelector('[aria-label="引用维度模型 风险等级维度表"]')).toBeNull();
	});

	it("renders the concept-dimension form without dimension-table fields or lifecycle actions", async () => {
		await render(makeProps({ draft: makeConceptDraft(), dimensionDefinitions: [], fieldRowIds: [] }));

		for (const label of ["数仓分层", "数据域", "系统编码", "中文名称", "描述"])
			expect(container.textContent).toContain(label);
		for (const label of [
			"存储策略",
			"表名规则",
			"表名",
			"表中文名",
			"生命周期",
			"负责人",
			"字段管理",
			"字段名称",
			"提交",
			"刷新",
			"关联关系",
			"发布",
			"日志",
			"质量检查",
			"高级 dbt 工作区",
			"导出",
		])
			expect(container.textContent).not.toContain(label);

		expect(container.querySelectorAll(".dmx-editor-toolbar button")).toHaveLength(1);
		expect(button("保存草稿")).toBeDefined();
		expect(container.querySelector<HTMLInputElement>('input[aria-label="系统编码"]')).toHaveProperty(
			"value",
			"保存后生成",
		);
	});

	it("keeps a saved DRAFT concept dimension editable without an attributes section", async () => {
		await render(
			makeProps({
				draft: makeConceptDraft({
					definitionBase: { ...definition, status: "DRAFT" as const },
					attributes: [{ code: "SUBJECT_CODE", name: "预算科目编码", definition: "", primaryKey: true, order: 1 }],
				}),
				dimensionDefinitions: [],
				fieldRowIds: [],
			}),
		);

		expect(container.querySelector("fieldset")).not.toHaveProperty("disabled", true);
		const saveButton = [...container.querySelectorAll("button")].find(
			(item) => squash(item.textContent).includes("保存") && !squash(item.textContent).includes("保存中"),
		);
		expect(saveButton).toBeDefined();
		expect((saveButton as HTMLButtonElement).disabled).toBe(false);
		expect(container.textContent).not.toContain("维度属性");
		expect(container.querySelector<HTMLInputElement>('input[type="checkbox"]')).toBeNull();
	});

	it("keeps a CURRENT concept dimension editable", async () => {
		await render(
			makeProps({
				draft: makeConceptDraft({ definitionBase: { ...definition, status: "CURRENT" as const } }),
				dimensionDefinitions: [],
				fieldRowIds: [],
				readOnly: false,
				dirty: true,
			}),
		);

		expect(container.querySelector("fieldset")).not.toHaveProperty("disabled", true);
		expect(button("保存草稿")).toHaveProperty("disabled", false);
	});

	it("shows the returned system code for a confirmed concept dimension", async () => {
		await render(
			makeProps({
				draft: makeConceptDraft({ definitionBase: { ...definition, status: "CURRENT" as const } }),
				dimensionDefinitions: [],
				fieldRowIds: [],
			}),
		);

		expect(container.querySelector<HTMLInputElement>('input[aria-label="系统编码"]')).toHaveProperty(
			"value",
			"DIM000001",
		);
	});

	it("renders the W1 dimension definition without W2 implementation controls", async () => {
		await render(makeProps({ definitionOnly: true }));
		for (const label of ["数仓分层", "数据域", "维度", "表中文名", "描述"])
			expect(container.textContent).toContain(label);
		for (const label of ["产出表英文名", "数据来源方式", "加载策略", "分区字段"])
			expect(container.textContent).not.toContain(label);
		expect(container.querySelectorAll('[aria-label="当前步骤操作"] .ant-btn-primary')).toHaveLength(1);
		expect(button("保存并继续")).toBeDefined();
	});

	it("disables the fieldset in read-only mode and shows table-name validation beside its field", async () => {
		await render(
			makeProps({
				readOnly: true,
				validationErrors: { physicalName: "产出表英文名只能使用小写字母、数字和下划线" },
			}),
		);

		expect(container.querySelector("fieldset")).toHaveProperty("disabled", true);
		const tableName = container.querySelector<HTMLInputElement>('input[aria-label="产出表英文名"]');
		expect(tableName?.closest("label")?.textContent).toContain("产出表英文名只能使用小写字母、数字和下划线");
	});

	it("keeps a missing persisted table name editable in the implementation step", async () => {
		await render(
			makeProps({
				draft: makeDraft({ base: { status: "DRAFT" } as ModelSpecView, physicalName: "" }),
			}),
		);

		const tableName = container.querySelector<HTMLInputElement>('input[aria-label="产出表英文名"]');
		expect(tableName).toHaveProperty("disabled", false);
	});

	it("shows the reason for a read-only editor instead of a silent disabled form", async () => {
		await render(
			makeProps({
				readOnly: true,
				editorAccessMessage: "当前版本为 PUBLISHED，只能查看；请创建新的草稿版本后修改。",
			}),
		);

		expect(container.querySelector("output.dmx-editor-access-note")?.textContent).toContain("PUBLISHED");
		expect(container.querySelector("fieldset")).toHaveProperty("disabled", true);
	});

	it("requires an explicit controlled generator choice for a new date implementation", async () => {
		const props = makeProps({
			draft: makeDraft({ implementationBase: null, implementationInputMode: "", generationStrategyType: "" }),
		});
		await render(props);

		const source = container.querySelector<HTMLSelectElement>('select[aria-label="数据来源方式"]');
		expect(source).toBeDefined();
		expect(source?.value).toBe("");
		await act(async () => {
			if (!source) return;
			source.value = "GENERATED";
			source.dispatchEvent(new Event("change", { bubbles: true }));
		});
		expect(props.onChange).toHaveBeenCalledWith(
			expect.objectContaining({
				implementationInputMode: "GENERATED",
				generationStrategyType: "DATE_DIMENSION",
			}),
		);
	});

	it("freezes editor mutations and toolbar actions while saving", async () => {
		const selectedModel = { id: "model-1", compatibilityMode: "CANONICAL" } as ModelSpecView;
		await render(makeProps({ saving: true, selectedModel }));

		expect(container.querySelector("fieldset")).toHaveProperty("disabled", true);
		for (const action of container.querySelectorAll('[aria-label="当前步骤操作"] button'))
			expect(action).toHaveProperty("disabled", true);
	});

	it("keeps W1 dimension selectors editable", async () => {
		await render(makeProps({ definitionOnly: true }));
		for (const label of ["数据域", "维度"])
			expect(container.querySelector<HTMLSelectElement>(`select[aria-label="${label}"]`)).toHaveProperty("disabled", false);
		expect(container.querySelector<HTMLInputElement>('input[aria-label="表中文名"]')).toHaveProperty("disabled", false);
		expect(container.querySelector<HTMLTextAreaElement>('textarea[aria-label="描述"]')).toHaveProperty("disabled", false);
		expect(container.querySelector('[aria-label="产出表英文名"]')).toBeNull();
	});

	it("locks persisted dimension contract selectors and preserves missing references", async () => {
		const draft = makeDraft({
			base: { id: "model-1" } as ModelSpecView,
			domainId: "retired-domain",
			dimensionDefinitionId: "retired-dimension",
		});
		await render(
			makeProps({
				definitionOnly: true,
				draft,
				context: {
					planId: "plan-1",
					domains: [],
					models: [],
					dimensions: [],
					standards: [],
					warehouseLayers: [],
					sources: [],
					implementationCapabilities,
				},
				dimensionDefinitions: [],
			}),
		);

		const domainSelect = container.querySelector<HTMLSelectElement>('select[aria-label="数据域"]');
		expect(domainSelect).toHaveProperty("disabled", true);
		expect(domainSelect?.value).toBe("retired-domain");
		expect(domainSelect?.querySelector('option[value="retired-domain"]')).toHaveProperty("disabled", true);

		const definitionSelect = container.querySelector<HTMLSelectElement>('select[aria-label="维度"]');
		expect(definitionSelect).toHaveProperty("disabled", true);
		expect(definitionSelect?.value).toBe("retired-dimension");
		expect(definitionSelect?.querySelector('option[value="retired-dimension"]')).toHaveProperty("disabled", true);
	});

	it("keeps W2 actions separate from delivery dialogs", async () => {
		const unpublished = await render();
		expect(button("提交加工配置并继续")).toHaveProperty("disabled", false);
		expect(button("暂存草稿")).toBeDefined();
		expect(unpublished.onDialog).not.toHaveBeenCalled();

		const selectedModel = { id: "model-1", compatibilityMode: "CANONICAL" } as ModelSpecView;
		const persisted = makeProps({
			dirty: false,
			selectedModel: { ...selectedModel, status: "DRAFT", revision: 1, checksum: "a".repeat(64) },
		});
		await render(persisted);
		expect(button("提交加工配置并继续")).toHaveProperty("disabled", true);
		expect(button("可视化模式")).toBeDefined();
		expect(button("代码模式")).toBeDefined();
	});

	it("renders an authoring request failure only once", async () => {
		await render(
			makeProps({
				authoringFailure: "模型创作草稿校验失败。",
				failureMessage: "模型创作草稿校验失败。",
			}),
		);

		expect(
			Array.from(container.querySelectorAll('[role="alert"]')).filter((item) =>
				item.textContent?.includes("模型创作草稿校验失败。"),
			),
		).toHaveLength(1);
	});

	it("uses provenance only as evidence and opens raw nodes in the shared code view", async () => {
		const selectedModel = {
			id: "model-imported",
			status: "DRAFT",
			revision: 3,
			checksum: "c".repeat(64),
			compatibilityMode: "CANONICAL",
			implementationMode: "DBT_MANAGED",
		} as ModelSpecView;
		const rawNode = { nodeId: "model.raw", kind: "RAW_SQL", editable: true, sourcePath: "models/raw.sql", line: 1 };
		const props = makeProps({
			draft: makeDraft({ base: selectedModel, implementationMode: "DBT_MANAGED" }),
			selectedModel,
			authoringContext: {
				model: selectedModel,
				implementation: null,
				provenance: { origin: "DBT_ZIP_IMPORT", sourceKind: "FROZEN_SOURCE_BUNDLE", lossless: true },
				projection: {
					coverage: "NONE",
					lossless: false,
					managedPaths: [],
					rawNodes: [rawNode],
					reasons: ["动态 SQL 保留为原始代码"],
				},
				openDraft: null,
				allowedActions: ["OPEN_VISUAL", "OPEN_CODE", "EDIT_MODEL", "EDIT_IMPLEMENTATION"],
				publishedForkRequired: false,
			},
		});
		await render(props);

		expect(container.querySelector("fieldset")).toHaveProperty("disabled", false);
		expect(container.textContent).not.toContain("来源 dbt ZIP 导入");
		expect(container.textContent).not.toContain("当前由代码维护");
		act(() => button("在代码视图定位").click());
		expect(props.onOpenRawNode).toHaveBeenCalledWith(rawNode);
	});

	it("blocks visual commit until the shared model definition issues are resolved", async () => {
		const selectedModel = {
			id: "model-draft",
			status: "DRAFT",
			revision: 3,
			checksum: "c".repeat(64),
			compatibilityMode: "CANONICAL",
			implementationMode: "DBT_MANAGED",
		} as ModelSpecView;
		await render(
			makeProps({
				draft: makeDraft({ base: selectedModel }),
				selectedModel,
				dirty: false,
				authoringValidation: {
					modelIssues: [
						{ code: "MODEL_SPEC_NAME_REQUIRED", field: "name", severity: "ERROR", message: "请填写模型名称" },
					],
					projectionIssues: [],
					implementationValidation: {
						draftId: "draft-92",
						state: "VALIDATED",
						etag: "etag-2",
						expiresAt: "2026-09-01T00:00:00Z",
						validatedChecksum: "b".repeat(64),
						diagnostics: [],
						proposedStructure: [],
					},
				},
			}),
		);

		expect(container.textContent).toContain("请填写模型名称");
		expect(Array.from(container.querySelectorAll("button")).some((item) => item.textContent === "提交加工配置")).toBe(
			false,
		);
	});

	it("keeps a published revision immutable and exposes one explicit fork action", async () => {
		const selectedModel = {
			id: "model-published",
			status: "PUBLISHED",
			revision: 7,
			checksum: "d".repeat(64),
			compatibilityMode: "CANONICAL",
		} as ModelSpecView;
		const props = makeProps({
			draft: makeDraft({ base: selectedModel }),
			selectedModel,
			readOnly: true,
			editorAccessMessage: "发布版本只读",
			authoringContext: {
				model: selectedModel,
				implementation: null,
				provenance: { origin: "SYSTEM_GENERATED", lossless: false },
				projection: {
					coverage: "UNKNOWN",
					lossless: false,
					managedPaths: [],
					rawNodes: [],
					reasons: ["MODEL_AUTHORING_PROJECTION_NOT_PREPARED"],
				},
				openDraft: null,
				allowedActions: ["OPEN_VISUAL", "OPEN_CODE", "FORK_DRAFT"],
				publishedForkRequired: true,
			},
		});
		await render(props);

		expect(container.querySelector("fieldset")).toHaveProperty("disabled", true);
		expect(container.textContent).toContain("发布版本只读");
		expect(container.textContent).not.toContain("加工配置尚未生成");
		act(() => button("创建新草稿版本").click());
		expect(props.onForkPublished).toHaveBeenCalledTimes(1);
	});

	it("keeps fact drafts on the explicit compatibility form", async () => {
		await render(makeProps({ definitionOnly: true, draft: makeDraft({ createKind: "fact" }) }));

		expect(container.textContent).toContain("模型粒度");
		expect(container.textContent).toContain("事实类型");
		expect(container.textContent).not.toContain("加载策略");
		expect(container.querySelector('[aria-label="数据来源方式"]')).toBeNull();
	});

	it("offers actual ODS planning layers for source definitions", async () => {
        const props = makeProps({ definitionOnly: true, draft: makeDraft({ createKind: "source", warehouseLayerCode: "ODS_RAW" }) });
        props.context.warehouseLayers = [
            ...props.context.warehouseLayers,
            ...["ODS_RAW", "ODS_STANDARDIZED"].map(code => ({ code, name: code, systemLayerCode: code, layerGroup: "STAGING", modelTypes: ["SOURCE"], kind: "SOURCE", responsibility: "source", namingPrefixes: [], optional: false, builtin: true, deletable: false } as any)),
        ];
        await render(props);
        const select = [...container.querySelectorAll("select")].find(item => item.value === "ODS_RAW");
        expect(Array.from(select?.options || []).map(option => option.value)).toEqual(["", "ODS_RAW", "ODS_STANDARDIZED"]);
    });

	it("lists only DWD-compatible layers for FACT drafts and excludes DWS custom layers", async () => {
		const draft = makeDraft({ createKind: "fact", warehouseLayerCode: "DWD" });
		await render(makeProps({ definitionOnly: true, draft }));

		const select = [...container.querySelectorAll("select")].find((item) =>
			[...item.options].some((option) => option.textContent?.includes("数仓分层") || item.value === "DWD"),
		);
		const options = Array.from(select?.options || []);
		expect(options.some((option) => option.textContent?.includes("FIN_DETAIL"))).toBe(true);
		expect(options.some((option) => option.textContent?.includes("FIN_SUMMARY"))).toBe(false);
		expect(options.some((option) => option.textContent?.includes(" · 自定义"))).toBe(true);
	});

	it("shows a disabled deleted-layer option so a persisted selection is never silently replaced", async () => {
		const draft = makeDraft({
			createKind: "fact",
			base: { id: "model-1" } as ModelSpecView,
			warehouseLayerCode: "GONE_LAYER",
		});
		await render(makeProps({ definitionOnly: true, draft }));

		const option = Array.from(container.querySelectorAll("option")).find((item) => item.value === "GONE_LAYER");
		expect(option).toBeDefined();
		expect(option?.textContent).toContain("已删除分层 · GONE_LAYER");
		expect(option?.disabled).toBe(true);
		const select = option?.closest("select") as HTMLSelectElement | null;
		expect(select?.value).toBe("GONE_LAYER");
	});

	it("keeps the concept-dimension form free of the warehouse layer selector", async () => {
		await render(makeProps({ draft: makeConceptDraft() }));

		expect(Array.from(container.querySelectorAll("option")).some((item) => item.value === "DWD")).toBe(false);
		expect(Array.from(container.querySelectorAll("option")).some((item) => item.value === "FIN_DETAIL")).toBe(false);
	});

	it("locks and preserves a persisted compatibility draft whose domain is missing", async () => {
		const draft = makeDraft({
			createKind: "fact",
			base: { id: "model-1" } as ModelSpecView,
			domainId: "retired-domain",
		});
		await render(
			makeProps({
				definitionOnly: true,
				draft,
				context: {
					planId: "plan-1",
					domains: [],
					models: [],
					dimensions: [],
					standards: [],
					warehouseLayers: [],
					sources: [],
					implementationCapabilities,
				},
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

describe("sprint-104 configuration editing", () => {
	it("permits TYPE2 metadata selection and binds current fields while explaining execution limits", async () => {
		const props = await render();
		const strategy = container.querySelector<HTMLSelectElement>('select[aria-label="历史保留策略"]');
		expect(strategy).not.toBeNull();
		await act(async () => {
			if (strategy) {
				strategy.value = "TYPE2";
				strategy.dispatchEvent(new Event("change", { bubbles: true }));
			}
		});
		expect(props.onChange).toHaveBeenLastCalledWith(
			expect.objectContaining({
				scdType: "TYPE2",
				dimensionProfile: expect.objectContaining({ scdPolicy: { type: "TYPE2" } }),
			}),
		);
		const type2 = makeProps({
			draft: makeDraft({ scdType: "TYPE2", dimensionProfile: { hierarchies: [], scdPolicy: { type: "TYPE2" } } }),
		});
		await render(type2);
		const binding = container.querySelector<HTMLSelectElement>('select[aria-label="生效开始字段"]');
		await act(async () => {
			if (binding) {
				binding.value = "subject_code";
				binding.dispatchEvent(new Event("change", { bubbles: true }));
			}
		});
		expect(type2.onChange).toHaveBeenLastCalledWith(
			expect.objectContaining({
				dimensionProfile: expect.objectContaining({ scdPolicy: { type: "TYPE2", effectiveFromField: "subject_code" } }),
			}),
		);
		expect(container.textContent).toContain("不提供历史版本维护");
	});

	it("shows unsupported persisted strategy and requires explicit clearing of old partitions", async () => {
		const props = makeProps({
			draft: makeDraft({
				createKind: "fact",
				loadStrategy: "SNAPSHOT",
				materialization: "snapshot",
				partitionFields: "old_month",
			}),
		});
		await render(props);
		expect(container.textContent).toContain("已保存但不可执行");
		expect(container.textContent).toContain("已保存分区：old_month");
		await act(async () => button("清空分区配置").click());
		expect(props.onChange).toHaveBeenLastCalledWith(
			expect.objectContaining({ partitionFields: "", loadStrategy: "SNAPSHOT" }),
		);
	});
});

describe("initial definition save", () => {
	it("shows logical dimension design and a single primary action", async () => {
		const props = await render(makeProps({ definitionOnly: true }));
		expect(container.querySelector('[aria-label="产出表英文名"]')).toBeNull();
		expect(container.querySelector('[aria-label="数据来源方式"]')).toBeNull();
		expect(container.querySelector('[aria-label="表中文名"]')).not.toBeNull();
		expect(container.querySelectorAll('[aria-label="当前步骤操作"] .ant-btn-primary')).toHaveLength(1);
		await act(async () => button("保存并继续").click());
		expect(props.onSave).toHaveBeenCalledOnce();
		expect(props.onValidateAuthoring).not.toHaveBeenCalled();
		expect(props.onCommitAuthoring).not.toHaveBeenCalled();
	});
	it("keeps application semantics editable before choosing upstream", async () => {
		await render(makeProps({ definitionOnly: true, draft: makeDraft({ createKind: "application" }) }));
		expect(container.querySelector('[aria-label="应用场景"]')).not.toBeNull();
		expect(container.querySelector('[aria-label="数据来源方式"]')).toBeNull();
	});
	it("keeps fact time semantics in definition and blocks save while busy", async () => {
		await render(makeProps({ definitionOnly: true, saving: true, draft: makeDraft({ createKind: "fact" }) }));
		expect(container.querySelector('[aria-label="事实类型"]')).not.toBeNull();
		expect(container.querySelector('[aria-label="时间字段"]')).not.toBeNull();
		expect(button("处理中…").disabled).toBe(true);
	});
});
