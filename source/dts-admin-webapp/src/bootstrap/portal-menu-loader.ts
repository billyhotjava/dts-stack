import { adminApi } from "@/admin/api/adminApi";
import { setPortalMenus } from "@/store/portalMenuStore";

export function applyPortalMenus(menus: {
	menus?: unknown[];
	allMenus?: unknown[];
} | null | undefined) {
	setPortalMenus(menus?.menus ?? [], menus?.allMenus ?? menus?.menus ?? []);
}

export async function prefetchPortalMenus() {
	try {
		const menus = await adminApi.getPortalMenus();
		applyPortalMenus(menus);
	} catch (e) {
		// eslint-disable-next-line no-console
		console.warn("[portal-menus] Failed to prefetch portal menus:", e);
		setPortalMenus([]);
	}
}
