import type {
	WarehousePlanCategoryBindingView,
	WarehousePlanEvidenceFreshness,
	WarehousePlanHeader,
	WarehousePlanLifecycleStatus,
	WarehousePlanStageCode,
	WarehousePlanStageStatus,
} from "@/api/warehousePlanApi";

export type WarehouseCategoryOptionSource = { id: string; name?: string | null; code?: string | null };
export type WarehouseCategoryOption = { value: string; label: string };
export type WarehousePlanLedgerLifecycleFilter = WarehousePlanLifecycleStatus | "ACTIVE" | "ALL";
export type WarehousePlanLedgerFilters = {
	keyword?: string;
	lifecycleStatus?: WarehousePlanLedgerLifecycleFilter;
	ownerId?: string;
};

const WAREHOUSE_PLAN_LIFECYCLE_LABELS: Record<WarehousePlanLifecycleStatus, string> = {
	DRAFT: "规划中",
	BASELINE_READY: "基线已确认",
	DESIGNING: "设计中",
	VALIDATING: "验证中",
	READY_TO_PUBLISH: "待发布",
	PUBLISHED: "已发布",
	ARCHIVED: "已归档",
};

export const warehousePlanLifecycleLabel = (status: WarehousePlanLifecycleStatus): string =>
	WAREHOUSE_PLAN_LIFECYCLE_LABELS[status];

export const canEditWarehousePlanHeader = (canMaintainPlan: boolean, status: WarehousePlanLifecycleStatus): boolean =>
	canMaintainPlan && status !== "PUBLISHED" && status !== "ARCHIVED";

export const canArchiveWarehousePlan = (canMaintainPlan: boolean, status: WarehousePlanLifecycleStatus): boolean =>
	canMaintainPlan && status !== "ARCHIVED";

export const filterWarehousePlans = <T extends WarehousePlanHeader>(
	plans: readonly T[],
	filters: WarehousePlanLedgerFilters = {},
): T[] => {
	const lifecycleStatus = filters.lifecycleStatus || "ACTIVE";
	const keyword = String(filters.keyword || "")
		.trim()
		.toLocaleLowerCase();
	const ownerId = String(filters.ownerId || "").trim();
	return plans.filter((plan) => {
		if (lifecycleStatus === "ACTIVE" && plan.lifecycleStatus === "ARCHIVED") return false;
		if (lifecycleStatus !== "ACTIVE" && lifecycleStatus !== "ALL" && plan.lifecycleStatus !== lifecycleStatus) {
			return false;
		}
		if (ownerId && plan.ownerId !== ownerId) return false;
		if (!keyword) return true;
		return [plan.name, plan.code, plan.objective, plan.scope, plan.ownerId, plan.ownerDepartmentId].some((value) =>
			String(value || "")
				.toLocaleLowerCase()
				.includes(keyword),
		);
	});
};

export const replaceWarehousePlanHeader = <T extends WarehousePlanHeader>(plans: readonly T[], updated: T): T[] => {
	const found = plans.some((plan) => plan.id === updated.id);
	return found ? plans.map((plan) => (plan.id === updated.id ? updated : plan)) : [updated, ...plans];
};

export const mergeWarehousePlanHeadersMonotonic = <T extends WarehousePlanHeader>(
	current: readonly T[],
	incoming: readonly T[],
): T[] => {
	const currentById = new Map(current.map((plan) => [plan.id, plan]));
	return incoming.map((plan) => {
		const observed = currentById.get(plan.id);
		return observed && observed.version > plan.version ? observed : plan;
	});
};

export const WAREHOUSE_STAGE_ORDER: readonly WarehousePlanStageCode[] = [
	"DATA_CONNECTION",
	"SOURCE_INVENTORY",
	"WAREHOUSE_PLANNING",
	"DATA_STANDARD",
	"MODEL_DESIGN",
	"BUILD_QUALITY_RELEASE",
	"DATA_ASSET",
	"METRIC_SYSTEM",
	"DATA_SERVICE_OPERATIONS",
] as const;

const STAGE_LABELS: Record<WarehousePlanStageCode, string> = {
	DATA_CONNECTION: "数据连接",
	SOURCE_INVENTORY: "来源盘点",
	WAREHOUSE_PLANNING: "数仓规划",
	DATA_STANDARD: "数据标准",
	MODEL_DESIGN: "事实与维度设计",
	BUILD_QUALITY_RELEASE: "构建、质量与发布",
	DATA_ASSET: "数据资产",
	METRIC_SYSTEM: "指标系统",
	DATA_SERVICE_OPERATIONS: "数据服务与运维",
};

const STAGE_ACTION_LABELS: Record<WarehousePlanStageCode, string> = {
	DATA_CONNECTION: "检查数据连接",
	SOURCE_INVENTORY: "继续来源盘点",
	WAREHOUSE_PLANNING: "完善数仓规划",
	DATA_STANDARD: "关联数据标准",
	MODEL_DESIGN: "继续模型设计",
	BUILD_QUALITY_RELEASE: "检查构建与发布条件",
	DATA_ASSET: "查看资产登记",
	METRIC_SYSTEM: "检查指标关联",
	DATA_SERVICE_OPERATIONS: "查看服务与运行",
};

const BLOCKER_MESSAGES: Record<string, string> = {
	CATEGORY_SCOPE_INCOMPLETE: "业务分类尚未确认",
	BUSINESS_SCOPE_INCOMPLETE: "业务分类尚未确认",
	DOMAIN_PROCESS_CONFIRMATION_INCOMPLETE: "业务分类尚未确认",
	SOURCE_INVENTORY_INCOMPLETE: "来源盘点尚未完成",
	SOURCE_BUSINESS_MAPPING_INCOMPLETE: "来源关联尚未完成",
	PLANNING_POLICY_INCOMPLETE: "数仓分层尚未确认",
	EVIDENCE_STALE: "完成记录已过期，需要重新核验",
	EVIDENCE_UNAVAILABLE: "完成记录暂时不可用",
};

const PLANNING_ISSUE_MESSAGES: Record<string, string> = {
	CATEGORY_BINDING_INVALID: "业务分类信息不完整，请重新选择",
	CATEGORY_SCOPE_REQUIRED: "请至少添加一个业务分类",
	CATEGORY_CONFIRMATION_REQUIRED: "请确认该业务分类是否纳入本计划",
	CATEGORY_DOMAIN_ARCHIVED: "该业务分类已归档，请替换",
	CATEGORY_DOMAIN_FORBIDDEN: "当前账号已无法访问该业务分类，请替换或申请权限",
	CATEGORY_DOMAIN_MISSING: "该业务分类已删除，请替换",
	LAYER_SCHEME_REQUIRED: "请选择数仓分层方案",
	LAYER_SCHEME_UNSUPPORTED: "当前分层方案不受支持，请重新选择",
	NAMING_POLICY_REQUIRED: "进入模型加工前请选择命名规则",
	NAMING_POLICY_UNSUPPORTED: "当前命名规则不受支持，请重新选择",
	HISTORY_POLICY_REQUIRED: "进入模型加工前请选择历史保留策略",
	HISTORY_POLICY_UNSUPPORTED: "当前历史保留策略不受支持，请重新选择",
	DEFAULT_TIME_ZONE_INVALID: "请输入有效的 IANA 时区名称",
};

const PLANNING_MUTATION_ERROR_MESSAGES: Record<string, string> = {
	WAREHOUSE_PLAN_HEADER_INVALID: "请检查规划名称和负责人",
	WAREHOUSE_PLAN_OWNER_FORBIDDEN: "当前账号不能转派给所选负责人",
	WAREHOUSE_PLAN_OWNER_DEPARTMENT_FORBIDDEN: "当前账号不能转派到所选负责部门",
	WAREHOUSE_PLAN_CATEGORY_FORBIDDEN: "当前账号不能使用所选业务分类，请替换或申请权限",
	WAREHOUSE_PLAN_CATEGORY_INVALID: "业务分类设置无效，请检查后重试",
	WAREHOUSE_PLAN_POLICY_INVALID: "数仓分层设置无效，请检查后重试",
	WAREHOUSE_PLAN_LIFECYCLE_CONFLICT: "当前计划已不可编辑，请重新加载计划状态",
	WAREHOUSE_PLAN_NOT_FOUND: "当前计划不存在或已不可访问",
	WAREHOUSE_PLAN_IF_MATCH_INVALID: "规划版本信息无效，请重新加载后重试",
	WAREHOUSE_PLAN_IF_MATCH_REQUIRED: "缺少规划版本信息，请重新加载后重试",
	WAREHOUSE_PLAN_EDIT_UNIT_VERSION_CONFLICT: "规划内容已被其他用户更新，请选择保留输入重试或加载最新版",
	WAREHOUSE_PLAN_VERSION_CONFLICT: "规划内容已被其他用户更新，请选择保留输入重试或加载最新版",
};

export const warehouseStageLabel = (code: WarehousePlanStageCode): string => STAGE_LABELS[code];

export const warehouseStageActionLabel = (code: WarehousePlanStageCode): string => STAGE_ACTION_LABELS[code];

export const warehouseBlockerMessage = (code: string, fallback?: string | null): string => {
	if (BLOCKER_MESSAGES[code]) return BLOCKER_MESSAGES[code];
	if (code.endsWith("_NOT_STARTED")) return "本阶段尚未产生可核验的完成记录";
	if (code.endsWith("_UNKNOWN")) return "本阶段的完成记录暂时无法核验";
	if (code.endsWith("_IN_PROGRESS")) return "本阶段仍在进行，尚未形成完整记录";
	if (code.endsWith("_BLOCKED")) return "本阶段存在尚未处理的阻塞";
	if (fallback && /[\u3400-\u9fff]/.test(fallback)) return fallback;
	return "本阶段存在尚未处理的阻塞";
};

export const warehousePlanIssueMessage = (code: string, fallback?: string | null): string => {
	if (PLANNING_ISSUE_MESSAGES[code]) return PLANNING_ISSUE_MESSAGES[code];
	if (fallback && /[\u3400-\u9fff]/.test(fallback)) return fallback;
	return "请检查当前规划设置";
};

export const warehousePlanMutationErrorMessage = (error: unknown, fallback: string): string => {
	if (!error || typeof error !== "object") return fallback;
	const responseData = (error as { response?: { data?: unknown } }).response?.data;
	if (!responseData || typeof responseData !== "object") return fallback;
	const code = (responseData as { code?: unknown }).code;
	return typeof code === "string" && PLANNING_MUTATION_ERROR_MESSAGES[code]
		? PLANNING_MUTATION_ERROR_MESSAGES[code]
		: fallback;
};

export const buildWarehouseCategoryOptions = (
	availableCategories: WarehouseCategoryOptionSource[],
	bindings: WarehousePlanCategoryBindingView[],
): WarehouseCategoryOption[] => {
	const options = new Map<string, WarehouseCategoryOption>();
	for (const category of availableCategories) {
		options.set(category.id, { value: category.id, label: category.name || category.code || category.id });
	}
	for (const binding of bindings) {
		const current = options.get(binding.domainId);
		if (binding.resolutionStatus === "FORBIDDEN") {
			options.set(binding.domainId, { value: binding.domainId, label: "不可访问分类" });
			continue;
		}
		if (binding.resolutionStatus === "MISSING") {
			options.set(binding.domainId, { value: binding.domainId, label: "已删除分类" });
			continue;
		}
		if (binding.resolutionStatus === "ARCHIVED") {
			const label = binding.name || binding.code || current?.label || "分类";
			options.set(binding.domainId, { value: binding.domainId, label: `${label}（已归档）` });
			continue;
		}
		if (!current) {
			options.set(binding.domainId, {
				value: binding.domainId,
				label: binding.name || binding.code || binding.domainId,
			});
		}
	}
	return [...options.values()];
};

export const stageStatusLabel = (
	status: WarehousePlanStageStatus,
	freshness: WarehousePlanEvidenceFreshness,
): string => {
	if (freshness === "STALE") return "记录已过期";
	if (status === "UNKNOWN" || freshness === "UNAVAILABLE") return "记录未知";
	return (
		{
			NOT_STARTED: "未开始",
			IN_PROGRESS: "进行中",
			BLOCKED: "有阻塞",
			COMPLETE: "已完成",
			UNKNOWN: "记录未知",
		} satisfies Record<WarehousePlanStageStatus, string>
	)[status];
};

export const isWarehouseStageComplete = (
	status: WarehousePlanStageStatus,
	freshness: WarehousePlanEvidenceFreshness,
): boolean => status === "COMPLETE" && freshness === "CURRENT";

const CANONICAL_WAREHOUSE_PLAN_ID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export const normalizeCanonicalWarehousePlanId = (value: string | null | undefined): string => {
	const planId = value?.trim() || "";
	return CANONICAL_WAREHOUSE_PLAN_ID.test(planId) ? planId : "";
};

export const buildWarehousePlanRoute = (
	planId: string,
	section?: "overview" | "baseline" | "architecture" | "models" | "implementation" | "deliverables",
	query: Record<string, string | null | undefined> = {},
): string => {
	const params = new URLSearchParams();
	for (const [key, value] of Object.entries(query)) {
		if (value) params.set(key, value);
	}
	params.set("planId", planId);
	if (section && section !== "overview") params.set("view", section);
	return `/data-modeling/planning/spaces?${params.toString()}`;
};

export const withWarehousePlanContext = (route: string, planId: string): string => {
	const [path, query = ""] = route.split("?");
	const params = new URLSearchParams(query);
	params.set("planId", planId);
	return `${path}?${params.toString()}`;
};

export const buildBusinessCategoryManagementRoute = (planId: string): string => {
	const returnTo = buildWarehousePlanRoute(planId, "baseline", { tab: "categories" });
	const params = new URLSearchParams({ planId, returnTo });
	return `/data-modeling/planning/business-categories?${params.toString()}`;
};

const WAREHOUSE_PLAN_RETURN_ORIGIN = "http://dts.local";
const WAREHOUSE_PLAN_BASELINE_TABS = new Set(["categories", "layers", "sources"]);

export const resolveWarehousePlanReturnTo = (rawReturnTo: string | null | undefined, planId: string): string | null => {
	if (!rawReturnTo || !planId || !rawReturnTo.startsWith("/") || rawReturnTo.startsWith("//")) return null;
	if (rawReturnTo.includes("\\")) return null;
	try {
		const target = new URL(rawReturnTo, WAREHOUSE_PLAN_RETURN_ORIGIN);
		const expectedPath = "/data-modeling/planning/spaces";
		if (target.origin !== WAREHOUSE_PLAN_RETURN_ORIGIN || target.pathname !== expectedPath || target.hash) return null;
		if (target.searchParams.getAll("planId").length !== 1 || target.searchParams.get("planId") !== planId) return null;
		if (target.searchParams.getAll("tab").length !== 1) return null;
		if (!WAREHOUSE_PLAN_BASELINE_TABS.has(target.searchParams.get("tab") || "")) return null;
		if (target.searchParams.getAll("view").length !== 1 || target.searchParams.get("view") !== "baseline") return null;
		for (const key of target.searchParams.keys()) {
			if (key !== "planId" && key !== "tab" && key !== "view") return null;
		}
		return `${target.pathname}?${target.searchParams.toString()}`;
	} catch {
		return null;
	}
};

export type WarehousePlanPageContext = { planId: string; returnTo: string | null };

export const resolveWarehousePlanPageContext = (searchParams: URLSearchParams): WarehousePlanPageContext | null => {
	const planValues = searchParams.getAll("planId");
	if (planValues.length !== 1) return null;
	const planId = normalizeCanonicalWarehousePlanId(planValues[0]);
	if (!planId) return null;

	const returnValues = searchParams.getAll("returnTo");
	if (returnValues.length > 1) return null;
	if (returnValues.length === 1) {
		const returnTo = resolveWarehousePlanReturnTo(returnValues[0], planId);
		return returnTo ? { planId, returnTo } : null;
	}
	return { planId, returnTo: null };
};

export const resolveWarehousePlanConflictVersion = (error: unknown): number | null => {
	if (!error || typeof error !== "object") return null;
	const response = (error as { response?: { status?: unknown; data?: unknown } }).response;
	if (response?.status !== 409 || !response.data || typeof response.data !== "object") return null;
	const code = (response.data as { code?: unknown }).code;
	if (code !== "WAREHOUSE_PLAN_VERSION_CONFLICT" && code !== "WAREHOUSE_PLAN_EDIT_UNIT_VERSION_CONFLICT") {
		return null;
	}
	const bodyData = (response.data as { data?: unknown }).data;
	if (!bodyData || typeof bodyData !== "object") return null;
	const currentVersion = Number((bodyData as { currentVersion?: unknown }).currentVersion);
	return Number.isInteger(currentVersion) && currentVersion > 0 ? currentVersion : null;
};
