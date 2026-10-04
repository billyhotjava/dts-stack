import assert from "node:assert/strict";
import test from "node:test";
import { filterDatasetsByDomain, normalizeDatasetDomain, UNASSIGNED_DOMAIN_ID } from "./datasetDomains.ts";

test("normalizes API domain fields and retains datasets without a business domain", () => {
	assert.deepEqual(normalizeDatasetDomain({ domainId: "sales", domainName: "销售域" }), {
		domainId: "sales",
		domainName: "销售域",
	});
	assert.deepEqual(normalizeDatasetDomain({ domain: { id: "finance", name: "财务域" } }), {
		domainId: "finance",
		domainName: "财务域",
	});
	assert.deepEqual(normalizeDatasetDomain({}), {
		domainId: UNASSIGNED_DOMAIN_ID,
		domainName: "未归属业务域",
	});
});

test("unassigned datasets remain selectable and can be filtered explicitly", () => {
	const datasets = [
		{ id: "a", name: "订单", domainId: "sales", domainName: "销售域" },
		{ id: "b", name: "历史表" },
	];

	assert.equal(filterDatasetsByDomain(datasets, undefined).length, 2);
	assert.deepEqual(
		filterDatasetsByDomain(datasets, UNASSIGNED_DOMAIN_ID).map((item) => item.id),
		["b"],
	);
});
