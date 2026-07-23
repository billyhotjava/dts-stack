import assert from "node:assert/strict";
import test from "node:test";

type NavigationModule = typeof import("./standardOwnerNavigation.ts");

const loadNavigation = async (): Promise<NavigationModule | null> =>
	import("./standardOwnerNavigation.ts").catch(() => null);

test("model-owned data element maintenance returns only to the matching model standards tab", async () => {
	const navigation = await loadNavigation();
	assert.ok(navigation, "standard owner navigation contract must exist");
	if (!navigation) return;

	const params = new URLSearchParams({
		modelSpecId: "30000000-0000-0000-0000-000000000003",
		revision: "3",
		planId: "10000000-0000-0000-0000-000000000001",
		returnTo:
			"/modeling/models/30000000-0000-0000-0000-000000000003?tab=standards&planId=10000000-0000-0000-0000-000000000001",
	});
	assert.deepEqual(navigation.resolveStandardOwnerReturnTo(params), {
		href:
			"/modeling/models/30000000-0000-0000-0000-000000000003?tab=standards&planId=10000000-0000-0000-0000-000000000001",
		label: "返回模型字段标准",
	});

	params.set(
		"returnTo",
		"/modeling/models/40000000-0000-0000-0000-000000000004?tab=standards&planId=10000000-0000-0000-0000-000000000001",
	);
	assert.equal(navigation.resolveStandardOwnerReturnTo(params), null);
});

test("plan-owned data element maintenance returns only to the matching canonical baseline", async () => {
	const navigation = await loadNavigation();
	assert.ok(navigation, "standard owner navigation contract must exist");
	if (!navigation) return;

	const params = new URLSearchParams({
		planId: "10000000-0000-0000-0000-000000000001",
		returnTo:
			"/modeling/plans/10000000-0000-0000-0000-000000000001/baseline?tab=categories&planId=10000000-0000-0000-0000-000000000001",
	});
	assert.deepEqual(navigation.resolveStandardOwnerReturnTo(params), {
		href:
			"/modeling/plans/10000000-0000-0000-0000-000000000001/baseline?tab=categories&planId=10000000-0000-0000-0000-000000000001",
		label: "返回建设规划",
	});

	for (const unsafe of ["https://evil.example", "//evil.example", "/ops/instances", "/modeling/plans/other/baseline?tab=categories&planId=other"]) {
		params.set("returnTo", unsafe);
		assert.equal(navigation.resolveStandardOwnerReturnTo(params), null, unsafe);
	}
});

test("standard package round trip preserves only canonical owner context", async () => {
	const navigation = await loadNavigation();
	assert.ok(navigation, "standard owner navigation contract must exist");
	if (!navigation) return;

	const params = new URLSearchParams({
		modelSpecId: "30000000-0000-0000-0000-000000000003",
		revision: "3",
		planId: "10000000-0000-0000-0000-000000000001",
		returnTo:
			"/modeling/models/30000000-0000-0000-0000-000000000003?tab=standards&planId=10000000-0000-0000-0000-000000000001",
		keyword: "amount",
		page: "2",
		bindingDraft: "1",
	});
	const packageUrl = new URL(navigation.buildStandardPackageImportRoute(params), "http://dts.local");
	assert.equal(packageUrl.pathname, "/foundation/standard-package");
	assert.deepEqual(Object.fromEntries(packageUrl.searchParams), {
		from: "elements",
		modelSpecId: "30000000-0000-0000-0000-000000000003",
		revision: "3",
		planId: "10000000-0000-0000-0000-000000000001",
		returnTo:
			"/modeling/models/30000000-0000-0000-0000-000000000003?tab=standards&planId=10000000-0000-0000-0000-000000000001",
	});

	const elementsUrl = new URL(navigation.buildDataElementReturnRoute(packageUrl.searchParams), "http://dts.local");
	assert.equal(elementsUrl.pathname, "/governance/standards/elements");
	assert.equal(elementsUrl.searchParams.get("from"), "standard-package");
	assert.equal(elementsUrl.searchParams.get("planId"), "10000000-0000-0000-0000-000000000001");
	assert.equal(
		elementsUrl.searchParams.get("returnTo"),
		"/modeling/models/30000000-0000-0000-0000-000000000003?tab=standards&planId=10000000-0000-0000-0000-000000000001",
	);
	assert.equal(elementsUrl.searchParams.get("applied"), null);
	assert.equal(elementsUrl.searchParams.get("bindingDraft"), null);

	const appliedUrl = new URL(
		navigation.buildDataElementReturnRoute(packageUrl.searchParams, { applied: true }),
		"http://dts.local",
	);
	assert.equal(appliedUrl.searchParams.get("applied"), "1");

	params.append("planId", "20000000-0000-0000-0000-000000000002");
	assert.equal(navigation.resolveStandardOwnerReturnTo(params), null);
});
