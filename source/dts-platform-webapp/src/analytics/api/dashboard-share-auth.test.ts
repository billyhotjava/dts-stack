import { afterEach, beforeEach, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({ refresh: vi.fn(), clear: vi.fn(), logout: vi.fn(), replace: vi.fn() }));
vi.mock("@/api/apiClient", () => ({ refreshPortalSessionIfPossible: mocks.refresh }));
vi.mock("@/store/userStore", () => ({
	default: { getState: () => ({ actions: { clearUserInfoAndToken: mocks.clear } }) },
}));
vi.mock("@/utils/portalSessionStorage", () => ({ markPortalSessionLogout: mocks.logout }));
vi.mock("@/routes/constants", () => ({
	resolveCurrentAppPath: () => "/bi/public/dashboard/share-id",
	resolveLoginHref: (path: string) => `/login?redirect=${encodeURIComponent(path)}`,
}));

import { fetchWithPlatformAuth } from "./analyticsApi";

const request = vi.fn();
beforeEach(() => {
	vi.resetAllMocks();
	vi.stubGlobal("fetch", request);
	vi.stubGlobal("window", { location: { replace: mocks.replace } });
	mocks.refresh.mockResolvedValue(null);
});
afterEach(() => vi.unstubAllGlobals());

it.each([
	"/bi/api/public/dashboard/share-id",
	"/analytics/api/public/dashboard/share-id/dashcard/1/card/2/query",
	"/bi/api/public/pivot/dashboard/share-id/dashcard/1/card/2/query",
])("redirects unauthenticated dashboard share requests back through login: %s", async (url) => {
	request.mockResolvedValue(new Response(null, { status: 401 }));
	await fetchWithPlatformAuth(url);
	expect(mocks.refresh).toHaveBeenCalledOnce();
	expect(mocks.replace).toHaveBeenCalledWith("/login?redirect=%2Fbi%2Fpublic%2Fdashboard%2Fshare-id");
});

it("refreshes the session before retrying with cookies", async () => {
	request.mockResolvedValueOnce(new Response(null, { status: 401 })).mockResolvedValueOnce(new Response("{}"));
	mocks.refresh.mockResolvedValue({ authenticated: true });
	expect((await fetchWithPlatformAuth("/bi/api/public/dashboard/share-id")).status).toBe(200);
	expect(request).toHaveBeenLastCalledWith(
		"/bi/api/public/dashboard/share-id",
		expect.objectContaining({ credentials: "include" }),
	);
	expect(mocks.replace).not.toHaveBeenCalled();
});

it.each([403, 404])("does not turn an authenticated denial into a login loop: %s", async (status) => {
	request.mockResolvedValue(new Response(null, { status }));
	await fetchWithPlatformAuth("/bi/api/public/dashboard/share-id");
	expect(mocks.refresh).not.toHaveBeenCalled();
	expect(mocks.replace).not.toHaveBeenCalled();
});

it("preserves the existing handling of other public endpoints", async () => {
	request.mockResolvedValue(new Response(null, { status: 401 }));
	await fetchWithPlatformAuth("/bi/api/public/card/card-id");
	expect(mocks.refresh).not.toHaveBeenCalled();
	expect(mocks.replace).not.toHaveBeenCalled();
});
