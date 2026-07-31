import assert from "node:assert/strict";
import test from "node:test";
import { qualityDatasetIdFromRef } from "./AccessGovernancePanels";

test("qualityDatasetIdFromRef accepts only canonical dataset references", () => {
	const datasetId = "6b6dc758-b894-4213-8008-62d0439f17d7";
	assert.equal(qualityDatasetIdFromRef(`dataset:${datasetId}`), datasetId);
	assert.equal(qualityDatasetIdFromRef(` dataset:${datasetId} `), datasetId);
	assert.equal(qualityDatasetIdFromRef(datasetId), undefined);
	assert.equal(qualityDatasetIdFromRef(`connection:${datasetId}`), undefined);
	assert.equal(qualityDatasetIdFromRef("dataset:not-a-uuid"), undefined);
});
