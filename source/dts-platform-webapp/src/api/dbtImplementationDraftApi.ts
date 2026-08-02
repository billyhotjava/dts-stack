import api from "@/api/apiClient";

export type DbtDraftFile = { path: string; content: string };

export type DbtDraftBundleFile = DbtDraftFile & { checksum: string; byteSize: number };

export type DbtDraftSourceBundle = {
	projectKey: string;
	projectChecksum: string;
	bundleChecksum: string;
	sourceKind: "FROZEN_SOURCE_BUNDLE" | "CANONICAL_ARTIFACT_RECONSTRUCTION" | "CANONICAL_INITIALIZATION";
	lossless: boolean;
	files: DbtDraftBundleFile[];
};

export type DbtImplementationDraft = {
	draftId: string;
	planId: string;
	modelSpecId: string;
	baseModelRevision: number;
	baseModelChecksum: string;
	baseImplementationRevision?: number | null;
	baseImplementationChecksum?: string | null;
	state: "DRAFT" | "VALIDATED" | "COMMITTING" | "COMMITTED";
	etag: string;
	expiresAt: string;
	sourceBundle?: DbtDraftSourceBundle | null;
};

export type DbtDraftValidation = {
	draftId: string;
	state: "VALIDATED";
	etag: string;
	expiresAt: string;
	validatedChecksum: string;
	diagnostics: Array<{
		code: string;
		severity: string;
		path?: string | null;
		modelUniqueId?: string | null;
		message: string;
	}>;
	proposedStructure: Array<{
		dbtUniqueId: string;
		name: string;
		resourcePath: string;
		materialization: string;
		nodeKind: string;
		dependencies: string[];
	}>;
};

export type DbtDraftCommit = {
	draftId: string;
	modelSpecId: string;
	modelRevision: number;
	modelChecksum: string;
	implementationId: string;
	implementationRevision: number;
	implementationChecksum: string;
	artifactCount: number;
	etag: string;
};

const basePath = (modelSpecId: string) => `/modeling/model-specs/${encodeURIComponent(modelSpecId)}/dbt-drafts`;

export const createDbtImplementationDraft = (
	modelSpecId: string,
	request: {
		planId: string;
		baseModelRevision: number;
		baseModelChecksum: string;
		baseImplementationRevision?: number | null;
		baseImplementationChecksum?: string | null;
		idempotencyKey: string;
	},
) => api.post<DbtImplementationDraft>({ url: basePath(modelSpecId), data: request, _skipErrorToast: true } as any);

export const saveDbtImplementationDraftFiles = (
	modelSpecId: string,
	draftId: string,
	request: { expectedEtag: string; files: DbtDraftFile[] },
) =>
	api.put<{ draftId: string; etag: string; expiresAt: string; fileCount: number; totalBytes: number }>({
		url: `${basePath(modelSpecId)}/${encodeURIComponent(draftId)}/files`,
		data: request,
		_skipErrorToast: true,
	} as any);

export const validateDbtImplementationDraft = (
	modelSpecId: string,
	draftId: string,
	request: { expectedEtag: string },
) =>
	api.post<DbtDraftValidation>({
		url: `${basePath(modelSpecId)}/${encodeURIComponent(draftId)}/validate`,
		data: request,
		_skipErrorToast: true,
	} as any);

export const commitDbtImplementationDraft = (
	modelSpecId: string,
	draftId: string,
	request: { expectedEtag: string; validatedChecksum: string; idempotencyKey: string },
) =>
	api.post<DbtDraftCommit>({
		url: `${basePath(modelSpecId)}/${encodeURIComponent(draftId)}/commit`,
		data: request,
		_skipErrorToast: true,
	} as any);
