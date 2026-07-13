import assert from "node:assert/strict";
import test from "node:test";
import { buildBusinessModelingRoute, resolveBusinessModelingContext } from "./businessModelingContext.ts";

test("business modeling context keeps the business process and leaves project space optional", () => {
	const context = resolveBusinessModelingContext(
		new URLSearchParams(
			"domainId=domain-1&domainName=%E8%AE%A2%E5%8D%95%E5%9F%9F&processId=order-fulfillment&processName=%E8%AE%A2%E5%8D%95%E5%B1%A5%E7%BA%A6&planningId=plan-1&warehouseLayer=DWD",
		),
	);

	assert.equal(context.domainId, "domain-1");
	assert.equal(context.processId, "order-fulfillment");
	assert.equal(context.projectSpaceId, undefined);
	assert.equal(context.projectSpaceMode, "implicit");
});

test("business modeling links preserve the process context across ledger pages", () => {
	const route = buildBusinessModelingRoute("/modeling/semantic/models?from=process", {
		domainId: "domain-1",
		processId: "order-fulfillment",
		planningId: "plan-1",
		warehouseLayer: "DWD",
		modelingMode: "dimension",
	});

	assert.match(route, /^\/modeling\/semantic\/models\?/);
	assert.match(route, /from=process/);
	assert.match(route, /domainId=domain-1/);
	assert.match(route, /processId=order-fulfillment/);
	assert.match(route, /planningId=plan-1/);
	assert.doesNotMatch(route, /projectSpaceId=/);
});
