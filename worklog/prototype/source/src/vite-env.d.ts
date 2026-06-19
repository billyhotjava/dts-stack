/// <reference types="vite/client" />

interface ImportMetaEnv {
	readonly VITE_USE_MOCK?: string;
	readonly VITE_MOCK_DELAY?: string;
}

interface ImportMeta {
	readonly env: ImportMetaEnv;
}
