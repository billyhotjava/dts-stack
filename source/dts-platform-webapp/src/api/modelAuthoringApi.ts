import api from "@/api/apiClient";
import type { ModelImplementationView } from "@/features/modeling/contracts/modelImplementationContract";
import type { ModelSpecFieldIssue, ModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
import type {
	DbtDraftCommit,
	DbtDraftFile,
	DbtDraftSourceBundle,
	DbtDraftValidation,
	DbtImplementationDraft,
	ModelAuthoringSnapshot,
} from "./dbtImplementationDraftApi";

export type ModelAuthoringAction =
	| "OPEN_VISUAL"
	| "OPEN_CODE"
	| "EDIT_MODEL"
	| "EDIT_IMPLEMENTATION"
	| "SAVE"
	| "VALIDATE"
	| "COMMIT"
	| "FORK_DRAFT";

export type ModelAuthoringOrigin = "SYSTEM_GENERATED" | "MANUAL_CODE" | "DBT_ZIP_IMPORT" | "UNKNOWN";
export type ModelProjectionCoverage = "FULL" | "PARTIAL" | "NONE" | "UNKNOWN";

export type ModelAuthoringProjectionNode = {
	nodeId: string;
	kind: string;
	editable: boolean;
	sourcePath?: string | null;
	line?: number | null;
	column?: number | null;
	checksum?: string | null;
};

export type ModelAuthoringProjection = {
	coverage: ModelProjectionCoverage;
	lossless: boolean;
	managedPaths: string[];
	rawNodes: ModelAuthoringProjectionNode[];
	reasons: string[];
};

export type ModelAuthoringProvenance = {
	origin: ModelAuthoringOrigin;
	sourceKind?: DbtDraftSourceBundle["sourceKind"] | null;
	lossless: boolean;
	bundleChecksum?: string | null;
};

export type ModelAuthoringContext = {
	model: ModelSpecView;
	implementation?: ModelImplementationView | null;
	provenance: ModelAuthoringProvenance;
	projection: ModelAuthoringProjection;
	openDraft?: DbtImplementationDraft | null;
	allowedActions: ModelAuthoringAction[];
	publishedForkRequired: boolean;
};

export type ModelAuthoringDraft = {
	model: ModelSpecView;
	draft: DbtImplementationDraft;
	provenance: ModelAuthoringProvenance;
	projection: ModelAuthoringProjection;
	allowedActions: ModelAuthoringAction[];
};

export type ModelAuthoringSave = {
	draftId: string;
	etag: string;
	modelSpecSnapshot: ModelAuthoringSnapshot;
	projection: ModelAuthoringProjection;
	fileCount: number;
	totalBytes: number;
	files: DbtDraftFile[];
};

export type ModelAuthoringValidation = {
	implementationValidation?: DbtDraftValidation | null;
	modelIssues: ModelSpecFieldIssue[];
	projectionIssues: DbtDraftValidation["diagnostics"];
};

export type ModelAuthoringCommit = {
	receipt: DbtDraftCommit;
	origin: ModelAuthoringOrigin;
};

const basePath = (modelSpecId: string) => `/modeling/model-specs/${encodeURIComponent(modelSpecId)}`;

export const getModelAuthoringContext = (modelSpecId: string) =>
	api.get<ModelAuthoringContext>({
		url: `${basePath(modelSpecId)}/authoring-context`,
		_skipErrorToast: true,
	} as any);

export const createModelAuthoringDraft = (
	modelSpecId: string,
	request: {
		intent: "EDIT_DRAFT" | "FORK_PUBLISHED";
		baseModelRevision: number;
		baseModelChecksum: string;
		baseImplementationRevision?: number | null;
		baseImplementationChecksum?: string | null;
		targetPhysicalName?: string | null;
		idempotencyKey: string;
	},
) =>
	api.post<ModelAuthoringDraft>({
		url: `${basePath(modelSpecId)}/authoring-drafts`,
		data: request,
		_skipErrorToast: true,
	} as any);

export const saveModelAuthoringDraft = (
	modelSpecId: string,
	draftId: string,
	request: {
		expectedEtag: string;
		modelSpecSnapshot: ModelAuthoringSnapshot;
		files: DbtDraftFile[];
		activeView: "VISUAL" | "CODE";
	},
) =>
	api.put<ModelAuthoringSave>({
		url: `${basePath(modelSpecId)}/authoring-drafts/${encodeURIComponent(draftId)}`,
		data: request,
		_skipErrorToast: true,
	} as any);

export const validateModelAuthoringDraft = (modelSpecId: string, draftId: string, expectedEtag: string) =>
	api.post<ModelAuthoringValidation>({
		url: `${basePath(modelSpecId)}/authoring-drafts/${encodeURIComponent(draftId)}/validate`,
		data: { expectedEtag },
		_skipErrorToast: true,
	} as any);

export const commitModelAuthoringDraft = (
	modelSpecId: string,
	draftId: string,
	request: {
		expectedEtag: string;
		validatedChecksum: string;
		dependencyChecksum?: string | null;
		idempotencyKey: string;
	},
) =>
	api.post<ModelAuthoringCommit>({
		url: `${basePath(modelSpecId)}/authoring-drafts/${encodeURIComponent(draftId)}/commit`,
		data: { implementation: request },
		_skipErrorToast: true,
	} as any);
