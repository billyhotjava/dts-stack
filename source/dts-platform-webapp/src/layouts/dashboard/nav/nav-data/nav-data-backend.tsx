import { Icon } from "@/components/icon";
import type { NavItemDataProps, NavProps } from "@/components/nav";
import type { MenuTree } from "@/types/entity";
import { Badge } from "@/ui/badge";
import { convertFlatToTree } from "@/utils/tree";
import { getMenus } from "@/store/menuStore";
// Note: platform-webapp no longer injects any admin/system menu locally.
// All navigation items come from backend menus (dts-platform via dts-admin) or the static frontend config.

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
	return convert(tree);
};

const hasChildren = (items: MenuTree[]): boolean => items.some((item) => Array.isArray(item.children) && item.children.length > 0);
