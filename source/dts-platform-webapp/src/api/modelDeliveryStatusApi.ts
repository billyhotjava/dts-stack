import api from "@/api/apiClient";
import type { ReleaseCandidateWorkbench } from "./modelSpecApi";

export const MODEL_WIZARD_STEPS = ["definition", "implementation", "verification"] as const;
export type ModelWizardStep = (typeof MODEL_WIZARD_STEPS)[number] | "delivery";
export type DeliveryStepState =
	| "NOT_STARTED"
	| "WAITING_INPUT"
	| "RUNNING"
	| "SUCCEEDED"
	| "FAILED"
	| "NOT_APPLICABLE"
	| "UNKNOWN";
export type DeliveryAction = {
	code: string;
	enabled: boolean;
	reasonCode: string | null;
	targetId: string | null;
	expectedVersion: number | null;
};
export type DeliveryOutput = {
	resourceId: string | null;
	state: DeliveryStepState;
	reasonCode: string | null;
	message: string;
	matchesCurrentTarget: boolean;
	updatedAt: string | null;
};
export type ModelingResult = {
	state: DeliveryStepState;
	reasonCode: string | null;
	modelSpecId: string;
	modelRevision: number;
	modelChecksum: string;
	implementationRevision: number | null;
	implementationChecksum: string | null;
	buildMode: "SCHEMA_ONLY" | "DATA_BUILD";
	environment: string | null;
	candidateId: string | null;
	runGroupId: string | null;
	targetRelation: string | null;
	matchesCurrentTarget: boolean;
	observedAt: string | null;
};
export type ModelDeliveryStatus = {
	modelingResult?: ModelingResult;
	dataPrimaryAction?: DeliveryAction | null;
	workspace: ReleaseCandidateWorkbench | null;
	modelSpecId: string;
	modelRevision: number;
	modelChecksum: string;
	planId: string;
	environment: string | null;
	candidate: {
		id: string;
		version: number;
		status: string;
		entryRevision: number;
		entryChecksum: string;
		matchesCurrentModel: boolean;
		updatedAt: string;
	} | null;
	observedAt: string;
	recommendedStep: ModelWizardStep;
	wizard: Array<{
		key: ModelWizardStep;
		canView: boolean;
		canEdit: boolean;
		reasonCode: string | null;
		primaryAction: DeliveryAction | null;
	}>;
	steps: Array<{
		key: "materialization" | "quality" | "publication" | "catalog" | "analysis";
		state: DeliveryStepState;
		reasonCode: string | null;
		message: string;
		evidenceRevision: number | null;
		matchesCurrentTarget: boolean;
		resourceId: string | null;
		updatedAt: string | null;
		outputs: DeliveryOutput[];
	}>;
	actions: DeliveryAction[];
};
export const getModelDeliveryStatus = (id: string, environment?: string, candidateId?: string, signal?: AbortSignal) =>
	api.get<ModelDeliveryStatus>({
		url: `/modeling/model-specs/${encodeURIComponent(id)}/delivery-status`,
		params: { environment: environment || undefined, candidateId: candidateId || undefined },
		signal,
		_skipErrorToast: true,
	} as any);

/** F15 K1: per-row build and publication facts for the modeling workbench; never quality or serving state. */
export type ModelPublishedRelease = {
	releaseId: string;
	modelRevision: number;
	environment: string | null;
	publishedAt: string;
};
export type ModelWorkbenchSummary = {
	modelSpecId: string;
	modelRevision: number;
	modelChecksum: string | null;
	readState: "OK" | "FAILED";
	reasonCode: string | null;
	build: ModelingResult | null;
	publishedReadState: "OK" | "FAILED";
	published: ModelPublishedRelease | null;
};
export const MODEL_WORKBENCH_SUMMARY_LIMIT = 50;
export const getModelWorkbenchSummaries = (modelSpecIds: string[], environment?: string, signal?: AbortSignal) =>
	api.post<ModelWorkbenchSummary[]>({
		url: "/modeling/model-specs/workbench-summaries",
		data: { modelSpecIds, environment: environment || undefined },
		signal,
		_skipErrorToast: true,
	} as any);

export function normalizeModelWizardStep(value: string | null): ModelWizardStep | null {
	return value === "delivery" || MODEL_WIZARD_STEPS.some(step => step === value) ? (value as ModelWizardStep) : null;
}
