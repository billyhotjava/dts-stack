import apiClient from "../apiClient";

export type ConnectorDriverBinding = {
	policy: "BUNDLED" | "ADMIN_PROVIDED" | "CUSTOM" | "NOT_REQUIRED";
	status: "READY" | "MISSING" | "CUSTOM_REQUIRED" | "NOT_REQUIRED";
	driverClass?: string;
	fileName?: string;
	version?: string;
	jdkSpec?: string;
	message?: string;
};

export type InfraConnector = {
	id: string;
	connectorKey: string;
	name: string;
	category: string;
	sourceType?: string;
	defaultEngine?: string;
	status?: string;
	displayOrder?: number;
	description?: string;
	capabilities?: Record<string, any>;
	configSchema?: Record<string, any>;
	sensitiveFields?: string[];
	compatibility?: Record<string, any>;
	driver?: ConnectorDriverBinding;
	createdAt?: string;
	lastUpdatedAt?: string;
};

export default {
	list: (params?: { category?: string; includeDisabled?: boolean }) =>
		apiClient.get<InfraConnector[]>({ url: "/infra/connectors", params }),
	detail: (connectorKey: string) => apiClient.get<InfraConnector>({ url: `/infra/connectors/${connectorKey}` }),
	seed: () => apiClient.post<InfraConnector[]>({ url: "/infra/connectors/seed" }),
};
