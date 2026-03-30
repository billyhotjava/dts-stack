import "./polyfills/legacy-browser";
import "./global.css";
import "./theme/theme.css";
import "./locales/i18n";

// Configure Monaco Editor to load from local bundle instead of CDN (jsdelivr).
// Without this, offline/air-gapped environments will hang waiting for CDN.
import { loader } from "@monaco-editor/react";
import * as monaco from "monaco-editor";
loader.config({ monaco });
import ReactDOM from "react-dom/client";
import { createBrowserRouter, createHashRouter, Outlet, RouterProvider } from "react-router";
import App from "./App";
import { registerLocalIcons } from "./components/icon";
import { GLOBAL_CONFIG } from "./global-config";
import ErrorBoundary from "./routes/components/error-boundary";
import { makeRoutesSection } from "./routes/sections";
import { analyticsChildren } from "./routes/sections/analytics";

await registerLocalIcons();

// MSW mock removed

const makeRouter = GLOBAL_CONFIG.routerHistory === "hash" ? createHashRouter : createBrowserRouter;
const router = makeRouter(
	[
		// Analytics: separate root-level route branch.
		// Must be declared BEFORE the App catch-all to avoid being swallowed
		// by the dashboard layout's pathless wildcard route.
		{
			path: "analytics",
			Component: () => (
				<App>
					<Outlet />
				</App>
			),
			errorElement: <ErrorBoundary />,
			children: analyticsChildren,
		},
		// Platform: everything else (login, dashboard, admin, etc.)
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
