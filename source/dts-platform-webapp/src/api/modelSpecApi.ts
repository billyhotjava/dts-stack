import api from "@/api/apiClient";
import type {
	CanonicalModelSpecView,
	CreateModelSpecCommand,
	ModelSpecCasToken,
	ModelSpecLayer,
	ModelSpecRevisionConflictDetails,
	ModelSpecType,
	ModelSpecView,
	UpdateModelSpecCommand,
} from "@/pages/modeling/modelSpecV2Contract";
import { toModelSpecEtag } from "@/pages/modeling/modelSpecV2Contract";

const MODEL_SPEC_RESOURCE = "/modeling/model-specs";

export type ModelSpecListParams = {
	planId?: string;
	domainId?: string;
	modelType?: ModelSpecType;
	layer?: ModelSpecLayer;
};

export type ModelSpecRevisionConflict = {
	code: "MODEL_SPEC_REVISION_CONFLICT";
	data: ModelSpecRevisionConflictDetails;
};

export const listModelSpecs = (params?: ModelSpecListParams) =>
	api.get<ModelSpecView[]>({ url: MODEL_SPEC_RESOURCE, params, _skipErrorToast: true } as any);

export const getModelSpec = (id: string) =>
	api.get<ModelSpecView>({
		url: `${MODEL_SPEC_RESOURCE}/${encodeURIComponent(id)}`,
		_skipErrorToast: true,
	} as any);

export const createModelSpec = (data: CreateModelSpecCommand) =>
	api.post<CanonicalModelSpecView>({ url: MODEL_SPEC_RESOURCE, data, _skipErrorToast: true } as any);

export const updateModelSpec = (expected: ModelSpecCasToken, data: UpdateModelSpecCommand) =>
	api.put<CanonicalModelSpecView>({
		url: `${MODEL_SPEC_RESOURCE}/${encodeURIComponent(expected.id)}`,
		headers: { "If-Match": toModelSpecEtag(expected) },
		data,
		_skipErrorToast: true,
	} as any);
