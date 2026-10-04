import { describe, expect, it } from "vitest";
import {
	clearDimensionModelOperationId,
	ensureDimensionModelOperationId,
	readDimensionModelOperationId,
} from "./dimensionModelOperationUrl";

const FIRST = "5a000000-0000-4000-8000-000000000001";
const SECOND = "5b000000-0000-4000-8000-000000000002";

describe("dimension model operation URL", () => {
	it("writes the operation id before the first request and reuses it after remount", () => {
		const first = ensureDimensionModelOperationId(new URLSearchParams("create=1"), () => FIRST);
		const remounted = ensureDimensionModelOperationId(first.searchParams, () => SECOND);

		expect(first.changed).toBe(true);
		expect(first.searchParams.get("dmOperationId")).toBe(FIRST);
		expect(remounted.operationId).toBe(FIRST);
		expect(remounted.changed).toBe(false);
	});

	it("replaces an invalid identifier instead of sending it to the server", () => {
		const result = ensureDimensionModelOperationId(new URLSearchParams("dmOperationId=not-a-uuid"), () => FIRST);

		expect(result.operationId).toBe(FIRST);
		expect(result.searchParams.get("dmOperationId")).toBe(FIRST);
	});

	it("accepts only lowercase canonical UUIDs", () => {
		expect(readDimensionModelOperationId(new URLSearchParams(`dmOperationId=${FIRST}`))).toBe(FIRST);
		expect(readDimensionModelOperationId(new URLSearchParams(`dmOperationId=${FIRST.toUpperCase()}`))).toBeNull();
		expect(() => ensureDimensionModelOperationId(new URLSearchParams(), () => FIRST.toUpperCase())).toThrow(
			"DIMENSION_MODEL_OPERATION_ID_INVALID",
		);
	});

	it("clears only the completed operation id", () => {
		const params = new URLSearchParams(`create=1&dmOperationId=${FIRST}`);
		expect(clearDimensionModelOperationId(params, SECOND).changed).toBe(false);

		const cleared = clearDimensionModelOperationId(params, FIRST);
		expect(cleared.changed).toBe(true);
		expect(cleared.searchParams.get("dmOperationId")).toBeNull();
		expect(cleared.searchParams.get("create")).toBe("1");
	});
});
