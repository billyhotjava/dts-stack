import api from "@/api/apiClient";
import type {
	ModelRepresentationScope,
	ModelRepresentationView,
} from "@/features/modeling/contracts/modelRepresentationContract";

export type ModelRepresentationParams = {
	modelRevision: number;
	implementationRevision?: number;
	representationScope: ModelRepresentationScope;
};

export const getModelRepresentation = (modelSpecId: string, params: ModelRepresentationParams) =>
	api.get<ModelRepresentationView>({
		url: `/modeling/model-specs/${encodeURIComponent(modelSpecId)}/representations`,
		params,
		_skipErrorToast: true,
	} as any);
