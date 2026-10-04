import api from "@/api/apiClient";

export type ScriptType = "PYTHON" | "SPARK";
export type ScriptStatus = "DRAFT" | "READY" | "DISABLED";
export type ScriptRunStatus = "PENDING" | "RUNNING" | "SUCCESS" | "FAILED" | "CANCELED";

export type ScriptAsset = {
	id: string;
	name: string;
	description?: string | null;
	scriptType: ScriptType;
	status: ScriptStatus;
	latestVersionNo: number;
	ownerDept?: string | null;
	enabled: boolean;
	latestContent?: string | null;
	createdBy?: string | null;
	createdDate?: string | null;
	lastModifiedDate?: string | null;
};

export type ScriptVersion = {
	id: string;
	scriptId: string;
	versionNo: number;
	status: "DRAFT" | "READY";
	content: string;
	changeSummary?: string | null;
	createdBy?: string | null;
	createdDate?: string | null;
};

export type ScriptRun = {
	id: string;
	scriptId: string;
	versionNo: number;
	executionId: string;
	status: ScriptRunStatus;
	failureType?: string | null;
	errorMessage?: string | null;
	logText?: string | null;
	startedAt?: string | null;
	finishedAt?: string | null;
	durationMs?: number | null;
	triggeredBy?: string | null;
	createdDate?: string | null;
};

export type ScriptCreateRequest = {
	name: string;
	description?: string;
	scriptType?: ScriptType;
	content: string;
	ownerDept?: string;
};

export type ScriptUpdateRequest = {
	name?: string;
	description?: string;
	scriptType?: ScriptType;
	status?: ScriptStatus;
	ownerDept?: string;
	enabled?: boolean;
};

export type ScriptSaveVersionRequest = {
	content: string;
	changeSummary?: string;
	status?: "DRAFT" | "READY";
};

export const listScripts = () => api.get<ScriptAsset[]>({ url: "/development/scripts" });

export const createScript = (payload: ScriptCreateRequest) =>
	api.post<ScriptAsset>({ url: "/development/scripts", data: payload });

export const updateScript = (scriptId: string, payload: ScriptUpdateRequest) =>
	api.put<ScriptAsset>({ url: `/development/scripts/${scriptId}`, data: payload });

export const listScriptVersions = (scriptId: string) =>
	api.get<ScriptVersion[]>({ url: `/development/scripts/${scriptId}/versions` });

export const saveScriptVersion = (scriptId: string, payload: ScriptSaveVersionRequest) =>
	api.post<ScriptVersion>({ url: `/development/scripts/${scriptId}/versions`, data: payload });

export const listScriptRuns = (scriptId: string) =>
	api.get<ScriptRun[]>({ url: `/development/scripts/${scriptId}/runs` });

export const runScript = (scriptId: string, payload?: { versionNo?: number }) =>
	api.post<ScriptRun>({ url: `/development/scripts/${scriptId}/run`, data: payload ?? {} });

export const getScriptRun = (runId: string) =>
	api.get<ScriptRun>({ url: `/development/scripts/runs/${runId}` });
