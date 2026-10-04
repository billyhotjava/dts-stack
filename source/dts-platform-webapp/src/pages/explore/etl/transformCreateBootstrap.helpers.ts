import type { DefaultDestinationStatus, IngestionConnectorCapabilityDTO, IngestionTaskTemplateDTO } from "@/api/ingestion";
import type { DataSourceSelectionResponse, InfraDataSource } from "@/api/services/dataSourcesService";

type SqlModelOption = { id?: string; name?: string; alias?: string };

export type TransformCreateBootstrapLoaders = {
	loadDataSources: () => Promise<InfraDataSource[] | unknown>;
	loadConnectorCapabilities: () => Promise<IngestionConnectorCapabilityDTO[] | unknown>;
	loadTaskTemplates: () => Promise<IngestionTaskTemplateDTO[] | unknown>;
	loadDefaultDestinationStatus: () => Promise<DefaultDestinationStatus>;
	loadTargetDataSourceSelections?: () => Promise<DataSourceSelectionResponse | unknown>;
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
	targetDataSources: InfraDataSource[];
	targetDataSourceDefaultId: string | undefined;
	targetDataSourcesError: string;
	targetDataSourceMessage: string;
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
	const [dataSourcesResult, capabilitiesResult, templatesResult, destinationResult, targetDataSourcesResult, sqlModelsResult] =
		await Promise.allSettled([
			loaders.loadDataSources(),
			loaders.loadConnectorCapabilities(),
			loaders.loadTaskTemplates(),
			loaders.loadDefaultDestinationStatus(),
			loaders.loadTargetDataSourceSelections ? loaders.loadTargetDataSourceSelections() : Promise.resolve(null),
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
	const targetSelection =
		targetDataSourcesResult.status === "fulfilled" &&
		targetDataSourcesResult.value &&
		typeof targetDataSourcesResult.value === "object" &&
		Array.isArray((targetDataSourcesResult.value as DataSourceSelectionResponse).items)
			? (targetDataSourcesResult.value as DataSourceSelectionResponse)
			: null;
	const targetDataSources = targetSelection?.items || [];
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
			targetDataSources,
			targetDataSourceDefaultId: targetSelection?.defaultDataSourceId,
			targetDataSourcesError:
				targetDataSourcesResult.status === "rejected" ? toMessage(targetDataSourcesResult.reason, "目标湖仓列表加载失败") : "",
			targetDataSourceMessage: targetSelection?.message || "",
			sqlModels,
		sqlModelsError:
			sqlModelsResult.status === "rejected" ? toMessage(sqlModelsResult.reason, "模型列表加载失败") : "",
	};
}
