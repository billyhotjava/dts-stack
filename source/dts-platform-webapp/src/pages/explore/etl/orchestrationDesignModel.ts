import type { IngestionTaskDesign, IngestionTaskDesignUpdate } from "@/api/ingestion";

export type CatalogDatasetOption = {
	id: string;
	name: string;
	hiveDatabase?: string;
	hiveTable?: string;
	type?: string;
	sourceId?: string;
	lifecycleStatus?: string;
	enabled?: boolean;
};

export type DesignFormValues = {
	taskName: string;
	description?: string;
	sourceDataSourceId?: string;
	sourceType: string;
	sourceConfigText: string;
	destinationType: string;
	destinationConfigText: string;
	targetDatasetId?: string;
	syncMode: string;
	syncSchedule?: string;
	tableMapping: Array<{ source: string; target: string }>;
	syncConfigText: string;
	postIngestionQualityEnabled: boolean;
};

export function catalogDatasetOption(item: any): CatalogDatasetOption | null {
	const id = String(item?.id || "").trim();
	const name = String(item?.name || "").trim();
	if (!id || !name) return null;
	return {
		id,
		name,
		hiveDatabase: item.hiveDatabase ? String(item.hiveDatabase) : undefined,
		hiveTable: item.hiveTable ? String(item.hiveTable) : undefined,
		type: item.type ? String(item.type) : undefined,
		sourceId: item.sourceId ? String(item.sourceId) : undefined,
		lifecycleStatus: item.lifecycleStatus ? String(item.lifecycleStatus) : undefined,
		enabled: item.enabled !== false,
	};
}

export function catalogDatasetOptions(response: any): CatalogDatasetOption[] {
	const content = Array.isArray(response?.content) ? response.content : [];
	return content.flatMap((item: any) => {
		const option = catalogDatasetOption(item);
		return option ? [option] : [];
	});
}

function stringifyJson(value: unknown): string {
	return JSON.stringify(value && typeof value === "object" ? value : {}, null, 2);
}

function parseJsonObject(value: string, label: string): Record<string, unknown> {
	try {
		const parsed = JSON.parse(value || "{}");
		if (!parsed || Array.isArray(parsed) || typeof parsed !== "object") {
			throw new Error(`${label}必须是 JSON 对象`);
		}
		return parsed as Record<string, unknown>;
	} catch (error: unknown) {
		if (error instanceof Error && error.message.endsWith("必须是 JSON 对象")) throw error;
		throw new Error(`${label}不是有效的 JSON`);
	}
}

export function formValues(design: IngestionTaskDesign): DesignFormValues {
	return {
		taskName: design.taskName,
		description: design.description,
		sourceDataSourceId: design.source?.dataSourceId,
		sourceType: design.source?.type || "",
		sourceConfigText: stringifyJson(design.source?.config),
		destinationType: design.destination?.type || "",
		destinationConfigText: stringifyJson(design.destination?.config),
		targetDatasetId: design.destination?.assetRef?.datasetId,
		syncMode: design.syncMode || "full_refresh",
		syncSchedule: design.syncSchedule,
		tableMapping: Array.isArray(design.tableMapping)
			? design.tableMapping.map((item) => ({
					source: String(item.source || ""),
					target: String(item.target || ""),
				}))
			: [],
		syncConfigText: stringifyJson(design.syncConfig),
		postIngestionQualityEnabled: Boolean(design.postIngestionQuality?.enabled),
	};
}

export function designPayload(values: DesignFormValues): IngestionTaskDesignUpdate {
	const targetDatasetId = values.targetDatasetId?.trim() || undefined;
	return {
		taskName: values.taskName.trim(),
		description: values.description?.trim() || undefined,
		sourceDataSourceId: values.sourceDataSourceId,
		sourceType: values.sourceType.trim(),
		sourceConfig: parseJsonObject(values.sourceConfigText, "来源参数"),
		destinationType: values.destinationType.trim(),
		destinationConfig: parseJsonObject(values.destinationConfigText, "目标参数"),
		targetDatasetId,
		syncMode: values.syncMode,
		syncSchedule: values.syncSchedule?.trim() || undefined,
		tableMapping: (values.tableMapping || []).map((item) => ({
			source: item.source.trim(),
			target: item.target.trim(),
		})),
		syncConfig: parseJsonObject(values.syncConfigText, "同步参数"),
		postIngestionQualityEnabled: Boolean(values.postIngestionQualityEnabled),
		qualityPolicyRef: values.postIngestionQualityEnabled && targetDatasetId ? `dataset:${targetDatasetId}` : undefined,
	};
}
