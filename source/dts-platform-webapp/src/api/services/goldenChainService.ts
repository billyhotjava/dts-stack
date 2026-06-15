import apiClient from "../apiClient";

export type GoldenChainSourceKind = "JDBC" | "API" | "FILE";
export type GoldenChainStage =
	| "DRAFT"
	| "SOURCE_READY"
	| "INGESTION_READY"
	| "ODS_READY"
	| "MODEL_READY"
	| "GOVERNANCE_READY"
	| "RELEASE_READY"
	| "CONSUMABLE"
	| "OPERATED";
export type GoldenChainStageStatus = "PENDING" | "READY" | "BLOCKED" | "SKIPPED";

export type GoldenChainSummary = {
	chainKey: string;
	displayName: string;
	sourceKind: GoldenChainSourceKind;
	currentStage: GoldenChainStage;
	currentStageLabel: string;
	status: GoldenChainStageStatus;
	owner: string;
};

export type GoldenChainStageSnapshot = {
	stage: GoldenChainStage;
	stageLabel: string;
	status: GoldenChainStageStatus;
	owner: string;
	evidenceRef?: string;
	failureReason?: string;
	nextAction?: string;
};

export type GoldenChainDetail = GoldenChainSummary & {
	stages: GoldenChainStageSnapshot[];
};

export default {
	list: () => apiClient.get<GoldenChainSummary[]>({ url: "/golden-chains" }),
	detail: (chainKey: string) =>
		apiClient.get<GoldenChainDetail>({ url: `/golden-chains/${encodeURIComponent(chainKey)}` }),
};
