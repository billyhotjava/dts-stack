import api from "@/api/apiClient";

const quietGet = <T>(url: string) => api.get<T>({ url, _skipErrorToast: true } as any);
const quietPost = <T>(url: string, data: unknown) => api.post<T>({ url, data, _skipErrorToast: true } as any);
const quietDelete = <T>(url: string) => api.delete<T>({ url, _skipErrorToast: true } as any);

export type Sprint64BusinessProcess = {
	version: number;
	processId: string;
	domainId: string;
	name: string;
	description?: string;
	createdAt?: string;
	updatedAt?: string;
};

export type Sprint64WarehouseLayer = {
	code: string;
	title: string;
	responsibility: string;
	allowedUpstream: string[];
	namingPrefixes: string[];
};

export type Sprint64ConformedDimension = {
	dimensionId: string;
	name: string;
	sourceModel?: string;
	domainIds: string[];
};

export type Sprint64BusMatrixLink = {
	processId: string;
	dimensionId: string;
	enabled: boolean;
};

export type Sprint64GrainValidation = {
	status: "not_required" | "blocked" | "ready" | string;
	message: string;
	statement?: string;
	grainKeys?: string[];
};

export const listBusinessProcessesApi = (domainId: string) =>
	quietGet<Sprint64BusinessProcess[]>(`/governance/sprint64/domains/${encodeURIComponent(domainId)}/processes`);

export const createBusinessProcessApi = (
	domainId: string,
	data: { processId: string; name: string; description?: string },
) => quietPost<Sprint64BusinessProcess>(`/governance/sprint64/domains/${encodeURIComponent(domainId)}/processes`, data);

export const deleteBusinessProcessApi = (domainId: string, processId: string) =>
	quietDelete<boolean>(
		`/governance/sprint64/domains/${encodeURIComponent(domainId)}/processes/${encodeURIComponent(processId)}`,
	);

export const listWarehouseLayersApi = () => quietGet<Sprint64WarehouseLayer[]>("/governance/sprint64/warehouse-layers");

export const listConformedDimensionsApi = (domainId: string) =>
	quietGet<Sprint64ConformedDimension[]>(
		`/governance/sprint64/domains/${encodeURIComponent(domainId)}/conformed-dimensions`,
	);

export const listBusMatrixApi = (domainId: string) =>
	quietGet<Sprint64BusMatrixLink[]>(`/governance/sprint64/domains/${encodeURIComponent(domainId)}/bus-matrix`);

export const saveBusMatrixLinkApi = (domainId: string, data: Sprint64BusMatrixLink) =>
	api.put<Sprint64BusMatrixLink>({
		url: `/governance/sprint64/domains/${encodeURIComponent(domainId)}/bus-matrix`,
		data,
	});

export const validateGrainApi = (data: {
	warehouseLayer: string;
	statement?: string;
	grainKeys?: string[];
}) => quietPost<Sprint64GrainValidation>("/governance/sprint64/grain/validate", data);
