import { listDataMarts } from "@/api/dataMartApi";
import { listModelSpecs } from "@/api/modelSpecApi";
import {
	listIndicatorsForModelingOverview,
	listStandardsForModelingOverview,
} from "@/api/services/modelingOverviewFactService";
import type { Sprint64BusinessProcess } from "@/api/sprint64GovernanceApi";
import { listBusinessProcessesApi } from "@/api/sprint64GovernanceApi";
import { listSubjectDomains } from "@/api/subjectDomainApi";
import { listWarehouseLayers } from "@/api/warehouseLayerApi";
import type { DataMartView } from "@/features/modeling/contracts/dataMartContract";
import type { ModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
import { listPlanningCatalogDomains, type PlanningCatalogDomain } from "./planningCatalogDomainService";

export type ModelingRequestFailure = {
	kind: "permission" | "request";
	message: string;
	code?: string | null;
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
	source?: Sprint64BusinessProcess | DataMartView | PlanningCatalogDomain | Record<string, unknown>;
};

export type PlanningProjection = {
	headers: string[];
	rows: PlanningProjectionRow[];
	readOnlyReason: string | null;
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
		return { kind: "request", message: evidence.length ? `${fallback}（${evidence.join("；")}）` : fallback, code };
	}
	const detail = error instanceof Error ? error.message.trim() : "";
	return { kind: "request", message: detail || fallback };
}

const modelTypeLabel = (model: ModelSpecView): string =>
	({ DIMENSION: "维度表", FACT: "明细表", SUMMARY: "汇总表", APPLICATION: "应用表" })[model.modelType];

const LAYER_GROUP_LABEL: Record<string, string> = {
	STAGING: "贴源层",
	COMMON: "公共层",
	APPLICATION: "应用层",
};

const LAYER_MODEL_TYPE_LABEL: Record<string, string> = {
	DIMENSION: "维度",
	FACT: "明细表",
	SUMMARY: "汇总表",
	APPLICATION: "应用表",
};

export async function loadModelingOverviewProjection(): Promise<ModelingOverviewProjection> {
	const [catalogDomains, models, standardsRaw, indicatorsRaw] = await Promise.all([
		listPlanningCatalogDomains(),
		listModelSpecs(),
		listStandardsForModelingOverview(),
		listIndicatorsForModelingOverview(),
	]);
	const standards = arrayPayload<Record<string, unknown>>(standardsRaw);
	const indicators = arrayPayload<Record<string, unknown>>(indicatorsRaw);
	const businessCategories = catalogDomains.filter((item) => !item.parentId);
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
	const tasks = models
		.filter((model) => model.status !== "PUBLISHED" && model.status !== "ARCHIVED")
		.slice(0, 8)
		.map((model) => ({
			id: model.id,
			task: `完善并发布模型：${model.name}`,
			object: model.name,
			stage: model.status,
			next: "进入维度建模",
		}));
	const publishedModels = models.filter((model) => model.status === "PUBLISHED").length;
	const delivery = [
		{ label: "规划目录", count: catalogDomains.length, total: catalogDomains.length },
		{ label: "数据标准", count: standards.length, total: standards.length },
		{ label: "逻辑模型", count: publishedModels, total: models.length },
		{ label: "数据指标", count: indicators.length, total: indicators.length },
	].map((item) => ({
		...item,
		percent: item.total ? Math.round((item.count / item.total) * 100) : 0,
	}));
	return {
		stats: [
			{
				label: "业务分类",
				value: businessCategories.length,
				meta: `${catalogDomains.length - businessCategories.length} 个数据域`,
				target: "planning/business-categories",
			},
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
});

export async function loadPlanningProjection(view: string): Promise<PlanningProjection> {
	if (view === "spaces") {
		return emptyProjection("建模空间尚无独立服务端 owner；本页不会复用其他业务对象，也不提供模拟 CRUD。");
	}
	if (view === "business-domains" || view === "domains" || view === "business-categories") {
		const domains = await listPlanningCatalogDomains();
		const names = new Map(domains.map((domain) => [domain.code, domain.name]));
		const selected =
			view === "business-domains"
				? domains
				: domains.filter((domain) => (view === "business-categories" ? !domain.parentId : Boolean(domain.parentId)));
		const combined = view === "business-domains";
		return {
			...emptyProjection(),
			headers: combined
				? ["对象类型", "编码", "名称", "上级分类", "负责人", "说明"]
				: [view === "business-categories" ? "分类编码" : "数据域编码", "名称", "上级分类", "负责人", "说明"],
			rows: selected.map((domain) => ({
				id: domain.id,
				cells: [
					...(combined ? [domain.parentId ? "数据域" : "业务分类"] : []),
					domain.code,
					domain.name,
					domain.parentCode ? names.get(domain.parentCode) || domain.parentCode : "—",
					domain.owner || "—",
					domain.description || "—",
				],
				source: domain,
			})),
		};
	}
	if (view === "processes") {
		const domains = (await listPlanningCatalogDomains()).filter((domain) => Boolean(domain.parentId));
		const entries = await Promise.all(
			domains.map(async (domain) => ({ domain, processes: await listBusinessProcessesApi(domain.id) })),
		);
		return {
			...emptyProjection(),
			headers: ["过程编码", "过程名称", "数据域", "状态", "业务定义"],
			rows: entries.flatMap(({ domain, processes }) =>
				processes.map((item) => ({
					id: `${domain.id}:${item.processId}`,
					cells: [
						item.processId,
						item.name,
						domain.name,
						String(item.lifecycleStatus || "ACTIVE").toUpperCase() === "RETIRED"
							? "已停用"
							: item.confirmed
								? "已确认"
								: "候选",
						item.description || "—",
					],
					source: item,
				})),
			),
		};
	}
	if (view === "layers") {
		const layers = await listWarehouseLayers();
		return {
			...emptyProjection(),
			headers: ["分层编码", "分层名称", "分层归属", "所属系统类型", "模型类型", "加工责任", "命名前缀", "来源"],
			rows: layers.map((item) => ({
				id: item.code,
				cells: [
					item.code,
					item.name,
					LAYER_GROUP_LABEL[item.layerGroup] || item.layerGroup,
					item.systemLayerCode,
					item.modelTypes.map((type) => LAYER_MODEL_TYPE_LABEL[type] || type).join("、") || "—",
					item.responsibility,
					item.namingPrefixes.join("、") || "—",
					item.builtin ? "系统" : "自定义",
				],
				source: item,
			})),
		};
	}
	if (view === "marts") {
		const marts = await listDataMarts({ limit: 100 });
		const categories = (await listPlanningCatalogDomains()).filter((item) => !item.parentId);
		const categoryNames = new Map(categories.map((item) => [item.id, item.name]));
		return {
			...emptyProjection(),
			headers: ["集市编码", "集市名称", "业务分类", "负责人", "状态"],
			rows: marts.map((mart) => ({
				id: mart.id,
				cells: [
					mart.code,
					mart.name,
					mart.businessCategoryIds.map((id) => categoryNames.get(id) || id).join("、") || "—",
					mart.ownerId,
					mart.status,
				],
				source: mart,
			})),
		};
	}
	if (view === "subjects") {
		const [subjects, marts] = await Promise.all([listSubjectDomains({ limit: 100 }), listDataMarts({ limit: 100 })]);
		const martNames = new Map(marts.map((mart) => [mart.id, mart.name]));
		return {
			...emptyProjection(),
			headers: ["主题域编码", "主题域名称", "数据集市", "用途说明", "状态"],
			rows: subjects.map((item) => ({
				id: item.id,
				cells: [item.code, item.name, martNames.get(item.martId) || item.martId, item.purpose || "—", item.status],
				source: item,
			})),
		};
	}
	if (view === "system") {
		return emptyProjection("当前版本尚未提供可维护的规划参数；配置能力接入前，本页仅说明功能边界。");
	}
	return emptyProjection("当前对象尚无统一权威台账；确认 owner 前不提供本地模拟 CRUD。");
}
