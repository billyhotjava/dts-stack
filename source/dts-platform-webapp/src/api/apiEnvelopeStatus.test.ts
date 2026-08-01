import { expect, test } from "vitest";
import { acceptsApiEnvelopeStatus } from "./apiEnvelopeStatus";

test("207 remains rejected unless the individual request explicitly opts in", () => {
	expect(acceptsApiEnvelopeStatus(200)).toBe(true);
	expect(acceptsApiEnvelopeStatus(207)).toBe(false);
	expect(acceptsApiEnvelopeStatus("207")).toBe(false);
	expect(acceptsApiEnvelopeStatus(207, [207])).toBe(true);
});
