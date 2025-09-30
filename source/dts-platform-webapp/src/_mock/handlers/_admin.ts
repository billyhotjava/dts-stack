import { faker } from "@faker-js/faker";
import { HttpResponse, http } from "msw";
import type {
	AdminRoleDetail,
	AdminUser,
	AuditEvent,
	ChangeRequest,
	OrganizationNode,
	OrganizationPayload,
	PermissionCatalogSection,
	PortalMenuItem,
	SystemConfigItem,
} from "@/admin/types";
import { ResultStatus } from "@/types/enum";
import { getActiveAdmin } from "../utils/session";

const ADMIN_API = "/api/admin";

let changeRequestId = 3;
const changeRequests: ChangeRequest[] = [
	{
		id: 1,
		resourceType: "USER",
		action: "CREATE",
		status: "APPROVED",
		requestedBy: "sysadmin",
		requestedAt: faker.date.recent({ days: 10 }).toISOString(),
		decidedBy: "authadmin",
		diffJson: JSON.stringify({ before: null, after: { username: "dataops" } }),
	},
	{
		id: 2,
		resourceType: "ROLE",
		action: "UPDATE",
		status: "PENDING",
		requestedBy: "sysadmin",
		requestedAt: faker.date.recent({ days: 2 }).toISOString(),
		payloadJson: JSON.stringify({ name: "DATA_CURATOR", permissions: ["dataset.read"] }),
	},
];

const auditEvents: AuditEvent[] = Array.from({ length: 40 }).map((_, index) => ({
	id: index + 1,
	timestamp: faker.date.recent({ days: 15 }).toISOString(),
	actor: faker.helpers.arrayElement(["sysadmin", "authadmin", "auditadmin", "dba"]),
	action: faker.helpers.arrayElement(["USER_CREATE", "ROLE_ASSIGN", "DATA_EXPORT", "LOGIN"]),
	resource: faker.helpers.arrayElement(["user:alice", "role:DATA_CURATOR", "dataset:ods_orders", "system"]),
	outcome: faker.helpers.arrayElement(["SUCCESS", "FAILURE"]),
	detailJson: faker.helpers.maybe(() => JSON.stringify({ ip: faker.internet.ipv4(), ua: faker.internet.userAgent() })),
}));

const systemConfigs: SystemConfigItem[] = [
	{ id: 1, key: "cluster.mode", value: "production", description: "Iceberg 集群运行模式" },
	{ id: 2, key: "minio.endpoint", value: "https://minio.internal", description: "对象存储地址" },
	{ id: 3, key: "airflow.deployment", value: "v2.9.0", description: "调度集群版本" },
];

const portalMenus: PortalMenuItem[] = [
	{
		id: 1,
		name: "数据总览",
		path: "/portal/overview",
		component: "/pages/portal/overview",
		sortOrder: 1,
		children: [
			{
				id: 11,
				name: "看板",
				path: "/portal/overview/dashboard",
				component: "/pages/portal/overview/dashboard",
				sortOrder: 1,
			},
		],
	},
	{
		id: 2,
		name: "数据接入",
		path: "/portal/ingestion",
		component: "/pages/portal/ingestion",
		sortOrder: 2,
	},
];

const organizations: OrganizationNode[] = [
	{
		id: 1,
		name: "数据与智能中心",
		dataLevel: "DATA_TOP_SECRET",
		sensitivity: "DATA_TOP_SECRET",
		contact: "李雷",
		phone: "13800000001",
		description: "统筹企业数据治理、开发与运营能力",
		parentId: null,
		children: [
			{
				id: 2,
				name: "数据平台组",
				dataLevel: "DATA_SECRET",
				sensitivity: "DATA_SECRET",
				contact: "韩梅",
				phone: "13800000002",
				description: "负责数据平台建设与稳定性",
				parentId: 1,
				children: [
					{
						id: 3,
						name: "数据运维团队",
						dataLevel: "DATA_INTERNAL",
						sensitivity: "DATA_INTERNAL",
						contact: "陈伟",
						phone: "021-88886666",
						description: "保障数据基础设施运行",
						parentId: 2,
					},
					{
						id: 5,
						name: "研发支持团队",
						dataLevel: "DATA_PUBLIC",
						sensitivity: "DATA_PUBLIC",
						contact: "赵敏",
						phone: "13800000005",
						description: "提供平台接入与研发支持",
						parentId: 2,
					},
				],
			},
			{
				id: 4,
				name: "数据治理组",
				dataLevel: "DATA_SECRET",
				sensitivity: "DATA_SECRET",
				contact: "王雪",
				phone: "13800000003",
				description: "制定数据标准与安全策略",
				parentId: 1,
			},
		],
	},
	{
		id: 10,
		name: "业务数据中心",
		dataLevel: "DATA_SECRET",
		sensitivity: "DATA_SECRET",
		contact: "赵强",
		phone: "010-55556666",
		description: "面向业务线的数据服务支撑",
		parentId: null,
		children: [
			{
				id: 11,
				name: "客户洞察部",
				dataLevel: "DATA_INTERNAL",
				sensitivity: "DATA_INTERNAL",
				contact: "孙丽",
				phone: "010-88889999",
				description: "提供客户全域分析能力",
				parentId: 10,
			},
		],
	},
];

let organizationId = 20;

function findOrganization(nodes: OrganizationNode[], id: number): OrganizationNode | null {
	for (const node of nodes) {
		if (node.id === id) {
			return node;
		}
		if (node.children?.length) {
			const found = findOrganization(node.children, id);
			if (found) {
				return found;
			}
		}
	}
	return null;
}

function insertOrganization(nodes: OrganizationNode[], parentId: number | null, node: OrganizationNode): boolean {
	if (parentId == null) {
		node.parentId = null;
		nodes.push(node);
		return true;
	}
	for (const item of nodes) {
		if (item.id === parentId) {
			node.parentId = parentId;
			if (item.children) {
				item.children.push(node);
			} else {
				item.children = [node];
			}
			return true;
		}
		if (item.children?.length && insertOrganization(item.children, parentId, node)) {
			return true;
		}
	}
	return false;
}

function updateOrganizationNode(
	nodes: OrganizationNode[],
	id: number,
	patch: Partial<OrganizationNode>,
): OrganizationNode | null {
	for (const node of nodes) {
		if (node.id === id) {
			const sanitized: Partial<OrganizationNode> = {};
			for (const [key, value] of Object.entries(patch) as [
				keyof OrganizationNode,
				OrganizationNode[keyof OrganizationNode],
			][]) {
				if (value !== undefined) {
					(sanitized as Record<string, unknown>)[key as string] = value as unknown;
				}
			}
			Object.assign(node, sanitized);
			if (sanitized.dataLevel) {
				node.sensitivity = sanitized.dataLevel;
			}
			return node;
		}
		if (node.children?.length) {
			const updated = updateOrganizationNode(node.children, id, patch);
			if (updated) {
				return updated;
			}
		}
	}
	return null;
}

function deleteOrganizationNode(nodes: OrganizationNode[], id: number): boolean {
	const index = nodes.findIndex((item) => item.id === id);
	if (index !== -1) {
		nodes.splice(index, 1);
		return true;
	}
	for (const node of nodes) {
		if (node.children?.length && deleteOrganizationNode(node.children, id)) {
			if (node.children.length === 0) {
				delete node.children;
			}
			return true;
		}
	}
	return false;
}

const adminUsers: AdminUser[] = Array.from({ length: 24 }).map((_, index) => {
	const status = faker.helpers.arrayElement(["ACTIVE", "PENDING", "DISABLED"]);
	return {
		id: index + 1,
		username: faker.internet.userName(),
		displayName: faker.person.fullName(),
		email: faker.internet.email(),
		orgPath: faker.helpers.arrayElements(["数据与智能中心", "数据平台组", "业务线A", "业务线B"], { min: 1, max: 3 }),
		roles: faker.helpers.arrayElements(["SYSADMIN", "AUTHADMIN", "AUDITADMIN", "DATA_STEWARD", "DATA_ANALYST"], {
			min: 1,
			max: 2,
		}),
		securityLevel: faker.helpers.arrayElement(["非密", "普通", "秘密", "机密"]),
		status,
		lastLoginAt: status === "DISABLED" ? undefined : faker.date.recent({ days: 6 }).toISOString(),
	};
});

const adminRoles: AdminRoleDetail[] = [
	{
		id: 1,
		name: "SYSADMIN",
		description: "系统管理员，负责平台配置、用户与菜单管理",
		securityLevel: "机密",
		permissions: ["user.create", "user.update", "role.manage", "dataset.publish"],
		memberCount: 3,
		approvalFlow: "授权管理员单人审批",
		updatedAt: faker.date.recent({ days: 3 }).toISOString(),
	},
	{
		id: 2,
		name: "AUTHADMIN",
		description: "授权管理员，处理审批流程",
		securityLevel: "秘密",
		permissions: ["approval.review", "approval.assign"],
		memberCount: 2,
		approvalFlow: "审计留痕",
		updatedAt: faker.date.recent({ days: 2 }).toISOString(),
	},
	{
		id: 3,
		name: "AUDITADMIN",
		description: "安全审计员，负责日志巡检与导出",
		securityLevel: "秘密",
		permissions: ["audit.read", "audit.export"],
		memberCount: 2,
		approvalFlow: "系统记录",
		updatedAt: faker.date.recent({ days: 5 }).toISOString(),
	},
];

const permissionCatalog: PermissionCatalogSection[] = [
	{
		category: "用户与组织",
		description: "账户、组织管理相关权限",
		permissions: [
			{ code: "user.create", name: "创建用户", securityLevel: "普通" },
			{ code: "user.update", name: "更新用户", securityLevel: "普通" },
			{ code: "org.manage", name: "管理组织", securityLevel: "秘密" },
		],
	},
	{
		category: "数据资产",
		description: "数据目录与安全策略",
		permissions: [
			{ code: "dataset.publish", name: "发布数据集", securityLevel: "秘密" },
			{ code: "dataset.mask", name: "配置脱敏", securityLevel: "机密" },
		],
	},
	{
		category: "审计与审批",
		description: "流程与审计操作",
		permissions: [
			{ code: "approval.review", name: "审批变更", securityLevel: "秘密" },
			{ code: "approval.assign", name: "分配审批人", securityLevel: "秘密" },
			{ code: "audit.export", name: "导出日志", securityLevel: "普通" },
		],
	},
];

function json(data: unknown) {
	return HttpResponse.json({
		status: ResultStatus.SUCCESS,
		message: "",
		data,
	});
}

export const adminHandlers = [
	http.get(`${ADMIN_API}/whoami`, () => json(getActiveAdmin())),

	http.get(`${ADMIN_API}/change-requests`, ({ request }) => {
		const url = new URL(request.url);
		const status = url.searchParams.get("status");
		const type = url.searchParams.get("type");
		const filtered = changeRequests.filter((item) => {
			const statusMatch = status ? item.status === status : true;
			const typeMatch = type ? item.resourceType === type : true;
			return statusMatch && typeMatch;
		});
		return json(filtered);
	}),

	http.get(`${ADMIN_API}/change-requests/mine`, () => {
		const { username } = getActiveAdmin();
		return json(changeRequests.filter((item) => item.requestedBy === username));
	}),

	http.post(`${ADMIN_API}/change-requests`, async ({ request }) => {
		const payload = (await request.json()) as Partial<ChangeRequest>;
		const now = new Date().toISOString();
		const { username } = getActiveAdmin();
		const created: ChangeRequest = {
			id: ++changeRequestId,
			resourceType: payload.resourceType || "UNKNOWN",
			action: payload.action || "UNKNOWN",
			resourceId: payload.resourceId,
			payloadJson: payload.payloadJson,
			status: "DRAFT",
			requestedBy: username || "sysadmin",
			requestedAt: now,
		};
		changeRequests.unshift(created);
		return json(created);
	}),

	http.post(`${ADMIN_API}/change-requests/:id/submit`, ({ params }) => {
		const id = Number(params.id);
		const item = changeRequests.find((request) => request.id === id);
		if (!item) {
			return HttpResponse.json({ status: ResultStatus.ERROR, message: "变更不存在" }, { status: 404 });
		}
		item.status = "PENDING";
		return json(item);
	}),

	http.post(`${ADMIN_API}/change-requests/:id/approve`, async ({ params, request }) => {
		const id = Number(params.id);
		const item = changeRequests.find((requestItem) => requestItem.id === id);
		if (!item) {
			return HttpResponse.json({ status: ResultStatus.ERROR, message: "变更不存在" }, { status: 404 });
		}
		const { reason } = (await request.json().catch(() => ({}))) as { reason?: string };
		item.status = "APPROVED";
		item.decidedBy = getActiveAdmin().username || "sysadmin";
		item.decidedAt = new Date().toISOString();
		item.reason = reason;
		return json(item);
	}),

	http.post(`${ADMIN_API}/change-requests/:id/reject`, async ({ params, request }) => {
		const id = Number(params.id);
		const item = changeRequests.find((requestItem) => requestItem.id === id);
		if (!item) {
			return HttpResponse.json({ status: ResultStatus.ERROR, message: "变更不存在" }, { status: 404 });
		}
		const { reason } = (await request.json().catch(() => ({}))) as { reason?: string };
		item.status = "REJECTED";
		item.decidedBy = getActiveAdmin().username || "sysadmin";
		item.decidedAt = new Date().toISOString();
		item.reason = reason || "";
		return json(item);
	}),

	http.get(`${ADMIN_API}/audit`, ({ request }) => {
		const url = new URL(request.url);
		const actor = url.searchParams.get("actor");
		const action = url.searchParams.get("action");
		const resource = url.searchParams.get("resource");
		const outcome = url.searchParams.get("outcome");
		const filtered = auditEvents.filter((event) => {
			return (
				(actor ? event.actor?.includes(actor) : true) &&
				(action ? event.action?.includes(action) : true) &&
				(resource ? event.resource?.includes(resource) : true) &&
				(outcome ? event.outcome === outcome : true)
			);
		});
		return json(filtered);
	}),

	http.get(`/admin/audit/export`, ({ request }) => {
		const url = new URL(request.url, "http://localhost");
		const format = url.searchParams.get("format") ?? "csv";
		const content = format === "json" ? JSON.stringify(auditEvents, null, 2) : buildCsv(auditEvents);
		return new HttpResponse(content, {
			headers: {
				"Content-Type": format === "json" ? "application/json" : "text/csv",
			},
		});
	}),

	http.get(`${ADMIN_API}/audit/export`, () => {
		const content = buildCsv(auditEvents);
		return new HttpResponse(content, {
			headers: { "Content-Type": "text/csv" },
		});
	}),

	http.get(`${ADMIN_API}/system/config`, () => json(systemConfigs)),

	http.post(`${ADMIN_API}/system/config`, async ({ request }) => {
		const config = (await request.json()) as SystemConfigItem;
		const draft: ChangeRequest = {
			id: ++changeRequestId,
			resourceType: "CONFIG",
			action: "CONFIG_SET",
			payloadJson: JSON.stringify(config),
			status: "PENDING",
			requestedBy: getActiveAdmin().username || "sysadmin",
			requestedAt: new Date().toISOString(),
		};
		changeRequests.unshift(draft);
		return json(draft);
	}),

	http.get(`${ADMIN_API}/portal/menus`, () => json(portalMenus)),

	http.post(`${ADMIN_API}/portal/menus`, async ({ request }) => {
		const body = (await request.json()) as PortalMenuItem;
		const draft: ChangeRequest = {
			id: ++changeRequestId,
			resourceType: "PORTAL_MENU",
			action: "CREATE",
			payloadJson: JSON.stringify(body),
			status: "PENDING",
			requestedBy: getActiveAdmin().username || "sysadmin",
			requestedAt: new Date().toISOString(),
		};
		changeRequests.unshift(draft);
		return json(draft);
	}),

	http.put(`${ADMIN_API}/portal/menus/:id`, async ({ request, params }) => {
		const body = (await request.json()) as PortalMenuItem;
		const draft: ChangeRequest = {
			id: ++changeRequestId,
			resourceType: "PORTAL_MENU",
			action: "UPDATE",
			resourceId: String(params.id),
			payloadJson: JSON.stringify(body),
			status: "PENDING",
			requestedBy: getActiveAdmin().username || "sysadmin",
			requestedAt: new Date().toISOString(),
		};
		changeRequests.unshift(draft);
		return json(draft);
	}),

	http.delete(`${ADMIN_API}/portal/menus/:id`, ({ params }) => {
		const draft: ChangeRequest = {
			id: ++changeRequestId,
			resourceType: "PORTAL_MENU",
			action: "DELETE",
			resourceId: String(params.id),
			status: "PENDING",
			requestedBy: getActiveAdmin().username || "sysadmin",
			requestedAt: new Date().toISOString(),
		};
		changeRequests.unshift(draft);
		return json(draft);
	}),

	http.post(`${ADMIN_API}/orgs`, async ({ request }) => {
		const payload = (await request.json()) as OrganizationPayload;
		const name = payload.name?.trim();
		if (!name) {
			return HttpResponse.json({ status: ResultStatus.ERROR, message: "部门名称不能为空" }, { status: 400 });
		}
		if (!payload.dataLevel) {
			return HttpResponse.json({ status: ResultStatus.ERROR, message: "请选择部门数据密级" }, { status: 400 });
		}
		const parentId = payload.parentId ?? null;
		if (parentId !== null && !findOrganization(organizations, parentId)) {
			return HttpResponse.json({ status: ResultStatus.ERROR, message: "父级部门不存在" }, { status: 400 });
		}
		const newOrg: OrganizationNode = {
			id: ++organizationId,
			name,
			dataLevel: payload.dataLevel,
			sensitivity: payload.dataLevel,
			parentId,
			contact: payload.contact?.trim() || undefined,
			phone: payload.phone?.trim() || undefined,
			description: payload.description?.trim() || undefined,
			children: [],
		};
		if (!insertOrganization(organizations, parentId, newOrg)) {
			organizationId -= 1;
			return HttpResponse.json({ status: ResultStatus.ERROR, message: "无法创建部门，请重试" }, { status: 400 });
		}
		return json(newOrg);
	}),

	http.put(`${ADMIN_API}/orgs/:id`, async ({ params, request }) => {
		const id = Number(params.id);
		if (Number.isNaN(id)) {
			return HttpResponse.json({ status: ResultStatus.ERROR, message: "部门ID不合法" }, { status: 400 });
		}
		const payload = (await request.json()) as OrganizationPayload;
		const name = payload.name?.trim();
		if (!name) {
			return HttpResponse.json({ status: ResultStatus.ERROR, message: "部门名称不能为空" }, { status: 400 });
		}
		if (!payload.dataLevel) {
			return HttpResponse.json({ status: ResultStatus.ERROR, message: "请选择部门数据密级" }, { status: 400 });
		}
		const updated = updateOrganizationNode(organizations, id, {
			name,
			dataLevel: payload.dataLevel,
			sensitivity: payload.dataLevel,
			contact: payload.contact?.trim() || undefined,
			phone: payload.phone?.trim() || undefined,
			description: payload.description?.trim() || undefined,
		});
		if (!updated) {
			return HttpResponse.json({ status: ResultStatus.ERROR, message: "部门不存在" }, { status: 404 });
		}
		return json(updated);
	}),

	http.delete(`${ADMIN_API}/orgs/:id`, ({ params }) => {
		const id = Number(params.id);
		if (Number.isNaN(id)) {
			return HttpResponse.json({ status: ResultStatus.ERROR, message: "部门ID不合法" }, { status: 400 });
		}
		const removed = deleteOrganizationNode(organizations, id);
		if (!removed) {
			return HttpResponse.json({ status: ResultStatus.ERROR, message: "部门不存在" }, { status: 404 });
		}
		return json({ id });
	}),

	http.get(`${ADMIN_API}/orgs`, () => json(organizations)),

	http.get(`${ADMIN_API}/users`, () => json(adminUsers)),

	http.get(`${ADMIN_API}/roles`, () => json(adminRoles)),

	http.get(`${ADMIN_API}/permissions/catalog`, () => json(permissionCatalog)),
];

function buildCsv(events: AuditEvent[]) {
	const header = "id,timestamp,actor,action,resource,outcome";
	const rows = events.map((event) =>
		[event.id, event.timestamp, event.actor ?? "", event.action ?? "", event.resource ?? "", event.outcome ?? ""]
			.map((value) => `"${String(value).replaceAll('"', '""')}"`)
			.join(","),
	);
	return [header, ...rows].join("\n");
}
