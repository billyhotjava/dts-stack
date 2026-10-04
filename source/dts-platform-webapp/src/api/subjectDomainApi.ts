import api from "@/api/apiClient";
import {
	type CreateSubjectDomainCommand,
	type SubjectDomainCasToken,
	type SubjectDomainStatus,
	type SubjectDomainView,
	toSubjectDomainEtag,
	type UpdateSubjectDomainCommand,
} from "@/features/modeling/contracts/subjectDomainContract";

const SUBJECT_DOMAIN_RESOURCE = "/modeling/subject-domains";

export const listSubjectDomains = (params?: {
	martId?: string;
	status?: SubjectDomainStatus;
	keyword?: string;
	offset?: number;
	limit?: number;
}) => api.get<SubjectDomainView[]>({ url: SUBJECT_DOMAIN_RESOURCE, params, _skipErrorToast: true } as any);

export const createSubjectDomain = (data: CreateSubjectDomainCommand) =>
	api.post<SubjectDomainView>({ url: SUBJECT_DOMAIN_RESOURCE, data, _skipErrorToast: true } as any);

export const updateSubjectDomain = (expected: SubjectDomainCasToken, data: UpdateSubjectDomainCommand) =>
	api.put<SubjectDomainView>({
		url: `${SUBJECT_DOMAIN_RESOURCE}/${encodeURIComponent(expected.id)}`,
		headers: { "If-Match": toSubjectDomainEtag(expected) },
		data,
		_skipErrorToast: true,
	} as any);

export const confirmSubjectDomain = (expected: SubjectDomainCasToken) =>
	api.post<SubjectDomainView>({
		url: `${SUBJECT_DOMAIN_RESOURCE}/${encodeURIComponent(expected.id)}/confirm`,
		headers: { "If-Match": toSubjectDomainEtag(expected) },
		_skipErrorToast: true,
	} as any);

export const retireSubjectDomain = (expected: SubjectDomainCasToken) =>
	api.post<SubjectDomainView>({
		url: `${SUBJECT_DOMAIN_RESOURCE}/${encodeURIComponent(expected.id)}/retire`,
		headers: { "If-Match": toSubjectDomainEtag(expected) },
		_skipErrorToast: true,
	} as any);
