import { listDataMarts } from "@/api/dataMartApi";
import { listModelSpecs } from "@/api/modelSpecApi";
import catalogDomainService, { type CatalogDomain } from "@/api/services/catalogDomainService";
import {
	listIndicatorsForModelingOverview,
	listStandardsForModelingOverview,
} from "@/api/services/modelingOverviewFactService";
import type { Sprint64BusinessProcess } from "@/api/sprint64GovernanceApi";
import { listBusinessProcessesApi, listWarehouseLayersApi } from "@/api/sprint64GovernanceApi";
import {
	getWarehousePlanPolicy,
	getWarehousePlanStageProjection,
	listWarehousePlans,
	type WarehousePlanHeader,
	type WarehousePlanPolicyView,
} from "@/api/warehousePlanApi";
import type { DataMartView } from "@/features/modeling/contracts/dataMartContract";
import type { ModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
import { warehouseStageLabel } from "@/features/modeling/navigation/warehousePlanViewModel";

export type ModelingRequestFailure = {
	kind: "permission" | "request";
	message: string;
};

export type ModelingOverviewProjection = {
	stats: Array<{ label: string; value: number; meta: string; target: string }>;
	recentModels: Array<{
		id: string;
		name: string;
		type: string;
		domain: string;
		version: string;
		status: string;
		updatedAt: string;
	}>;
	delivery: Array<{ label: string; count: number; total: number; percent: number }>;
	tasks: Array<{ id: string; task: string; object: string; stage: string; next: string }>;
};

export type PlanningProjectionRow = {
	id: string;
	cells: string[];
	plan?: WarehousePlanHeader;
	source?: Sprint64BusinessProcess | DataMartView | CatalogDomain | Record<string, unknown>;
};

export type PlanningProjection = {
	headers: string[];
	rows: PlanningProjectionRow[];
	readOnlyReason: string | null;
	plans: WarehousePlanHeader[];
	selectedPlan: WarehousePlanHeader | null;
	policy: { value: WarehousePlanPolicyView; version: number } | null;
};

const SAFE_ERROR_CODE = /^[A-Z][A-Z0-9_]{2,79}$/;
const SAFE_CORRELATION_ID = /^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$/;

const safeString = (value: unknown, pattern: RegExp): string | null =>
	typeof value === "string" && pattern.test(value.trim()) ? value.trim() : null;

const recordValue = (value: unknown, key: string): unknown =>
	value && typeof value === "object" ? (value as Record<string, unknown>)[key] : undefined;

const headerValue = (headers: unknown, name: string): unknown => {
	if (!headers || typeof headers !== "object") return undefined;
	const getter = (headers as { get?: (key: string) => unknown }).get;
	if (typeof getter === "function") return getter.call(headers, name);
	const record = headers as Record<string, unknown>;
	return record[name] ?? record[name.toLowerCase()];
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

export function normalizeModelingRequestFailure(error: unknown, fallback: string): ModelingRequestFailure {
	const response = (error as { response?: { status?: unknown; data?: unknown; headers?: unknown } } | null)?.response;
	const status = Number(response?.status ?? 0);
	if (status === 401 || status === 403) {
		return { kind: "permission", message: "当前账号无权访问该建模数据，请联系管理员授权。" };
	}
	if (response) {
		const code =
			safeString(recordValue(response.data, "errorCode"), SAFE_ERROR_CODE) ??
			safeString(recordValue(response.data, "code"), SAFE_ERROR_CODE) ??
			safeString(recordValue(response.data, "error"), SAFE_ERROR_CODE);
		const correlationId =
			safeString(recordValue(response.data, "correlationId"), SAFE_CORRELATION_ID) ??
			safeString(recordValue(response.data, "traceId"), SAFE_CORRELATION_ID) ??
			safeString(recordValue(response.data, "requestId"), SAFE_CORRELATION_ID) ??
			safeString(headerValue(response.headers, "x-correlation-id"), SAFE_CORRELATION_ID) ??
			safeString(headerValue(response.headers, "x-request-id"), SAFE_CORRELATION_ID);
		const evidence = [code ? `错误码 ${code}` : null, correlationId ? `关联 ID ${correlationId}` : null].filter(
			Boolean,
		);
		return { kind: "request", message: evidence.length ? `${fallback}（${evidence.join("；")}）` : fallback };
	}
	const detail = error instanceof Error ? error.message.trim() : "";
	return { kind: "request", message: detail || fallback };
}

const modelTypeLabel = (model: ModelSpecView): string =>
	({ DIMENSION: "维度表", FACT: "明细表", SUMMARY: "汇总表", APPLICATION: "应用表" })[model.modelType];

export async function loadModelingOverviewProjection(): Promise<ModelingOverviewProjection> {
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
	const tasks = projections.flatMap(({ plan, projection }) =>
		projection.primaryBlocker
			? [
					{
						id: `${plan.id}:${projection.primaryBlocker.stageCode}`,
						task: projection.primaryBlocker.message || "处理规划阶段阻塞",
						object: plan.name,
						stage: warehouseStageLabel(projection.primaryBlocker.stageCode),
						next: projection.nextAction?.label || "查看建设计划",
					},
				]
			: [],
	);
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
			percent: total ? Math.round((count / total) * 100) : 0,
		};
	});
	return {
		stats: [
			{ label: "建设计划", value: activePlans.length, meta: `${plans.length} 个可见计划`, target: "planning/spaces" },
			{
				label: "逻辑模型",
				value: models.length,
				meta: `${models.filter((model) => model.status === "PUBLISHED").length} 个已发布`,
				target: "dimensions/workbench",
			},
			{ label: "数据标准", value: standards.length, meta: "治理标准目录", target: "standards/fields" },
			{ label: "数据指标", value: indicators.length, meta: "指标治理目录", target: "metrics/atomic" },
		],
		recentModels,
		delivery,
		tasks,
	};
}

const emptyProjection = (readOnlyReason: string | null = null): PlanningProjection => ({
	headers: [],
	rows: [],
	readOnlyReason,
	plans: [],
	selectedPlan: null,
	policy: null,
});

export async function loadPlanningProjection(view: string, requestedPlanId?: string): Promise<PlanningProjection> {
	if (view === "spaces") {
		return emptyProjection(
			"当前服务端没有独立建模空间 owner；旧建设计划编辑器不会映射为建模空间。配置 owner 契约前本页不提供模拟 CRUD。",
		);
	}
	if (view === "domains" || view === "business-categories") {
		const domains = await catalogDomainService.list();
		const names = new Map(domains.map((domain) => [domain.code, domain.name]));
		const selected = view === "business-categories" ? domains.filter((domain) => !domain.parentCode) : domains;
		return {
			...emptyProjection("业务分类与数据域由数据治理目录统一维护，本页展示真实只读投影。"),
			headers: ["分类编码", "分类名称", "上级分类"],
			rows: selected.map((domain) => ({
				id: domain.code,
				cells: [domain.code, domain.name, domain.parentCode ? names.get(domain.parentCode) || domain.parentCode : "—"],
				source: domain,
			})),
		};
	}
	if (view === "processes") {
		const domains = await catalogDomainService.list();
		const entries = await Promise.all(
			domains.map(async (domain) => ({ domain, processes: await listBusinessProcessesApi(domain.code) })),
		);
		return {
			...emptyProjection(),
			headers: ["过程编码", "过程名称", "数据域", "状态", "业务定义"],
			rows: entries.flatMap(({ domain, processes }) =>
				processes.map((item) => ({
					id: `${domain.code}:${item.processId}`,
					cells: [item.processId, item.name, domain.name, item.confirmed ? "已确认" : "候选", item.description || "—"],
					source: item,
				})),
			),
		};
	}
	if (view === "layers") {
		const layers = await listWarehouseLayersApi();
		return {
			...emptyProjection("系统分层字典只读；计划采用的策略请在规划参数配置中维护。"),
			headers: ["分层编码", "分层名称", "分层类型", "加工责任", "命名前缀", "要求"],
			rows: layers.map((item) => ({
				id: item.code,
				cells: [
					item.code,
					item.title,
					item.kind || "—",
					item.responsibility,
					item.namingPrefixes.join("、") || "—",
					item.optional ? "可选" : "必选",
				],
			})),
		};
	}
	if (view === "marts") {
		const marts = await listDataMarts({ limit: 500 });
		return {
			...emptyProjection(),
			headers: ["集市编码", "集市名称", "数据域", "负责人", "状态"],
			rows: marts.map((mart) => ({
				id: mart.id,
				cells: [mart.code, mart.name, mart.domainIds.join("、") || "—", mart.ownerId, mart.status],
				source: mart,
			})),
		};
	}
	if (view === "system") {
		const plans = await listWarehousePlans();
		const selectedPlan = plans.find((plan) => plan.id === requestedPlanId) || plans[0] || null;
		const policy = selectedPlan ? await getWarehousePlanPolicy(selectedPlan.id) : null;
		return { ...emptyProjection(), plans, selectedPlan, policy };
	}
	return emptyProjection("当前对象尚无统一权威台账；确认 owner 前不提供本地模拟 CRUD。");
}
