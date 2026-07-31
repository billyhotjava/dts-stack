import api from "@/api/apiClient";
import {
	type CreateDataMartCommand,
	type DataMartCasToken,
	type DataMartStatus,
	type DataMartView,
	toDataMartEtag,
	type UpdateDataMartCommand,
	type WarehousePlanDataMartBaseline,
} from "@/features/modeling/contracts/dataMartContract";

const DATA_MART_RESOURCE = "/modeling/data-marts";

export const listDataMarts = (params?: {
	domainId?: string;
	status?: DataMartStatus;
	keyword?: string;
	offset?: number;
	limit?: number;
}) => api.get<DataMartView[]>({ url: DATA_MART_RESOURCE, params, _skipErrorToast: true } as any);

export const createDataMart = (data: CreateDataMartCommand) =>
	api.post<DataMartView>({ url: DATA_MART_RESOURCE, data, _skipErrorToast: true } as any);

export const updateDataMart = (expected: DataMartCasToken, data: UpdateDataMartCommand) =>
	api.put<DataMartView>({
		url: `${DATA_MART_RESOURCE}/${encodeURIComponent(expected.id)}`,
		headers: { "If-Match": toDataMartEtag(expected) },
		data,
		_skipErrorToast: true,
	} as any);

export const confirmDataMart = (expected: DataMartCasToken) =>
	api.post<DataMartView>({
		url: `${DATA_MART_RESOURCE}/${encodeURIComponent(expected.id)}/confirm`,
		headers: { "If-Match": toDataMartEtag(expected) },
		_skipErrorToast: true,
	} as any);

export const retireDataMart = (expected: DataMartCasToken) =>
	api.post<DataMartView>({
		url: `${DATA_MART_RESOURCE}/${encodeURIComponent(expected.id)}/retire`,
		headers: { "If-Match": toDataMartEtag(expected) },
		_skipErrorToast: true,
	} as any);

const baselineResource = (planId: string) =>
	`/modeling/warehouse-plans/${encodeURIComponent(planId)}/baseline/data-marts`;

export const getWarehousePlanDataMarts = (planId: string) =>
	api.get<WarehousePlanDataMartBaseline>({
		url: baselineResource(planId),
		_skipErrorToast: true,
	} as any);

export const saveWarehousePlanDataMarts = (planId: string, dataMartIds: string[], expectedVersion: number) =>
	api.put<WarehousePlanDataMartBaseline>({
		url: baselineResource(planId),
		data: { dataMartIds, expectedVersion },
		_skipErrorToast: true,
	} as any);
