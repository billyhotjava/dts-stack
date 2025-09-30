/// <reference types="vite/client" />

interface ImportMetaEnv {
	/** Default route path for the application */
	readonly VITE_APP_DEFAULT_ROUTE: string;
	/** Public path for static assets */
	readonly VITE_APP_PUBLIC_PATH: string;
	/** Base URL for API endpoints */
	readonly VITE_APP_API_BASE_URL: string;
	/** Routing mode: frontend routing or backend routing */
	readonly VITE_APP_ROUTER_MODE: "frontend" | "backend";
	/** Optional: proxy target for Vite dev server (e.g., http://localhost:8081 or https://localhost) */
	readonly VITE_API_PROXY_TARGET?: string;
	/** Optional: proxy prefix for Vite dev server (e.g., /platform) */
	readonly VITE_API_PROXY_PREFIX?: string;
}

interface ImportMeta {
	readonly env: ImportMetaEnv;
}
