// @vitest-environment jsdom
import { describe, expect, it } from "vitest";
import useUserStore from "./userStore";

describe("userStore persistence", () => {
	it("persists session state and user info but excludes portal tokens", () => {
		const partialize = (useUserStore as any).persist.getOptions().partialize as (state: Record<string, unknown>) => Record<string, unknown>;

		const persisted = partialize({
			userInfo: { username: "sysadmin", roles: ["ROLE_SYS_ADMIN"] },
			userToken: { accessToken: "legacy-access", refreshToken: "legacy-refresh" },
			session: {
				initialized: true,
				checking: false,
				authenticated: true,
				reason: "authenticated",
				browserId: "browser-admin-1",
			},
		});

		expect(persisted.userInfo).toEqual({ username: "sysadmin", roles: ["ROLE_SYS_ADMIN"] });
		expect(persisted.session).toMatchObject({
			initialized: true,
			authenticated: true,
			browserId: "browser-admin-1",
		});
		expect(persisted.userToken).toBeUndefined();
	});
});
