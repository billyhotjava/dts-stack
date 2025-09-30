import type { Menu, MenuTree } from "#/entity";
import apiClient from "../apiClient";
import { useMenuStore } from "@/store/menuStore";

export enum MenuApi {
	Menu = "/menu",
  MenuTree = "/menu/tree",
}

const getMenuList = async () => {
	const data = await apiClient.get<Menu[]>({ url: MenuApi.Menu });
	// Store menus for runtime consumption (nav/routes)
	useMenuStore.getState().setMenus(data as any);
	return data;
};

const getMenuTree = async () => {
  const data = await apiClient.get<MenuTree[]>({ url: MenuApi.MenuTree });
  // Persist into store as tree structure
  useMenuStore.getState().setMenus(data);
  // Print to console for verification
  // Note: production builds may drop console.* per vite config
  try {
    // Stringify with limited depth to avoid circular refs
    // eslint-disable-next-line no-console
    console.log("[menuService] /api/menu/tree response:", data);
  } catch {}
  return data;
};

export default {
	getMenuList,
  getMenuTree,
};
