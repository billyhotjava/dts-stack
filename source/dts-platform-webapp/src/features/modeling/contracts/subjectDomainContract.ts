export type SubjectDomainStatus = "DRAFT" | "CURRENT" | "RETIRED";

export type CreateSubjectDomainCommand = {
	code: string;
	name: string;
	purpose?: string;
	martId: string;
	idempotencyKey: string;
};

export type UpdateSubjectDomainCommand = {
	name: string;
	purpose?: string;
	martId: string;
};

export type SubjectDomainView = {
	id: string;
	code: string;
	name: string;
	purpose?: string | null;
	martId: string;
	status: SubjectDomainStatus;
	revision: number;
	checksum: string;
	createdAt: string;
	updatedAt: string;
};

export type SubjectDomainCasToken = Pick<SubjectDomainView, "id" | "revision" | "checksum">;

export const toSubjectDomainEtag = ({ id, revision, checksum }: SubjectDomainCasToken) =>
	`"subject-domain:${id}:${revision}:${checksum}"`;
