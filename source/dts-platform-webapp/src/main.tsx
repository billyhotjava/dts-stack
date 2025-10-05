import "./global.css";
import "./theme/theme.css";
import "./locales/i18n";
import ReactDOM from "react-dom/client";
import { createBrowserRouter, createHashRouter, Outlet, RouterProvider } from "react-router";
import App from "./App";
import menuService from "./api/services/menuService";
import useUserStore from "./store/userStore";
import { registerLocalIcons } from "./components/icon";
import { GLOBAL_CONFIG } from "./global-config";
import ErrorBoundary from "./routes/components/error-boundary";
import { makeRoutesSection } from "./routes/sections";

await registerLocalIcons();

// MSW mock removed

if (GLOBAL_CONFIG.routerMode === "backend") {
    // Only prefetch backend menus when an access token is present to avoid 401 during app bootstrap
    const token = useUserStore.getState().userToken?.accessToken;
    if (token) {
        try {
            const tree = await menuService.getMenuTree();
            // eslint-disable-next-line no-console
            console.log("[main] Loaded backend menu tree:", tree);
        } catch (e) {
            // eslint-disable-next-line no-console
            console.warn("Failed to load backend menus, continuing with empty menu:", e);
        }
    }
}

const makeRouter = GLOBAL_CONFIG.routerHistory === "hash" ? createHashRouter : createBrowserRouter;
const router = makeRouter(
	[
		{
			Component: () => (
				<App>
					<Outlet />
				</App>
			),
			errorElement: <ErrorBoundary />,
			children: makeRoutesSection(),
		},
	],
	{
		basename: GLOBAL_CONFIG.publicPath,
	},
);

const root = ReactDOM.createRoot(document.getElementById("root") as HTMLElement);
root.render(<RouterProvider router={router} />);
