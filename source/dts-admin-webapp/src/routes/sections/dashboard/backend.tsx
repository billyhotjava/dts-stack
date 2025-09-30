import type { RouteObject } from "react-router";
import { Navigate } from "react-router";
import type { PortalMenuItem } from "@/admin/types";
import { getPortalMenus } from "@/store/portalMenuStore";
import type { MenuMetaInfo, MenuTree } from "@/types/entity";
import { PermissionType } from "@/types/enum";
import { convertFlatToTree } from "@/utils/tree";
import { Component } from "./utils";

/**
 * get route path from menu path and parent path
 * @param menuPath '/a/b/c'
 * @param parentPath '/a/b'
 * @returns '/c'
 *
 * @example
 * getRoutePath('/a/b/c', '/a/b') // '/c'
 */
const getRoutePath = (menuPath?: string, parentPath?: string) => {
	const menuPathArr = menuPath?.split("/").filter(Boolean) || [];
	const parentPathArr = parentPath?.split("/").filter(Boolean) || [];

	// remove parentPath items from menuPath
	const result = menuPathArr.slice(parentPathArr.length).join("/");
	return result;
};

/**
 * generate props for menu component
 * @param metaInfo
 * @returns
 */
const generateProps = (metaInfo: MenuMetaInfo) => {
	const props: any = {};
	if (metaInfo.externalLink) {
		props.src = metaInfo.externalLink?.toString() || "";
	}
	return props;
};

/**
 * convert menu to route
 * @param items
 * @param parent
 * @returns
 */
const convertToRoute = (items: MenuTree[], parent?: MenuTree): RouteObject[] => {
	const routes: RouteObject[] = [];

	const processItem = (item: MenuTree) => {
		// if group, process children
		if (item.type === PermissionType.GROUP) {
			for (const child of item.children || []) {
				processItem(child);
			}
		}

		// if catalogue, process Dhildren
		if (item.type === PermissionType.CATALOGUE) {
			const children = item.children || [];
			if (children.length > 0) {
				const firstChild = children[0];
				if (firstChild.path) {
					routes.push({
						path: getRoutePath(item.path, parent?.path),
						children: [
							{
								index: true,
								element: <Navigate to={getRoutePath(firstChild.path, item.path)} replace />,
							},
							...convertToRoute(children, item),
						],
					});
				}
			}
		}

		// if menu, create route
		if (item.type === PermissionType.MENU) {
			const props = generateProps(item);

			routes.push({
				path: getRoutePath(item.path, parent?.path),
				element: Component(item.component, props),
			});
		}
	};

	for (const item of items) {
		processItem(item);
	}
	return routes;
};

export function getBackendDashboardRoutes() {
    const pm = getPortalMenus();
    const tree = mapPortalMenusToMenuTree(pm);
    return convertToRoute(convertFlatToTree(tree));
}

function mapPortalMenusToMenuTree(items: PortalMenuItem[]): MenuTree[] {
    const parseMeta = (metadata?: string): { icon?: string } | undefined => {
        if (!metadata) return undefined;
        try { return JSON.parse(metadata) as { icon?: string }; } catch { return undefined; }
    };
    const walk = (nodes: PortalMenuItem[], parent?: PortalMenuItem): MenuTree[] => {
        return (nodes || []).map((node) => {
            const meta = parseMeta(node.metadata);
            const hasChildren = Array.isArray(node.children) && node.children.length > 0;
            const n: MenuTree = {
                id: String(node.id ?? `${parent?.path || ""}/${node.path}`),
                parentId: parent ? String(parent.id ?? parent.path ?? parent?.name) : "",
                name: node.name,
                path: node.path || "",
                component: node.component || "",
                icon: meta?.icon,
                type: hasChildren ? 1 : 2,
                children: [],
            } as unknown as MenuTree;
            if (hasChildren) n.children = walk(node.children!, node);
            return n;
        });
    };
    return walk(items);
}
