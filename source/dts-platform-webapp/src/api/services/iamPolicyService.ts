import apiClient from "../apiClient";

export type SubjectType = "user" | "role" | "org" | "project";

export interface DomainNode {
	id: string;
	name: string;
	datasets: Array<{
		id: string;
		name: string;
		fields: string[];
	}>;
}

export interface ObjectPolicyItem {
	subjectType: "USER" | "ROLE" | "ORG" | "PROJECT";
	subjectId: string;
	subjectName: string;
	effect: "ALLOW" | "DENY";
	validFrom?: string;
	validTo?: string | null;
	source: "MANUAL" | "INHERITED" | "SYSTEM";
}

export interface FieldPolicyItem {
	field: string;
	subjectType: "USER" | "ROLE" | "ORG" | "PROJECT";
	subjectName: string;
	effect: "ALLOW" | "DENY";
}

export interface RowConditionItem {
	subjectType: "USER" | "ROLE" | "ORG" | "PROJECT";
	subjectName: string;
	expression: string;
	description?: string;
}

export interface DatasetPoliciesResponse {
	objectPolicies: ObjectPolicyItem[];
	fieldPolicies: FieldPolicyItem[];
	rowConditions: RowConditionItem[];
}

export interface SubjectVisibleResponse {
	objects: { datasetId: string; datasetName: string; effect: "ALLOW" | "DENY" }[];
	fields: { datasetName: string; field: string; effect: "ALLOW" | "DENY" }[];
	expressions: { datasetName: string; expression: string }[];
}

export interface BatchAuthorizationInput {
	subjects: { type: SubjectType; id: string; name: string }[];
	objects: { datasetId: string; datasetName: string }[];
	scope: {
		objectEffect?: "ALLOW" | "DENY";
		fields?: { name: string; effect: "ALLOW" | "DENY" }[];
		rowExpression?: string;
		validFrom?: string;
		validTo?: string | null;
	};
}

export interface ConflictItem {
	kind: "override" | "conflict";
	target: string;
	subject: string;
	old: string;
	next: string;
}

export default {
	getDomainDatasetTree(): Promise<DomainNode[]> {
		return apiClient.get({ url: "/iam/policies/domains-with-datasets" });
	},
	getDatasetPolicies(datasetId: string): Promise<DatasetPoliciesResponse> {
		return apiClient.get({ url: `/iam/policies/dataset/${datasetId}/policies` });
	},
	getSubjectVisible(type: SubjectType, id: string): Promise<SubjectVisibleResponse> {
		return apiClient.get({ url: `/iam/policies/subject/${type}/${id}/visible` });
	},
	searchSubjects(type: SubjectType, keyword: string): Promise<{ id: string; name: string }[]> {
		return apiClient.get({ url: "/iam/policies/subjects", params: { type, keyword } });
	},
	previewConflicts(input: BatchAuthorizationInput): Promise<{ conflicts: ConflictItem[] }> {
		return apiClient.post({ url: "/iam/policies/preview", data: input });
	},
	applyBatch(input: BatchAuthorizationInput): Promise<{ ok: boolean; appliedAt: string }> {
		return apiClient.post({ url: "/iam/policies/apply", data: input });
	},
};
