export type StatusTone = "ok" | "warn" | "neutral";

export interface RouteItem {
	path: string;
	title: string;
	stage: string;
	description: string;
}

export interface RouteGroup {
	group: string;
	items: RouteItem[];
}

export interface MetricsHealth {
	status?: string;
	service?: string;
	enabled?: boolean;
	edition?: string;
	checkedAt?: string;
}

export interface MetricsCapabilities {
	service?: string;
	enabled?: boolean;
	edition?: string;
	mvp?: string[];
	platformContract?: {
		platformBaseUrl?: string;
		apiPath?: string;
		serviceTokenConfigured?: boolean;
		authHeaders?: {
			service?: string;
			token?: string;
		};
	};
}

export interface MetricAsset {
	code: string;
	name: string;
	domain: string;
	type: string;
	grain: string;
	status: string;
	version: string;
	owner: string;
	terms: string[];
	consumer: string;
}

export interface SubjectMapping {
	domain: string;
	code: string;
	platformState: string;
	assets: number;
	metrics: number;
	standards: string;
	gap: string;
}

export interface ObjectJoin {
	object: string;
	source: string;
	key: string;
	grain: string;
	joins: string[][];
	guardrails: string[];
}

export interface FormulaBlock {
	code: string;
	name: string;
	display: string;
	unit: string;
	format: string;
	warning: string;
	dsl: string;
}

export interface ModelCandidate {
	layer: string;
	name: string;
	purpose: string;
	grain: string;
	materialization: string;
	refresh: string;
	fields: string[];
	sql: string;
}

export interface WorkspaceSnapshot {
	source?: string;
	generatedAt?: string;
	platformContracts?: string[][];
	metricAssets?: MetricAsset[];
	subjectMappings?: SubjectMapping[];
	objectJoins?: ObjectJoin[];
	formulaBlocks?: FormulaBlock[];
	modelCandidates?: ModelCandidate[];
	publishGates?: string[][];
	runRecords?: string[][];
	actions?: Record<string, string>;
}
