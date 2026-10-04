import { describe, expect, it } from "vitest";
import { LAYER_META, LAYER_ORDER } from "./assetPageShared";

describe("数仓分层标签", () => {
	it("label 为中文，code 保留英文代号", () => {
		const expected: Record<string, { label: string; code?: string }> = {
			SOURCE: { label: "来源层", code: "SOURCE" },
			ODS: { label: "贴源层", code: "ODS" },
			STG: { label: "暂存层", code: "STG" },
			DWD: { label: "明细层", code: "DWD" },
			DIM: { label: "维度层", code: "DIM" },
			DWS: { label: "汇总层", code: "DWS" },
			ADS: { label: "应用层", code: "ADS" },
			OTHER: { label: "未分层", code: undefined },
		};
		for (const key of LAYER_ORDER) {
			expect(LAYER_META[key].label, `${key} 的中文 label 不符`).toBe(expected[key].label);
			expect(LAYER_META[key].code, `${key} 的代号不符`).toBe(expected[key].code);
		}
	});
});
