import type { Menu, MenuTree } from "#/entity";
import apiClient from "../apiClient";
import { useMenuStore } from "@/store/menuStore";

export enum MenuApi {
  // Platform backend exposes adapted menu endpoints
  Menu = "/menu",
  MenuTree = "/menu/tree",
}

const getMenuList = async () => {
  const data = await apiClient.get<Menu[]>({ url: MenuApi.Menu });
  // Normalize to tree for consumers; empty array means无权限，保持空菜单
  const tree = Array.isArray(data) && data.length > 0 ? (data as any) : [];
  useMenuStore.getState().setMenus(tree as any);
  return data;
};

const getMenuTree = async () => {
  const data = await apiClient.get<MenuTree[]>({ url: MenuApi.MenuTree });
  const tree = Array.isArray(data) && data.length > 0 ? (data as any) : [];
  useMenuStore.getState().setMenus(tree as any);
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
