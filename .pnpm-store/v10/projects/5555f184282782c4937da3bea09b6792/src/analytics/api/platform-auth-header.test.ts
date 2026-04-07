import { describe, expect, it } from "vitest";
import { withPlatformAuthorization } from "./platform-auth-header";

describe("withPlatformAuthorization", () => {
	it("adds bearer authorization when token exists and header is absent", () => {
		const headers = withPlatformAuthorization(
			{
				accept: "application/json",
			},
			"platform-access-token",
		);

		expect(headers.get("accept")).toBe("application/json");
		expect(headers.get("authorization")).toBe("Bearer platform-access-token");
	});

	it("does not overwrite an existing authorization header", () => {
		const headers = withPlatformAuthorization(
			{
				Authorization: "Bearer explicit-token",
			},
			"platform-access-token",
		);

		expect(headers.get("authorization")).toBe("Bearer explicit-token");
	});

	it("does not add authorization when token is blank", () => {
		const headers = withPlatformAuthorization(undefined, "   ");

		expect(headers.has("authorization")).toBe(false);
	});
});
