// @vitest-environment jsdom

import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
import type { DimensionDefinitionView } from "@/features/modeling/contracts/dimensionDefinitionContract";
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
	editorAccessMessage: "",
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
	it("binds a manually created dimension table to a confirmed warehouse source", async () => {
		const initial = await render();
		const mode = container.querySelector<HTMLSelectElement>('select[aria-label="实现输入方式"]');
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

	it("lets a new model choose manual dbt SQL ownership while keeping source relationships editable", async () => {
		const props = await render(makeProps({ draft: makeDraft({ implementationInputMode: "PHYSICAL_ASSET" }) }));
		const ownership = container.querySelector<HTMLSelectElement>('select[aria-label="实现维护方式"]');
		expect(ownership).not.toBeNull();

		await act(async () => {
			if (!ownership) return;
			ownership.value = "DBT_MANAGED";
			ownership.dispatchEvent(new Event("change", { bubbles: true }));
		});

		expect(props.onChange).toHaveBeenCalledWith(expect.objectContaining({ implementationMode: "DBT_MANAGED" }));
		await render(
			makeProps({
				draft: makeDraft({ implementationMode: "DBT_MANAGED", implementationInputMode: "PHYSICAL_ASSET" }),
			}),
		);
		expect(container.querySelector<HTMLInputElement>('input[aria-label="选择来源 预算执行 ODS"]')).toHaveProperty(
			"disabled",
			false,
		);
	});

	it("lets a manually maintained dimension declare that its SQL has no upstream", async () => {
		const props = await render(
			makeProps({
				draft: makeDraft({
					implementationMode: "DBT_MANAGED",
					implementationInputMode: "GENERATED",
					generationStrategyType: "",
				}),
			}),
		);
		const source = container.querySelector<HTMLSelectElement>('select[aria-label="实现输入方式"]');
		expect(source).not.toBeNull();
		expect(Array.from(source?.options || []).map((option) => option.textContent)).toContain("无上游（手工 SQL 生成）");
		expect(container.textContent).toContain("当前 SQL 不读取物理来源或上游模型");

		await act(async () => {
			if (!source) return;
			source.value = "PHYSICAL_ASSET";
			source.dispatchEvent(new Event("change", { bubbles: true }));
		});
		expect(props.onChange).toHaveBeenCalledWith(
			expect.objectContaining({ implementationInputMode: "PHYSICAL_ASSET", generationStrategyType: "" }),
		);
	});

	it("shows FACT semantics and APPLICATION consumption scenario in the unified form", async () => {
		await render(
			makeProps({
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

		for (const label of ["实现输入方式", "事实类型", "时间语义", "时间字段"])
			expect(container.textContent).toContain(label);

		await render(
			makeProps({
				draft: makeDraft({
					createKind: "application",
					warehouseLayerCode: "ADS",
					grainStatement: "一个应用输出对象一行",
					implementationInputMode: "UPSTREAM_MODEL",
				}),
			}),
		);
		expect(container.textContent).toContain("应用场景");
	});

	it("lets an APPLICATION bind a current data mart and subject domain", async () => {
		await render(
			makeProps({
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
		const props = await render(makeProps({ draft, context: { ...baseProps.context, models: [dimension] } }));

		const checkbox = container.querySelector<HTMLInputElement>('input[aria-label="引用维度模型 风险等级维度表"]');
		expect(checkbox).not.toBeNull();
		await act(async () => checkbox?.click());

		expect(props.onChange).toHaveBeenLastCalledWith(
			expect.objectContaining({
				sourceRefs: draft.sourceRefs,
				dimensionRefs: [{ modelSpecId: dimension.id, revision: dimension.revision }],
			}),
		);
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
			"质量规则",
			"高级 dbt 工作区",
			"导出",
		])
			expect(container.textContent).not.toContain(label);

		expect(container.querySelectorAll(".dmx-editor-toolbar button")).toHaveLength(1);
		expect(button("保存")).toBeDefined();
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

	it("locks a confirmed concept dimension", async () => {
		await render(
			makeProps({
				draft: makeConceptDraft({ definitionBase: { ...definition, status: "CURRENT" as const } }),
				dimensionDefinitions: [],
				fieldRowIds: [],
			}),
		);

		expect(container.querySelector("fieldset")).toHaveProperty("disabled", true);
		expect(button("保存")).toHaveProperty("disabled", true);
	});

	it("shows the returned system code and locks a confirmed concept dimension", async () => {
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
		expect(container.querySelector("fieldset")).toHaveProperty("disabled", true);
		expect(button("保存")).toHaveProperty("disabled", true);
	});

	it("renders the approved dimension form and toolbar without compatibility-only controls", async () => {
		await render();
		for (const label of [
			"数仓分层",
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

		for (const label of ["保存", "提交", "刷新", "关联关系", "发布", "日志", "质量规则", "高级 dbt 工作区", "导出"])
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

	it("keeps a missing persisted table name editable and explains the required backfill", async () => {
		await render(
			makeProps({
				draft: makeDraft({ base: { status: "DRAFT" } as ModelSpecView, physicalName: "" }),
			}),
		);

		const tableName = container.querySelector<HTMLInputElement>('input[aria-label="表名"]');
		expect(tableName).toHaveProperty("disabled", false);
		expect(tableName?.closest("label")?.textContent).toContain("历史草稿尚未保存物理表名，请补录后保存");
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

		const source = container.querySelector<HTMLSelectElement>('select[aria-label="实现输入方式"]');
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
		for (const label of ["保存中…", "提交", "刷新", "关联关系", "发布", "日志", "质量规则", "高级 dbt 工作区", "导出"])
			expect(button(label)).toHaveProperty("disabled", true);
	});

	it("keeps dimension draft selects editable and authority inputs disabled", async () => {
		await render();

		for (const label of ["数仓分层", "数据域", "存储策略", "维度"])
			expect(container.querySelector<HTMLSelectElement>(`select[aria-label="${label}"]`)).toHaveProperty(
				"disabled",
				false,
			);
		for (const label of ["表名规则", "生命周期", "负责人"])
			expect(container.querySelector<HTMLInputElement>(`input[aria-label="${label}"]`)).toHaveProperty(
				"disabled",
				true,
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

	it("locks persisted dimension contract selectors and preserves missing references", async () => {
		const draft = makeDraft({
			base: { id: "model-1" } as ModelSpecView,
			domainId: "retired-domain",
			dimensionDefinitionId: "retired-dimension",
		});
		await render(
			makeProps({
				draft,
				context: {
					planId: "plan-1",
					domains: [],
					models: [],
					dimensions: [],
					standards: [],
					warehouseLayers: [],
					sources: [],
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

	it("disables unpublished actions and maps every supported toolbar dialog", async () => {
		const unpublished = await render();
		for (const label of ["提交", "关联关系", "发布", "日志", "质量规则", "高级 dbt 工作区", "导出"]) {
			expect(button(label)).toHaveProperty("disabled", true);
			act(() => button(label).click());
		}
		expect(unpublished.onDialog).not.toHaveBeenCalled();

		const selectedModel = { id: "model-1", compatibilityMode: "CANONICAL" } as ModelSpecView;
		const published = makeProps({ dirty: false, selectedModel });
		await render(published);
		for (const [label, dialog] of [
			["提交", "gates"],
			["关联关系", "association"],
			["发布", "publish"],
			["日志", "logs"],
			["质量规则", "quality"],
			["高级 dbt 工作区", "advanced"],
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

	it("lists only DWD-compatible layers for FACT drafts and excludes DWS custom layers", async () => {
		const draft = makeDraft({ createKind: "fact", warehouseLayerCode: "DWD" });
		await render(makeProps({ draft }));

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
		await render(makeProps({ draft }));

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
				draft,
				context: {
					planId: "plan-1",
					domains: [],
					models: [],
					dimensions: [],
					standards: [],
					warehouseLayers: [],
					sources: [],
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
