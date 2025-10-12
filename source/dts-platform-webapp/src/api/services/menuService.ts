import type { Menu, MenuTree } from "#/entity";
import apiClient from "../apiClient";
import { useMenuStore } from "@/store/menuStore";
import { PORTAL_NAV_SECTIONS } from "@/constants/portal-navigation";
import { useUserInfo } from "@/store/userStore";

export enum MenuApi {
  // Platform backend exposes adapted menu endpoints
  Menu = "/menu",
  MenuTree = "/menu/tree",
}

const getMenuList = async () => {
  const data = await apiClient.get<Menu[]>({ url: MenuApi.Menu });
  // Normalize to tree for consumers
  const tree = Array.isArray(data) && data.length > 0 ? (data as any) : toMenuTreeFallback();
  // Ensure foundation section appears disabled for non-OP_ADMIN users
  const processed = ensureFoundationDisabled(tree as any);
  useMenuStore.getState().setMenus(processed as any);
  return data;
};

const getMenuTree = async () => {
  const data = await apiClient.get<MenuTree[]>({ url: MenuApi.MenuTree });
  const tree = Array.isArray(data) && data.length > 0 ? (data as any) : toMenuTreeFallback();
  const processed = ensureFoundationDisabled(tree as any);
  useMenuStore.getState().setMenus(processed as any);
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

// Helpers
function hasOpAdmin(): boolean {
  try {
    const roles = useUserInfo()?.roles || [];
    const set = new Set<string>((Array.isArray(roles) ? roles : [] as any[]).map((r) => String(r || "").toUpperCase()));
    if (set.has("OPADMIN")) return true;
    if (set.has("ROLE_OP_ADMIN")) return true;
    return false;
  } catch {
    return false;
  }
}

function ensureFoundationDisabled(tree: MenuTree[]): MenuTree[] {
  const op = hasOpAdmin();
  const withDisabled = (nodes: MenuTree[]): MenuTree[] =>
    (nodes || []).map((n) => ({
      ...n,
      disabled: isFoundationRoot(n) ? !op : n.disabled,
      children: n.children ? withDisabled(n.children) : [],
    }));

  let found = false;
  const updated = (tree || []).map((n) => {
    if (isFoundationRoot(n)) {
      found = true;
      return {
        ...n,
        disabled: !op,
        children: (n.children || []).map((c) => ({ ...c, disabled: !op })),
      };
    }
    return n;
  });

  if (found) return updated;

  // Not present from backend (likely filtered by admin). Append a disabled foundation section for UX consistency.
  const foundation = PORTAL_NAV_SECTIONS.find((s) => s.key === "foundation");
  if (!foundation) return updated;
  const foundationNode: MenuTree = {
    id: foundation.key,
    parentId: "",
    name: foundation.titleKey,
    code: foundation.key,
    order: 0,
    type: 1 as any,
    path: `/${foundation.path}`,
    icon: foundation.icon,
    caption: undefined,
    info: undefined,
    disabled: !op,
    auth: undefined,
    hidden: false,
    component: undefined,
    children: foundation.children.map((c) => ({
      id: `${foundation.key}:${c.key}`,
      parentId: foundation.key,
      name: c.titleKey,
      code: c.key,
      order: 0,
      type: 2 as any,
      path: `/${foundation.path}/${c.path}`,
      icon: undefined,
      caption: undefined,
      info: undefined,
      disabled: !op,
      auth: undefined,
      hidden: false,
      component: undefined,
      children: [],
    })),
  } as unknown as MenuTree;

  return [...updated, foundationNode];
}

function isFoundationRoot(node: MenuTree): boolean {
  const path = String((node as any)?.path || "").trim();
  if (path === "/foundation" || path.startsWith("/foundation/")) return true;
  // Try metadata.sectionKey when available
  const metadata = String((node as any)?.metadata || "");
  if (metadata && metadata.includes("\"sectionKey\":\"foundation\"")) return true;
  // Or code/name hints
  const name = String((node as any)?.name || "").toLowerCase();
  const code = String((node as any)?.code || "").toLowerCase();
  return name.includes("foundation") || code.includes("foundation");
}
