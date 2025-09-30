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
import { urlJoin } from "./utils";

await registerLocalIcons();

// Disable MSW by default; only enable if explicitly VITE_ENABLE_MOCK="true"
const shouldEnableMock = import.meta.env.VITE_ENABLE_MOCK === "true";

// In production build, MSW is not bundled unless explicitly enabled via VITE_ENABLE_MOCK.
// Use dynamic import so that prod bundle tree-shakes mock code by default.
if (import.meta.env.DEV && shouldEnableMock) {
	const { worker } = await import("./_mock");
	await worker.start({
		onUnhandledRequest: "bypass",
		serviceWorker: { url: urlJoin(GLOBAL_CONFIG.publicPath, "mockServiceWorker.js") },
	});
}

if (GLOBAL_CONFIG.routerMode === "backend") {
    await menuService.getMenuList();
    try {
        const menus = await adminApi.getPortalMenus();
        setPortalMenus(menus || []);
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
