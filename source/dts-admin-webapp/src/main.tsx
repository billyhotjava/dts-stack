import "./polyfills/legacy-browser";
import "./global.css";
import "./theme/theme.css";
import "./locales/i18n";
import ReactDOM from "react-dom/client";
import { createBrowserRouter, Outlet, RouterProvider } from "react-router";
import App from "./App";
import menuService from "./api/services/menuService";
import { prefetchPortalMenus } from "./bootstrap/portal-menu-loader";
import { shouldPrefetchPortalMenus } from "./bootstrap/portal-menu-prefetch";
import userStore from "./store/userStore";
import { registerLocalIcons } from "./components/icon";
import { GLOBAL_CONFIG } from "./global-config";
import ErrorBoundary from "./routes/components/error-boundary";
import { getRoutesSection } from "./routes/sections";

function canPrefetchPortalMenus() {
	if (typeof window === "undefined") {
		return false;
	}
	return shouldPrefetchPortalMenus(window.location.pathname, userStore.getState().userToken.accessToken);
}

await registerLocalIcons();

if (GLOBAL_CONFIG.routerMode === "backend") {
	await menuService.getMenuList();
	if (canPrefetchPortalMenus()) {
		await prefetchPortalMenus();
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

if (GLOBAL_CONFIG.routerMode !== "backend" && canPrefetchPortalMenus()) {
	void prefetchPortalMenus();
}
