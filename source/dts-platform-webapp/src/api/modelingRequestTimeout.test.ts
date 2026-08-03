import { expect, test } from "vitest";
import { MODELING_REQUEST_TIMEOUT_MS, withModelingRequestTimeout } from "./modelingRequestTimeout";

test("withModelingRequestTimeout applies 60s timeout when omitted", () => {
	const config = withModelingRequestTimeout({ url: "/modeling/model-specs" });

	expect(config.timeout).toBe(MODELING_REQUEST_TIMEOUT_MS);
});

test("withModelingRequestTimeout upgrades shorter timeout to 60s", () => {
	const config = withModelingRequestTimeout({ url: "/modeling/model-specs/demo/implementation", timeout: 15_000 });

	expect(config.timeout).toBe(MODELING_REQUEST_TIMEOUT_MS);
});

test("withModelingRequestTimeout preserves longer explicit timeout", () => {
	const config = withModelingRequestTimeout({ url: "/modeling/model-specs/demo", timeout: 90_000 });

	expect(config.timeout).toBe(90_000);
});
