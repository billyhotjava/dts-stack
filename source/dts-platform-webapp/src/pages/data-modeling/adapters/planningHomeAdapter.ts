import { listDataMarts } from "@/api/dataMartApi";
import { listModelSpecs } from "@/api/modelSpecApi";
import catalogDomainService, { type CatalogDomain } from "@/api/services/catalogDomainService";
import {
	listIndicatorsForModelingOverview,
	listStandardsForModelingOverview,
} from "@/api/services/modelingOverviewFactService";
import {
	listBusinessProcessesApi,
	listWarehouseLayersApi,
	type Sprint64BusinessProcess,
	type Sprint64WarehouseLayer,
} from "@/api/sprint64GovernanceApi";
import {
	getWarehousePlanPolicy,
	getWarehousePlanStageProjection,
	listWarehousePlans,
	type WarehousePlanHeader,
	type WarehousePlanPolicyView,
	type WarehousePlanStageProjection,
} from "@/api/warehousePlanApi";
import type { ModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
import {
	stageStatusLabel,
	warehousePlanLifecycleLabel,
	warehouseStageLabel,
} from "@/features/modeling/navigation/warehousePlanViewModel";
import type { DemoRow, TableColumn } from "../types";

export type ModelingLoadFailure = {
	kind: "permission" | "error";
	message: string;
};

export type PlanningCatalog = {
	columns: TableColumn[];
	rows: DemoRow[];
	domains: CatalogDomain[];
	policy: { value: WarehousePlanPolicyView; version: number } | null;
	readOnlyReason: string | null;
};

export type ModelingHomeProjection = {
	stats: Array<{ label: string; value: number; meta: string }>;
	recentModels: DemoRow[];
	tasks: DemoRow[];
	delivery: Array<{ label: string; count: number; total: number; percent: number }>;
};

const arrayPayload = <T>(raw: unknown): T[] => {
	if (Array.isArray(raw)) return raw as T[];
	if (!raw || typeof raw !== "object") return [];
	const record = raw as Record<string, unknown>;
	if (Array.isArray(record.content)) return record.content as T[];
	if (Array.isArray(record.data)) return record.data as T[];
	if (record.data && typeof record.data === "object") {
		const nested = record.data as Record<string, unknown>;
		if (Array.isArray(nested.content)) return nested.content as T[];
	}
	return [];
};

const responseStatus = (error: unknown): number | null => {
	if (!error || typeof error !== "object") return null;
	const status = Number((error as { response?: { status?: unknown } }).response?.status);
	return Number.isInteger(status) ? status : null;
};

export function classifyModelingLoadFailure(error: unknown, fallback: string): ModelingLoadFailure {
	const status = responseStatus(error);
	if (status === 401 || status === 403) {
		return { kind: "permission", message: "当前账号无权访问该建模数据，请联系管理员授权。" };
	}
	return { kind: "error", message: fallback };
}

export const loadWarehousePlanningHeaders = () => listWarehousePlans();

const domainColumns: TableColumn[] = [
	{ key: "code", title: "分类编码", width: 200 },
	{ key: "name", title: "分类名称" },
	{ key: "parent", title: "上级分类", width: 200 },
];

const planRows = (plans: WarehousePlanHeader[]): DemoRow[] =>
	plans.map((plan) => ({
		id: plan.id,
		code: plan.code,
		name: plan.name,
		owner: plan.ownerId,
		mode: plan.onboardingMode === "BUSINESS_FIRST" ? "业务驱动" : "资产驱动",
		status: warehousePlanLifecycleLabel(plan.lifecycleStatus),
		version: plan.version,
	}));

const processRows = (items: Sprint64BusinessProcess[]): DemoRow[] =>
	items.map((item) => ({
		id: item.processId,
		code: item.processId,
		name: item.name,
		domain: item.domainId,
		status: item.confirmed ? "已确认" : "候选",
		description: item.description || "—",
	}));

const layerRows = (items: Sprint64WarehouseLayer[]): DemoRow[] =>
	items.map((item) => ({
		code: item.code,
		name: item.title,
		type: item.kind || "—",
		description: item.responsibility,
		prefixes: item.namingPrefixes.join("、") || "—",
		status: item.optional ? "可选" : "必选",
	}));

export async function loadPlanningCatalog(
	view: string,
	context: { plans: WarehousePlanHeader[]; planId?: string; domainId?: string },
): Promise<PlanningCatalog> {
	if (view === "spaces") {
		return {
			columns: [
				{ key: "code", title: "计划编码", width: 190 },
				{ key: "name", title: "建设计划" },
				{ key: "owner", title: "负责人", width: 160 },
				{ key: "mode", title: "启动方式", width: 120 },
				{ key: "status", title: "状态", width: 130 },
				{ key: "version", title: "版本", width: 80 },
			],
			rows: planRows(context.plans),
			domains: [],
			policy: null,
			readOnlyReason: null,
		};
	}

	if (view === "domains" || view === "business-categories") {
		const domains = await catalogDomainService.list();
		const nameByCode = new Map(domains.map((domain) => [domain.code, domain.name]));
		return {
			columns: domainColumns,
			rows: domains.map((domain) => ({
				code: domain.code,
				name: domain.name,
				parent: domain.parentCode ? nameByCode.get(domain.parentCode) || domain.parentCode : "—",
			})),
			domains,
			policy: null,
			readOnlyReason: "业务分类由数据治理目录统一维护，本页提供真实只读投影。",
		};
	}

	if (view === "processes") {
		const domains = await catalogDomainService.list();
		const domainId = context.domainId || domains[0]?.code;
		const processes = domainId ? await listBusinessProcessesApi(domainId) : [];
		return {
			columns: [
				{ key: "code", title: "过程编码", width: 210 },
				{ key: "name", title: "过程名称" },
				{ key: "domain", title: "数据域", width: 190 },
				{ key: "status", title: "状态", width: 110 },
				{ key: "description", title: "业务定义" },
			],
			rows: processRows(processes),
			domains,
			policy: null,
			readOnlyReason: "业务过程由数据治理目录统一维护，本页提供真实只读投影。",
		};
	}

	if (view === "layers") {
		const layers = await listWarehouseLayersApi();
		return {
			columns: [
				{ key: "code", title: "分层编码", width: 140 },
				{ key: "name", title: "分层名称" },
				{ key: "type", title: "分层类型", width: 150 },
				{ key: "description", title: "加工责任" },
				{ key: "prefixes", title: "命名前缀", width: 170 },
				{ key: "status", title: "要求", width: 100 },
			],
			rows: layerRows(layers),
			domains: [],
			policy: null,
			readOnlyReason: "系统分层字典只读；计划采用的分层策略请在规划参数配置中维护。",
		};
	}

	if (view === "marts") {
		const marts = await listDataMarts({ limit: 500 });
		return {
			columns: [
				{ key: "code", title: "集市编码", width: 190 },
				{ key: "name", title: "集市名称" },
				{ key: "domain", title: "数据域", width: 190 },
				{ key: "owner", title: "负责人", width: 160 },
				{ key: "status", title: "状态", width: 120 },
			],
			rows: arrayPayload<Record<string, unknown>>(marts).map((mart) => ({
				code: String(mart.code || mart.id || "—"),
				name: String(mart.name || "—"),
				domain: String(mart.domainId || "—"),
				owner: String(mart.ownerId || "—"),
				status: String(mart.status || "—"),
			})),
			domains: [],
			policy: null,
			readOnlyReason: "数据集市由统一集市台账维护，本页提供真实只读投影。",
		};
	}

	if (view === "system" && context.planId) {
		const policy = await getWarehousePlanPolicy(context.planId);
		return { columns: [], rows: [], domains: [], policy, readOnlyReason: null };
	}

	return {
		columns: [],
		rows: [],
		domains: [],
		policy: null,
		readOnlyReason: "当前对象尚无统一权威台账，请在客户现场确认 owner 后接入。",
	};
}

const lifecycleTask = (plan: WarehousePlanHeader, projection: WarehousePlanStageProjection): DemoRow | null => {
	if (!projection.primaryBlocker) return null;
	return {
		task: projection.primaryBlocker.message || "处理规划阶段阻塞",
		object: plan.name,
		stage: warehouseStageLabel(projection.primaryBlocker.stageCode),
		status: "阻塞",
		next: projection.nextAction?.label || "查看建设计划",
	};
};

const modelTypeLabel = (model: ModelSpecView): string =>
	({ DIMENSION: "维度表", FACT: "明细表", SUMMARY: "汇总表", APPLICATION: "应用表" })[model.modelType];

export async function loadModelingHomeProjection(): Promise<ModelingHomeProjection> {
	const [plans, models, standardsRaw, indicatorsRaw] = await Promise.all([
		listWarehousePlans(),
		listModelSpecs(),
		listStandardsForModelingOverview(),
		listIndicatorsForModelingOverview(),
	]);
	const activePlans = plans.filter((plan) => plan.lifecycleStatus !== "ARCHIVED");
	const projections = await Promise.all(
		activePlans.map(async (plan) => ({ plan, projection: await getWarehousePlanStageProjection(plan.id) })),
	);
	const standards = arrayPayload<Record<string, unknown>>(standardsRaw);
	const indicators = arrayPayload<Record<string, unknown>>(indicatorsRaw);
	const recentModels = [...models]
		.filter((model) => Boolean(model.updatedAt))
		.sort((left, right) => right.updatedAt.localeCompare(left.updatedAt))
		.slice(0, 8)
		.map((model) => ({
			id: model.id,
			name: model.name,
			type: modelTypeLabel(model),
			domain: model.domainId || "未归属",
			version: `r${model.revision}`,
			status: model.status,
			updatedAt: model.updatedAt,
		}));
	const tasks = projections
		.map(({ plan, projection }) => lifecycleTask(plan, projection))
		.filter((item): item is DemoRow => item !== null);
	const deliveryCodes = ["WAREHOUSE_PLANNING", "DATA_STANDARD", "MODEL_DESIGN", "BUILD_QUALITY_RELEASE"] as const;
	const delivery = deliveryCodes.map((code) => {
		const stages = projections.map(({ projection }) => projection.stages.find((stage) => stage.code === code));
		const present = stages.filter((stage) => Boolean(stage));
		const count = present.filter((stage) => stage?.status === "COMPLETE" && stage.freshness === "CURRENT").length;
		const total = present.length;
		return {
			label: warehouseStageLabel(code),
			count,
			total,
			percent: total === 0 ? 0 : Math.round((count / total) * 100),
		};
	});
	return {
		stats: [
			{ label: "建设计划", value: activePlans.length, meta: `${plans.length} 个可见计划` },
			{
				label: "逻辑模型",
				value: models.length,
				meta: `${models.filter((model) => model.status === "PUBLISHED").length} 个已发布`,
			},
			{ label: "数据标准", value: standards.length, meta: "治理标准目录" },
			{ label: "数据指标", value: indicators.length, meta: "指标治理目录" },
		],
		recentModels,
		tasks,
		delivery,
	};
}

export const planningCatalogStatusLabel = stageStatusLabel;
