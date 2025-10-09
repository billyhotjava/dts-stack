import type { Menu, MenuTree } from "#/entity";
import axios from "axios";
import { GLOBAL_CONFIG } from "@/global-config";
import { useMenuStore } from "@/store/menuStore";
import userStore from "@/store/userStore";
import { PORTAL_NAV_SECTIONS } from "@/constants/portal-navigation";

export enum MenuApi {
  // Admin service endpoints; always prefix with GLOBAL_CONFIG.adminApiBaseUrl
  Menu = "/menu",
  // Legacy/compat path in some older admin builds
  MenuTreeLegacy = "/menu/tree",
}

function buildAudienceQuery(): string {
  try {
    const rolesRaw = (userStore.getState().userInfo as any)?.roles as unknown;
    const roles: string[] = Array.isArray(rolesRaw)
      ? (rolesRaw as any[]).map((r) => (typeof r === "string" ? r : String((r as any)?.code ?? (r as any)?.name ?? ""))).filter(Boolean)
      : [];
    const norm = Array.from(
      new Set(
        roles
          .map((r) => String(r || "").trim())
          .filter(Boolean)
          .map((r) => r.toUpperCase())
          .map((r) => (r.startsWith("ROLE_") ? r : `ROLE_${r}`))
      )
    );
    const qs = norm.map((r) => `roles=${encodeURIComponent(r)}`).join("&");
    return qs ? `?${qs}` : "";
  } catch {
    return "";
  }
}

const getMenuList = async () => {
  const base = GLOBAL_CONFIG.adminApiBaseUrl.replace(/\/+$/, "");
  const qs = buildAudienceQuery();
  const primary = `${base}${MenuApi.Menu}${qs}`;
  const legacy = `${base}${MenuApi.MenuTreeLegacy}${qs}`;
  let list: any[] = [];
  try {
    const { data } = await axios.get<any>(primary, { withCredentials: false });
    list = Array.isArray(data) ? (data as any) : Array.isArray((data as any)?.data) ? (data as any).data : [];
  } catch (e) {
    try {
      const { data } = await axios.get<any>(legacy, { withCredentials: false });
      list = Array.isArray(data) ? (data as any) : Array.isArray((data as any)?.data) ? (data as any).data : [];
    } catch {
      list = [];
    }
  }
  const tree: MenuTree[] = Array.isArray(list) && list.length > 0 ? (list as any) : toMenuTreeFallback();
  useMenuStore.getState().setMenus(tree as any);
  return list as Menu[];
};

const getMenuTree = async () => {
  const base = GLOBAL_CONFIG.adminApiBaseUrl.replace(/\/+$/, "");
  const qs = buildAudienceQuery();
  const primary = `${base}${MenuApi.Menu}${qs}`;
  const legacy = `${base}${MenuApi.MenuTreeLegacy}${qs}`;
  let list: any[] = [];
  try {
    const { data } = await axios.get<any>(primary, { withCredentials: false });
    list = Array.isArray(data) ? (data as any) : Array.isArray((data as any)?.data) ? (data as any).data : [];
  } catch (e) {
    try {
      const { data } = await axios.get<any>(legacy, { withCredentials: false });
      list = Array.isArray(data) ? (data as any) : Array.isArray((data as any)?.data) ? (data as any).data : [];
    } catch {
      list = [];
    }
  }
  const tree: MenuTree[] = Array.isArray(list) && list.length > 0 ? (list as any) : toMenuTreeFallback();
  useMenuStore.getState().setMenus(tree as any);
  try {
    // eslint-disable-next-line no-console
    console.log("[menuService] admin /menu response:", Array.isArray(list) ? list.length : typeof list);
  } catch {}
  return list as MenuTree[];
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
