export type DimensionDefinitionStatus = "DRAFT" | "CURRENT" | "RETIRED";
export type DimensionDefinitionReuseScope = "PLAN" | "DOMAIN" | "TENANT";
export type DimensionDefinitionScopeType = "DOMAIN" | "DATA_MART";

export type DimensionDefinitionHierarchyLevel = {
	code: string;
	name: string;
	order: number;
};

export type DimensionDefinitionHierarchy = {
	code: string;
	name: string;
	levels: DimensionDefinitionHierarchyLevel[];
};

export type DimensionDefinitionAttribute = {
	code: string;
	name: string;
	definition: string;
	primaryKey: boolean;
	standardRef?: string | null;
	standardVersion?: string | null;
	order: number;
};

export type CreateDimensionDefinitionCommand = {
	domainId: string;
	name: string;
	definition: string;
	ownerId: string;
	reuseScope: DimensionDefinitionReuseScope;
	hierarchies?: DimensionDefinitionHierarchy[];
	scopeType?: DimensionDefinitionScopeType;
	dataMartId?: string | null;
	attributes?: DimensionDefinitionAttribute[];
	idempotencyKey: string;
};

export type UpdateDimensionDefinitionCommand = Omit<CreateDimensionDefinitionCommand, "domainId" | "idempotencyKey">;

export type DimensionDefinitionView = {
	id: string;
	/** Server-generated identity. It is display-only and must never be sent by a writer. */
	systemCode: string;
	domainId: string;
	name: string;
	definition: string;
	ownerId: string;
	reuseScope: DimensionDefinitionReuseScope;
	hierarchies: DimensionDefinitionHierarchy[];
	scopeType: DimensionDefinitionScopeType;
	dataMartId: string | null;
	attributes: DimensionDefinitionAttribute[];
	status: DimensionDefinitionStatus;
	revision: number;
	checksum: string;
	usageCount: number;
	createdAt: string;
	updatedAt: string;
};

export type DimensionDefinitionCasToken = Pick<DimensionDefinitionView, "id" | "revision" | "checksum">;

export const toDimensionDefinitionEtag = ({ id, revision, checksum }: DimensionDefinitionCasToken): string =>
	`"dimension-definition:${id}:${revision}:${checksum}"`;
