import type { FormInstance } from "antd/es/form";
import type {
	DefaultDestinationStatus,
	FileUploadResult,
	IngestionConnectorCapabilityDTO,
	IngestionTaskDTO,
	IngestionTaskTemplateDTO,
} from "@/api/ingestion";
import type { InfraDataSource } from "@/api/services/dataSourcesService";

export type ExtraColumnDef = {
	name: string;
	type?: string;
	defaultValue?: string;
	label?: string;
};

export type SyncModeOption = {
	value: string;
	label: string;
	disabled?: boolean;
};

export type IngestionFormContext = {
	form: FormInstance;
	isEdit: boolean;
	editId: number | undefined;
	editingTask: IngestionTaskDTO | null;

	/* file upload */
	fileUploadResult: FileUploadResult | null;
	setFileUploadResult: (result: FileUploadResult | null) => void;

	/* data source */
	selectedDataSource: InfraDataSource | null;
	dataSources: InfraDataSource[];
	sourceCategory: string;
	setSourceCategory: (cat: string) => void;

	/* extra columns */
	extraColumns: ExtraColumnDef[];
	setExtraColumns: (cols: ExtraColumnDef[]) => void;

	/* table selection */
	selectedTableKeys: string[];
	setSelectedTableKeys: (keys: string[]) => void;

	/* destination & sync */
	defaultDestinationStatus: DefaultDestinationStatus | null;
	syncModeOptions: SyncModeOption[];
	connectorCapability: IngestionConnectorCapabilityDTO | null;

	/* editor */
	editorMode: string;
	setEditorMode: (mode: string) => void;
	isFileFlow: boolean;

	/* templates */
	taskTemplates: IngestionTaskTemplateDTO[];
	loadingTaskTemplates: boolean;
	selectedTemplateId: string | undefined;
	setSelectedTemplateId: (id: string | undefined) => void;
	applyTemplate: () => Promise<void>;
	applyingTemplate: boolean;

	/* ODS matching */
	odsColumns: any[];
	odsMatchApplied: boolean;
};
