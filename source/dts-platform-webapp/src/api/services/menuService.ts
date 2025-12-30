import type { Menu, MenuTree } from "#/entity";
import { PermissionType } from "#/enum";
import apiClient from "../apiClient";
import { useMenuStore } from "@/store/menuStore";
import { parseMenuMetadata, resolveMenuPath } from "@/utils/menuTree";

export enum MenuApi {
  // Platform backend exposes adapted menu endpoints
  Menu = "/menu",
  MenuTree = "/menu/tree",
}

const hasAnalyticsMenu = (menus: MenuTree[]): boolean => {
  const stack = Array.isArray(menus) ? [...menus] : [];
  while (stack.length) {
    const node = stack.pop();
    if (!node) continue;
    const meta = parseMenuMetadata((node as any).metadata);
    const path = resolveMenuPath(node as any, meta as any);
    if (path?.startsWith("/analytics")) {
      return true;
    }
    if (Array.isArray((node as any).children)) {
      for (const child of (node as any).children as MenuTree[]) {
        stack.push(child);
      }
    }
  }
  return false;
};

const findFirstMenuByCode = (menus: MenuTree[], codes: string[]): MenuTree | null => {
  const wanted = new Set(codes.map((c) => c.toLowerCase()));
  const queue = Array.isArray(menus) ? [...menus] : [];
  while (queue.length) {
    const node = queue.shift();
    if (!node) continue;
    const code = typeof (node as any).code === "string" ? (node as any).code.toLowerCase() : "";
    if (code && wanted.has(code)) {
      return node as any;
    }
    if (Array.isArray((node as any).children)) {
      queue.push(...((node as any).children as MenuTree[]));
    }
  }
  return null;
};

const ensureAnalyticsMenu = (menus: MenuTree[]): MenuTree[] => {
  if (!Array.isArray(menus) || menus.length === 0) {
    return menus;
  }
  if (hasAnalyticsMenu(menus)) {
    return menus;
  }

  const analyticsMenu: MenuTree = {
    id: "analytics",
    parentId: "0",
    name: "自助分析",
    displayName: "自助分析",
    code: "analytics",
    order: 10_000,
    type: PermissionType.MENU,
    path: "/analytics/",
    icon: "local:ic-analysis",
  };

  const cloned = structuredClone(menus) as MenuTree[];
  const exploreRoot = findFirstMenuByCode(cloned, ["explore", "analysis", "bi"]);
  if (exploreRoot) {
    exploreRoot.children = Array.isArray(exploreRoot.children) ? exploreRoot.children : [];
    exploreRoot.children.push({
      ...analyticsMenu,
      parentId: exploreRoot.id,
    });
    return cloned;
  }

  cloned.push(analyticsMenu);
  return cloned;
};

const getMenuList = async () => {
  const data = await apiClient.get<Menu[]>({ url: MenuApi.Menu });
  // Normalize to tree for consumers; empty array means无权限，保持空菜单
  const treeRaw = Array.isArray(data) && data.length > 0 ? (data as any) : [];
  const tree = ensureAnalyticsMenu(treeRaw as MenuTree[]);
  useMenuStore.getState().setMenus(tree as any);
  return data;
};

const getMenuTree = async () => {
  const data = await apiClient.get<MenuTree[]>({ url: MenuApi.MenuTree });
  const treeRaw = Array.isArray(data) && data.length > 0 ? (data as any) : [];
  const tree = ensureAnalyticsMenu(treeRaw as MenuTree[]);
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
