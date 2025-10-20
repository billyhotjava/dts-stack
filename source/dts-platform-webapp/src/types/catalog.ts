// Catalog-related domain models and enums
// Keep colocated with usage. Used by pages and mocks.

export type SecurityLevel = "PUBLIC" | "INTERNAL" | "SECRET" | "CONFIDENTIAL";
export type DataLevel = "DATA_PUBLIC" | "DATA_INTERNAL" | "DATA_SECRET" | "DATA_CONFIDENTIAL";

// SourceType values seen in backend/CSV imports use both HIVE and INCEPTOR; treat INCEPTOR as HIVE alias
export type SourceType = "HIVE" | "INCEPTOR" | "TRINO" | "EXTERNAL" | "API";

export type ExposureType = "VIEW" | "RANGER" | "API" | "DIRECT";

export type MaskingStrategy = "PARTIAL" | "HASH" | "TOKENIZE" | "CUSTOM" | "NONE";


export interface ColumnSchema {
	id: string;
	name: string;
	displayName?: string;
	dataType: string;
	nullable?: boolean;
	tags?: string[];
	description?: string;
	sensitiveTags?: string[]; // e.g., ["PII:phone", "PII:id"]
}

export interface TableSchema {
	id?: string;
	name: string;
	tableName?: string;
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
	ownerDept?: string;
    tags: string[];
	description?: string;
    editable?: boolean;
    // During transition, some screens submit/receive flattened source fields
    // Make both shapes acceptable to keep build stable.
    source?: DatasetSource;
    // Flattened source fields (optional)
    type?: SourceType;
    hiveDatabase?: string;
    hiveTable?: string;
	exposure: ExposureType[];
	table?: TableSchema;
	tables?: TableSchema[];
	createdAt: string;
	updatedAt: string;
}

export interface DatasetGrant {
	id: string;
	datasetId?: string;
	userId?: string;
	username: string;
	displayName?: string;
	deptCode?: string;
	createdBy?: string;
	createdDate?: string;
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
