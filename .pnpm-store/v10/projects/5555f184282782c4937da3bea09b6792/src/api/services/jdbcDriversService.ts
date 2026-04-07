import apiClient from "../apiClient";

export type InfraJdbcDriver = {
	id: string;
	fileName: string;
	filePath: string;
	driverClass?: string;
	version?: string;
	jdkSpec?: string;
	createdAt?: string;
	lastUpdatedAt?: string;
	missing?: boolean;
};

export type JdbcDriverUpdatePayload = {
	driverClass?: string;
	version?: string;
	jdkSpec?: string;
};

export default {
	list: () => apiClient.get<InfraJdbcDriver[]>({ url: "/infra/jdbc-drivers" }),
	upload: (file: File) => {
		const formData = new FormData();
		formData.append("file", file);
		return apiClient.post<InfraJdbcDriver>({ url: "/infra/jdbc-drivers", data: formData });
	},
	update: (id: string, payload: JdbcDriverUpdatePayload) =>
		apiClient.put<InfraJdbcDriver>({ url: `/infra/jdbc-drivers/${id}`, data: payload }),
	remove: (id: string) => apiClient.delete<void>({ url: `/infra/jdbc-drivers/${id}` }),
};
