import type { Sprint64BusinessProcess } from "@/api/sprint64GovernanceApi";
import {
	getWarehousePlanCategories,
	getWarehousePlanPolicy,
	type WarehousePlanBusinessCategoryMode,
	type WarehousePlanBusinessProcessMode,
	type WarehousePlanCategoryBindingView,
	type WarehousePlanPolicyView,
} from "@/api/warehousePlanApi";
import { resolveDefaultModelingContextId } from "@/api/services/modelingImportContextService";

export type PlanningBusinessProcessMode = WarehousePlanBusinessProcessMode;

export type PlanningContextPolicy = {
	planId: string;
	version: number;
	policy: WarehousePlanPolicyView & {
		businessCategoryMode: WarehousePlanBusinessCategoryMode;
		businessProcessMode: WarehousePlanBusinessProcessMode;
		defaultBusinessCategoryId: string | null;
	};
	categories: WarehousePlanCategoryBindingView[];
};

const availableCategories = (categories: readonly WarehousePlanCategoryBindingView[]) =>
	categories.filter((item) => item.confirmationStatus === "CONFIRMED" && item.resolutionStatus === "AVAILABLE");

export function resolveDefaultBusinessCategoryId(
	configuredId: string | null | undefined,
	categories: readonly WarehousePlanCategoryBindingView[],
): string | null {
	const available = availableCategories(categories);
	if (configuredId) return available.some((item) => item.domainId === configuredId) ? configuredId : null;
	return available.length === 1 ? available[0].domainId : null;
}

export async function loadPlanningContextPolicy(planId = ""): Promise<PlanningContextPolicy> {
	const resolvedPlanId = planId || (await resolveDefaultModelingContextId());
	if (!resolvedPlanId) throw new Error("服务端尚未提供可写建模上下文，请联系管理员初始化");
	const [versionedPolicy, versionedCategories] = await Promise.all([
		getWarehousePlanPolicy(resolvedPlanId),
		getWarehousePlanCategories(resolvedPlanId),
	]);
	const categories = availableCategories(versionedCategories.value.domainBindings);
	const businessCategoryMode = versionedPolicy.value.businessCategoryMode || "SINGLE_DEFAULT";
	const businessProcessMode = versionedPolicy.value.businessProcessMode || "AUTO_SELECT_SINGLE";
	const defaultBusinessCategoryId = resolveDefaultBusinessCategoryId(
		versionedPolicy.value.defaultBusinessCategoryId,
		categories,
	);
	return {
		planId: resolvedPlanId,
		version: versionedPolicy.version,
		policy: {
			...versionedPolicy.value,
			businessCategoryMode,
			businessProcessMode,
			defaultBusinessCategoryId,
		},
		categories,
	};
}

export type BusinessProcessBindingResolution = {
	processes: Sprint64BusinessProcess[];
	selectedId: string | null;
	showSelector: boolean;
	message: string;
};

export function resolveBusinessProcessBinding(
	mode: WarehousePlanBusinessProcessMode,
	currentId: string | null | undefined,
	items: readonly Sprint64BusinessProcess[],
): BusinessProcessBindingResolution {
	const processes = items.filter(
		(item) => item.confirmed && String(item.lifecycleStatus || "ACTIVE").toUpperCase() !== "RETIRED",
	);
	const current = processes.find((item) => item.id === currentId)?.id || null;
	if (!processes.length) {
		return {
			processes,
			selectedId: null,
			showSelector: false,
			message: "当前数据域尚无有效业务过程，请先定义能表达业务事件与事实粒度的业务过程。",
		};
	}
	if (mode === "AUTO_SELECT_SINGLE" && processes.length === 1) {
		return {
			processes,
			selectedId: processes[0].id,
			showSelector: false,
			message: `已自动绑定业务过程：${processes[0].name}`,
		};
	}
	return {
		processes,
		selectedId: current,
		showSelector: true,
		message:
			mode === "MANAGED" ? "当前规划要求人工确认业务过程。" : "当前数据域存在多个有效业务过程，请按事实粒度选择。",
	};
}
