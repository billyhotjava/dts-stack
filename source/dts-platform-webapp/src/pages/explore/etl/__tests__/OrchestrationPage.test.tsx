// @vitest-environment jsdom

import { describe, expect, it } from "vitest";
import { type DesignFormValues, designPayload } from "../OrchestrationPage";

function values(overrides: Partial<DesignFormValues> = {}): DesignFormValues {
	return {
		taskName: "成本中心接入",
		description: "财务主数据",
		sourceDataSourceId: "10000000-0000-0000-0000-000000000013",
		sourceType: "postgresqlreader",
		sourceConfigText: '{"schema":"erp"}',
		destinationType: "postgresqlwriter",
		destinationConfigText: '{"table":["ods_cost_center"]}',
		targetDatasetId: "00000000-0000-0000-0000-000000000013",
		syncMode: "full_refresh",
		syncSchedule: "0 0 2 * * *",
		tableMapping: [{ source: "erp.cost_center", target: "ods.ods_cost_center" }],
		syncConfigText: "{}",
		postIngestionQualityEnabled: true,
		...overrides,
	};
}

describe("data integration flow payload", () => {
	it("keeps target asset identity independent and derives the post-ingestion quality reference", () => {
		const payload = designPayload(values());

		expect(payload.targetDatasetId).toBe("00000000-0000-0000-0000-000000000013");
		expect(payload.qualityPolicyRef).toBe("dataset:00000000-0000-0000-0000-000000000013");
		expect(payload.postIngestionQualityEnabled).toBe(true);
	});

	it("retains the target asset while explicitly disabling quality verification", () => {
		const payload = designPayload(values({ postIngestionQualityEnabled: false }));

		expect(payload.targetDatasetId).toBe("00000000-0000-0000-0000-000000000013");
		expect(payload.qualityPolicyRef).toBeUndefined();
		expect(payload.postIngestionQualityEnabled).toBe(false);
	});

	it("rejects non-object configuration before submitting a design", () => {
		expect(() => designPayload(values({ destinationConfigText: "[]" }))).toThrow("目标参数必须是 JSON 对象");
		expect(() => designPayload(values({ sourceConfigText: "{" }))).toThrow("来源参数不是有效的 JSON");
	});
});
