import "./polyfills/legacy-browser";
import "./global.css";
import "./theme/theme.css";
import "./locales/i18n";
import ReactDOM from "react-dom/client";
import { createBrowserRouter, createHashRouter, Outlet, RouterProvider } from "react-router";
import App from "./App";
import { registerLocalIcons } from "./components/icon";
import { GLOBAL_CONFIG } from "./global-config";
import ErrorBoundary from "./routes/components/error-boundary";
import { makeRoutesSection } from "./routes/sections";

if (import.meta.env.DEV) {
	import("./debug/register-koal-devtools").catch((error) => {
		// Dev-only optional tooling; never block the app bootstrap.
		console.warn("[devtools] register-koal-devtools failed to load", error);
	});
}

await registerLocalIcons();

// MSW mock removed

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
