import { useEffect, useReducer } from "react";
import { toast } from "sonner";
import type { FormInstance } from "antd";
import type {
	DefaultDestinationStatus,
	IngestionConnectorCapabilityDTO,
	IngestionTaskTemplateDTO,
} from "@/api/ingestion";
import type { InfraDataSource } from "@/api/services/dataSourcesService";
import { loadTransformCreateBootstrap } from "../transformCreateBootstrap.helpers";
import { ingestionTaskAPI } from "@/api/ingestion";
import dataSourcesService from "@/api/services/dataSourcesService";
import { listSqlModels } from "@/api/platformApi";

// ---------------------------------------------------------------------------
// State & Actions
// ---------------------------------------------------------------------------

export interface TransformBootstrapState {
	dataSources: InfraDataSource[];
	loadingDataSources: boolean;
	connectorCapabilities: IngestionConnectorCapabilityDTO[];
	capabilityLoadFailed: boolean;
	taskTemplates: IngestionTaskTemplateDTO[];
	loadingTaskTemplates: boolean;
	sqlModels: Array<{ id?: string; name?: string; alias?: string }>;
	loadingSqlModels: boolean;
	defaultDestinationStatus: DefaultDestinationStatus | null;
	loadingDefaultDestination: boolean;
	defaultDestinationError: string;
}

type Action =
	| { type: "LOAD_START" }
	| {
			type: "LOAD_DONE";
			payload: {
				dataSources: InfraDataSource[];
				connectorCapabilities: IngestionConnectorCapabilityDTO[];
				capabilityLoadFailed: boolean;
				taskTemplates: IngestionTaskTemplateDTO[];
				sqlModels: Array<{ id?: string; name?: string; alias?: string }>;
				defaultDestinationStatus: DefaultDestinationStatus | null;
				defaultDestinationError: string;
				dataSourcesError?: string;
				sqlModelsError?: string;
			};
	  };

const initialState: TransformBootstrapState = {
	dataSources: [],
	loadingDataSources: false,
	connectorCapabilities: [],
	capabilityLoadFailed: false,
	taskTemplates: [],
	loadingTaskTemplates: false,
	sqlModels: [],
	loadingSqlModels: false,
	defaultDestinationStatus: null,
	loadingDefaultDestination: false,
	defaultDestinationError: "",
};

function reducer(state: TransformBootstrapState, action: Action): TransformBootstrapState {
	switch (action.type) {
		case "LOAD_START":
			return {
				...state,
				loadingDataSources: true,
				loadingTaskTemplates: true,
				loadingDefaultDestination: true,
				loadingSqlModels: true,
			};
		case "LOAD_DONE":
			return {
				...state,
				dataSources: action.payload.dataSources,
				connectorCapabilities: action.payload.connectorCapabilities,
				capabilityLoadFailed: action.payload.capabilityLoadFailed,
				taskTemplates: action.payload.taskTemplates,
				sqlModels: action.payload.sqlModels,
				defaultDestinationStatus: action.payload.defaultDestinationStatus,
				defaultDestinationError: action.payload.defaultDestinationError,
				loadingDataSources: false,
				loadingTaskTemplates: false,
				loadingDefaultDestination: false,
				loadingSqlModels: false,
			};
		default:
			return state;
	}
}

// ---------------------------------------------------------------------------
// Hook
// ---------------------------------------------------------------------------

export function useTransformBootstrap(
	form: FormInstance,
	selectedTemplateId: string | undefined,
	setSelectedTemplateId: (id: string | undefined) => void,
) {
	const [state, dispatch] = useReducer(reducer, initialState);

	useEffect(() => {
		let active = true;
		const loadBootstrap = async () => {
			dispatch({ type: "LOAD_START" });
			const bootstrap = await loadTransformCreateBootstrap(
				{
					loadDataSources: () => dataSourcesService.list(),
					loadConnectorCapabilities: () => ingestionTaskAPI.getConnectorCapabilities(),
					loadTaskTemplates: () => ingestionTaskAPI.getTaskTemplates(),
					loadDefaultDestinationStatus: () => ingestionTaskAPI.getDefaultDestinationStatus(),
					loadSqlModels: () => listSqlModels() as Promise<Array<{ id?: string; name?: string; alias?: string }>>,
				},
				selectedTemplateId,
			);
			if (!active) return;
			dispatch({
				type: "LOAD_DONE",
				payload: {
					dataSources: bootstrap.dataSources,
					connectorCapabilities: bootstrap.connectorCapabilities,
					capabilityLoadFailed: bootstrap.capabilityLoadFailed,
					taskTemplates: bootstrap.taskTemplates,
					sqlModels: bootstrap.sqlModels,
					defaultDestinationStatus: bootstrap.defaultDestinationStatus,
					defaultDestinationError: bootstrap.defaultDestinationError,
					dataSourcesError: bootstrap.dataSourcesError,
					sqlModelsError: bootstrap.sqlModelsError,
				},
			});
			if (bootstrap.selectedTemplateId && bootstrap.selectedTemplateId !== selectedTemplateId) {
				setSelectedTemplateId(bootstrap.selectedTemplateId);
			}
			if (bootstrap.defaultDestinationStatus?.writerType) {
				form.setFieldValue("writerType", bootstrap.defaultDestinationStatus.writerType);
			}
			if (bootstrap.dataSourcesError) {
				toast.error(bootstrap.dataSourcesError);
			}
			if (bootstrap.sqlModelsError) {
				toast.error(bootstrap.sqlModelsError);
			}
		};
		void loadBootstrap();
		return () => {
			active = false;
		};
	}, [form]);

	return state;
}
