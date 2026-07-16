import type { BusinessObject, ModelSpec } from "./modelingVnextContract";
import type { SemanticBusinessObject, SemanticModel } from "@/api/semanticModelingApi";

export type LedgerReadiness = "READY" | "BLOCKED" | "IN_REVIEW";
export type ModelLedgerLayer = "ODS" | "STG" | "DWD" | "DWS" | "ADS" | "UNKNOWN";

export type BusinessObjectLedgerRow = SemanticBusinessObject & {
	objectKind: "FACT" | "DIMENSION" | "ENTITY" | "EVENT" | "SNAPSHOT";
	processLabel: string;
	grainLabel: string;
	grainStatus: LedgerReadiness;
	keyLabel: string;
	sourceLabel: string;
	standardStatus: LedgerReadiness;
	implementationMode: "DESIGNER_GENERATED" | "DBT_MANAGED" | "LEGACY_READONLY";
	nextAction: string;
};

export type ModelLedgerRow = SemanticModel & {
	layer: ModelLedgerLayer;
	implementationMode: "DESIGNER_GENERATED" | "DBT_MANAGED" | "LEGACY_READONLY";
	artifactLabel: string;
	readiness: LedgerReadiness;
	nextAction: string;
};

const nonEmpty = (value?: string | null) => Boolean(value && value.trim());

export function toLegacyBusinessObject(object: BusinessObject): SemanticBusinessObject {
	return {
		id: object.id,
		processId: object.processId,
		code: object.code,
		name: object.name,
		description: object.description,
		primaryKey: object.businessKey?.join(", "),
		mainTable: object.sourceRefs?.[0]?.ref,
		objectKind: object.objectKind,
		grain: object.grain?.statement,
		implementationMode: object.implementationMode,
		status: object.status,
	};
}

export function toLegacySemanticModel(model: ModelSpec): SemanticModel {
	const implementation = model.implementationMode === "DBT_MANAGED" ? "dbt managed" : "designer generated";
	return {
		id: model.id,
		objectId: model.objectId,
		processId: model.processId,
		type: model.layer,
		name: model.name,
		tableName: model.name,
		description: `${implementation}; revision ${model.revision}`,
		grain: model.grain?.statement,
		materialization: model.materialization,
		status: "DRAFT",
		reviewStatus: "DRAFT",
	};
}

export function buildBusinessObjectLedgerRows(objects: SemanticBusinessObject[]): BusinessObjectLedgerRow[] {
	return objects.map((object) => {
		const hasGrain = nonEmpty(object.primaryKey);
		const hasSource = nonEmpty(object.mainTable);
		const implementationMode = object.implementationMode
			?? (object.description?.includes("dbt") ? "DBT_MANAGED" : "DESIGNER_GENERATED");
		return {
			...object,
			objectKind: object.objectKind ?? "FACT",
			processLabel: object.processId || "未绑定业务过程",
			grainLabel: nonEmpty(object.grain) ? object.grain! : hasGrain ? `按 ${object.primaryKey} 唯一` : "未声明粒度",
			grainStatus: hasGrain ? "READY" : "BLOCKED",
			keyLabel: object.primaryKey || "待补充业务键/技术键",
			sourceLabel: object.mainTable || "待接入来源模型",
			standardStatus: "IN_REVIEW",
			implementationMode,
			nextAction: !hasSource ? "补充来源" : !hasGrain ? "声明粒度" : "进入模型台账",
		};
	});
}

export function buildModelLedgerRows(models: SemanticModel[]): ModelLedgerRow[] {
	return models.map((model) => {
		const layer = ["ODS", "STG", "DWD", "DWS", "ADS"].includes(model.type || "")
			? (model.type as Exclude<ModelLedgerLayer, "UNKNOWN">)
			: "UNKNOWN";
		const implementationMode = model.description?.includes("legacy")
			? "LEGACY_READONLY"
			: model.description?.includes("dbt")
				? "DBT_MANAGED"
				: "DESIGNER_GENERATED";
		const readiness: LedgerReadiness = model.reviewStatus === "SUBMITTED" ? "IN_REVIEW" : model.reviewStatus === "REJECTED" ? "BLOCKED" : "READY";
		return {
			...model,
			layer,
			implementationMode,
			artifactLabel: model.tableName || "未生成物理表",
			readiness,
			nextAction: implementationMode === "LEGACY_READONLY" ? "查看兼容来源" : readiness === "BLOCKED" ? "修复编译/审核" : "查看运行证据",
		};
	});
}
