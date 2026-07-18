import assert from "node:assert/strict";
import test from "node:test";
import {
	createWarehousePlanIdempotencyKey,
	createLatestRequestGuard,
	EMPTY_WAREHOUSE_PLAN_CREATE_SESSION,
	hasWarehousePlanCreateAccess,
	loadRequestedWarehousePlan,
	mergeWarehousePlanLists,
	reduceWarehousePlanCreateSession,
	validateWarehousePlanInitialSources,
} from "./warehousePlanCreateFlow.ts";

test("uses randomUUID when the browser provides it", () => {
	assert.equal(
		createWarehousePlanIdempotencyKey({ randomUUID: () => "10000000-0000-4000-8000-000000000001" }),
		"10000000-0000-4000-8000-000000000001",
	);
});

test("uses getRandomValues for Chrome 95 and formats an RFC 4122 v4 key", () => {
	const key = createWarehousePlanIdempotencyKey({
		getRandomValues: (values) => {
			values.set([0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15]);
			return values;
		},
	});

	assert.equal(key, "00010203-0405-4607-8809-0a0b0c0d0e0f");
});

test("fails closed when no cryptographic random source exists", () => {
	assert.throws(() => createWarehousePlanIdempotencyKey({}), /cryptographic random source/i);
});

test("matches the backend maintainer-role gate and ignores permission-only access", () => {
	assert.equal(hasWarehousePlanCreateAccess(["ROLE_DEPT_DATA_OWNER"]), true);
	assert.equal(hasWarehousePlanCreateAccess(["ROLE_USER"]), false);
	assert.equal(hasWarehousePlanCreateAccess([]), false);
});

test("retains the create key across failures and clears it only on cancel or success", () => {
	const opened = reduceWarehousePlanCreateSession(EMPTY_WAREHOUSE_PLAN_CREATE_SESSION, {
		type: "OPEN",
		idempotencyKey: "request-1",
	});
	const failed = reduceWarehousePlanCreateSession(opened, { type: "REQUEST_FAILED" });
	const conflicted = reduceWarehousePlanCreateSession(failed, { type: "IDEMPOTENCY_CONFLICT" });
	const replaced = reduceWarehousePlanCreateSession(conflicted, { type: "REPLACE_KEY", idempotencyKey: "request-2" });

	assert.equal(failed, opened);
	assert.deepEqual(conflicted, { idempotencyKey: "request-1", idempotencyConflict: true });
	assert.deepEqual(replaced, { idempotencyKey: "request-2", idempotencyConflict: false });
	assert.deepEqual(reduceWarehousePlanCreateSession(replaced, { type: "CLEAR" }), EMPTY_WAREHOUSE_PLAN_CREATE_SESSION);
});

test("restores a requested plan even when the plan list endpoint fails", async () => {
	const requested = { id: "plan-requested", name: "Requested plan" };
	const calls: string[] = [];
	let rejectList: ((reason: Error) => void) | undefined;
	let restoredBeforeListSettled: typeof requested | null = null;
	const resultPromise = loadRequestedWarehousePlan(
		requested.id,
		async (planId) => {
			calls.push(`get:${planId}`);
			return requested;
		},
		async () => {
			calls.push("list");
			return await new Promise((_, reject) => {
				rejectList = reject;
			});
		},
		(plan) => {
			restoredBeforeListSettled = plan;
		},
	);
	await new Promise((resolve) => setTimeout(resolve, 0));
	assert.equal(restoredBeforeListSettled, requested);
	rejectList?.(new Error("list unavailable"));
	const result = await resultPromise;

	assert.deepEqual(calls, ["get:plan-requested", "list"]);
	assert.deepEqual(result.plans, [requested]);
	assert.equal(result.requestedPlan, requested);
	assert.equal(result.requestedPlanFailed, false);
	assert.equal(result.listFailed, true);
});

test("does not substitute a listed plan when the requested plan cannot be read", async () => {
	const fallback = { id: "plan-fallback", name: "Fallback plan" };
	const result = await loadRequestedWarehousePlan(
		"plan-missing",
		async () => {
			throw new Error("not found");
		},
		async () => [fallback],
	);

	assert.deepEqual(result.plans, [fallback]);
	assert.equal(result.requestedPlan, null);
	assert.equal(result.requestedPlanFailed, true);
	assert.equal(result.listFailed, false);
});

test("ignores a stale A response after a newer B request has started", async () => {
	const guard = createLatestRequestGuard();
	const applied: string[] = [];
	let resolveA: ((value: string) => void) | undefined;
	let resolveB: ((value: string) => void) | undefined;
	const request = async (value: Promise<string>) => {
		const isCurrent = guard.begin();
		const resolved = await value;
		if (isCurrent()) applied.push(resolved);
	};

	const a = request(new Promise((resolve) => { resolveA = resolve; }));
	const b = request(new Promise((resolve) => { resolveB = resolve; }));
	resolveB?.("B");
	await b;
	resolveA?.("A");
	await a;

	assert.deepEqual(applied, ["B"]);
});

test("invalidates an in-flight create response when its dialog session changes", () => {
	const guard = createLatestRequestGuard();
	const isCurrentCreate = guard.begin();
	guard.invalidate();

	assert.equal(isCurrentCreate(), false);
});

test("validates duplicate and oversized asset-first source references", () => {
	assert.equal(
		validateWarehousePlanInitialSources([
			{ sourceType: "CATALOG_TABLE", sourceId: " asset-1 " },
			{ sourceType: "CATALOG_TABLE", sourceId: "asset-1" },
		]),
		"同一类型和标识的数据来源不能重复",
	);
	assert.equal(
		validateWarehousePlanInitialSources([{ sourceType: "CATALOG_TABLE", sourceId: "a".repeat(257) }]),
		"来源标识不能超过 256 个字符",
	);
	assert.equal(
		validateWarehousePlanInitialSources([
			{ sourceType: "CATALOG_TABLE", sourceId: "asset-1", sourceVersion: "v".repeat(129) },
		]),
		"来源版本不能超过 128 个字符",
	);
	assert.equal(
		validateWarehousePlanInitialSources([{ sourceType: "CATALOG_TABLE", sourceId: "asset-1", sourceVersion: "v1" }]),
		null,
	);
});

test("merges a refreshed plan list without losing the exact selected plan", () => {
	const selected = { id: "plan-selected", name: "Selected" };
	const refreshed = [
		{ id: "plan-other", name: "Other" },
		{ id: "plan-selected", name: "Selected refreshed" },
	];

	assert.deepEqual(mergeWarehousePlanLists([selected], refreshed, selected.id), [
		selected,
		{ id: "plan-other", name: "Other" },
	]);
});
