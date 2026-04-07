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

import { listDepartments } from "@/api/services/deptService";
import roleService from "@/api/services/roleService";

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
    async searchSubjects(type: SubjectType, keyword: string): Promise<{ id: string; name: string }[]> {
        const kw = (keyword || "").trim().toLowerCase();
        if (type === "org") {
            const list = await listDepartments(keyword);
            return list.map((d) => ({ id: d.code, name: d.nameZh || d.nameEn || d.code }));
        }
        if (type === "role") {
            try {
                const roles = await roleService.listRealmRoles();
                const filtered = roles.filter((r) => {
                    const name = (r.name || "").trim();
                    return kw ? name.toLowerCase().includes(kw) : true;
                });
                return filtered.map((r) => ({ id: r.name, name: r.name }));
            } catch (e) {
                // Fall back to platform endpoint if admin is unreachable
            }
        }
        return apiClient.get({ url: "/iam/policies/subjects", params: { type, keyword } });
    },
	previewConflicts(input: BatchAuthorizationInput): Promise<{ conflicts: ConflictItem[] }> {
		return apiClient.post({ url: "/iam/policies/preview", data: input });
	},
	applyBatch(input: BatchAuthorizationInput): Promise<{ ok: boolean; appliedAt: string }> {
		return apiClient.post({ url: "/iam/policies/apply", data: input });
	},
};
