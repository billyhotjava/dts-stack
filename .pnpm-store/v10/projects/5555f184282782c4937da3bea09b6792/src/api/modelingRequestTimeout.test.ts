import assert from "node:assert/strict";
import test from "node:test";
import { MODELING_REQUEST_TIMEOUT_MS, withModelingRequestTimeout } from "./modelingRequestTimeout";

test("withModelingRequestTimeout applies 60s timeout when omitted", () => {
	const config = withModelingRequestTimeout({ url: "/modeling/sql-models" });

	assert.equal(config.timeout, MODELING_REQUEST_TIMEOUT_MS);
});

test("withModelingRequestTimeout upgrades shorter timeout to 60s", () => {
	const config = withModelingRequestTimeout({ url: "/modeling/sql-models/demo/columns", timeout: 15_000 });

	assert.equal(config.timeout, MODELING_REQUEST_TIMEOUT_MS);
});

test("withModelingRequestTimeout preserves longer explicit timeout", () => {
	const config = withModelingRequestTimeout({ url: "/modeling/sql-models/demo", timeout: 90_000 });

	assert.equal(config.timeout, 90_000);
});
