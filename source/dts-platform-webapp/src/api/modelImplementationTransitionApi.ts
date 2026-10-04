import api from "@/api/apiClient";
import type { ModelImplementationView } from "@/features/modeling/contracts/modelImplementationContract";
import type { CanonicalModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";

export type DbtPreviewFile = {
	path: string;
	content: string;
	checksum: string;
	nodeKind: string;
	artifactTypes: string[];
};

export type DbtImplementationPreview = {
	modelRevision: number;
	modelChecksum: string;
	implementationRevision: number;
	implementationChecksum: string;
	ownership: "DESIGNER_GENERATED" | "DBT_MANAGED";
	files: DbtPreviewFile[];
	previewChecksum: string;
	readOnly: true;
};

export type OwnershipTransitionValidation = {
	allowed: boolean;
	reasons: string[];
	previewChecksum: string;
	reversible: boolean;
};

export type OwnershipTransitionResult = {
	transitionId: string;
	model: CanonicalModelSpecView;
	implementation: ModelImplementationView;
	sourceOwnership: "DESIGNER_GENERATED" | "DBT_MANAGED";
	targetOwnership: "DBT_MANAGED";
};

const implementationPath = (modelSpecId: string) => `/modeling/model-specs/${encodeURIComponent(modelSpecId)}/implementation`;

export const getDbtImplementationPreview = (modelSpecId: string, params: { modelRevision: number; implementationRevision: number }) =>
	api.get<DbtImplementationPreview>({ url: `${implementationPath(modelSpecId)}/dbt-preview`, params, _skipErrorToast: true } as any);

export const validateDbtOwnershipTransition = (
	modelSpecId: string,
	params: { modelRevision: number; implementationRevision: number; modelEtag: string; implementationEtag: string },
) =>
	api.post<OwnershipTransitionValidation>({
		url: `${implementationPath(modelSpecId)}/ownership-transitions/validate`,
		params: { modelRevision: params.modelRevision, implementationRevision: params.implementationRevision },
		data: { targetOwnership: "DBT_MANAGED" },
		headers: { "If-Match": params.modelEtag, "If-Match-Implementation": params.implementationEtag },
		_skipErrorToast: true,
	} as any);

export const transitionDbtOwnership = (
	modelSpecId: string,
	params: {
		modelRevision: number;
		implementationRevision: number;
		modelEtag: string;
		implementationEtag: string;
		previewChecksum: string;
		idempotencyKey: string;
	},
) =>
	api.post<OwnershipTransitionResult>({
		url: `${implementationPath(modelSpecId)}/ownership-transitions`,
		params: { modelRevision: params.modelRevision, implementationRevision: params.implementationRevision },
		data: { targetOwnership: "DBT_MANAGED", previewChecksum: params.previewChecksum, idempotencyKey: params.idempotencyKey },
		headers: { "If-Match": params.modelEtag, "If-Match-Implementation": params.implementationEtag },
		_skipErrorToast: true,
	} as any);
