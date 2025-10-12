// Catalog-related domain models and enums
// Keep colocated with usage. Used by pages and mocks.

export type SecurityLevel = "PUBLIC" | "INTERNAL" | "SECRET" | "TOP_SECRET";
export type DataLevel = "DATA_PUBLIC" | "DATA_INTERNAL" | "DATA_SECRET" | "DATA_TOP_SECRET";
export type Scope = "DEPT" | "INST";
export type ShareScope = "PRIVATE_DEPT" | "SHARE_INST" | "PUBLIC_INST";

// SourceType values seen in backend/CSV imports use both HIVE and INCEPTOR; treat INCEPTOR as HIVE alias
export type SourceType = "HIVE" | "INCEPTOR" | "TRINO" | "EXTERNAL" | "API";

export type ExposureType = "VIEW" | "RANGER" | "API" | "DIRECT";

export type MaskingStrategy = "PARTIAL" | "HASH" | "TOKENIZE" | "CUSTOM" | "NONE";

export type SecureViewRefresh = "NONE" | "SCHEDULED" | "ON_DEMAND";

export interface ColumnSchema {
	id: string;
	name: string;
	dataType: string;
	description?: string;
	sensitiveTags?: string[]; // e.g., ["PII:phone", "PII:id"]
}

export interface TableSchema {
	tableName: string;
	columns: ColumnSchema[];
}

export interface DatasetSource {
	sourceType: SourceType;
	hiveDatabase?: string;
	hiveTable?: string;
	trinoCatalog?: string;
}

export interface DatasetAsset {
	id: string;
	name: string;
	owner: string;
	bizDomainId: string; // domain id
	classification: SecurityLevel;
	// ABAC fields (optional during transition)
	dataLevel?: DataLevel;
	scope?: Scope;
	ownerDept?: string;
	shareScope?: ShareScope;
    tags: string[];
	description?: string;
    // During transition, some screens submit/receive flattened source fields
    // Make both shapes acceptable to keep build stable.
    source?: DatasetSource;
    // Flattened source fields (optional)
    type?: SourceType;
    hiveDatabase?: string;
    hiveTable?: string;
	exposure: ExposureType[];
	table?: TableSchema;
	createdAt: string;
	updatedAt: string;
}

export interface RowFilterRule {
	id: string;
	expression: string;
	roles: string[]; // Keycloak role names
}

export interface MaskingRule {
	id: string;
	column: string; // column name
	strategy: MaskingStrategy;
	params?: Record<string, string>;
}

export interface AccessPolicy {
	datasetId: string;
	allowRoles: string[]; // allowed Keycloak roles
	rowFilters: RowFilterRule[];
	maskingRules: MaskingRule[];
	defaultMasking?: MaskingStrategy; // fallback for unspecified sensitive columns
}

export interface SecureViewDefinition {
	viewName: string;
	level: SecurityLevel;
	rowFilter?: string;
	maskColumns?: string[];
	refresh: SecureViewRefresh;
	cron?: string;
}

export type DatasetJobStatus = "QUEUED" | "RUNNING" | "SUCCEEDED" | "FAILED";

export interface DatasetJob {
	id: string;
	datasetId: string | null;
	jobType: string;
	status: DatasetJobStatus;
	message?: string;
	submittedBy?: string;
	startedAt?: string;
	finishedAt?: string;
	createdAt?: string;
	updatedAt?: string;
	detail?: any;
}
