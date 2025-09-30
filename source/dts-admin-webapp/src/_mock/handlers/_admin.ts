import { faker } from "@faker-js/faker";
import { HttpResponse, http } from "msw";
import type {
	AdminCustomRole,
	AdminDataset,
	AdminRoleAssignment,
	AdminRoleDetail,
	AdminUser,
	AuditEvent,
	ChangeRequest,
	CreateCustomRolePayload,
	CreateRoleAssignmentPayload,
	DataOperation,
	OrganizationNode,
	OrganizationPayload,
	OrgDataLevel,
	PermissionCatalogSection,
	PortalMenuItem,
	SecurityLevel,
	SystemConfigItem,
} from "@/admin/types";
import { ResultStatus } from "@/types/enum";
import { getActiveAdmin } from "../utils/session";
import portalMenus from "../data/portal-menus.json";

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

// portalMenus are generated from dts-platform-webapp portal navigation as demo data
// See: dts-platform-webapp/public/portal-menus.demo.json

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

function resolveOrgName(orgId: number | null): string {
	if (orgId == null) {
		return "全院共享区";
	}
	const node = findOrganization(organizations, orgId);
	return node?.name ?? `组织 ${orgId}`;
}

function getCustomRoleByName(name: string) {
	return customRoles.find((role) => role.name === name);
}

function getRoleOperations(role: string): DataOperation[] {
	if (BUILT_IN_ROLE_OPERATIONS[role]) {
		return BUILT_IN_ROLE_OPERATIONS[role];
	}
	const custom = getCustomRoleByName(role);
	if (custom) {
		return custom.operations;
	}
	switch (role) {
		case "SYSADMIN":
		case "OPADMIN":
			return ["read", "write", "export"];
		case "AUTHADMIN":
			return ["read"];
		case "AUDITADMIN":
			return ["read", "export"];
		default:
			return ["read"];
	}
}

function getRoleScope(role: string): "DEPARTMENT" | "INSTITUTE" | null {
	if (BUILT_IN_ROLE_SCOPE[role]) {
		return BUILT_IN_ROLE_SCOPE[role];
	}
	const custom = getCustomRoleByName(role);
	return custom?.scope ?? null;
}

function validateAssignment(payload: CreateRoleAssignmentPayload): string | null {
	if (!payload.role) {
		return "请选择角色";
	}
	if (!payload.username || !payload.displayName) {
		return "请填写用户信息";
	}
	if (!Array.isArray(payload.datasetIds) || payload.datasetIds.length === 0) {
		return "请至少选择一个数据集";
	}
	if (!Array.isArray(payload.operations) || payload.operations.length === 0) {
		return "请选择需要授权的操作";
	}
	const allowed = getRoleOperations(payload.role);
	const overLimit = payload.operations.filter((op) => !allowed.includes(op));
	if (overLimit.length > 0) {
		return `角色不支持以下操作：${overLimit.join(", ")}`;
	}
	const securityRank = SECURITY_RANK[payload.userSecurityLevel];
	if (securityRank === undefined) {
		return "无效的用户密级";
	}
	const scopeType = getRoleScope(payload.role);
	if (scopeType === "DEPARTMENT" && payload.scopeOrgId == null) {
		return "部门类角色必须绑定具体机构";
	}
	if (scopeType === "INSTITUTE" && payload.scopeOrgId != null) {
		return "全院类角色需选择全院共享区作用域";
	}
	const selectedDatasets = datasets.filter((dataset) => payload.datasetIds.includes(dataset.id));
	if (selectedDatasets.length !== payload.datasetIds.length) {
		return "存在无效的数据集ID";
	}
	for (const dataset of selectedDatasets) {
		if (securityRank < DATA_RANK[dataset.dataLevel]) {
			return `用户密级不足以访问数据集 ${dataset.businessCode}`;
		}
		if (payload.scopeOrgId == null) {
			if (!dataset.isInstituteShared) {
				return `数据集 ${dataset.businessCode} 未进入全院共享区，无法以全院范围授权`;
			}
		} else if (dataset.ownerOrgId !== payload.scopeOrgId) {
			return `数据集 ${dataset.businessCode} 不属于所选机构`;
		}
	}
	return null;
}

const adminUsers: AdminUser[] = Array.from({ length: 24 }).map((_, index) => {
	const status = faker.helpers.arrayElement(["ACTIVE", "PENDING", "DISABLED"]);
	return {
		id: index + 1,
		username: faker.internet.userName(),
		displayName: faker.person.fullName(),
		email: faker.internet.email(),
		orgPath: faker.helpers.arrayElements(["数据与智能中心", "数据平台组", "业务线A", "业务线B"], { min: 1, max: 3 }),
		roles: faker.helpers.arrayElements(
			[
				"SYSADMIN",
				"AUTHADMIN",
				"AUDITADMIN",
				"DEPT_OWNER",
				"DEPT_EDITOR",
				"DEPT_VIEWER",
				"INST_OWNER",
				"INST_EDITOR",
				"INST_VIEWER",
				"DATA_STEWARD",
				"DATA_ANALYST",
			],
			{
				min: 1,
				max: 2,
			},
		),
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
		name: "OPADMIN",
		description: "业务运维管理员，负责业务平台整体运维和关联工具组件的运维",
		securityLevel: "机密",
		permissions: ["user.update", "dataset.publish", "system.operate"],
		memberCount: 2,
		approvalFlow: "授权管理员单人审批",
		updatedAt: faker.date.recent({ days: 2 }).toISOString(),
	},
	{
		id: 3,
		name: "AUTHADMIN",
		description: "授权管理员，处理审批流程",
		securityLevel: "秘密",
		permissions: ["approval.review", "approval.assign"],
		memberCount: 2,
		approvalFlow: "审计留痕",
		updatedAt: faker.date.recent({ days: 2 }).toISOString(),
	},
	{
		id: 4,
		name: "AUDITADMIN",
		description: "安全审计员，负责日志巡检与导出",
		securityLevel: "秘密",
		permissions: ["audit.read", "audit.export"],
		memberCount: 2,
		approvalFlow: "系统记录",
		updatedAt: faker.date.recent({ days: 5 }).toISOString(),
	},
	{
		id: 5,
		name: "DEPT_OWNER",
		description: "部门主管，负责本部门数据治理与授权",
		securityLevel: "重要",
		permissions: ["dataset.read", "dataset.write", "dataset.export", "dataset.authorize"],
		memberCount: 6,
		approvalFlow: "部门负责人备案",
		updatedAt: faker.date.recent({ days: 4 }).toISOString(),
	},
	{
		id: 6,
		name: "DEPT_EDITOR",
		description: "部门数据专员，可维护本部门数据集",
		securityLevel: "普通",
		permissions: ["dataset.read", "dataset.write", "dataset.export"],
		memberCount: 9,
		approvalFlow: "部门主管审批",
		updatedAt: faker.date.recent({ days: 6 }).toISOString(),
	},
	{
		id: 7,
		name: "DEPT_VIEWER",
		description: "数据查阅员，仅在本部门查阅数据",
		securityLevel: "普通",
		permissions: ["dataset.read"],
		memberCount: 14,
		approvalFlow: "自动备案",
		updatedAt: faker.date.recent({ days: 7 }).toISOString(),
	},
	{
		id: 8,
		name: "INST_OWNER",
		description: "研究所领导，治理全院共享区数据",
		securityLevel: "核心",
		permissions: ["dataset.read", "dataset.write", "dataset.export", "dataset.authorize"],
		memberCount: 3,
		approvalFlow: "研究所负责人审批",
		updatedAt: faker.date.recent({ days: 8 }).toISOString(),
	},
	{
		id: 9,
		name: "INST_EDITOR",
		description: "研究所数据专员，可编辑全院共享区数据",
		securityLevel: "重要",
		permissions: ["dataset.read", "dataset.write", "dataset.export"],
		memberCount: 5,
		approvalFlow: "研究所负责人审批",
		updatedAt: faker.date.recent({ days: 6 }).toISOString(),
	},
	{
		id: 9,
		name: "INST_VIEWER",
		description: "研究所数据查阅员，可查看全院共享区数据",
		securityLevel: "普通",
		permissions: ["dataset.read"],
		memberCount: 11,
		approvalFlow: "自动备案",
		updatedAt: faker.date.recent({ days: 10 }).toISOString(),
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

const SECURITY_RANK: Record<SecurityLevel, number> = {
	NON_SECRET: 0,
	GENERAL: 1,
	IMPORTANT: 2,
	CORE: 3,
};

const DATA_RANK: Record<OrgDataLevel, number> = {
	DATA_PUBLIC: 0,
	DATA_INTERNAL: 1,
	DATA_SECRET: 2,
	DATA_TOP_SECRET: 3,
};

const BUILT_IN_ROLE_OPERATIONS: Record<string, DataOperation[]> = {
	DEPT_OWNER: ["read", "write", "export"],
	DEPT_EDITOR: ["read", "write", "export"],
	DEPT_VIEWER: ["read"],
	INST_OWNER: ["read", "write", "export"],
	INST_EDITOR: ["read", "write", "export"],
	INST_VIEWER: ["read"],
};

const BUILT_IN_ROLE_SCOPE: Record<string, "DEPARTMENT" | "INSTITUTE"> = {
	DEPT_OWNER: "DEPARTMENT",
	DEPT_EDITOR: "DEPARTMENT",
	DEPT_VIEWER: "DEPARTMENT",
	INST_OWNER: "INSTITUTE",
	INST_EDITOR: "INSTITUTE",
	INST_VIEWER: "INSTITUTE",
};

const datasets: AdminDataset[] = [
	{
		id: 301,
		name: "ODS_ORDERS",
		businessCode: "ODS_ORDERS",
		description: "销售订单宽表，供部门内分析使用",
		dataLevel: "DATA_INTERNAL",
		ownerOrgId: 2,
		ownerOrgName: "数据平台组",
		isInstituteShared: false,
		rowCount: 820_000,
		updatedAt: faker.date.recent({ days: 1 }).toISOString(),
	},
	{
		id: 302,
		name: "DIM_CUSTOMER_SECURE",
		businessCode: "DIM_CUSTOMER_SECURE",
		description: "含联系方式的客户主数据，仅限本部门",
		dataLevel: "DATA_SECRET",
		ownerOrgId: 2,
		ownerOrgName: "数据平台组",
		isInstituteShared: false,
		rowCount: 210_500,
		updatedAt: faker.date.recent({ days: 3 }).toISOString(),
	},
	{
		id: 303,
		name: "INST_SHARED_MARKET_INSIGHT",
		businessCode: "INST_SHARED_MARKET_INSIGHT",
		description: "全院共享的市场洞察结果集",
		dataLevel: "DATA_INTERNAL",
		ownerOrgId: 1,
		ownerOrgName: "数据与智能中心",
		isInstituteShared: true,
		rowCount: 64_000,
		updatedAt: faker.date.recent({ days: 5 }).toISOString(),
	},
	{
		id: 304,
		name: "INST_SHARED_RISK_ALERT",
		businessCode: "INST_SHARED_RISK_ALERT",
		description: "风险预警指标共享区",
		dataLevel: "DATA_SECRET",
		ownerOrgId: 4,
		ownerOrgName: "数据治理组",
		isInstituteShared: true,
		rowCount: 18_200,
		updatedAt: faker.date.recent({ days: 2 }).toISOString(),
	},
	{
		id: 305,
		name: "ODS_PLATFORM_AUDIT",
		businessCode: "ODS_PLATFORM_AUDIT",
		description: "平台操作审计日志",
		dataLevel: "DATA_TOP_SECRET",
		ownerOrgId: 4,
		ownerOrgName: "数据治理组",
		isInstituteShared: false,
		rowCount: 1_280_000,
		updatedAt: faker.date.recent({ days: 1 }).toISOString(),
	},
];

let customRoleId = 400;
const customRoles: AdminCustomRole[] = [
	{
		id: ++customRoleId,
		name: "DEPT_EXPORT_LIMITED",
		scope: "DEPARTMENT",
		operations: ["read", "export"],
		maxRows: 50_000,
		allowDesensitizeJson: true,
		maxDataLevel: "DATA_SECRET",
		description: "部门内批量导出需脱敏，限制行数",
		createdBy: "sysadmin",
		createdAt: faker.date.recent({ days: 6 }).toISOString(),
	},
	{
		id: ++customRoleId,
		name: "INST_VIEW_INTERNAL",
		scope: "INSTITUTE",
		operations: ["read"],
		maxRows: null,
		allowDesensitizeJson: false,
		maxDataLevel: "DATA_INTERNAL",
		description: "面向全院共享区的内部可查阅角色",
		createdBy: "authadmin",
		createdAt: faker.date.recent({ days: 8 }).toISOString(),
	},
];

let roleAssignmentId = 9000;
const roleAssignments: AdminRoleAssignment[] = [
	{
		id: ++roleAssignmentId,
		role: "DEPT_OWNER",
		username: "luqi",
		displayName: "卢琦",
		userSecurityLevel: "IMPORTANT",
		scopeOrgId: 2,
		scopeOrgName: "数据平台组",
		datasetIds: [301, 302],
		operations: ["read", "write", "export"],
		grantedBy: "sysadmin",
		grantedAt: faker.date.recent({ days: 2 }).toISOString(),
	},
	{
		id: ++roleAssignmentId,
		role: "DEPT_VIEWER",
		username: "lixiaomei",
		displayName: "李晓美",
		userSecurityLevel: "GENERAL",
		scopeOrgId: 2,
		scopeOrgName: "数据平台组",
		datasetIds: [301],
		operations: ["read"],
		grantedBy: "luqi",
		grantedAt: faker.date.recent({ days: 4 }).toISOString(),
	},
	{
		id: ++roleAssignmentId,
		role: "INST_OWNER",
		username: "jianghui",
		displayName: "姜辉",
		userSecurityLevel: "CORE",
		scopeOrgId: null,
		scopeOrgName: "全院共享区",
		datasetIds: [303, 304],
		operations: ["read", "write", "export"],
		grantedBy: "sysadmin",
		grantedAt: faker.date.recent({ days: 1 }).toISOString(),
	},
	{
		id: ++roleAssignmentId,
		role: "DEPT_EXPORT_LIMITED",
		username: "chenyu",
		displayName: "陈宇",
		userSecurityLevel: "NON_SECRET",
		scopeOrgId: 2,
		scopeOrgName: "数据平台组",
		datasetIds: [301],
		operations: ["read", "export"],
		grantedBy: "luqi",
		grantedAt: faker.date.recent({ days: 7 }).toISOString(),
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

	http.get(`${ADMIN_API}/datasets`, () => json(datasets)),

	http.get(`${ADMIN_API}/custom-roles`, () => json(customRoles)),

	http.post(`${ADMIN_API}/custom-roles`, async ({ request }) => {
		const payload = (await request.json()) as CreateCustomRolePayload;
		if (!payload.name) {
			return HttpResponse.json({ status: ResultStatus.ERROR, message: "角色名称不能为空" }, { status: 400 });
		}
		if (BUILT_IN_ROLE_OPERATIONS[payload.name] || getCustomRoleByName(payload.name)) {
			return HttpResponse.json({ status: ResultStatus.ERROR, message: "角色名称已存在" }, { status: 409 });
		}
		if (!Array.isArray(payload.operations) || payload.operations.length === 0) {
			return HttpResponse.json({ status: ResultStatus.ERROR, message: "请选择角色权限" }, { status: 400 });
		}
		const invalidOps = payload.operations.filter((op) => !["read", "write", "export"].includes(op));
		if (invalidOps.length > 0) {
			return HttpResponse.json(
				{ status: ResultStatus.ERROR, message: `不支持的操作：${invalidOps.join(", ")}` },
				{ status: 400 },
			);
		}
		if (!payload.maxDataLevel || !(payload.maxDataLevel in DATA_RANK)) {
			return HttpResponse.json({ status: ResultStatus.ERROR, message: "请选择最大数据密级" }, { status: 400 });
		}
		const scope = payload.scope;
		if (scope !== "DEPARTMENT" && scope !== "INSTITUTE") {
			return HttpResponse.json({ status: ResultStatus.ERROR, message: "作用域无效" }, { status: 400 });
		}
		const created: AdminCustomRole = {
			id: ++customRoleId,
			name: payload.name,
			scope,
			operations: Array.from(new Set(payload.operations)) as DataOperation[],
			maxRows: payload.maxRows ?? null,
			allowDesensitizeJson: Boolean(payload.allowDesensitizeJson),
			maxDataLevel: payload.maxDataLevel,
			description: payload.description,
			createdBy: getActiveAdmin().username ?? "sysadmin",
			createdAt: new Date().toISOString(),
		};
		customRoles.unshift(created);
		return json(created);
	}),

	http.get(`${ADMIN_API}/role-assignments`, () => json(roleAssignments)),

	http.post(`${ADMIN_API}/role-assignments`, async ({ request }) => {
		const payload = (await request.json()) as CreateRoleAssignmentPayload;
		const error = validateAssignment(payload);
		if (error) {
			return HttpResponse.json({ status: ResultStatus.ERROR, message: error }, { status: 400 });
		}
		const record: AdminRoleAssignment = {
			id: ++roleAssignmentId,
			role: payload.role,
			username: payload.username,
			displayName: payload.displayName,
			userSecurityLevel: payload.userSecurityLevel,
			scopeOrgId: payload.scopeOrgId,
			scopeOrgName: resolveOrgName(payload.scopeOrgId),
			datasetIds: payload.datasetIds,
			operations: Array.from(new Set(payload.operations)) as DataOperation[],
			grantedBy: getActiveAdmin().username ?? "sysadmin",
			grantedAt: new Date().toISOString(),
		};
		roleAssignments.unshift(record);
		return json(record);
	}),

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
