import api from "@/api/apiClient";

const quietGet = <T>(url: string) => api.get<T>({ url, _skipErrorToast: true } as never);
const quietPost = <T>(url: string, data: unknown) => api.post<T>({ url, data, _skipErrorToast: true } as never);
const quietDelete = <T>(url: string) => api.delete<T>({ url, _skipErrorToast: true } as never);

export type WarehouseSystemLayerCode = "ODS_RAW" | "ODS_STANDARDIZED" | "STG" | "DWD" | "DWS" | "ADS";

export type WarehouseLayerView = {
	code: string;
	name: string;
	systemLayerCode: WarehouseSystemLayerCode;
	kind: string;
	responsibility: string;
	namingPrefixes: string[];
	optional: boolean;
	builtin: boolean;
	deletable: boolean;
	disabledReason?: string | null;
};

export type CreateWarehouseLayerCommand = {
	code: string;
	name: string;
	systemLayerCode: WarehouseSystemLayerCode;
	description?: string;
	namingPrefix?: string;
};

export const listWarehouseLayers = () => quietGet<WarehouseLayerView[]>("/modeling/warehouse-layers");

export const createWarehouseLayer = (data: CreateWarehouseLayerCommand) =>
	quietPost<WarehouseLayerView>("/modeling/warehouse-layers", data);

export const deleteWarehouseLayer = (code: string) =>
	quietDelete<void>(`/modeling/warehouse-layers/${encodeURIComponent(code)}`);

export const normalizeWarehouseLayerView = (value: unknown): WarehouseLayerView | undefined => {
	const record = (value ?? {}) as Record<string, unknown>;
	const code = typeof record.code === "string" ? record.code : undefined;
	const name = typeof record.name === "string" ? record.name : undefined;
	const systemLayerCode = typeof record.systemLayerCode === "string" ? record.systemLayerCode : undefined;
	if (!code || !name || !systemLayerCode) return undefined;
	const prefixes = Array.isArray(record.namingPrefixes)
		? record.namingPrefixes.filter((item): item is string => typeof item === "string")
		: [];
	return {
		code,
		name,
		systemLayerCode: systemLayerCode as WarehouseSystemLayerCode,
		kind: typeof record.kind === "string" ? record.kind : "",
		responsibility: typeof record.responsibility === "string" ? record.responsibility : "",
		namingPrefixes: prefixes,
		optional: record.optional === true,
		builtin: record.builtin === true,
		deletable: record.deletable === true,
		disabledReason:
			record.disabledReason == null ? null : typeof record.disabledReason === "string" ? record.disabledReason : null,
	};
};
