import { describe, expect, it } from "vitest";

import {
	edgeEndpoint,
	isColumnNodeId,
	lineageEvidenceDescription,
	nodeTone,
	normalizeColumnNodeId,
	relationStroke,
} from "./lineageContracts";

describe("lineageContracts", () => {
	it("edgeEndpoint resolves from/to ids with legacy fallbacks", () => {
		const edge = { fromId: "f", toId: "t", upstreamDatasetId: "u", downstreamDatasetId: "d" };
		expect(edgeEndpoint(edge, "from")).toBe("f");
		expect(edgeEndpoint(edge, "to")).toBe("t");
		expect(edgeEndpoint({ upstreamDatasetId: "u", downstreamDatasetId: "d" }, "from")).toBe("u");
		expect(edgeEndpoint({ upstreamDatasetId: "u", downstreamDatasetId: "d" }, "to")).toBe("d");
	});

	it("normalizeColumnNodeId produces stable {dsid}:{col} ids", () => {
		expect(normalizeColumnNodeId("ds-1", "amount")).toBe("ds-1:amount");
		expect(normalizeColumnNodeId("ds-1", undefined)).toBeUndefined();
		expect(normalizeColumnNodeId(undefined, "amount")).toBeUndefined();
		expect(isColumnNodeId("ds-1:amount")).toBe(true);
		expect(isColumnNodeId("ds-1")).toBe(false);
	});

	it("nodeTone maps layers and kinds deterministically", () => {
		expect(nodeTone({ kind: "source" }).color).toBe("#9e1068");
		expect(nodeTone({ kind: "job" }).color).toBe("#ad4e00");
		expect(nodeTone({ layer: "ADS" }).color).toBe("#237804");
		expect(nodeTone({ layer: "DWS" }).color).toBe("#006d75");
		expect(nodeTone({ layer: "DWD" }).color).toBe("#0958d9");
		expect(nodeTone({ layer: "DIM" }).color).toBe("#531dab");
		expect(nodeTone({ layer: "UNKNOWN" }).color).toBe("#262626");
	});

	it("relationStroke resolves known relations and defaults otherwise", () => {
		expect(relationStroke("dbt")).toBe("#d46b08");
		expect(relationStroke("ADDAX")).toBe("#389e0d");
		expect(relationStroke("AIRFLOW")).toBe("#08979c");
		expect(relationStroke("MANUAL")).toBe("#8c8c8c");
		expect(relationStroke("unknown")).toBe("#bfbfbf");
	});

	it("lineageEvidenceDescription explains missing evidence without inventing lineage", () => {
		expect(
			lineageEvidenceDescription({
				state: "MISSING",
				reasonCodes: [
					"TABLE_LINEAGE_EVIDENCE_MISSING",
					"UPSTREAM_SOURCE_OR_ODS_EVIDENCE_MISSING",
					"COLUMN_LINEAGE_EVIDENCE_MISSING",
				],
				tableLineageCount: 0,
				columnLineageCount: 0,
				odsNodeCount: 0,
				sourceNodeCount: 0,
				columnEvidenceRequested: true,
			}),
		).toBe("缺少表级血缘；未发现上游源系统或 ODS 证据；缺少字段级血缘");
	});
});
