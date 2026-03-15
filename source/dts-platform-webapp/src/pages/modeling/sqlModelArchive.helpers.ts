export type ArchiveableSqlModel = {
	id?: string;
	planId?: string;
	name?: string;
	alias?: string;
	layer?: string;
	sourceDataSourceId?: string;
	schemaName?: string;
	materialized?: string;
	tags?: string;
	description?: string;
	sql?: string;
	enabled?: boolean;
	status?: string;
};

export type SqlModelArchivePayload = {
	planId: string;
	name?: string;
	alias?: string;
	layer?: string;
	sourceDataSourceId?: string;
	schemaName?: string;
	materialized?: string;
	tags?: string;
	description?: string;
	sql?: string;
	enabled?: boolean;
	status?: string;
};

export function collectUnassignedModelIds(models: ArchiveableSqlModel[]): string[] {
	return models
		.filter((model) => !model.planId && !!model.id)
		.map((model) => model.id as string);
}

export function buildArchivePayload(model: ArchiveableSqlModel, planId: string): SqlModelArchivePayload {
	return {
		planId,
		name: model.name,
		alias: model.alias,
		layer: model.layer,
		sourceDataSourceId: model.sourceDataSourceId,
		schemaName: model.schemaName,
		materialized: model.materialized,
		tags: model.tags,
		description: model.description,
		sql: model.sql,
		enabled: model.enabled,
		status: model.status,
	};
}
