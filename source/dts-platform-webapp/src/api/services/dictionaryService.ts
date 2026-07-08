import apiClient from "../apiClient";

export type PlatformSystemType = {
	code?: string;
	key?: string;
	value?: string;
	type?: string;
	name?: string;
	label?: string;
	displayName?: string;
	description?: string;
	connectorKey?: string;
	status?: string;
	enabled?: boolean;
};

const pickList = (payload: unknown): PlatformSystemType[] => {
	if (Array.isArray(payload)) {
		return payload as PlatformSystemType[];
	}
	if (!payload || typeof payload !== "object") {
		return [];
	}
	const record = payload as Record<string, unknown>;
	const candidates = [record.items, record.content, record.data, record.records];
	const list = candidates.find(Array.isArray);
	return Array.isArray(list) ? (list as PlatformSystemType[]) : [];
};

export default {
	listSystemTypes: async () =>
		pickList(await apiClient.get<unknown>({ url: "/platform/dict/system-types", _skipErrorToast: true } as any)),
};
