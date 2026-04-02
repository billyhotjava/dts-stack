// @vitest-environment jsdom
import { describe, expect, it } from "vitest";
import useUserStore from "./userStore";

describe("userStore persistence", () => {
	it("persists session state and user info but excludes portal tokens", () => {
		const partialize = (useUserStore as any).persist.getOptions().partialize as (state: Record<string, unknown>) => Record<string, unknown>;

		const persisted = partialize({
			userInfo: { username: "alice", roles: ["ROLE_OP_ADMIN"] },
			userToken: { accessToken: "portal-access", refreshToken: "portal-refresh" },
			session: {
				initialized: true,
				checking: false,
				authenticated: true,
				reason: "authenticated",
				browserId: "browser-1",
			},
		});

		expect(persisted.userInfo).toEqual({ username: "alice", roles: ["ROLE_OP_ADMIN"] });
		expect(persisted.session).toMatchObject({
			initialized: true,
			authenticated: true,
			browserId: "browser-1",
		});
		expect(persisted.userToken).toBeUndefined();
	});
});
