import type { PortalMenuItem } from "@/admin/types";

let portalMenus: PortalMenuItem[] = [];
let fullMenuList: PortalMenuItem[] = [];

export function setPortalMenus(menus: PortalMenuItem[] | undefined, allMenus?: PortalMenuItem[]) {
  portalMenus = Array.isArray(menus) ? menus : [];
  fullMenuList = Array.isArray(allMenus) ? allMenus : [];
}

export function getPortalMenus() {
  return portalMenus;
}

export function getAllPortalMenuItems() {
  return fullMenuList;
}
