import { describe, expect, it } from "vitest";
import { withPlatformAuthorization } from "./platform-auth-header";

describe("withPlatformAuthorization", () => {
	it("preserves normal headers without adding bearer authorization", () => {
		const headers = withPlatformAuthorization({
			accept: "application/json",
		});

		expect(headers.get("accept")).toBe("application/json");
		expect(headers.has("authorization")).toBe(false);
	});

	it("does not overwrite an existing authorization header", () => {
		const headers = withPlatformAuthorization({
			Authorization: "Bearer explicit-token",
		});

		expect(headers.get("authorization")).toBe("Bearer explicit-token");
	});

	it("does not add authorization for empty headers", () => {
		const headers = withPlatformAuthorization();

		expect(headers.has("authorization")).toBe(false);
	});
});
