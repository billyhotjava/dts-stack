import { HttpResponse, http } from "msw";
import type {
	ApprovalActionRequest,
	ApprovalRequest,
	ApprovalRequestDetail,
	CreateGroupRequest,
	CreateRoleRequest,
	CreateUserRequest,
	KeycloakApiResponse,
	KeycloakRole,
	KeycloakUser,
	SetUserEnabledRequest,
	UpdateGroupRequest,
	UpdateRoleRequest,
	UpdateUserRequest,
	UserProfileConfig,
} from "#/keycloak";
import { ResultStatus } from "@/types/enum";
import { keycloakDb } from "../db/keycloak";

const API_PREFIX = "/api/keycloak";

const BUILT_IN_ROLES = new Set([
	"SYSADMIN",
	"OPADMIN",
	"AUTHADMIN",
	"AUDITADMIN",
	"DEPT_OWNER",
	"DEPT_EDITOR",
	"DEPT_VIEWER",
	"INST_OWNER",
	"INST_EDITOR",
	"INST_VIEWER",
]);

const ok = <T>(data: T, message = "success") =>
	HttpResponse.json({ status: ResultStatus.SUCCESS, message, data } as KeycloakApiResponse<T>);

const randomId = () => globalThis.crypto?.randomUUID?.() ?? Math.random().toString(36).slice(2, 10);

const findUserIndex = (id: string) => keycloakDb.users.findIndex((user) => user.id === id);

const approvalRequests: ApprovalRequestDetail[] = [
	{
		id: 202405001,
		requester: "sysadmin",
		type: "CREATE_USER",
		reason: "为数据运维团队新成员申请 dataops 账号",
		createdAt: "2024-05-12T09:30:00+08:00",
		status: "PENDING",
		approver: "authadmin",
		items: [
			{
				id: 5101,
				targetKind: "USER",
				targetId: "dataops",
				seqNumber: 1,
				payload: JSON.stringify({
					username: "dataops",
					email: "data.ops@example.com",
					firstName: "数据运维",
					enabled: true,
					attributes: {
						department: "数据平台组",
						title: "平台工程师",
					},
				}),
			},
		],
	},
	{
		id: 202405000,
		requester: "sysadmin",
		type: "UPDATE_USER",
		reason: "同步数据治理专员的组织信息",
		createdAt: "2024-05-10T11:20:00+08:00",
		decidedAt: "2024-05-10T15:45:00+08:00",
		status: "APPROVED",
		approver: "authadmin",
		decisionNote: "属性更新已确认",
		items: [
			{
				id: 5102,
				targetKind: "USER",
				targetId: "chenyu",
				seqNumber: 1,
				payload: JSON.stringify({
					username: "chenyu",
					email: "chen.yu@demo.cn",
					firstName: "陈宇",
					enabled: true,
					attributes: {
						department: "数据治理小组",
						title: "数据治理专员",
					},
				}),
			},
		],
	},
	{
		id: 202404998,
		requester: "sysadmin",
		type: "DELETE_USER",
		reason: "清理已离职账号 legacy.ops",
		createdAt: "2024-05-08T08:10:00+08:00",
		decidedAt: "2024-05-08T09:00:00+08:00",
		status: "REJECTED",
		approver: "authadmin",
		decisionNote: "请补充离职审批单据",
		items: [
			{
				id: 5103,
				targetKind: "USER",
				targetId: "legacy.ops",
				seqNumber: 1,
				payload: JSON.stringify({
					username: "legacy.ops",
					email: "legacy.ops@demo.cn",
					enabled: false,
				}),
			},
		],
	},
	{
		id: 202404997,
		requester: "sysadmin",
		type: "GRANT_ROLE",
		reason: "为数据分析组批量授权 DATA_STEWARD 角色",
		createdAt: "2024-05-06T10:00:00+08:00",
		decidedAt: "2024-05-06T13:25:00+08:00",
		status: "FAILED",
		approver: "authadmin",
		decisionNote: "Keycloak API 超时，稍后重试",
		errorMessage: "调用 Keycloak 接口超时",
		items: [
			{
				id: 5104,
				targetKind: "ROLE",
				targetId: "DATA_STEWARD",
				seqNumber: 1,
				payload: JSON.stringify({
					usernames: ["lixiaomei", "wanghong"],
					role: "DATA_STEWARD",
				}),
			},
		],
	},
	{
		id: 202404996,
		requester: "sysadmin",
		type: "UPDATE_USER",
		reason: "更新 jaycee 的多因素认证策略",
		createdAt: "2024-05-04T09:50:00+08:00",
		decidedAt: "2024-05-04T11:05:00+08:00",
		status: "APPLIED",
		approver: "authadmin",
		decisionNote: "策略已同步至 Keycloak",
		items: [
			{
				id: 5105,
				targetKind: "USER",
				targetId: "jaycee",
				seqNumber: 1,
				payload: JSON.stringify({
					username: "jaycee",
					email: "jay.cee@demo.cn",
					attributes: {
						mfa: "required",
					},
				}),
			},
		],
	},
];

const listApprovalRequests = (): ApprovalRequest[] => approvalRequests.map(({ items, ...rest }) => ({ ...rest }));

const findApprovalIndex = (id: number) => approvalRequests.findIndex((item) => item.id === id);

const keycloakHandlers = [
	// ---- Users ----
	http.get(`${API_PREFIX}/users`, ({ request }) => {
		const url = new URL(request.url);
		const first = Number(url.searchParams.get("first") ?? 0);
		const max = Number(url.searchParams.get("max") ?? keycloakDb.users.length);
		const slice = keycloakDb.users.slice(first, first + max);
		return HttpResponse.json(slice);
	}),

	http.get(`${API_PREFIX}/users/search`, ({ request }) => {
		const url = new URL(request.url);
		const username = (url.searchParams.get("username") ?? "").toLowerCase();
		const list = keycloakDb.users.filter((user) => user.username.toLowerCase().includes(username));
		return HttpResponse.json(list);
	}),

	http.get(`${API_PREFIX}/users/:id`, ({ params }) => {
		const user = keycloakDb.users.find((item) => item.id === params.id);
		if (!user) {
			return HttpResponse.json({ status: ResultStatus.ERROR, message: "用户不存在" }, { status: 404 });
		}
		return HttpResponse.json(user);
	}),

	http.post(`${API_PREFIX}/users`, async ({ request }) => {
		const payload = (await request.json()) as CreateUserRequest;
		if (!payload.username) {
			return HttpResponse.json({ status: ResultStatus.ERROR, message: "用户名不能为空" }, { status: 400 });
		}
		const newUser: KeycloakUser = {
			id: randomId(),
			username: payload.username,
			email: payload.email,
			firstName: payload.firstName,
			lastName: payload.lastName,
			enabled: payload.enabled ?? true,
			attributes: payload.attributes ?? {},
			groups: [],
			realmRoles: ["DATA_STEWARD"],
			createdTimestamp: Date.now(),
		};
		keycloakDb.users.unshift(newUser);
		return ok(newUser, "用户创建成功");
	}),

	http.put(`${API_PREFIX}/users/:id`, async ({ params, request }) => {
		const index = findUserIndex(params.id as string);
		if (index === -1) {
			return HttpResponse.json({ status: ResultStatus.ERROR, message: "用户不存在" }, { status: 404 });
		}
		const payload = (await request.json()) as UpdateUserRequest;
		const current = keycloakDb.users[index];
		const nextUser: KeycloakUser = {
			...current,
			...payload,
			attributes: { ...current.attributes, ...payload.attributes },
		};
		keycloakDb.users[index] = nextUser;
		return ok(nextUser, "用户更新成功");
	}),

	http.delete(`${API_PREFIX}/users/:id`, ({ params }) => {
		const index = findUserIndex(params.id as string);
		if (index === -1) {
			return HttpResponse.json({ status: ResultStatus.ERROR, message: "用户不存在" }, { status: 404 });
		}
		keycloakDb.users.splice(index, 1);
		return ok(null, "用户已删除");
	}),

	http.post(`${API_PREFIX}/users/:id/reset-password`, async ({ params, request }) => {
		const index = findUserIndex(params.id as string);
		if (index === -1) {
			return HttpResponse.json({ status: ResultStatus.ERROR, message: "用户不存在" }, { status: 404 });
		}
		await request.json();
		return ok(null, "密码重置成功");
	}),

	http.put(`${API_PREFIX}/users/:id/enabled`, async ({ params, request }) => {
		const index = findUserIndex(params.id as string);
		if (index === -1) {
			return HttpResponse.json({ status: ResultStatus.ERROR, message: "用户不存在" }, { status: 404 });
		}
		const body = (await request.json()) as SetUserEnabledRequest;
		keycloakDb.users[index].enabled = body.enabled;
		return ok(keycloakDb.users[index], "状态更新成功");
	}),

	http.get(`${API_PREFIX}/users/:id/roles`, ({ params }) => {
		const user = keycloakDb.users.find((item) => item.id === params.id);
		if (!user) {
			return HttpResponse.json({ status: ResultStatus.ERROR, message: "用户不存在" }, { status: 404 });
		}
		const roles = keycloakDb.roles.filter((role) => user.realmRoles?.includes(role.name));
		return HttpResponse.json(roles);
	}),

	http.post(`${API_PREFIX}/users/:id/roles`, async ({ params, request }) => {
		const user = keycloakDb.users.find((item) => item.id === params.id);
		if (!user) {
			return HttpResponse.json({ status: ResultStatus.ERROR, message: "用户不存在" }, { status: 404 });
		}
		const body = (await request.json()) as KeycloakRole[];
		user.realmRoles = Array.from(new Set([...(user.realmRoles ?? []), ...body.map((role) => role.name)]));
		return ok(null, "角色绑定成功");
	}),

	http.delete(`${API_PREFIX}/users/:id/roles`, async ({ params, request }) => {
		const user = keycloakDb.users.find((item) => item.id === params.id);
		if (!user) {
			return HttpResponse.json({ status: ResultStatus.ERROR, message: "用户不存在" }, { status: 404 });
		}
		const body = (await request.json()) as KeycloakRole[];
		const toRemove = new Set(body.map((role) => role.name));
		user.realmRoles = (user.realmRoles ?? []).filter((name) => !toRemove.has(name));
		return ok(null, "角色解绑成功");
	}),

	// ---- Groups ----
	http.get(`${API_PREFIX}/groups`, () => {
		return HttpResponse.json(keycloakDb.groups);
	}),

	http.get(`${API_PREFIX}/groups/:id`, ({ params }) => {
		const group = keycloakDb.groups.find((item) => item.id === params.id);
		if (!group) {
			return HttpResponse.json({ status: ResultStatus.ERROR, message: "用户组不存在" }, { status: 404 });
		}
		return HttpResponse.json(group);
	}),

	http.get(`${API_PREFIX}/groups/:id/members`, ({ params }) => {
		const members = keycloakDb.groupMembers[params.id as string] ?? [];
		return HttpResponse.json(members);
	}),

	http.post(`${API_PREFIX}/groups`, async ({ request }) => {
		const payload = (await request.json()) as CreateGroupRequest;
		const group = {
			id: randomId(),
			name: payload.name,
			path: payload.path ?? `/${payload.name}`,
			attributes: payload.attributes ?? {},
			subGroups: [],
		};
		keycloakDb.groups.push(group);
		keycloakDb.groupMembers[group.id] = [];
		return ok(group, "用户组创建成功");
	}),

	http.put(`${API_PREFIX}/groups/:id`, async ({ params, request }) => {
		const index = keycloakDb.groups.findIndex((item) => item.id === params.id);
		if (index === -1) {
			return HttpResponse.json({ status: ResultStatus.ERROR, message: "用户组不存在" }, { status: 404 });
		}
		const payload = (await request.json()) as UpdateGroupRequest;
		keycloakDb.groups[index] = {
			...keycloakDb.groups[index],
			...payload,
			attributes: { ...keycloakDb.groups[index].attributes, ...payload.attributes },
		};
		return ok(keycloakDb.groups[index], "用户组更新成功");
	}),

	http.delete(`${API_PREFIX}/groups/:id`, ({ params }) => {
		const index = keycloakDb.groups.findIndex((item) => item.id === params.id);
		if (index === -1) {
			return HttpResponse.json({ status: ResultStatus.ERROR, message: "用户组不存在" }, { status: 404 });
		}
		const removed = keycloakDb.groups.splice(index, 1)[0];
		delete keycloakDb.groupMembers[params.id as string];
		return ok(removed, "用户组已删除");
	}),

	http.post(`${API_PREFIX}/groups/:id/members/:userId`, ({ params }) => {
		const groupId = params.id as string;
		const userId = params.userId as string;
		const members = keycloakDb.groupMembers[groupId] ?? [];
		if (!members.includes(userId)) {
			keycloakDb.groupMembers[groupId] = [...members, userId];
		}
		return ok(null, "成员添加成功");
	}),

	http.delete(`${API_PREFIX}/groups/:id/members/:userId`, ({ params }) => {
		const groupId = params.id as string;
		const userId = params.userId as string;
		const remaining = (keycloakDb.groupMembers[groupId] ?? []).filter((member: string) => member !== userId);
		keycloakDb.groupMembers[groupId] = remaining;
		return ok(null, "成员已移除");
	}),

	http.get(`${API_PREFIX}/groups/user/:userId`, ({ params }) => {
		const userId = params.userId as string;
		const joinedIds = Object.entries(keycloakDb.groupMembers)
			.filter(([, members]) => members.includes(userId))
			.map(([groupId]) => groupId);
		const list = keycloakDb.groups.filter((group) => joinedIds.includes(group.id ?? ""));
		return HttpResponse.json(list);
	}),

	// ---- Localization ----
	http.get(`${API_PREFIX}/localization/zh-CN`, () => {
		const translations = {
			userManagement: { created: "创建成功", updated: "更新成功" },
			roleManagement: { created: "角色创建成功", updated: "角色更新成功" },
			groupManagement: { created: "用户组创建成功", updated: "用户组更新成功" },
			commonActions: { save: "保存", cancel: "取消" },
			statusMessages: { pending: "审批中", approved: "已通过" },
			formLabels: { username: "用户名", email: "邮箱" },
			pagination: { next: "下一页", prev: "上一页" },
		};
		return HttpResponse.json(translations);
	}),

	// ---- User Profile ----
	http.get(`${API_PREFIX}/userprofile/config`, () => {
		const config: UserProfileConfig = {
			attributes: [
				{ name: "title", displayName: "sys.profile.title", multivalued: false },
				{ name: "department", displayName: "sys.profile.department", multivalued: false },
			],
		};
		return HttpResponse.json(config);
	}),

	// ---- Roles ----
	http.get(`${API_PREFIX}/roles`, () => {
		return HttpResponse.json(keycloakDb.roles);
	}),

	http.get(`${API_PREFIX}/roles/:name`, ({ params }) => {
		const role = keycloakDb.roles.find((item) => item.name === params.name);
		if (!role) {
			return HttpResponse.json({ status: ResultStatus.ERROR, message: "角色不存在" }, { status: 404 });
		}
		return HttpResponse.json(role);
	}),

	http.post(`${API_PREFIX}/roles`, async ({ request }) => {
		const body = (await request.json()) as CreateRoleRequest;
		if (!body.name) {
			return HttpResponse.json({ status: ResultStatus.ERROR, message: "角色名称不能为空" }, { status: 400 });
		}
		if (keycloakDb.roles.some((role) => role.name === body.name)) {
			return HttpResponse.json({ status: ResultStatus.ERROR, message: "角色已存在" }, { status: 409 });
		}
		if (BUILT_IN_ROLES.has(body.name)) {
			return HttpResponse.json({ status: ResultStatus.ERROR, message: "内置角色由系统维护" }, { status: 400 });
		}
		const role: KeycloakRole = {
			id: randomId(),
			name: body.name,
			description: body.description,
			composite: body.composite ?? false,
			clientRole: body.clientRole ?? false,
			containerId: "realm",
			attributes: body.attributes,
		};
		keycloakDb.roles.unshift(role);
		return ok(role, "角色创建成功");
	}),

	http.put(`${API_PREFIX}/roles/:name`, async ({ params, request }) => {
		const index = keycloakDb.roles.findIndex((item) => item.name === params.name);
		if (index === -1) {
			return HttpResponse.json({ status: ResultStatus.ERROR, message: "角色不存在" }, { status: 404 });
		}
		if (BUILT_IN_ROLES.has(params.name as string)) {
			return HttpResponse.json({ status: ResultStatus.ERROR, message: "内置角色不可修改" }, { status: 400 });
		}
		const payload = (await request.json()) as UpdateRoleRequest;
		const current = keycloakDb.roles[index];
		const nextAttributes = payload.attributes ? { ...current.attributes, ...payload.attributes } : current.attributes;
		keycloakDb.roles[index] = {
			...current,
			...payload,
			attributes: nextAttributes,
			name: payload.name ?? current.name,
		};
		return ok(keycloakDb.roles[index], "角色更新成功");
	}),

	http.delete(`${API_PREFIX}/roles/:name`, ({ params }) => {
		const index = keycloakDb.roles.findIndex((item) => item.name === params.name);
		if (index === -1) {
			return HttpResponse.json({ status: ResultStatus.ERROR, message: "角色不存在" }, { status: 404 });
		}
		if (BUILT_IN_ROLES.has(params.name as string)) {
			return HttpResponse.json({ status: ResultStatus.ERROR, message: "内置角色不可删除" }, { status: 400 });
		}
		const removed = keycloakDb.roles.splice(index, 1)[0];
		return ok(removed, "角色已删除");
	}),

	// ---- Approvals & Sync ----
	http.get("/api/approval-requests", () => {
		return HttpResponse.json({
			status: ResultStatus.SUCCESS,
			message: "success",
			data: listApprovalRequests(),
		});
	}),

	http.get("/api/approval-requests/:id", ({ params }) => {
		const id = Number(params.id);
		const record = approvalRequests.find((item) => item.id === id);
		if (!record) {
			return HttpResponse.json({ status: ResultStatus.ERROR, message: "审批请求不存在" }, { status: 404 });
		}
		return HttpResponse.json({
			status: ResultStatus.SUCCESS,
			message: "success",
			data: record,
		});
	}),

	http.get(`${API_PREFIX}/approvals`, () => {
		return ok(listApprovalRequests(), "success");
	}),

	http.post(`${API_PREFIX}/approvals/:id/:action`, async ({ params, request }) => {
		const id = Number(params.id);
		if (!Number.isFinite(id)) {
			return HttpResponse.json({ status: ResultStatus.ERROR, message: "请求不存在" }, { status: 404 });
		}
		const index = findApprovalIndex(id);
		if (index === -1) {
			return HttpResponse.json({ status: ResultStatus.ERROR, message: "请求不存在" }, { status: 404 });
		}
		let body: ApprovalActionRequest | undefined;
		try {
			body = (await request.json()) as ApprovalActionRequest;
		} catch (error) {
			body = undefined;
		}

		const action = String(params.action ?? "").toLowerCase();
		const now = new Date().toISOString();
		const current = approvalRequests[index];
		const approver = current.approver || "authadmin";

		if (action === "approve") {
			approvalRequests[index] = {
				...current,
				status: "APPROVED",
				approver,
				decidedAt: now,
				decisionNote: body?.note || "Approved via mock",
				errorMessage: undefined,
			};
			return ok(approvalRequests[index], "审批通过成功");
		}

		if (action === "reject") {
			approvalRequests[index] = {
				...current,
				status: "REJECTED",
				approver,
				decidedAt: now,
				decisionNote: body?.note || "已拒绝",
			};
			return ok(approvalRequests[index], "审批拒绝成功");
		}

		if (action === "process") {
			approvalRequests[index] = {
				...current,
				status: "APPLIED",
				approver,
				decidedAt: now,
				decisionNote: body?.note || current.decisionNote,
				errorMessage: undefined,
			};
			return ok(approvalRequests[index], "同步完成");
		}

		return HttpResponse.json(
			{ status: ResultStatus.ERROR, message: `不支持的操作: ${params.action}` },
			{ status: 400 },
		);
	}),

	http.post(`${API_PREFIX}/user-sync/process/:id`, ({ params }) => {
		const id = Number(params.id);
		const index = findApprovalIndex(id);
		if (index === -1) {
			return HttpResponse.json({ status: ResultStatus.ERROR, message: "请求不存在" }, { status: 404 });
		}
		const current = approvalRequests[index];
		approvalRequests[index] = {
			...current,
			status: "APPLIED",
			approver: current.approver || "authadmin",
			decidedAt: current.decidedAt ?? new Date().toISOString(),
			decisionNote: current.decisionNote ?? "已同步至 Keycloak",
			errorMessage: undefined,
		};
		return ok(null, "同步完成");
	}),
];

export { keycloakHandlers };
