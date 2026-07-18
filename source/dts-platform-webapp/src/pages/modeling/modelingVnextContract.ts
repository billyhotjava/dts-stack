export const MODELING_CONTRACT_VERSION = 1 as const;

export type ModelingLayer = "ODS" | "STG" | "DWD" | "DWS" | "ADS";
export type ModelingObjectKind = "ENTITY" | "FACT" | "EVENT" | "SNAPSHOT" | "DIMENSION";
export type ModelingModelType = "FACT" | "DIMENSION" | "SUMMARY" | "APPLICATION";
export type ModelingImplementationMode = "DESIGNER_GENERATED" | "DBT_MANAGED";
export type ModelingSourceKind = "TABLE" | "DBT_MODEL" | "DATASET";
export type DomainResolution = "AVAILABLE" | "MISSING" | "ARCHIVED" | "FORBIDDEN";
export type DomainActivityIssue = {
	code: "MODEL_DOMAIN_REQUIRED" | "MODEL_BUSINESS_ACTIVITY_UNAVAILABLE" | "MODEL_BUSINESS_ACTIVITY_NOT_ALLOWED";
	field: "domainId" | "businessActivityRef";
	severity: "ERROR" | "WARNING";
};

export type ModelingGrain = {
	statement: string;
	keys: string[];
};

export type ModelingSourceRef = {
	kind: ModelingSourceKind;
	ref: string;
	layer: ModelingLayer;
};

export type ModelingStandardBinding = {
	fieldName: string;
	standardElementId?: string;
	referenceCode?: string;
	securityLevel?: string;
};

export type BusinessObject = {
	id: string;
	code: string;
	name: string;
	description?: string;
	objectKind: ModelingObjectKind;
	processId: string;
	businessKey: string[];
	grain: ModelingGrain;
	sourceRefs: ModelingSourceRef[];
	status: string;
	implementationMode: ModelingImplementationMode;
};

export type ModelSpec = {
	id: string;
	objectId: string;
	processId: string;
	layer: ModelingLayer;
	modelType: ModelingModelType;
	implementationMode: ModelingImplementationMode;
	name: string;
	grain: ModelingGrain;
	standardBindings: ModelingStandardBinding[];
	sourceRefs: ModelingSourceRef[];
	dimensions?: string[];
	metrics?: string[];
	materialization?: string;
	revision: number;
	dependsOn?: string[];
	legacyRef?: string;
};

export type LegacyModelRef = {
	modelId: string;
	dbtUniqueId: string;
	path: string;
	status: "LEGACY_READONLY";
};

export type ModelingContractFixture = {
	contractVersion: typeof MODELING_CONTRACT_VERSION;
	businessObject: BusinessObject;
	modelSpec: ModelSpec;
};

export type ModelSpecValidation = {
	valid: boolean;
	issues: string[];
};

/** Canonical model boundary: category is required; business activity is optional FACT context. */
export const validateDomainActivity = (
	modelType: ModelingModelType,
	domainId: string | null | undefined,
	businessActivityRef: string | null | undefined,
	resolution: DomainResolution,
): DomainActivityIssue[] => {
	const issues: DomainActivityIssue[] = [];
	if (!domainId?.trim()) {
		issues.push({ code: "MODEL_DOMAIN_REQUIRED", field: "domainId", severity: "ERROR" });
	}
	if (!businessActivityRef?.trim()) {
		return issues;
	}
	if (modelType !== "FACT") {
		issues.push({
			code: "MODEL_BUSINESS_ACTIVITY_NOT_ALLOWED",
			field: "businessActivityRef",
			severity: "ERROR",
		});
	} else if (resolution !== "AVAILABLE") {
		issues.push({
			code: "MODEL_BUSINESS_ACTIVITY_UNAVAILABLE",
			field: "businessActivityRef",
			severity: "WARNING",
		});
	}
	return issues;
};

export const validateModelSpec = (model: ModelSpec): ModelSpecValidation => {
	const issues: string[] = [];
	if (model.layer === "DWD" && model.grain.keys.length === 0) {
		issues.push("DWD 模型必须声明粒度键");
	}
	if (
		model.layer === "DWD" &&
		model.implementationMode === "DESIGNER_GENERATED" &&
		model.standardBindings.length === 0
	) {
		issues.push("DWD 设计器模型至少绑定一个数据标准");
	}
	return { valid: issues.length === 0, issues };
};
