export type DataMartStatus = "DRAFT" | "CURRENT" | "RETIRED";

export type CreateDataMartCommand = {
	code: string;
	name: string;
	purpose: string;
	ownerId: string;
	domainIds: string[];
	idempotencyKey: string;
};

export type UpdateDataMartCommand = Omit<CreateDataMartCommand, "code" | "idempotencyKey">;

export type DataMartView = {
	id: string;
	code: string;
	name: string;
	purpose: string;
	ownerId: string;
	domainIds: string[];
	status: DataMartStatus;
	revision: number;
	checksum: string;
	usageCount: number;
	createdAt: string;
	updatedAt: string;
};

export type DataMartCasToken = Pick<DataMartView, "id" | "revision" | "checksum">;

export type WarehousePlanDataMartBaseline = {
	planId: string;
	dataMartIds: string[];
	version: number;
};

export const toDataMartEtag = ({ id, revision, checksum }: DataMartCasToken) =>
	`"data-mart:${id}:${revision}:${checksum}"`;
