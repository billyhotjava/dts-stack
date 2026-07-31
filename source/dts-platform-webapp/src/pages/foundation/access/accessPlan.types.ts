import type {
	ApiConnectionTestResultDTO,
	ClassificationSealReference,
	DefaultDestinationStatus,
	IngestionTaskDTO,
	ManagedFileUploadResult,
	TableInfo,
} from "@/api/ingestion";
import type { DataSourceSelectionItem } from "@/api/services/dataSourcesService";
import type { ClassificationLevel } from "@/utils/classification";

export type AccessKind = "database" | "api" | "file";
export type AccessPlanStep = 0 | 1 | 2;
export type AccessScheduleType = "manual" | "interval" | "cron";
export type AccessSyncMode = "full_refresh" | "incremental";

export type AccessPlanFormValues = {
	name: string;
	description?: string;
	sourceSystem?: string;
	sourceDataSourceId?: string;
	targetDataSourceId: string;
	readerType?: string;
	readerSchema?: string;
	readerTablePattern?: string;
	tableSelectionMode: "all" | "manual";
	selectedTables: string[];
	syncPrefix?: string;
	syncMode: AccessSyncMode;
	scheduleType: AccessScheduleType;
	scheduleCron?: string;
	scheduleIntervalMinutes?: number;
	incrementalColumn?: string;
	incrementalType?: "datetime" | "number" | "string";
	initialWatermark?: string;
	airflowEnabled: boolean;
	runNow: boolean;
	apiResourceId?: string;
	apiResourceDisplayName?: string;
	apiResourcePath?: string;
	apiMethod: "GET" | "POST" | "PUT" | "PATCH";
	apiRecordPath?: string;
	apiPageParam?: string;
	apiSizeParam?: string;
	apiPageSize?: number;
	apiCursorField?: string;
	apiCursorParam?: string;
	fileClassification: ClassificationLevel;
	fileTargetTable?: string;
	fileAutoId: boolean;
};

export type AccessPlanSourceSpec = {
	dataSourceId?: string;
	type: string;
	config: Record<string, unknown>;
};

export type AccessPlanApiResourceDTO = Record<string, unknown> & {
	resourceId: string;
	path: string;
	method: string;
	targetTable: string;
};

export type AccessPlanApiSourceConfigDTO = Record<string, unknown> & {
	readerType: string;
	connectorType: "api";
	sourceCategory: "api";
	resource: AccessPlanApiResourceDTO;
	resources: AccessPlanApiResourceDTO[];
};

export type AccessPlanCreateRequest = {
	name: string;
	description?: string;
	owner?: string;
	source: AccessPlanSourceSpec;
	destination: {
		usePlatformDefault: true;
		type: string;
		config: { targetDataSourceId: string };
	};
	sync: Record<string, unknown>;
	streams: {
		selection: "all" | "manual";
		include?: string[];
		exclude?: string[];
		schema?: string;
		tablePattern?: string;
	};
	airflow: { enabled: boolean };
	runNow: boolean;
	draft: boolean;
	classificationSeal?: ClassificationSealReference;
	fieldClassifications?: Record<string, ClassificationLevel | string>;
};

export type AccessPlanPayloadContext = {
	kind: AccessKind;
	values: AccessPlanFormValues;
	owner?: string;
	defaultDestination: DefaultDestinationStatus | null;
	selectedSource?: DataSourceSelectionItem | null;
	selectedTarget?: DataSourceSelectionItem | null;
	fileUploadResult?: ManagedFileUploadResult | null;
};

export type AccessPlanBootstrap = {
	dataSources: DataSourceSelectionItem[];
	targetDataSources: DataSourceSelectionItem[];
	defaultDestination: DefaultDestinationStatus | null;
	loading: boolean;
	error: string;
};

export type AccessPlanRuntimeState = AccessPlanBootstrap & {
	loadedContextKey: string | null;
	existingTask: IngestionTaskDTO | null;
	fileUploadResult: ManagedFileUploadResult | null;
	discoveredTables: TableInfo[];
	discoveryFingerprint: string | null;
	discoveringTables: boolean;
	discoverError: string;
	apiPreview: ApiConnectionTestResultDTO | null;
	apiPreviewFingerprint: string | null;
	apiPreviewing: boolean;
	uploadingFile: boolean;
	saving: boolean;
	editError: string;
};

export const ACCESS_KIND_LABELS: Record<AccessKind, string> = {
	database: "数据库接入",
	api: "API 接入",
	file: "离线文件接入",
};
