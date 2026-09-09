import api from "@/api/apiClient";

const MODEL_SPEC_RESOURCE = "/modeling/model-specs";

export type ModelUpstreamPin =
	import("@/features/modeling/contracts/modelImplementationContract").PinnedUpstreamModelImplementationInput;
export type ModelInputAvailability = {
	modelSpecId: string;
	revision: number | null;
	checksum: string | null;
	implementationState: "NONE" | "ACTIVE" | "INACTIVE" | "UNKNOWN";
	currentPin: ModelUpstreamPin | null;
	selectable: boolean;
	blockReason: string | null;
	referenceState: "UNSELECTED" | "CURRENT" | "DESIGN_DRIFT" | "IMPLEMENTATION_DRIFT" | "UNAVAILABLE";
	selectedPin: ModelUpstreamPin | null;
	referenceReason: string | null;
};
export type ModelInputOwner = { ownerModelSpecId: string; ownerRevision: number; ownerChecksum: string };
export const getModelUpstreamAvailability = (
	data: ModelInputOwner & { modelSpecIds: string[]; selectedInputs: ModelUpstreamPin[] },
	signal?: AbortSignal,
) =>
	api.post<ModelInputOwner & { observedAt: string; items: ModelInputAvailability[] }>({
		url: `${MODEL_SPEC_RESOURCE}/upstream-availability`,
		data,
		signal,
		_skipErrorToast: true,
	} as any);
export type ModelInputFieldSource = {
	index: number;
	input: import("@/features/modeling/contracts/modelImplementationContract").ModelImplementationInput | null;
	alias: string;
	schemaState: "RESOLVED" | "UNAVAILABLE";
	fields: Array<{ name: string; dataType: string; nullable: boolean | null }>;
};
export const getModelInputFields = (
	data: ModelInputOwner & {
		inputMode: import("@/features/modeling/contracts/modelImplementationContract").ModelImplementationInputMode;
		inputs: import("@/features/modeling/contracts/modelImplementationContract").ModelImplementationInput[];
	},
	signal?: AbortSignal,
) =>
	api.post<
		ModelInputOwner & { sources: ModelInputFieldSource[]; issues: Array<{ reason: string; fieldPath: string }> }
	>({
		url: `${MODEL_SPEC_RESOURCE}/upstream-fields`,
		data,
		signal,
		_skipErrorToast: true,
	} as any);
