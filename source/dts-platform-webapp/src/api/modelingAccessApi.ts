import api from "@/api/apiClient";
export type ModelingAuthorization = {
	canModel: boolean;
	canSelectDepartment: boolean;
	departmentCode: string;
	departments: { code: string; name: string }[];
};
export type ModelAccess = {
	ownerId: string | null;
	ownerName: string | null;
	canEdit: boolean;
	canManage: boolean;
	departmentCode: string;
};
export type ModelGrant = {
	id: string;
	granteeType: "USER" | "ROLE";
	granteeId: string;
	granteeName: string;
	permission: "EDITOR";
};
export type ModelingCandidate = { id: string; displayName: string; username: string; deptCode: string };
let department = "";
export const selectedModelingDepartment = () => department || undefined;
export const selectModelingDepartment = (value: string) => {
	department = value;
};
export const getModelingAuthorization = () =>
	api.get<ModelingAuthorization>({ url: "/modeling/authorization", _skipErrorToast: true } as any);
export async function getModelAccess(ids: string[]): Promise<Record<string, ModelAccess>> {
	const result: Record<string, ModelAccess> = {};
	const unique = Array.from(new Set(ids)).sort();
	for (let offset = 0; offset < unique.length; offset += 200)
		Object.assign(
			result,
			await api.get<Record<string, ModelAccess>>({
				url: "/modeling/model-specs/access-capabilities",
				params: { ids: unique.slice(offset, offset + 200).join(",") },
				_skipErrorToast: true,
			} as any),
		);
	return result;
}
const grantsUrl = (id: string) => `/modeling/model-specs/${encodeURIComponent(id)}/access-grants`;
export const getModelGrants = (id: string) =>
	api.get<{ canManage: boolean; items: ModelGrant[] }>({ url: grantsUrl(id), _skipErrorToast: true } as any);
export const getModelAccessCandidates = (id: string) =>
	api.get<ModelingCandidate[]>({
		url: `/modeling/model-specs/${encodeURIComponent(id)}/access-candidates`,
		_skipErrorToast: true,
	} as any);
export const grantModelEdit = (id: string, granteeType: "USER" | "ROLE", granteeId: string) =>
	api.post<ModelGrant>({
		url: grantsUrl(id),
		data: { granteeType, granteeId, permission: "EDITOR" },
		_skipErrorToast: true,
	} as any);
export const revokeModelEdit = (id: string, grantId: string) =>
	api.delete({ url: `${grantsUrl(id)}/${encodeURIComponent(grantId)}`, _skipErrorToast: true } as any);
export type SimilarModel = { modelSpecId: string; name: string; layer: string; status: string; matchReasons: string[] };
export const findSimilarModels = (params: {
	planId: string;
	modelType: string;
	businessProcessId?: string | null;
	sourceKeys?: string;
	grainKeys?: string;
	excludeModelSpecId?: string;
}) =>
	api.get<SimilarModel[]>({
		url: "/modeling/model-specs/similar",
		params,
		timeout: 2000,
		_skipErrorToast: true,
	} as any);
