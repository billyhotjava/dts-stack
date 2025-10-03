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
				path: "/management",
				icon: <Icon icon="local:ic-management" size="24" />,
				children: [
					{
						title: "sys.nav.usermgmt.system.user",
						path: "/management/system/user",
						auth: SYSADMIN_ROLES,
					},
					{
						title: "sys.nav.usermgmt.system.permission",
						path: "/management/system/menu",
						auth: SYSADMIN_ROLES,
					},
					{
						title: "sys.nav.usermgmt.system.role",
						path: "/management/system/role",
						auth: SYSADMIN_ROLES,
					},
					{
						title: "sys.nav.usermgmt.system.group",
						path: "/management/system/group",
						auth: SYSADMIN_ROLES,
					},
					{
						title: "sys.nav.usermgmt.system.approval",
						path: "/management/system/approval",
						auth: AUTHADMIN_ROLES,
					},
					{
						title: "sys.nav.usermgmt.system.my_changes",
						path: "/management/system/my-changes",
						auth: SYSADMIN_ROLES,
					},
					{
						title: "sys.nav.usermgmt.system.audit_log",
						path: "/management/system/audit-log",
						auth: [...AUDITADMIN_ROLES, ...AUTHADMIN_ROLES],
					},
				],
			},
		],
	},
];
