import "./polyfills/legacy-browser";
import "./global.css";
import "./theme/theme.css";
import "./locales/i18n";
import ReactDOM from "react-dom/client";
import { createBrowserRouter, Outlet, RouterProvider } from "react-router";
import App from "./App";
import menuService from "./api/services/menuService";
import { adminApi } from "./admin/api/adminApi";
import { setPortalMenus } from "./store/portalMenuStore";
import { registerLocalIcons } from "./components/icon";
import { GLOBAL_CONFIG } from "./global-config";
import ErrorBoundary from "./routes/components/error-boundary";
import { getRoutesSection } from "./routes/sections";

await registerLocalIcons();

if (GLOBAL_CONFIG.routerMode === "backend") {
	await menuService.getMenuList();
	try {
		const menus = await adminApi.getPortalMenus();
		setPortalMenus(menus?.menus ?? [], menus?.allMenus ?? menus?.menus ?? []);
	} catch (e) {
		// eslint-disable-next-line no-console
		console.warn("[main] Failed to prefetch portal menus:", e);
		setPortalMenus([]);
	}
}

const router = createBrowserRouter(
	[
		{
			Component: () => (
				<App>
					<Outlet />
				</App>
			),
			errorElement: <ErrorBoundary />,
			children: getRoutesSection(),
		},
	],
	{
		basename: GLOBAL_CONFIG.publicPath,
	},
);

const root = ReactDOM.createRoot(document.getElementById("root") as HTMLElement);
root.render(<RouterProvider router={router} />);
