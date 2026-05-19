export type LoadState<T> = { state: "loading" } | { state: "loaded"; value: T } | { state: "error"; error: unknown };

export type SemanticQueryBody = {
	base?: string;
	joins?: Array<{ to: string; via?: string; type?: string }>;
	measures?: string[];
	dimensions?: Array<string | { id: string; granularity?: string }>;
	filters?: Array<{ field: string; op: string; value?: unknown; value_to?: unknown }>;
	derived_metrics?: Array<{ id: string; label?: string; expression: string; format?: Record<string, unknown> }>;
	order_by?: Array<{ field: string; direction?: "asc" | "desc" }>;
	limit?: number;
	format?: "json" | "arrow_ipc";
	cache_hint?: string;
};

export type SemanticColumn = {
	id?: string;
	label?: string;
	type?: string;
	format?: Record<string, unknown> | null;
};

export type SemanticModelMeta = {
	id?: string | number;
	label?: string;
	subject_area?: string | null;
	security_level?: string | null;
	grain?: string | null;
	database_id?: number;
	schema_name?: string | null;
	table_name?: string | null;
	description?: string | null;
	metrics?: Array<Record<string, unknown>>;
	dimensions?: Array<Record<string, unknown>>;
	joins?: Array<Record<string, unknown>>;
};

export type SemanticMetaResponse = {
	spec_version?: string;
	generated_at?: string;
	models?: SemanticModelMeta[];
};

export type SemanticQueryResponse = {
	status?: string;
	meta?: {
		sql_preview?: string;
		row_count?: number;
		elapsed_ms?: number;
		cache_hit?: boolean;
		security_applied?: string[];
		warnings?: string[];
	};
	columns?: SemanticColumn[];
	rows?: unknown[][];
};

export type SemanticMetric = {
	id?: string;
	objectId?: string;
	code: string;
	name: string;
	formulaType?: string;
	formulaJson?: string;
	format?: string;
	unit?: string;
	status?: string;
};

export type QueryFilterDraft = { field: string; op: string; value: string };

export type DerivedMetricDraft = { id: string; label: string; expression: string };
