import { Icon } from "@/components/icon";
import type { NavItemDataProps, NavProps } from "@/components/nav";
import type { MenuTree } from "@/types/entity";
import { Badge } from "@/ui/badge";
import { convertFlatToTree } from "@/utils/tree";
import { getMenus } from "@/store/menuStore";
import useUserStore from "@/store/userStore";

const ADMIN_ROLE_CODES = new Set(["ROLE_OP_ADMIN", "ROLE_SYS_ADMIN"]);

const hasAdminPrivileges = () => {
	const roles = useUserStore.getState().userInfo.roles || [];
	return roles.some((role: any) => {
		const value = typeof role === "string" ? role : role?.code;
		return value ? ADMIN_ROLE_CODES.has(value.toString().toUpperCase()) : false;
	});
};

const createAdminNavData = (): NavProps["data"] => [
	{
		name: "系统管理",
		items: [
			{
				title: "用户管理",
				path: "/management/system/user",
				icon: <Icon icon="solar:users-group-outline" size="24" />,
			},
			{
				title: "角色管理",
				path: "/management/system/role",
				icon: <Icon icon="solar:shield-user-bold" size="24" />,
			},
			{
				title: "组织机构",
				path: "/management/system/group",
				icon: <Icon icon="solar:buildings-outline" size="24" />,
			},
			{
				title: "菜单管理",
				path: "/management/system/permission",
				icon: <Icon icon="solar:list-bold" size="24" />,
			},
			{
				title: "任务审批",
				path: "/management/system/approval",
				icon: <Icon icon="solar:verify-outline" size="24" />,
			},
			{
				title: "审计日志",
				path: "/management/system/auditlog",
				icon: <Icon icon="solar:history-bold" size="24" />,
			},
		],
	},
];

const convertChildren = (children?: MenuTree[]): NavItemDataProps[] => {
	if (!children?.length) return [];

	return children.map((child) => ({
		title: child.displayName || child.name,
		path: child.path || "",
		icon: child.icon ? typeof child.icon === "string" ? <Icon icon={child.icon} size="24" /> : child.icon : null,
		caption: child.caption,
		info: child.info ? <Badge variant="default">{child.info}</Badge> : null,
		disabled: child.disabled,
		externalLink: child.externalLink,
		auth: child.auth,
		hidden: child.hidden,
		children: convertChildren(child.children),
	}));
};

const convert = (menuTree: MenuTree[]): NavProps["data"] => {
	return menuTree.map((item) => ({
		name: item.displayName || item.name,
		items: convertChildren(item.children),
	}));
};

export const getBackendNavData = (): NavProps["data"] => {
	const source = getMenus();
	const tree = hasChildren(source) ? source : convertFlatToTree(source);
	const data = convert(tree);
	if (hasAdminPrivileges()) {
		// avoid duplicating section if already present
		const hasSystemSection = data.some((section) => section.name === "系统管理");
		if (!hasSystemSection) {
			data.push(...createAdminNavData());
		}
	}
	return data;
};

const hasChildren = (items: MenuTree[]): boolean => items.some((item) => Array.isArray(item.children) && item.children.length > 0);
