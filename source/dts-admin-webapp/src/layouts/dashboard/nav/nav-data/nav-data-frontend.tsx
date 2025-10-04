import { Icon } from "@/components/icon";
import type { NavProps } from "@/components/nav";

const SYSADMIN_ROLES = ["ROLE_SYS_ADMIN", "SYSADMIN", "OPADMIN"];
const AUTHADMIN_ROLES = ["ROLE_AUTH_ADMIN", "AUTHADMIN"];
const AUDITADMIN_ROLES = ["ROLE_AUDITOR_ADMIN", "AUDITADMIN"];

export const frontendNavData: NavProps["data"] = [
	{
		//name: "sys.nav.dashboard",
		items: [
			{
				title: "sys.nav.workbench",
				path: "/workbench",
				icon: <Icon icon="local:ic-workbench" size="24" />,
			},
		],
	},
	{
		//name: "sys.nav.mgmtpages",
		items: [
				{
					title: "sys.nav.management",
					path: "/admin",
					icon: <Icon icon="local:ic-management" size="24" />,
					children: [
						{
							title: "sys.nav.usermgmt.system.user",
							path: "/admin/users",
							icon: <Icon icon="local:ic-users" size={20} />,
							auth: SYSADMIN_ROLES,
						},
						{
							title: "sys.nav.usermgmt.system.permission",
							path: "/admin/portal-menus",
							icon: <Icon icon="local:ic-menu" size={20} />,
							auth: SYSADMIN_ROLES,
						},
						{
							title: "sys.nav.usermgmt.system.role",
							path: "/admin/roles",
							icon: <Icon icon="local:ic-roles" size={20} />,
							auth: SYSADMIN_ROLES,
						},
						{
							title: "sys.nav.usermgmt.system.group",
							path: "/admin/orgs",
							icon: <Icon icon="local:ic-orgs" size={20} />,
							auth: SYSADMIN_ROLES,
						},
						{
							title: "sys.nav.usermgmt.system.my_changes",
							path: "/admin/my-changes",
							icon: <Icon icon="local:ic-mail" size={20} />,
							auth: SYSADMIN_ROLES,
						},
						{
							title: "sys.nav.usermgmt.system.approval",
							path: "/admin/approval",
							icon: <Icon icon="local:ic-approval" size={20} />,
							auth: AUTHADMIN_ROLES,
						},
						// Removed: My changes
						{
							title: "sys.nav.usermgmt.system.audit_log",
							path: "/admin/audit",
							icon: <Icon icon="local:ic-audit" size={20} />,
							auth: [...AUDITADMIN_ROLES, ...AUTHADMIN_ROLES],
						},
					],
				},
		],
	},
];
