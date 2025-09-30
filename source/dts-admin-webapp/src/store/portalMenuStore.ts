import type { PortalMenuItem } from "@/admin/types";

let portalMenus: PortalMenuItem[] = [];

export function setPortalMenus(menus: PortalMenuItem[] | undefined) {
  portalMenus = Array.isArray(menus) ? menus : [];
}

export function getPortalMenus() {
  return portalMenus;
}

