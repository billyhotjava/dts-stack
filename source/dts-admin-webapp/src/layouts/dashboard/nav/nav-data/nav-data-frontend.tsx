import { Icon } from "@/components/icon";
import type { NavProps } from "@/components/nav";

const SYSADMIN_ROLES = ["ROLE_SYS_ADMIN", "SYSADMIN"];
const AUTHADMIN_ROLES = ["ROLE_AUTH_ADMIN", "AUTHADMIN"];
const AUDITADMIN_ROLES = ["ROLE_SECURITY_AUDITOR", "AUDITADMIN"];
const AUDIT_ALLOWED_ROLES = Array.from(new Set([...AUDITADMIN_ROLES, ...AUTHADMIN_ROLES]));

export const frontendNavData: NavProps["data"] = [
	{
		items: [
			{
				title: "sys.nav.usermgmt.system.my_changes",
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
			{
				title: "sys.nav.usermgmt.system.group",
				path: "/admin/orgs",
				icon: <Icon icon="local:ic-orgs" size={24} />,
				auth: SYSADMIN_ROLES,
			},
			{
				title: "sys.nav.usermgmt.system.system_config",
				path: "/admin/system/data-sources",
				icon: <Icon icon="local:ic-setting" size={24} />,
				auth: SYSADMIN_ROLES,
				children: [
					{
						title: "sys.nav.usermgmt.system.datasource",
						path: "/admin/system/data-sources",
						icon: <Icon icon="local:ic-analysis" size={18} />,
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
