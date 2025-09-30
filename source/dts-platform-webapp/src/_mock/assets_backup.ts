import { faker } from "@faker-js/faker";
import type { Menu, Permission, Role, User } from "#/entity";
import { PermissionType } from "#/enum";

const { GROUP, MENU, CATALOGUE } = PermissionType;

export const DB_MENU: Menu[] = [
	// group
	{ id: "group_dashboard", name: "sys.nav.dashboard", code: "dashboard", parentId: "", type: GROUP },

	// group_dashboard
	{
		id: "workbench",
		parentId: "group_dashboard",
		name: "sys.nav.workbench",
		code: "workbench",
		icon: "local:ic-workbench",
		type: MENU,
		path: "/workbench",
		component: "/pages/dashboard/workbench",
	},
	{
		id: "data_security",
		parentId: "group_dashboard",
		name: "sys.nav.dataSecurity",
		code: "security",
		icon: "solar:shield-keyhole-bold",
		type: MENU,
		path: "/security/assets",
		component: "/pages/security/data-security",
	},

	// group_pages
	// management
	{
		id: "management",
		parentId: "group_pages",
		name: "sys.nav.management",
		code: "management",
		icon: "local:ic-management",
		type: CATALOGUE,
		path: "/management",
	},
	{
		id: "management_user",
		parentId: "management",
		name: "sys.nav.user.index",
		code: "management:user",
		type: CATALOGUE,
		path: "/management/user",
	},
	{
		id: "management_user_profile",
		parentId: "management_user",
		name: "sys.nav.user.profile",
		code: "management:user:profile",
		type: MENU,
		path: "management/user/profile",
		component: "/pages/management/user/profile",
	},
	{
		id: "management_user_account",
		parentId: "management_user",
		name: "sys.nav.user.account",
		code: "management:user:account",
		type: MENU,
		path: "management/user/account",
		component: "/pages/management/user/account",
	},
	{
		id: "management_system",
		parentId: "management",
		name: "sys.nav.system.index",
		code: "management:system",
		type: CATALOGUE,
		path: "management/system",
	},
	{
		id: "management_system_user",
		parentId: "management_system",
		name: "sys.nav.system.user",
		code: "management:system:user",
		type: MENU,
		path: "/management/system/user",
		component: "/pages/management/system/user",
	},
	{
		id: "management_system_role",
		parentId: "management_system",
		name: "sys.nav.system.role",
		code: "management:system:role",
		type: MENU,
		path: "/management/system/role",
		component: "/pages/management/system/role",
	},
	{
		id: "management_system_group",
		parentId: "management_system",
		name: "sys.nav.system.group",
		code: "management:system:group",
		type: MENU,
		path: "/management/system/group",
		component: "/pages/management/system/group",
	},
	{
		id: "management_system_permission",
		parentId: "management_system",
		name: "sys.nav.system.permission",
		code: "management:system:permission",
		type: MENU,
		path: "/management/system/permission",
		component: "/pages/management/system/permission",
	},
	{
		id: "management_system_approval",
		parentId: "management_system",
		name: "sys.nav.system.approval",
		code: "management:system:approval",
		type: MENU,
		path: "/management/system/approval",
		component: "/pages/management/system/approval",
	},
	// 添加审计日志菜单项
	{
		id: "management_system_audit_log",
		parentId: "management_system",
		name: "sys.nav.system.audit_log",
		code: "management:system:audit_log",
		type: MENU,
		path: "/management/system/audit-log",
		component: "/pages/management/system/audit-log/index",
	},
];

export const DB_USER: User[] = [
	{
		id: "user_admin_id",
		username: "admin",
		password: "demo1234",
		avatar: faker.image.avatarGitHub(),
		email: "admin@slash.com",
	},
	{
		id: "user_test_id",
		username: "test",
		password: "demo1234",
		avatar: faker.image.avatarGitHub(),
		email: "test@slash.com",
	},
	{
		id: "user_guest_id",
		username: "guest",
		password: "demo1234",
		avatar: faker.image.avatarGitHub(),
		email: "guest@slash.com",
	},
];

export const DB_ROLE: Role[] = [
	{ id: "role_admin_id", name: "admin", code: "SUPER_ADMIN" },
	{ id: "role_test_id", name: "test", code: "TEST" },
];

export const DB_PERMISSION: Permission[] = [
	{ id: "permission_create", name: "permission-create", code: "permission:create" },
	{ id: "permission_read", name: "permission-read", code: "permission:read" },
	{ id: "permission_update", name: "permission-update", code: "permission:update" },
	{ id: "permission_delete", name: "permission-delete", code: "permission:delete" },
];

export const DB_USER_ROLE = [
	{ id: "user_admin_role_admin", userId: "user_admin_id", roleId: "role_admin_id" },
	{ id: "user_test_role_test", userId: "user_test_id", roleId: "role_test_id" },
];

export const DB_ROLE_PERMISSION = [
	{ id: faker.string.uuid(), roleId: "role_admin_id", permissionId: "permission_create" },
	{ id: faker.string.uuid(), roleId: "role_admin_id", permissionId: "permission_read" },
	{ id: faker.string.uuid(), roleId: "role_admin_id", permissionId: "permission_update" },
	{ id: faker.string.uuid(), roleId: "role_admin_id", permissionId: "permission_delete" },

	{ id: faker.string.uuid(), roleId: "role_test_id", permissionId: "permission_read" },
	{ id: faker.string.uuid(), roleId: "role_test_id", permissionId: "permission_update" },
];
