import { describe, expect, it } from "vitest";
import { resolveModelDeliveryCell } from "./ModelDeliveryStatusCell";

const model = { revision: 4 } as never;
const delivery = (revision = 4, current = true) =>
	({
		modelRevision: revision,
		candidate: { matchesCurrentModel: current },
		steps: [
			{ key: "materialization", state: "SUCCEEDED", matchesCurrentTarget: true, resourceId: null },
			{ key: "catalog", state: "SUCCEEDED", matchesCurrentTarget: true, resourceId: "asset-1" },
			{ key: "analysis", state: "FAILED", matchesCurrentTarget: true, resourceId: null },
		],
	}) as never;

describe("resolveModelDeliveryCell", () => {
	it("separates catalog registration from failed analysis preparation", () => {
		expect(resolveModelDeliveryCell(model, delivery(), "catalog")).toMatchObject({
			label: "资产已登记",
			assetId: "asset-1",
		});
		expect(resolveModelDeliveryCell(model, delivery(), "analysis")).toMatchObject({ label: "分析准备失败" });
	});

	it("does not display cross-revision or stale candidate evidence as complete", () => {
		expect(resolveModelDeliveryCell(model, delivery(3), "catalog").label).toBe("暂无当前记录");
		expect(resolveModelDeliveryCell(model, delivery(4, false), "catalog").label).toBe("暂无当前记录");
		expect(resolveModelDeliveryCell(model, { ...delivery(), candidate: null }, "catalog").label).toBe("暂无当前记录");
	});
});
