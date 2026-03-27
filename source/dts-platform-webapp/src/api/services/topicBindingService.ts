import apiClient from "../apiClient";
import { withModelingRequestTimeout } from "../modelingRequestTimeout";

export type TopicBindingTemplateView = {
	templateCode: string;
	templateName: string;
	description?: string;
	status?: string;
	bindingScope?: string;
	enabled?: boolean;
};

export type TopicBindingRow = {
	templateCode: string;
	templateName?: string;
	entityCode: string;
	entityName?: string;
	required?: boolean;
	sourceName?: string;
	logicalTableName?: string;
	expectedSchema?: string;
	bound?: boolean;
	boundSchemaName?: string;
	boundTableName?: string;
	bindingStatus?: string;
	batchId?: string | null;
	odsMappingId?: string | null;
	notes?: string | null;
};

export type TopicBindingDiagnostics = {
	selector?: string;
	relevantTemplateCodes?: string[];
	rows: TopicBindingRow[];
	missingRequired: string[];
};

export type TopicBindingView = {
	id: string;
	templateCode: string;
	entityCode: string;
	bindingMode: string;
	scopeKey: string;
	schemaName?: string;
	tableName?: string;
	status?: string;
};

export type BindTopicOdsTablePayload = {
	templateCode: string;
	entityCode: string;
	dataSourceId?: string;
	schemaName?: string;
	tableName: string;
	odsMappingId?: string;
	batchId?: string;
	notes?: string;
};

export default {
	listTemplates: () => apiClient.get<TopicBindingTemplateView[]>({ url: "/topic-bindings/templates" }),
	getStatus: (selector?: string) =>
		apiClient.get<TopicBindingDiagnostics>(
			withModelingRequestTimeout({
				url: "/topic-bindings/status",
				params: selector ? { selector } : undefined,
			}),
		),
	bindOdsTable: (payload: BindTopicOdsTablePayload) =>
		apiClient.post<TopicBindingView>({ url: "/topic-bindings/ods", data: payload }),
};
