import type { Menu, MenuTree } from "#/entity";
import axios from "axios";
import { GLOBAL_CONFIG } from "@/global-config";
import { useMenuStore } from "@/store/menuStore";
import { convertFlatToTree } from "@/utils/tree";
import { PORTAL_NAV_SECTIONS } from "@/constants/portal-navigation";
import type { MenuTree } from "#/entity";

export enum MenuApi {
  // These are admin-service endpoints; always prefix with GLOBAL_CONFIG.adminApiBaseUrl
  Menu = "/menu",
  MenuTree = "/menu/tree",
}

const getMenuList = async () => {
    const base = GLOBAL_CONFIG.adminApiBaseUrl.replace(/\/+$/, "");
    const url = `${base}${MenuApi.Menu}`;
    const { data } = await axios.get<any>(url, { withCredentials: false });
    const list: Menu[] = Array.isArray(data) ? (data as any) : Array.isArray((data as any)?.data) ? (data as any).data : [];
    // Normalize flat list into tree structure for store consumers
    let tree = convertFlatToTree(list as any);
    if (!Array.isArray(tree) || tree.length === 0) {
      // Fallback: use static portal sections when backend returns empty
      tree = toMenuTreeFallback();
    }
    useMenuStore.getState().setMenus(tree as any);
    return list;
};

const getMenuTree = async () => {
  const base = GLOBAL_CONFIG.adminApiBaseUrl.replace(/\/+$/, "");
  const url = `${base}${MenuApi.MenuTree}`;
  const { data } = await axios.get<any>(url, { withCredentials: false });
  const list: MenuTree[] = Array.isArray(data) ? (data as any) : Array.isArray((data as any)?.data) ? (data as any).data : [];
  const tree = list.length ? list : toMenuTreeFallback();
  useMenuStore.getState().setMenus(tree as any);
  // Print to console for verification
  // Note: production builds may drop console.* per vite config
  try {
    // Stringify with limited depth to avoid circular refs
    // eslint-disable-next-line no-console
    console.log("[menuService] admin /menu/tree response:", list);
  } catch {}
  return list;
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
