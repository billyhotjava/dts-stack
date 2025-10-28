import { clone, concat } from "ramda";
import { Suspense } from "react";
import { Outlet, ScrollRestoration, useLocation } from "react-router";
import { AuthGuard } from "@/components/auth/auth-guard";
import { LineLoading } from "@/components/loading";
import { GLOBAL_CONFIG } from "@/global-config";
import Page403 from "@/pages/sys/error/Page403";
import { useSettings } from "@/store/settingStore";
import { cn } from "@/utils";
import { flattenTrees } from "@/utils/tree";
import { getBackendNavData } from "./nav/nav-data/nav-data-backend";
import { frontendNavData } from "./nav/nav-data/nav-data-frontend";

/**
 * find auth by path
 * @param path
 * @returns
 */
function findAuthByPath(path: string, items: any[]): string[] {
	const foundItem = items.find((item) => item.path === path);
	return foundItem?.auth || [];
}

const Main = () => {
	const { themeStretch } = useSettings();

	const { pathname } = useLocation();
	const navData = GLOBAL_CONFIG.routerMode === "frontend" ? clone(frontendNavData) : getBackendNavData();
	const allItems = navData.reduce((acc: any[], group) => {
		const flattenedItems = flattenTrees(group.items);
		return concat(acc, flattenedItems);
	}, []);
	const currentNavAuth = findAuthByPath(pathname, allItems);

	return (
		<AuthGuard checkAny={currentNavAuth} fallback={<Page403 />}>
			<main
				data-slot="slash-layout-main"
				className={cn(
					"flex-auto w-full flex flex-col text-sm",
					"transition-[max-width] duration-300 ease-in-out",
					"px-4 sm:px-6 py-4 sm:py-6 md:px-8 mx-auto",
					{
						"max-w-full": themeStretch,
						"xl:max-w-screen-2xl": !themeStretch,
					},
				)}
				style={{
					willChange: "max-width",
				}}
			>
				<Suspense fallback={<LineLoading />}>
					<Outlet />
					<ScrollRestoration />
				</Suspense>
			</main>
		</AuthGuard>
	);
};

export default Main;
