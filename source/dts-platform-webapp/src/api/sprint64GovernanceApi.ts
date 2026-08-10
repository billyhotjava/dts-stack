import api from "@/api/apiClient";

const quietGet = <T>(url: string) => api.get<T>({ url, _skipErrorToast: true } as any);
const quietPost = <T>(url: string, data: unknown) => api.post<T>({ url, data, _skipErrorToast: true } as any);
const quietDelete = <T>(url: string) => api.delete<T>({ url, _skipErrorToast: true } as any);

export type ModelingFactProvenance = {
	sourceType: "MANUAL" | "TEMPLATE" | "IMPORTED" | "SCANNED" | string;
	sourceId?: string;
	sourceVersion?: string;
	confirmed: boolean;
};

export type Sprint64BusinessProcess = ModelingFactProvenance & {
	id: string;
	version: number;
	processId: string;
	domainId: string;
	name: string;
	description?: string;
	lifecycleStatus?: "ACTIVE" | "RETIRED" | string;
	createdAt?: string;
	updatedAt?: string;
};

export type Sprint64WarehouseLayer = {
	code: string;
	title: string;
	responsibility: string;
	kind?: "INGESTION" | "TECHNICAL" | "DETAIL" | "SERVICE" | "APPLICATION" | string;
	optional?: boolean;
	businessOutput?: boolean;
	dbtRole?: string;
	allowedUpstream: string[];
	namingPrefixes: string[];
};

export type Sprint64ConformedDimension = ModelingFactProvenance & {
	dimensionId: string;
	name: string;
	sourceModel?: string;
	domainIds: string[];
};

export type ModelingCandidateConfirmationRequest = {
	processIds: string[];
	dimensionIds: string[];
};

export type ModelingCandidateConfirmationResult = {
	confirmedProcesses: number;
	confirmedDimensions: number;
};

export type ModelingTemplateBusinessProcess = {
	processId: string;
	name: string;
	description?: string;
};

export type ModelingTemplateConformedDimension = {
	dimensionId: string;
	name: string;
	sourceModel?: string;
};

export type ModelingTemplate = {
	contractVersion: number;
	templateId: string;
	version: string;
	name: string;
	description?: string;
	industry?: string;
	optional: boolean;
	installationMode: string;
	businessProcesses: ModelingTemplateBusinessProcess[];
	conformedDimensions: ModelingTemplateConformedDimension[];
};

export type ModelingTemplateInstallationResult = {
	domainId: string;
	templateId: string;
	templateVersion: string;
	status: "INSTALLED" | "ALREADY_INSTALLED" | string;
	createdProcesses: number;
	createdDimensions: number;
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
	quietGet<Sprint64BusinessProcess[]>(`/modeling/business-processes?domainId=${encodeURIComponent(domainId)}`);

export const createBusinessProcessApi = (
	domainId: string,
	data: { processId: string; name: string; description?: string },
) => quietPost<Sprint64BusinessProcess>(`/modeling/business-processes?domainId=${encodeURIComponent(domainId)}`, data);

export const deleteBusinessProcessApi = (domainId: string, processId: string) =>
	quietDelete<boolean>(
		`/modeling/business-processes/${encodeURIComponent(processId)}?domainId=${encodeURIComponent(domainId)}`,
	);

export const listWarehouseLayersApi = () => quietGet<Sprint64WarehouseLayer[]>("/governance/sprint64/warehouse-layers");

export const listConformedDimensionsApi = (domainId: string) =>
	quietGet<Sprint64ConformedDimension[]>(
		`/governance/sprint64/domains/${encodeURIComponent(domainId)}/conformed-dimensions`,
	);

export const createConformedDimensionApi = (
	domainId: string,
	data: { dimensionId: string; name: string; sourceModel?: string },
) =>
	quietPost<Sprint64ConformedDimension>(
		`/governance/sprint64/domains/${encodeURIComponent(domainId)}/conformed-dimensions`,
		data,
	);

export const deleteConformedDimensionApi = (domainId: string, dimensionId: string) =>
	quietDelete<boolean>(
		`/governance/sprint64/domains/${encodeURIComponent(domainId)}/conformed-dimensions/${encodeURIComponent(dimensionId)}`,
	);

export const confirmModelingCandidatesApi = (domainId: string, data: ModelingCandidateConfirmationRequest) =>
	quietPost<ModelingCandidateConfirmationResult>(
		`/governance/sprint64/domains/${encodeURIComponent(domainId)}/modeling-candidates/confirm`,
		data,
	);

export const listModelingTemplatesApi = () => quietGet<ModelingTemplate[]>("/governance/modeling-templates");

export const installModelingTemplateApi = (templateId: string, domainId: string) =>
	quietPost<ModelingTemplateInstallationResult>(
		`/governance/modeling-templates/${encodeURIComponent(templateId)}/install?domainId=${encodeURIComponent(domainId)}`,
		undefined,
	);

export const listBusMatrixApi = (domainId: string) =>
	quietGet<Sprint64BusMatrixLink[]>(`/governance/sprint64/domains/${encodeURIComponent(domainId)}/bus-matrix`);

export const saveBusMatrixLinkApi = (domainId: string, data: Sprint64BusMatrixLink) =>
	api.put<Sprint64BusMatrixLink>({
		url: `/governance/sprint64/domains/${encodeURIComponent(domainId)}/bus-matrix`,
		data,
	});

export const validateGrainApi = (data: { warehouseLayer: string; statement?: string; grainKeys?: string[] }) =>
	quietPost<Sprint64GrainValidation>("/governance/sprint64/grain/validate", data);
