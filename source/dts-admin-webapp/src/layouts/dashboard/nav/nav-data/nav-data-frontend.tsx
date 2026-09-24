import { Icon } from "@/components/icon";
import type { NavProps } from "@/components/nav";

const SYSADMIN_ROLES = ["ROLE_SYS_ADMIN", "SYSADMIN"];
const AUTHADMIN_ROLES = ["ROLE_AUTH_ADMIN", "AUTHADMIN"];
const AUDITADMIN_ROLES = ["ROLE_SECURITY_AUDITOR", "AUDITADMIN"];
const AUDIT_ALLOWED_ROLES = Array.from(new Set([...AUDITADMIN_ROLES, ...AUTHADMIN_ROLES]));

export const frontendNavData: NavProps["data"] = [
	{
		name: "三元管理",
		items: [
			{
				title: "申请记录",
				path: "/admin/my-changes",
				icon: <Icon icon="local:ic-my-requests" size={24} />,
				auth: SYSADMIN_ROLES,
			},
			{
				title: "sys.nav.usermgmt.system.user",
				path: "/admin/users",
				icon: <Icon icon="local:ic-users" size={24} />,
				auth: SYSADMIN_ROLES,
			},
			{
				title: "sys.nav.usermgmt.system.role",
				path: "/admin/roles",
				icon: <Icon icon="local:ic-roles" size={24} />,
				auth: SYSADMIN_ROLES,
			},
			{
				title: "sys.nav.usermgmt.system.permission",
				path: "/admin/portal-menus",
				icon: <Icon icon="local:ic-menu" size={24} />,
				auth: SYSADMIN_ROLES,
			},
		],
	},
	{
		name: "主数据管理",
		items: [
			{
				title: "sys.nav.usermgmt.system.group",
				path: "/admin/orgs",
				icon: <Icon icon="local:ic-orgs" size={24} />,
				auth: SYSADMIN_ROLES,
			},
		],
	},
	{
		name: "基础管理",
		items: [
			{
				title: "sys.nav.usermgmt.system.system_config",
				path: "/admin/system",
				icon: <Icon icon="local:ic-setting" size={24} />,
				auth: SYSADMIN_ROLES,
				children: [
					{
						title: "sys.nav.usermgmt.system.ops",
						path: "/admin/ops",
						icon: <Icon icon="solar:settings-bold-duotone" size={18} />,
						auth: SYSADMIN_ROLES,
					},
					{
						title: "数据湖配置",
						path: "/admin/data-lake",
						icon: <Icon icon="local:ic-management" size={18} />,
						auth: SYSADMIN_ROLES,
					},
					{
						title: "集成设置",
						path: "/admin/infra-settings",
						icon: <Icon icon="local:ic-setting" size={18} />,
						auth: SYSADMIN_ROLES,
					},
					{
						title: "模型发布治理",
						path: "/admin/model-governance-policy",
						icon: <Icon icon="local:ic-setting" size={18} />,
						auth: SYSADMIN_ROLES,
					},
					{
						title: "工作流配置",
						path: "/admin/workflows",
						icon: <Icon icon="solar:shuffle-bold-duotone" size={18} />,
						auth: SYSADMIN_ROLES,
					},
				],
			},
			{
				title: "任务审批",
				path: "/admin/approval",
				icon: <Icon icon="local:ic-approval" size={24} />,
				auth: AUTHADMIN_ROLES,
			},
			{
				title: "日志审计",
				path: "/admin/audit",
				icon: <Icon icon="local:ic-audit" size={24} />,
				auth: AUDIT_ALLOWED_ROLES,
			},
		],
	},
];
