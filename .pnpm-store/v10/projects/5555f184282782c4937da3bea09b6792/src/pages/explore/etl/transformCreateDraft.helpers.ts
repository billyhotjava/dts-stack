type TransformCreateDraftPayloadInput = {
	name: string;
	description?: string;
	owner?: string;
	isFileDraft: boolean;
	sourceDataSourceId?: string;
	resolvedReaderType?: string;
	readerConfig?: Record<string, any>;
	writerConfig?: Record<string, any>;
	syncMode?: string;
	syncSchedule?: Record<string, any>;
	syncPrefix?: string;
	incrementalColumn?: string;
	incrementalType?: string;
	initialWatermark?: string;
	governanceSyncFields?: Record<string, any>;
	selectionMode: string;
	includeTables?: string[];
	excludeTables?: string[];
	readerSchema?: string;
	readerTablePattern?: string;
	airflowEnabled?: boolean;
	dbtModelSelector?: string;
	dbtDagSelector?: string;
	jobConfig?: Record<string, any>;
};

export function buildTransformCreateDraftPayload(input: TransformCreateDraftPayloadInput): Record<string, any> {
	const payload: Record<string, any> = {
		draft: true,
		name: input.name,
		description: input.description,
		owner: input.owner,
		source: {
			dataSourceId: input.isFileDraft ? undefined : input.sourceDataSourceId,
			type: input.resolvedReaderType,
			config: input.readerConfig || {},
		},
		sync: {
			mode: input.syncMode || "full_refresh",
			schedule: input.syncSchedule,
			prefix: input.syncPrefix,
			incrementalColumn: input.incrementalColumn,
			incrementalType: input.incrementalType,
			initialWatermark: input.initialWatermark,
			...(input.governanceSyncFields || {}),
		},
		streams: {
			selection: input.selectionMode,
			include: input.selectionMode === "manual" && input.includeTables?.length ? input.includeTables : undefined,
			exclude: input.selectionMode === "all" && input.excludeTables?.length ? input.excludeTables : undefined,
			schema: input.readerSchema,
			tablePattern: input.readerTablePattern,
		},
		airflow: {
			enabled: input.airflowEnabled ?? true,
		},
		dbt: {
			modelSelector: input.dbtModelSelector,
			dagSelector: input.dbtDagSelector,
		},
		jobConfig: input.jobConfig,
	};

	if (input.writerConfig && Object.keys(input.writerConfig).length > 0) {
		payload.destination = {
			usePlatformDefault: true,
			config: input.writerConfig,
		};
	}

	return payload;
}
