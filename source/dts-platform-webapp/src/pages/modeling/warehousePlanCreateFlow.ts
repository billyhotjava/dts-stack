export type CryptoRandomSource = {
	randomUUID?: () => string;
	getRandomValues?: (values: Uint8Array) => Uint8Array;
};

export type RequestedWarehousePlanLoad<T extends { id: string }> = {
	plans: T[];
	requestedPlan: T | null;
	requestedPlanFailed: boolean;
	listFailed: boolean;
};

export type WarehousePlanCreateSession = {
	idempotencyKey: string | null;
	idempotencyConflict: boolean;
};

export type WarehousePlanCreateSessionAction =
	| { type: "OPEN" | "REPLACE_KEY"; idempotencyKey: string }
	| { type: "REQUEST_FAILED" | "IDEMPOTENCY_CONFLICT" | "CLEAR" };

export const EMPTY_WAREHOUSE_PLAN_CREATE_SESSION: WarehousePlanCreateSession = {
	idempotencyKey: null,
	idempotencyConflict: false,
};

export type LatestRequestGuard = {
	begin: () => () => boolean;
	invalidate: () => void;
};

const WAREHOUSE_PLAN_MAINTAINER_ROLES = new Set([
	"ADMIN",
	"OP_ADMIN",
	"INST_DATA_OWNER",
	"DEPT_DATA_OWNER",
	"INST_LEADER",
	"DEPT_LEADER",
]);

const hex = (value: number) => value.toString(16).padStart(2, "0");

export function createLatestRequestGuard(): LatestRequestGuard {
	let sequence = 0;
	return {
		begin: () => {
			const requestSequence = ++sequence;
			return () => requestSequence === sequence;
		},
		invalidate: () => {
			sequence += 1;
		},
	};
}

export function hasWarehousePlanCreateAccess(roles: unknown[]): boolean {
	return roles.some((role) =>
		WAREHOUSE_PLAN_MAINTAINER_ROLES.has(
			String(role || "")
				.trim()
				.toUpperCase()
				.replace(/^ROLE_/, ""),
		),
	);
}

export function reduceWarehousePlanCreateSession(
	state: WarehousePlanCreateSession,
	action: WarehousePlanCreateSessionAction,
): WarehousePlanCreateSession {
	switch (action.type) {
		case "OPEN":
		case "REPLACE_KEY":
			return { idempotencyKey: action.idempotencyKey, idempotencyConflict: false };
		case "IDEMPOTENCY_CONFLICT":
			return { ...state, idempotencyConflict: true };
		case "REQUEST_FAILED":
			return state;
		case "CLEAR":
			return EMPTY_WAREHOUSE_PLAN_CREATE_SESSION;
	}
}

/** Generates a request identity without time or pseudo-random fallbacks. */
export function createWarehousePlanIdempotencyKey(
	randomSource: CryptoRandomSource | undefined = globalThis.crypto as CryptoRandomSource | undefined,
): string {
	if (typeof randomSource?.randomUUID === "function") {
		return randomSource.randomUUID();
	}
	if (typeof randomSource?.getRandomValues !== "function") {
		throw new Error("A cryptographic random source is required to create a warehouse plan");
	}

	const bytes = randomSource.getRandomValues(new Uint8Array(16));
	bytes[6] = (bytes[6] & 0x0f) | 0x40;
	bytes[8] = (bytes[8] & 0x3f) | 0x80;
	const value = Array.from(bytes, hex).join("");
	return `${value.slice(0, 8)}-${value.slice(8, 12)}-${value.slice(12, 16)}-${value.slice(16, 20)}-${value.slice(20)}`;
}

/** Restores an explicit URL selection independently from the optional plan list. */
export async function loadRequestedWarehousePlan<T extends { id: string }>(
	requestedPlanId: string,
	getRequestedPlan: (planId: string) => Promise<T>,
	listPlans: () => Promise<T[]>,
	onRequestedPlan?: (plan: T) => void,
	onRequestedPlanFailure?: () => void,
): Promise<RequestedWarehousePlanLoad<T>> {
	const requestedPromise = Promise.resolve().then(() => getRequestedPlan(requestedPlanId));
	const listPromise = Promise.resolve()
		.then(listPlans)
		.then(
			(plans) => ({ plans: Array.isArray(plans) ? plans : [], failed: false }),
			() => ({ plans: [] as T[], failed: true }),
		);

	let requestedPlan: T | null = null;
	let requestedPlanFailed = false;
	try {
		requestedPlan = await requestedPromise;
		onRequestedPlan?.(requestedPlan);
	} catch {
		requestedPlanFailed = true;
		onRequestedPlanFailure?.();
	}

	const listResult = await listPromise;
	return {
		plans: requestedPlan
			? [requestedPlan, ...listResult.plans.filter((plan) => plan.id !== requestedPlan.id)]
			: listResult.plans,
		requestedPlan,
		requestedPlanFailed,
		listFailed: listResult.failed,
	};
}
