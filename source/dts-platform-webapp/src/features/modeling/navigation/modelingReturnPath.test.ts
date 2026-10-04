import { describe, expect, it } from "vitest";
import { sanitizeModelingReturnTo } from "./modelingReturnPath";

describe("modeling return path", () => {
	it("accepts the new data-modeling namespace", () => {
		expect(sanitizeModelingReturnTo("/data-modeling/dimensions/workbench?model=demo")).toBe(
			"/data-modeling/dimensions/workbench?model=demo",
		);
	});

	it("rejects external and protocol-relative targets", () => {
		expect(sanitizeModelingReturnTo("https://example.com/data-modeling")).toBeUndefined();
		expect(sanitizeModelingReturnTo("//example.com/data-modeling")).toBeUndefined();
		expect(sanitizeModelingReturnTo("/data-modeling\\@example.com")).toBeUndefined();
	});
});
