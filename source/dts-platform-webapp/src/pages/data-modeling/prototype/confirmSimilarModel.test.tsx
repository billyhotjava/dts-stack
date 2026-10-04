// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({ find: vi.fn(), confirm: vi.fn(), warn: vi.fn() }));
vi.mock("@/api/modelingAccessApi", () => ({ findSimilarModels: mocks.find }));
vi.mock("antd", () => ({ Modal: { confirm: mocks.confirm }, message: { warning: mocks.warn } }));
vi.mock("./services/modelWorkbenchService", () => ({
	modelDraftToUpdateCommand: () => ({
		modelType: "FACT",
		businessProcessId: "process",
		sourceRefs: [{ ref: "source" }],
		grain: { keys: ["id"] },
	}),
}));

import { confirmSimilarModel } from "./confirmSimilarModel";
import type { ModelSpecDraft } from "./services/modelWorkbenchService";

const draft = { planId: "dept-plan" } as ModelSpecDraft;
beforeEach(() => vi.clearAllMocks());
describe("public model similarity prompt", () => {
	it.each([401, 403, 404])("never ignores authorization failure %s", async (status) => {
		const error = { response: { status } };
		mocks.find.mockRejectedValue(error);
		await expect(confirmSimilarModel(draft)).rejects.toBe(error);
		expect(mocks.warn).not.toHaveBeenCalled();
	});
	it("an ordinary service failure remains an explicit optional warning", async () => {
		mocks.find.mockRejectedValue({ response: { status: 503 } });
		await expect(confirmSimilarModel(draft)).resolves.toBe(true);
		expect(mocks.warn).toHaveBeenCalledOnce();
	});
	it("lets the author return to editing when a similar model exists", async () => {
		mocks.find.mockResolvedValue([{ modelSpecId: "existing", name: "既有订单模型", matchReasons: ["粒度相同"] }]);
		mocks.confirm.mockImplementation((options) => options.onCancel());
		await expect(confirmSimilarModel(draft)).resolves.toBe(false);
	});
	it("does not create a department context just to search similar models", async () => {
		await expect(confirmSimilarModel({ ...draft, planId: "" })).resolves.toBe(true);
		expect(mocks.find).not.toHaveBeenCalled();
	});
});
