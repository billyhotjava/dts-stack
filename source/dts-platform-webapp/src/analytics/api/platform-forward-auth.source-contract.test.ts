import fs from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";

describe("platform-forward-auth source contract", () => {
	it("forwards authorization headers for analytics api requests", () => {
		const source = fs.readFileSync(
			path.resolve(import.meta.dirname, "../../../../../services/dts-proxy/dynamic/traefik-dynamic.yml"),
			"utf8",
		);

		const blockMatch = source.match(/platform-forward-auth:\s*[\s\S]*?authRequestHeaders:\s*([\s\S]*?)authResponseHeaders:/);
		expect(blockMatch, "platform-forward-auth block should exist").toBeTruthy();

		const authRequestHeadersBlock = blockMatch?.[1] ?? "";
		expect(authRequestHeadersBlock.includes("- Cookie")).toBe(true);
		expect(authRequestHeadersBlock.includes("- Authorization")).toBe(true);
	});
});
