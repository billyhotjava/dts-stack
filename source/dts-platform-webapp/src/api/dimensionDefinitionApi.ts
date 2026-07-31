import api from "@/api/apiClient";
import {
	type CreateDimensionDefinitionCommand,
	type DimensionDefinitionCasToken,
	type DimensionDefinitionStatus,
	type DimensionDefinitionView,
	toDimensionDefinitionEtag,
	type UpdateDimensionDefinitionCommand,
} from "@/features/modeling/contracts/dimensionDefinitionContract";

const DIMENSION_DEFINITION_RESOURCE = "/modeling/dimension-definitions";

export type DimensionDefinitionListParams = {
	domainId?: string;
	dataMartId?: string;
	status?: DimensionDefinitionStatus;
	offset?: number;
	limit?: number;
};

const versionHeaders = (expected: DimensionDefinitionCasToken) => ({
	"If-Match": toDimensionDefinitionEtag(expected),
});

export const listDimensionDefinitions = (params?: DimensionDefinitionListParams) =>
	api.get<DimensionDefinitionView[]>({ url: DIMENSION_DEFINITION_RESOURCE, params, _skipErrorToast: true } as any);

export const getDimensionDefinition = (id: string) =>
	api.get<DimensionDefinitionView>({
		url: `${DIMENSION_DEFINITION_RESOURCE}/${encodeURIComponent(id)}`,
		_skipErrorToast: true,
	} as any);

export const getDimensionDefinitionRevision = (id: string, revision: number) =>
	api.get<DimensionDefinitionView>({
		url: `${DIMENSION_DEFINITION_RESOURCE}/${encodeURIComponent(id)}/revisions/${encodeURIComponent(String(revision))}`,
		_skipErrorToast: true,
	} as any);

export const createDimensionDefinition = (data: CreateDimensionDefinitionCommand) =>
	api.post<DimensionDefinitionView>({ url: DIMENSION_DEFINITION_RESOURCE, data, _skipErrorToast: true } as any);

export const updateDimensionDefinition = (
	expected: DimensionDefinitionCasToken,
	data: UpdateDimensionDefinitionCommand,
) =>
	api.put<DimensionDefinitionView>({
		url: `${DIMENSION_DEFINITION_RESOURCE}/${encodeURIComponent(expected.id)}`,
		headers: versionHeaders(expected),
		data,
		_skipErrorToast: true,
	} as any);

export const confirmDimensionDefinition = (expected: DimensionDefinitionCasToken) =>
	api.post<DimensionDefinitionView>({
		url: `${DIMENSION_DEFINITION_RESOURCE}/${encodeURIComponent(expected.id)}/confirm`,
		headers: versionHeaders(expected),
		_skipErrorToast: true,
	} as any);

export const retireDimensionDefinition = (expected: DimensionDefinitionCasToken) =>
	api.post<DimensionDefinitionView>({
		url: `${DIMENSION_DEFINITION_RESOURCE}/${encodeURIComponent(expected.id)}/retire`,
		headers: versionHeaders(expected),
		_skipErrorToast: true,
	} as any);
