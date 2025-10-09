import type { Menu, MenuTree } from "#/entity";
import apiClient from "../apiClient";
import { useMenuStore } from "@/store/menuStore";
import { PORTAL_NAV_SECTIONS } from "@/constants/portal-navigation";

export enum MenuApi {
  // Platform backend exposes adapted menu endpoints
  Menu = "/menu",
  MenuTree = "/menu/tree",
}

const getMenuList = async () => {
  const data = await apiClient.get<Menu[]>({ url: MenuApi.Menu });
  // Normalize to tree for consumers
  const tree = Array.isArray(data) && data.length > 0 ? (data as any) : toMenuTreeFallback();
  useMenuStore.getState().setMenus(tree as any);
  return data;
};

const getMenuTree = async () => {
  const data = await apiClient.get<MenuTree[]>({ url: MenuApi.MenuTree });
  const tree = Array.isArray(data) && data.length > 0 ? (data as any) : toMenuTreeFallback();
  useMenuStore.getState().setMenus(tree as any);
  try {
    // eslint-disable-next-line no-console
    console.log("[menuService] /api/menu/tree response:", Array.isArray(data) ? data.length : typeof data);
  } catch {}
  return data;
};

function toMenuTreeFallback(): MenuTree[] {
  // Build minimal MenuTree[] from static portal navigation definition
  const sections = PORTAL_NAV_SECTIONS.map((sec) => ({
    id: sec.key,
    parentId: "",
    name: sec.titleKey,
    code: sec.key,
    order: 0,
    type: 1 as any,
    path: `/${sec.path}`,
    icon: sec.icon,
    caption: undefined,
    info: undefined,
    disabled: false,
    auth: undefined,
    hidden: false,
    component: undefined,
    children: sec.children.map((c) => ({
      id: `${sec.key}:${c.key}`,
      parentId: sec.key,
      name: c.titleKey,
      code: c.key,
      order: 0,
      type: 2 as any,
      path: `/${sec.path}/${c.path}`,
      icon: undefined,
      caption: undefined,
      info: undefined,
      disabled: false,
      auth: undefined,
      hidden: false,
      component: undefined,
      children: [],
    })),
  })) as unknown as MenuTree[];
  return sections;
}

export default {
	getMenuList,
  getMenuTree,
};
