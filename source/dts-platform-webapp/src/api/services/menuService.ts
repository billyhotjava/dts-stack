import type { Menu, MenuTree } from "#/entity";
import apiClient from "../apiClient";
import { useMenuStore } from "@/store/menuStore";
import { parseMenuMetadata, resolveMenuPath } from "@/utils/menuTree";

export enum MenuApi {
  // Platform backend exposes adapted menu endpoints
  Menu = "/menu",
  MenuTree = "/menu/tree",
}

const getMenuList = async () => {
  const data = await apiClient.get<Menu[]>({ url: MenuApi.Menu });
  // Normalize to tree for consumers; empty array means无权限，保持空菜单
  const treeRaw = Array.isArray(data) && data.length > 0 ? normalizeMenuTreePaths(data as any) : [];
  useMenuStore.getState().setMenus(treeRaw as any);
  return data;
};

const getMenuTree = async () => {
  const data = await apiClient.get<MenuTree[]>({ url: MenuApi.MenuTree });
  const treeRaw = Array.isArray(data) && data.length > 0 ? normalizeMenuTreePaths(data as any) : [];
  useMenuStore.getState().setMenus(treeRaw as any);
  try {
    // eslint-disable-next-line no-console
    console.log("[menuService] /api/menu/tree response:", Array.isArray(data) ? data.length : typeof data);
  } catch {}
  return data;
};

export default {
	getMenuList,
  getMenuTree,
};

function normalizeMenuTreePaths<T extends Menu | MenuTree>(items: T[], parentPath?: string): T[] {
	return items.map((item) => {
		const menuTreeItem = item as T & { children?: T[] };
		const meta = parseMenuMetadata((item as any).metadata);
		const resolvedPath = resolveMenuPath(item as any, meta, parentPath);
		const normalizedChildren = Array.isArray(menuTreeItem.children)
			? normalizeMenuTreePaths(menuTreeItem.children, resolvedPath || parentPath)
			: undefined;

		return {
			...item,
			path: resolvedPath || (item as any).path,
			children: normalizedChildren,
		};
	});
}
