// @vitest-environment jsdom
import { beforeEach, expect, test, vi } from "vitest";

const apiGet = vi.hoisted(() => vi.fn());

vi.mock("./apiClient", () => ({
	default: {
		get: apiGet,
	},
}));

import { ingestionTaskAPI } from "./ingestion";

beforeEach(() => {
	apiGet.mockReset();
});

test("latest execution treats the platform business 404 envelope as no run history", async () => {
	apiGet.mockImplementation(async (config) => {
		if (!config._returnEnvelope || !config._acceptedEnvelopeStatuses?.includes(404)) {
			throw new Error("business 404 was rejected before the ingestion API could normalize it");
		}
		return { status: 404, message: "ingestion service error", data: null };
	});

	await expect(ingestionTaskAPI.getLatestExecution(1)).resolves.toBeNull();
});

test("latest execution still unwraps a successful platform envelope", async () => {
	const execution = { id: 9, taskId: 1, status: "SUCCESS" };
	apiGet.mockResolvedValue({ status: 200, message: "ok", data: execution });

	await expect(ingestionTaskAPI.getLatestExecution(1)).resolves.toEqual(execution);
});

test("latest execution continues to reject non-404 failures", async () => {
	const failure = { response: { status: 503 } };
	apiGet.mockRejectedValue(failure);

	await expect(ingestionTaskAPI.getLatestExecution(1)).rejects.toBe(failure);
});
