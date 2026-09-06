import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { archiveModelSpec, deleteModelSpec } from "@/api/modelSpecApi";
import type { ModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
import { type CatalogActionOptions, useCatalogActions } from "./useCatalogActions";

vi.mock("react", () => ({ useCallback: (callback: unknown) => callback }));
vi.mock("@/api/modelSpecApi", () => ({ archiveModelSpec: vi.fn(), deleteModelSpec: vi.fn() }));
vi.mock("@/api/dimensionDefinitionApi", () => ({}));
vi.mock("./services/modelWorkbenchService", () => ({}));
vi.mock("./services/planningProjectionService", () => ({
	normalizeModelingRequestFailure: (error: { response?: { data?: { errorCode?: string } } }, message: string) => ({
		kind: "request",
		message,
		code: error.response?.data?.errorCode,
	}),
}));

const model = {
	id: "model-1",
	name: "失败模型",
	status: "DRAFT",
	compatibilityMode: "CANONICAL",
	revision: 3,
	checksum: "checksum-3",
} as ModelSpecView;
const pins = { id: model.id, revision: model.revision, checksum: model.checksum };
const failure = (errorCode: string) => ({ response: { status: 409, data: { errorCode } } });

describe("removing a model with delivery history", () => {
	let options: CatalogActionOptions;
	const confirm = vi.fn();

	beforeEach(() => {
		vi.resetAllMocks();
		confirm.mockReturnValue(true);
		vi.stubGlobal("window", { confirm });
		options = {
			navigate: vi.fn(),
			canMaintain: true,
			ownerId: "owner",
			savingRef: { current: false },
			confirmDiscard: () => true,
			selectedModelId: model.id,
			selectedDimensionId: "",
			replaceDraft: vi.fn(),
			syncWorkbenchUrl: vi.fn(),
			requestedModelIdRef: { current: model.id },
			requestedDimensionIdRef: { current: "" },
			show: vi.fn(),
			setSaving: vi.fn(),
			setFailure: vi.fn(),
			reload: vi.fn().mockResolvedValue(undefined),
		};
	});

	afterEach(() => vi.unstubAllGlobals());

	it("permanently deletes an ordinary draft", async () => {
		await useCatalogActions(options).removeModel(model);
		expect(deleteModelSpec).toHaveBeenCalledWith(pins);
		expect(archiveModelSpec).not.toHaveBeenCalled();
		expect(options.show).toHaveBeenCalledWith("模型已删除：失败模型");
	});

	it("archives only after explicit confirmation when deletion is blocked by history", async () => {
		vi.mocked(deleteModelSpec).mockRejectedValue(failure("MODEL_SPEC_DELETE_IN_USE"));
		await useCatalogActions(options).removeModel(model);
		expect(confirm).toHaveBeenCalledTimes(2);
		expect(confirm.mock.calls[1][0]).toContain("保留历史记录和物理表");
		expect(archiveModelSpec).toHaveBeenCalledWith(pins);
		expect(options.show).toHaveBeenCalledWith("模型已归档：失败模型");
		expect(options.requestedModelIdRef.current).toBe("");
		expect(options.reload).toHaveBeenCalledOnce();
		expect(options.savingRef.current).toBe(false);
	});

	it("keeps the model selected when archive confirmation is cancelled", async () => {
		vi.mocked(deleteModelSpec).mockRejectedValue(failure("MODEL_SPEC_DELETE_IN_USE"));
		confirm.mockReturnValueOnce(true).mockReturnValueOnce(false);
		await useCatalogActions(options).removeModel(model);
		expect(archiveModelSpec).not.toHaveBeenCalled();
		expect(options.requestedModelIdRef.current).toBe(model.id);
		expect(options.reload).not.toHaveBeenCalled();
		expect(options.show).not.toHaveBeenCalled();
		expect(options.savingRef.current).toBe(false);
		expect(options.setSaving).toHaveBeenLastCalledWith(false);
	});

	it.each(["MODEL_SPEC_DELETE_REFERENCED", "MODEL_SPEC_REVISION_CONFLICT"])(
		"does not attempt archive for %s",
		async (code) => {
			vi.mocked(deleteModelSpec).mockRejectedValue(failure(code));
			await useCatalogActions(options).removeModel(model);
			expect(archiveModelSpec).not.toHaveBeenCalled();
			expect(confirm).toHaveBeenCalledOnce();
			expect(options.setFailure).toHaveBeenLastCalledWith(expect.objectContaining({ code }));
		},
	);

	it("reports archive failure and preserves the selection", async () => {
		vi.mocked(deleteModelSpec).mockRejectedValue(failure("MODEL_SPEC_DELETE_IN_USE"));
		vi.mocked(archiveModelSpec).mockRejectedValue(failure("MODEL_SPEC_ARCHIVE_REFERENCED"));
		await useCatalogActions(options).removeModel(model);
		expect(options.setFailure).toHaveBeenLastCalledWith(
			expect.objectContaining({ message: "模型归档失败。", code: "MODEL_SPEC_ARCHIVE_REFERENCED" }),
		);
		expect(options.show).not.toHaveBeenCalled();
		expect(options.requestedModelIdRef.current).toBe(model.id);
		expect(options.savingRef.current).toBe(false);
	});
});
