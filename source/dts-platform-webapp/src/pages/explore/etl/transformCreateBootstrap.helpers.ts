import type { DefaultDestinationStatus, IngestionConnectorCapabilityDTO, IngestionTaskTemplateDTO } from "@/api/ingestion";
import type { InfraDataSource } from "@/api/services/dataSourcesService";

type SqlModelOption = { id?: string; name?: string; alias?: string };

export type TransformCreateBootstrapLoaders = {
	loadDataSources: () => Promise<InfraDataSource[] | unknown>;
	loadConnectorCapabilities: () => Promise<IngestionConnectorCapabilityDTO[] | unknown>;
	loadTaskTemplates: () => Promise<IngestionTaskTemplateDTO[] | unknown>;
	loadDefaultDestinationStatus: () => Promise<DefaultDestinationStatus>;
	loadSqlModels: () => Promise<SqlModelOption[] | unknown>;
};

export type TransformCreateBootstrapResult = {
	dataSources: InfraDataSource[];
	dataSourcesError: string;
	connectorCapabilities: IngestionConnectorCapabilityDTO[];
	capabilityLoadFailed: boolean;
	taskTemplates: IngestionTaskTemplateDTO[];
	selectedTemplateId: string | undefined;
	defaultDestinationStatus: DefaultDestinationStatus | null;
	defaultDestinationError: string;
	sqlModels: SqlModelOption[];
	sqlModelsError: string;
};

const toMessage = (error: unknown, fallback: string) => {
	if (error && typeof error === "object" && "message" in error && typeof (error as any).message === "string") {
		return (error as any).message;
	}
	return fallback;
};

export async function loadTransformCreateBootstrap(
	loaders: TransformCreateBootstrapLoaders,
	currentSelectedTemplateId?: string
): Promise<TransformCreateBootstrapResult> {
	const [dataSourcesResult, capabilitiesResult, templatesResult, destinationResult, sqlModelsResult] =
		await Promise.allSettled([
			loaders.loadDataSources(),
			loaders.loadConnectorCapabilities(),
			loaders.loadTaskTemplates(),
			loaders.loadDefaultDestinationStatus(),
			loaders.loadSqlModels(),
		]);

	const dataSources = dataSourcesResult.status === "fulfilled" && Array.isArray(dataSourcesResult.value)
		? dataSourcesResult.value
		: [];
	const connectorCapabilities =
		capabilitiesResult.status === "fulfilled" && Array.isArray(capabilitiesResult.value)
			? capabilitiesResult.value
			: [];
	const taskTemplates = templatesResult.status === "fulfilled" && Array.isArray(templatesResult.value)
		? templatesResult.value
		: [];
	const selectedTemplateId =
		currentSelectedTemplateId || (taskTemplates.length ? String(taskTemplates[0].id) : undefined);
	const defaultDestinationStatus = destinationResult.status === "fulfilled" ? destinationResult.value : null;
	const sqlModels = sqlModelsResult.status === "fulfilled" && Array.isArray(sqlModelsResult.value)
		? sqlModelsResult.value
		: [];

	return {
		dataSources,
		dataSourcesError:
			dataSourcesResult.status === "rejected" ? toMessage(dataSourcesResult.reason, "数据源列表加载失败") : "",
		connectorCapabilities,
		capabilityLoadFailed: capabilitiesResult.status === "rejected",
		taskTemplates,
		selectedTemplateId,
		defaultDestinationStatus,
		defaultDestinationError:
			destinationResult.status === "rejected"
				? toMessage(destinationResult.reason, "无法获取默认数据湖配置")
				: "",
		sqlModels,
		sqlModelsError:
			sqlModelsResult.status === "rejected" ? toMessage(sqlModelsResult.reason, "模型列表加载失败") : "",
	};
}
