import { Icon } from "@/components/icon";
import type { NavItemDataProps, NavProps } from "@/components/nav";
import type { MenuTree } from "@/types/entity";
import { Badge } from "@/ui/badge";
import { convertFlatToTree } from "@/utils/tree";
import { getPortalMenus } from "@/store/portalMenuStore";
import type { PortalMenuItem } from "@/admin/types";

const convertChildren = (children?: MenuTree[]): NavItemDataProps[] => {
	if (!children?.length) return [];

	return children.map((child) => ({
		title: child.name,
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
		name: item.name,
		items: convertChildren(item.children),
	}));
};

function mapPortalMenusToMenuTree(items: PortalMenuItem[]): MenuTree[] {
	const parseMeta = (metadata?: string): Record<string, any> | undefined => {
		if (!metadata) return undefined;
		try {
			const parsed = JSON.parse(metadata);
			// Handle double-encoded JSON string
			if (typeof parsed === "string") {
				try {
					return JSON.parse(parsed) as Record<string, any>;
				} catch {
					return undefined;
				}
			}
			return parsed as Record<string, any>;
		} catch {
			return undefined;
		}
	};
	const resolveExternalLink = (meta: Record<string, any> | undefined): string | undefined => {
		if (!meta) return undefined;
		const external = meta.externalLink ?? meta.external_link ?? meta.url ?? meta.href ?? meta.link ?? meta.src;
		return typeof external === "string" && external.trim() ? external.trim() : undefined;
	};
	const walk = (nodes: PortalMenuItem[], parent?: PortalMenuItem): MenuTree[] => {
		return (nodes || []).map((node) => {
			const meta = parseMeta(node.metadata);
			const hasChildren = Array.isArray(node.children) && node.children.length > 0;
			const externalLink = resolveExternalLink(meta);
			const n: MenuTree = {
				id: String(node.id ?? `${parent?.path || ""}/${node.path}`),
				parentId: parent ? String(parent.id ?? parent.path ?? parent.name) : "",
				name: node.displayName ?? node.name,
				path: externalLink || node.path || "",
				component: node.component || "",
				icon: node.icon ?? meta?.icon,
				externalLink: externalLink as unknown as URL,
				type: hasChildren ? 1 : 2, // CATALOGUE:1, MENU:2 (match enum values in app)
				children: [],
			} as unknown as MenuTree;
			if (hasChildren) n.children = walk(node.children!, node);
			return n;
		});
	};
	return walk(items);
}

export const getBackendNavData = (): NavProps["data"] => {
	const portalMenus = getPortalMenus();
	const tree = mapPortalMenusToMenuTree(portalMenus);
	return convert(convertFlatToTree(tree));
};
