export type PlatformTokens = {
	accessToken: string;
	refreshToken: string;
};

const DEFAULT_STORE_KEY = "userStore";

type AnyObject = Record<string, unknown>;

function readJson(raw: string | null): AnyObject | null {
	if (!raw) return null;
	try {
		const v = JSON.parse(raw);
		return v && typeof v === "object" ? (v as AnyObject) : null;
	} catch {
		return null;
	}
}

function asObject(v: unknown): AnyObject | null {
	return v && typeof v === "object" ? (v as AnyObject) : null;
}

function pickString(obj: AnyObject | null, keys: string[]): string {
	if (!obj) return "";
	for (const key of keys) {
		const v = obj[key];
		if (typeof v === "string" && v.trim()) return v.trim();
	}
	return "";
}

export function getPlatformTokens(storeKey: string = DEFAULT_STORE_KEY): PlatformTokens {
	const store = readJson(localStorage.getItem(storeKey));
	const state = asObject(store?.state);
	const userToken = asObject(state?.userToken);

	const accessToken = pickString(userToken, ["accessToken", "access_token", "token"]);
	const refreshToken = pickString(userToken, ["refreshToken", "refresh_token"]);
	return { accessToken, refreshToken };
}

export function setPlatformTokens(tokens: Partial<PlatformTokens>, storeKey: string = DEFAULT_STORE_KEY) {
	const store = readJson(localStorage.getItem(storeKey)) ?? { state: {}, version: 0 };
	const state = (asObject(store.state) ?? {}) as AnyObject;
	const userToken = (asObject(state.userToken) ?? {}) as AnyObject;

	if (tokens.accessToken !== undefined) userToken.accessToken = tokens.accessToken;
	if (tokens.refreshToken !== undefined) userToken.refreshToken = tokens.refreshToken;

	state.userToken = userToken;
	(store as AnyObject).state = state;
	localStorage.setItem(storeKey, JSON.stringify(store));
}

function pickTokenFromResponse(body: unknown): PlatformTokens | null {
	const obj = asObject(body);
	const data = asObject(obj?.data) ?? asObject(obj?.result) ?? asObject(obj?.payload);

	const accessToken =
		pickString(obj, ["accessToken", "access_token", "token"]) || pickString(data, ["accessToken", "access_token", "token"]);
	if (!accessToken) return null;

	const refreshToken =
		pickString(obj, ["refreshToken", "refresh_token"]) || pickString(data, ["refreshToken", "refresh_token"]);
	return { accessToken, refreshToken };
}

export async function refreshPlatformAccessToken(refreshToken: string): Promise<PlatformTokens | null> {
	const rt = String(refreshToken ?? "").trim();
	if (!rt) return null;

	const response = await fetch("/api/keycloak/auth/refresh", {
		method: "POST",
		credentials: "include",
		headers: { "content-type": "application/json", accept: "application/json" },
		body: JSON.stringify({ refreshToken: rt }),
	});
	if (!response.ok) return null;

	const body = await response.json().catch(() => null);
	const tokens = pickTokenFromResponse(body);
	if (!tokens) return null;

	setPlatformTokens({ accessToken: tokens.accessToken, refreshToken: tokens.refreshToken || rt });
	return { accessToken: tokens.accessToken, refreshToken: tokens.refreshToken || rt };
}

