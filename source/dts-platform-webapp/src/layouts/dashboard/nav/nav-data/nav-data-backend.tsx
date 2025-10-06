import type { ReactNode } from "react";
import { Icon } from "@/components/icon";
import type { NavItemDataProps, NavProps } from "@/components/nav";
import type { MenuTree } from "@/types/entity";
import { PORTAL_NAV_SECTIONS } from "@/constants/portal-navigation";
import { Badge } from "@/ui/badge";
import { convertFlatToTree } from "@/utils/tree";
import { getMenus } from "@/store/menuStore";
// Note: platform-webapp no longer injects any admin/system menu locally.
// All navigation items come from backend menus (dts-platform via dts-admin) or the static frontend config.

const sectionIconByKey = PORTAL_NAV_SECTIONS.reduce<Record<string, string>>((acc, section) => {
	acc[section.key] = section.icon;
	return acc;
}, {});

const sectionIconByPath = PORTAL_NAV_SECTIONS.reduce<Record<string, string>>((acc, section) => {
	const normalized = normalizePathSegment(section.path);
	acc[normalized] = section.icon;
	return acc;
}, {});

function normalizePathSegment(path?: string | null) {
	if (!path) return "";
	return String(path).replace(/^\/+/, "").split("/")[0] ?? "";
}

function resolveFallbackIcon(node: MenuTree): ReactNode {
	const candidateKeys = [normalizePathSegment(node.path), node.code, node.name].filter(Boolean);
	for (const key of candidateKeys) {
		if (!key) continue;
		const iconByPath = sectionIconByPath[key];
		if (iconByPath) {
			return <Icon icon={iconByPath} size="24" />;
		}
		const iconByKey = sectionIconByKey[key];
		if (iconByKey) {
			return <Icon icon={iconByKey} size="24" />;
		}
	}
	return <Icon icon="solar:menu-dots-bold-duotone" size="24" />;
}

const convertChildren = (children?: MenuTree[]): NavItemDataProps[] => {
	if (!children?.length) return [];

	return children.map((child) => ({
		title: child.displayName || child.name,
		path: child.path || "",
		icon: resolveChildIcon(child),
		caption: child.caption,
		info: child.info ? <Badge variant="default">{child.info}</Badge> : null,
		disabled: child.disabled,
		externalLink: child.externalLink,
		auth: child.auth,
		hidden: child.hidden,
		children: convertChildren(child.children),
	}));
};

function resolveChildIcon(child: MenuTree): ReactNode {
	if (child.icon) {
		return typeof child.icon === "string" ? <Icon icon={child.icon} size="24" /> : child.icon;
	}
	return resolveFallbackIcon(child);
}

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
