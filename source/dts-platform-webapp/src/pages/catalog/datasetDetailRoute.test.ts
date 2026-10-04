import { describe, expect, it } from "vitest";
import { resolveDatasetDetailId } from "./datasetDetailRoute";

describe("resolveDatasetDetailId", () => {
	const datasetId = "0d981240-29b6-45aa-9263-14ba33c249d9";

	it("prefers an id supplied by a parameterized route", () => {
		expect(resolveDatasetDetailId(` ${datasetId} `, "/catalog/datasets/11111111-1111-4111-8111-111111111111")).toBe(
			datasetId,
		);
	});

	it("reads the id when the dynamic menu resolver mounts the detail page from a wildcard route", () => {
		expect(resolveDatasetDetailId(undefined, `/catalog/datasets/${datasetId}`)).toBe(datasetId);
		expect(resolveDatasetDetailId(undefined, `/catalog/datasets/${datasetId}/`)).toBe(datasetId);
	});

	it("rejects invalid ids and paths that escape the dataset-id segment", () => {
		expect(resolveDatasetDetailId("not-a-uuid", `/catalog/datasets/${datasetId}`)).toBe("");
		expect(resolveDatasetDetailId(undefined, "/catalog/datasets")).toBe("");
		expect(resolveDatasetDetailId(undefined, `/catalog/datasets/${datasetId}/fields`)).toBe("");
		expect(resolveDatasetDetailId(undefined, `/catalog/datasets/${datasetId}%2Ffields`)).toBe("");
		expect(resolveDatasetDetailId(undefined, "/catalog/datasets/bad%2")).toBe("");
	});
});
