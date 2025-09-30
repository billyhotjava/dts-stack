// Catalog-related domain models and enums
// Keep colocated with usage. Used by pages and mocks.

export type SecurityLevel = "PUBLIC" | "INTERNAL" | "SECRET" | "TOP_SECRET";

export type SourceType = "HIVE" | "TRINO" | "EXTERNAL" | "API";

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
	tags: string[];
	description?: string;
	source: DatasetSource;
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
