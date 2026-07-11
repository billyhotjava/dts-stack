import {
	buildPlanningRoute,
	type WarehousePlanningContext,
	type WarehousePlanningSource,
} from "../governance/warehousePlanningContext";
import { resolveGrainDeclaration, type GrainDeclaration } from "./grainDeclaration";

export type DimensionCandidateGateStatus = "ready" | "missing" | "blocked";

export type DimensionCandidateGateInput = {
	planningContext: WarehousePlanningContext | null;
	planningSource: WarehousePlanningSource;
	planningBlockedReason?: string;
	standardDraftId?: string | null;
	standardFieldCount: number;
	grainRequired?: boolean;
	grainDeclaration?: GrainDeclaration;
	grainFieldNames?: string[];
};

export type DimensionCandidateGateResult = {
	status: DimensionCandidateGateStatus;
	reason: string;
	repairRoute?: string;
};

const planningRepairRoute = (context: WarehousePlanningContext | null) => {
	if (!context) return "/governance/subjects";
	return buildPlanningRoute(`/governance/subjects?active=${encodeURIComponent(context.domainId)}`, context);
};

const standardsRepairRoute = (context: WarehousePlanningContext) =>
	buildPlanningRoute("/governance/standards/elements?bindingDraft=1", context);

export const resolveDimensionCandidateGate = ({
	planningContext,
	planningSource,
	planningBlockedReason,
	standardDraftId,
	standardFieldCount,
	grainRequired = false,
	grainDeclaration,
	grainFieldNames = [],
}: DimensionCandidateGateInput): DimensionCandidateGateResult => {
	if (planningBlockedReason) {
		return {
			status: "blocked",
			reason: planningBlockedReason,
			repairRoute: planningRepairRoute(planningContext),
		};
	}
	if (!planningContext) {
		return {
			status: "missing",
			reason: "缺少数仓规划，请先选择主题域并创建 DWD 维度规划",
			repairRoute: "/governance/subjects",
		};
	}
	if (planningSource !== "session") {
		return {
			status: "blocked",
			reason: "规划仅来自 URL，缺少可信的 session 规划草稿",
			repairRoute: planningRepairRoute(planningContext),
		};
	}
	if (!planningContext.domainId) {
		return {
			status: "blocked",
			reason: "规划缺少主题域，不能生成维度模型候选",
			repairRoute: planningRepairRoute(planningContext),
		};
	}
	if (planningContext.warehouseLayer !== "DWD" || planningContext.modelingMode !== "dimension") {
		return {
			status: "blocked",
			reason: "当前规划不是 DWD 维度建模模式",
			repairRoute: planningRepairRoute(planningContext),
		};
	}
	if (grainRequired) {
		const grainGate = resolveGrainDeclaration(grainDeclaration, grainFieldNames);
		if (grainGate.status !== "ready") {
			return {
				status: grainGate.status,
				reason: grainGate.reason,
				repairRoute: planningRepairRoute(planningContext),
			};
		}
	}
	if (!standardDraftId || !planningContext.standardDraftId) {
		return {
			status: "missing",
			reason: "缺少字段标准草稿，请先完成数据元落标",
			repairRoute: standardsRepairRoute(planningContext),
		};
	}
	if (standardFieldCount <= 0) {
		return {
			status: "blocked",
			reason: "标准草稿没有可用于维度建模的字段",
			repairRoute: standardsRepairRoute(planningContext),
		};
	}
	return { status: "ready", reason: "规划、主题域和标准字段已齐备" };
};
