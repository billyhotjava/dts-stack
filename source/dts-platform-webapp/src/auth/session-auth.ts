import { create } from "zustand";
import { currentRoutePath as resolveCurrentRoutePath } from "@dts/session-core/route";
import type { CurrentSessionPayload } from "@/auth/session-state";
import { GLOBAL_CONFIG } from "@/global-config";

const SESSION_DOMAIN = "platform";
const LOG_PREFIX = `[session:${SESSION_DOMAIN}]`;

let currentSessionPromise: Promise<CurrentSessionPayload> | null = null;

function normalizeStringList(value: unknown): string[] {
	if (!Array.isArray(value)) return [];
	return value
		.map((item) => (typeof item === "string" ? item.trim() : ""))
		.filter(Boolean);
}

function normalizeCurrentSessionPayload(raw: unknown): CurrentSessionPayload {
	const record = raw && typeof raw === "object" ? (raw as Record<string, unknown>) : {};
	const authenticated = Boolean(record.authenticated);
	if (!authenticated) {
		return { authenticated: false };
	}
	return {
		authenticated: true,
		username: typeof record.username === "string" ? record.username.trim() : undefined,
		displayName: typeof record.displayName === "string" ? record.displayName.trim() : undefined,
		browserId: typeof record.browserId === "string" ? record.browserId.trim() : undefined,
		expiresAt: typeof record.expiresAt === "string" ? record.expiresAt.trim() : undefined,
		deptCode: typeof record.deptCode === "string" ? record.deptCode.trim() : undefined,
		personnelLevel: typeof record.personnelLevel === "string" ? record.personnelLevel.trim() : undefined,
		roles: normalizeStringList(record.roles),
		permissions: normalizeStringList(record.permissions),
	};
}

export async function fetchCurrentSession(): Promise<CurrentSessionPayload> {
	if (currentSessionPromise) {
		console.debug(LOG_PREFIX, "probe: joined in-flight request");
		return currentSessionPromise;
	}

	currentSessionPromise = (async () => {
		try {
			const apiBase = GLOBAL_CONFIG.apiBaseUrl || "/api";
			const response = await fetch(`${apiBase}/session/current`, {
				method: "GET",
				credentials: "include",
				headers: {
					accept: "application/json",
				},
			});
			if (response.status === 401 || response.status === 403) {
				return { authenticated: false };
			}
			if (!response.ok) {
				throw new Error(`Session probe failed: HTTP ${response.status}`);
			}
			const body = await response.json().catch(() => null);
			const data = body?.data ?? body?.result ?? body?.payload ?? body;
			return normalizeCurrentSessionPayload(data);
		} catch (error) {
			console.warn(LOG_PREFIX, "probe: failed", error);
			throw error;
		}
	})().finally(() => {
		currentSessionPromise = null;
	});

	return currentSessionPromise;
}

type RedirectIntent = {
	returnPath: string;
	seq: number;
} | null;

type RedirectIntentStore = {
	intent: RedirectIntent;
	requestRedirect: (returnPath: string) => void;
	clearIntent: () => void;
};

let redirectSeq = 0;

export const useRedirectIntentStore = create<RedirectIntentStore>((set, get) => ({
	intent: null,
	requestRedirect: (returnPath: string) => {
		const current = get().intent;
		if (current && current.returnPath === returnPath) return;
		redirectSeq += 1;
		console.warn(LOG_PREFIX, "redirect: intent", { returnPath, seq: redirectSeq });
		set({ intent: { returnPath, seq: redirectSeq } });
	},
	clearIntent: () => set({ intent: null }),
}));

export function currentRoutePath(): string {
	return resolveCurrentRoutePath(GLOBAL_CONFIG.routerHistory);
}

export function redirectToLoginWithReturn(): void {
	const loginPath = "/auth/login";
	const current = currentRoutePath();
	const pathOnly = current.split("?")[0];
	if (pathOnly === loginPath || pathOnly.endsWith(loginPath)) {
		console.debug(LOG_PREFIX, "redirect: skipped (already on login page)");
		return;
	}
	useRedirectIntentStore.getState().requestRedirect(current);
}

export function resetLoginRedirectFlag(): void {
	useRedirectIntentStore.getState().clearIntent();
}
