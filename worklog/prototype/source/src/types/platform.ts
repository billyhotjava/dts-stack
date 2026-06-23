/** 平台级（跨部门）旁路能力类型。 */

export interface ApiService {
	id: string;
	name: string;
	method: "GET" | "POST";
	path: string;
	departmentId: string;
	status: "online" | "offline";
	calls: number;
}

export interface AccessToken {
	id: string;
	name: string;
	scope: string;
	createdAt: string;
	lastUsed?: string;
	status: "active" | "revoked";
}

export interface BiLink {
	id: string;
	name: string;
	tool: string;
	url: string;
	departmentId: string;
}

export interface AuditEvent {
	id: string;
	at: string;
	actor: string;
	action: string;
	resource: string;
	result: "success" | "deny";
}

export interface AccessRequest {
	id: string;
	dataset: string;
	requester: string;
	requesterDept: string;
	status: "pending" | "approved" | "rejected";
	at: string;
}

export interface OpsJob {
	id: string;
	name: string;
	type: "采集" | "转换" | "发布";
	status: "running" | "success" | "failed" | "queued";
	lastRun: string;
	durationMs?: number;
}

export interface AlertEvent {
	id: string;
	at: string;
	level: "info" | "warn" | "error";
	source: string;
	message: string;
}
