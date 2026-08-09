// @vitest-environment jsdom

import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { ModelSpecStageGate, ReleaseCandidate, ReleaseCandidateWorkbench } from "@/api/modelSpecApi";
import type { ModelImplementationView } from "@/features/modeling/contracts/modelImplementationContract";
import type { ModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
import { ModelPublishDialog } from "./ModelPublishDialog";
import { ModelWorkbenchDialog } from "./ModelWorkbenchDialog";

const apiMocks = vi.hoisted(() => ({
	createCandidate: vi.fn(),
	getWorkbench: vi.fn(),
	lockCandidate: vi.fn(),
	publishCandidate: vi.fn(),
	retryCandidate: vi.fn(),
	rematerializeCandidate: vi.fn(),
	startBuildIntent: vi.fn(),
	startPublicationIntent: vi.fn(),
	getStageGates: vi.fn(),
	getLifecycle: vi.fn(),
	compileLifecycle: vi.fn(),
	getRepresentation: vi.fn(),
	createDbtDraft: vi.fn(),
	saveDbtFiles: vi.fn(),
	validateDbtDraft: vi.fn(),
	commitDbtDraft: vi.fn(),
}));

vi.mock("@/api/modelSpecApi", async (importOriginal) => ({
	...(await importOriginal<typeof import("@/api/modelSpecApi")>()),
	createReleaseCandidate: apiMocks.createCandidate,
	getReleaseCandidateWorkbench: apiMocks.getWorkbench,
	lockReleaseCandidate: apiMocks.lockCandidate,
	publishReleaseCandidate: apiMocks.publishCandidate,
	retryReleaseCandidate: apiMocks.retryCandidate,
	rematerializeReleaseCandidate: apiMocks.rematerializeCandidate,
	startModelBuildIntent: apiMocks.startBuildIntent,
	startModelPublicationIntent: apiMocks.startPublicationIntent,
	getModelSpecStageGates: apiMocks.getStageGates,
	getModelLifecycle: apiMocks.getLifecycle,
	compileModelLifecycle: apiMocks.compileLifecycle,
}));

vi.mock("@/api/modelRepresentationApi", () => ({ getModelRepresentation: apiMocks.getRepresentation }));

vi.mock("@/api/dbtImplementationDraftApi", () => ({
	commitDbtImplementationDraft: apiMocks.commitDbtDraft,
	createDbtImplementationDraft: apiMocks.createDbtDraft,
	saveDbtImplementationDraftFiles: apiMocks.saveDbtFiles,
	validateDbtImplementationDraft: apiMocks.validateDbtDraft,
}));

vi.mock("react-router", async (importOriginal) => ({
	...(await importOriginal<typeof import("react-router")>()),
	useNavigate: () => vi.fn(),
}));

const model = {
	id: "10000000-0000-0000-0000-000000000001",
	planId: "20000000-0000-0000-0000-000000000001",
	name: "预算事实表",
	revision: 3,
	checksum: "model-checksum",
	contractVersion: 2,
	compatibilityMode: "CANONICAL",
	implementationMode: "DESIGNER_GENERATED",
	materialization: "table",
} as unknown as ModelSpecView;
const secondModel = {
	...model,
	id: "10000000-0000-0000-0000-000000000002",
	name: "预算科目维度表",
} as ModelSpecView;

const implementation = {
	id: "40000000-0000-0000-0000-000000000001",
	modelSpecId: model.id,
	planId: model.planId,
	revision: model.revision,
	modelChecksum: model.checksum,
	ownership: "DESIGNER_GENERATED",
	projectKey: "system-managed",
	dbtUniqueId: `model.${model.id}`,
	status: "ACTIVE",
	implementationRevision: 1,
	implementationChecksum: "implementation-checksum",
	inputMode: "GENERATED",
	inputs: [{ generatorType: "DATE_DIMENSION", config: {} }],
	fieldMappings: [],
	settings: { targetPhysicalName: "dim_budget_date", loadStrategy: "FULL", partitionFields: [] },
	materialization: "table",
} satisfies ModelImplementationView;

const candidate = (origin: ReleaseCandidate["origin"], status = "DRAFT"): ReleaseCandidate =>
	({
		id: "30000000-0000-0000-0000-000000000001",
		planId: model.planId,
		version: 4,
		origin,
		status,
		entries: [{ modelSpecId: model.id }],
	}) as ReleaseCandidate;

const workspace = (
	allowedActions: ReleaseCandidateWorkbench["allowedActions"],
	current: ReleaseCandidate | null,
): ReleaseCandidateWorkbench =>
	({
		planId: model.planId,
		state: current ? "READY" : "EMPTY",
		candidate: current,
		allowedActions,
		evidence: [],
		entryEvidence: [],
		primaryBlocker: null,
		etag: null,
	}) as ReleaseCandidateWorkbench;

let container: HTMLDivElement;
let root: Root;

const flush = async () => {
	await act(async () => {
		await Promise.resolve();
		await Promise.resolve();
	});
};

const button = (label: string) =>
	Array.from(container.querySelectorAll("button")).find((item) => item.textContent?.trim() === label);

beforeEach(() => {
	(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
	container = document.createElement("div");
	document.body.appendChild(container);
	root = createRoot(container);
	Object.values(apiMocks).forEach((mock) => mock.mockReset());
	apiMocks.getLifecycle.mockResolvedValue({ implementation, artifacts: [], events: [] });
	apiMocks.compileLifecycle.mockResolvedValue({ implementation, artifacts: [], event: {} });
	vi.stubGlobal("crypto", { randomUUID: vi.fn(() => "idem-1") });
});

afterEach(async () => {
	await act(async () => root.unmount());
	container.remove();
	vi.unstubAllGlobals();
});

describe("release and materialization dispatch", () => {
	it("creates and locks a plan-owned candidate with the selected model UUID", async () => {
		const created = candidate("BATCH_WORKBENCH");
		apiMocks.getWorkbench.mockResolvedValue(workspace(["CREATE_CANDIDATE"], null));
		apiMocks.createCandidate.mockResolvedValue({ candidate: created });
		apiMocks.lockCandidate.mockResolvedValue({ candidate: created });

		await act(async () =>
			root.render(<ModelWorkbenchDialog canMaintain dialog="publish" model={model} onClose={vi.fn()} />),
		);
		await flush();
		await act(async () => button("创建并运行")?.click());

		expect(apiMocks.getLifecycle).toHaveBeenCalledWith(model.id);
		expect(apiMocks.compileLifecycle).toHaveBeenCalledWith(model, implementation, "idem-1");
		expect(apiMocks.createCandidate).toHaveBeenCalledWith(
			model.planId,
			"idem-1",
			expect.objectContaining({
				entries: [{ modelSpecId: model.id, sortOrder: 0, selectedReason: "从模型工作台选择" }],
			}),
		);
		expect(apiMocks.lockCandidate).toHaveBeenCalledWith(model.planId, created, "idem-1", "从模型工作台启动构建");
		expect(apiMocks.compileLifecycle.mock.invocationCallOrder[0]).toBeLessThan(
			apiMocks.createCandidate.mock.invocationCallOrder[0],
		);
		expect(apiMocks.createCandidate.mock.invocationCallOrder[0]).toBeLessThan(
			apiMocks.lockCandidate.mock.invocationCallOrder[0],
		);
		expect(apiMocks.startBuildIntent).not.toHaveBeenCalled();
	});

	it("does not create a release candidate when lifecycle compilation fails", async () => {
		apiMocks.getWorkbench.mockResolvedValue(workspace(["CREATE_CANDIDATE"], null));
		apiMocks.compileLifecycle.mockRejectedValue(new Error("当前模型实现编译失败"));

		await act(async () =>
			root.render(<ModelWorkbenchDialog canMaintain dialog="publish" model={model} onClose={vi.fn()} />),
		);
		await flush();
		await act(async () => button("创建并运行")?.click());

		expect(apiMocks.createCandidate).not.toHaveBeenCalled();
		expect(apiMocks.lockCandidate).not.toHaveBeenCalled();
		expect(container.textContent).toContain("当前模型实现编译失败");
	});

	it("compiles and submits one bounded candidate scope for multiple selected models", async () => {
		const created = candidate("BATCH_WORKBENCH");
		apiMocks.getWorkbench.mockResolvedValue(workspace(["CREATE_CANDIDATE"], null));
		apiMocks.createCandidate.mockResolvedValue({ candidate: created });
		apiMocks.lockCandidate.mockResolvedValue({ candidate: created });

		await act(async () =>
			root.render(<ModelPublishDialog canMaintain models={[model, secondModel]} onClose={vi.fn()} />),
		);
		await flush();
		await act(async () => button("创建并运行 2 个模型")?.click());

		expect(apiMocks.compileLifecycle).toHaveBeenCalledTimes(2);
		expect(apiMocks.createCandidate).toHaveBeenCalledWith(
			model.planId,
			"idem-1",
			expect.objectContaining({
				entries: [
					{ modelSpecId: model.id, sortOrder: 0, selectedReason: "从模型工作台选择" },
					{ modelSpecId: secondModel.id, sortOrder: 1, selectedReason: "从模型工作台选择" },
				],
			}),
		);
	});

	it("does not create a release candidate when the saved implementation is missing", async () => {
		apiMocks.getWorkbench.mockResolvedValue(workspace(["CREATE_CANDIDATE"], null));
		apiMocks.getLifecycle.mockResolvedValue({ implementation: null, artifacts: [], events: [] });

		await act(async () =>
			root.render(<ModelWorkbenchDialog canMaintain dialog="publish" model={model} onClose={vi.fn()} />),
		);
		await flush();
		await act(async () => button("创建并运行")?.click());

		expect(apiMocks.compileLifecycle).not.toHaveBeenCalled();
		expect(apiMocks.createCandidate).not.toHaveBeenCalled();
		expect(container.textContent).toContain("当前模型尚未保存可编译的数据实现");
	});

	it("uses retry for a failed candidate instead of replaying the single-model build intent", async () => {
		const failed = candidate("BATCH_WORKBENCH", "BUILD_FAILED");
		apiMocks.getWorkbench.mockResolvedValue(workspace(["RETRY_BUILD"], failed));
		apiMocks.retryCandidate.mockResolvedValue({ candidate: failed });

		await act(async () =>
			root.render(<ModelWorkbenchDialog canMaintain dialog="publish" model={model} onClose={vi.fn()} />),
		);
		await flush();
		await act(async () => button("重试构建")?.click());

		expect(apiMocks.retryCandidate).toHaveBeenCalledWith(model.planId, failed, "idem-1", "从模型工作台重试构建");
		expect(apiMocks.startBuildIntent).not.toHaveBeenCalled();
	});

	it("shows durable physical evidence and atomically rematerializes a successful candidate", async () => {
		const built = candidate("BATCH_WORKBENCH", "BUILT");
		apiMocks.getWorkbench.mockResolvedValue({
			...workspace(["RUN_QUALITY", "REMATERIALIZE"], built),
			entryEvidence: [
				{
					candidateEntryId: "entry-1",
					modelSpecId: model.id,
					modelName: model.name,
					modelRevision: model.revision,
					implementationRevision: 1,
					targetRelation: "public.dim_budget_date",
					runStatus: "BUILT",
					relationState: "VERIFIED",
					attempt: 2,
					finishedAt: "2026-08-09T02:47:00Z",
					observedAt: "2026-08-09T02:47:01Z",
				},
			],
		});
		apiMocks.rematerializeCandidate.mockResolvedValue({ candidate: built });

		await act(async () =>
			root.render(<ModelWorkbenchDialog canMaintain dialog="publish" model={model} onClose={vi.fn()} />),
		);
		await flush();

		expect(container.textContent).toContain("public.dim_budget_date");
		expect(container.textContent).toContain("关系已核验");
		await act(async () => button("重新物化")?.click());

		expect(apiMocks.rematerializeCandidate).toHaveBeenCalledWith(
			model.planId,
			built,
			"idem-1",
			expect.objectContaining({
				entries: [{ modelSpecId: model.id, sortOrder: 0, selectedReason: "从模型工作台选择" }],
			}),
		);
	});

	it("publishes a batch candidate through the plan-owned endpoint", async () => {
		const approved = candidate("BATCH_WORKBENCH", "APPROVED");
		apiMocks.getWorkbench.mockResolvedValue(workspace(["PUBLISH"], approved));
		apiMocks.publishCandidate.mockResolvedValue({ candidate: approved });

		await act(async () =>
			root.render(<ModelWorkbenchDialog canMaintain dialog="publish" model={model} onClose={vi.fn()} />),
		);
		await flush();
		await act(async () => button("发布模型")?.click());
		await act(async () => button("发布")?.click());

		expect(apiMocks.publishCandidate).toHaveBeenCalledWith(model.planId, approved, "idem-1", "从模型工作台发布");
		expect(apiMocks.startPublicationIntent).not.toHaveBeenCalled();
	});
});

describe("stage gate dispatch", () => {
	it("binds submit check to DESIGNED instead of future RELEASE_READY blockers", async () => {
		apiMocks.getStageGates.mockResolvedValue([
			{
				modelSpecId: model.id,
				revision: model.revision,
				checksum: model.checksum,
				stage: "DESIGNED",
				status: "READY",
				blockers: [],
			},
			{
				modelSpecId: model.id,
				revision: model.revision,
				checksum: model.checksum,
				stage: "RELEASE_READY",
				status: "BLOCKED",
				blockers: [
					{
						code: "MODEL_SPEC_PERMISSION_EVIDENCE_STALE",
						field: "fields",
						message: "字段权限分级未完成",
						repairRoute: "/modeling/models/model-1?tab=governance",
					},
				],
			},
		] satisfies ModelSpecStageGate[]);

		await act(async () =>
			root.render(<ModelWorkbenchDialog canMaintain dialog="gates" model={model} onClose={vi.fn()} />),
		);
		await flush();

		expect(container.textContent).toContain("设计提交检查");
		expect(container.querySelectorAll("tbody tr")).toHaveLength(1);
		expect(container.querySelector("tbody")?.textContent).toContain("DESIGNED");
		expect(container.textContent).not.toContain("MODEL_SPEC_PERMISSION_EVIDENCE_STALE");
	});
});

describe("advanced dbt draft lifecycle", () => {
	it("does not request the technical representation for a read-only account", async () => {
		await act(async () =>
			root.render(<ModelWorkbenchDialog canMaintain={false} dialog="advanced" model={model} onClose={vi.fn()} />),
		);
		await flush();

		expect(apiMocks.getRepresentation).not.toHaveBeenCalled();
		expect(container.textContent).toContain("无高级实现维护权限");
	});

	it("locks a committed draft and offers a new draft instead of allowing further edits", async () => {
		apiMocks.getRepresentation.mockResolvedValue({
			allowedActions: ["OPEN_ADVANCED_DBT"],
			capabilityReasons: [],
			implementationRevision: 2,
			implementationChecksum: "impl-2",
			ownershipMode: "DBT_MANAGED",
		});
		apiMocks.createDbtDraft.mockResolvedValue({
			draftId: "draft-1",
			planId: model.planId,
			modelSpecId: model.id,
			baseModelRevision: model.revision,
			baseModelChecksum: model.checksum,
			state: "DRAFT",
			etag: "e1",
			expiresAt: "2026-08-04T00:00:00Z",
			sourceBundle: { files: [{ path: "models/budget.sql", content: "select 1" }] },
		});
		apiMocks.saveDbtFiles.mockResolvedValue({ etag: "e2", expiresAt: "2026-08-04T00:00:00Z" });
		apiMocks.validateDbtDraft.mockResolvedValue({
			draftId: "draft-1",
			state: "VALIDATED",
			etag: "e3",
			expiresAt: "2026-08-04T00:00:00Z",
			validatedChecksum: "validated-1",
			diagnostics: [],
			proposedStructure: [],
		});
		apiMocks.commitDbtDraft.mockResolvedValue({
			draftId: "draft-1",
			modelSpecId: model.id,
			modelRevision: 4,
			modelChecksum: "model-4",
			implementationId: "impl-1",
			implementationRevision: 3,
			implementationChecksum: "impl-3",
			artifactCount: 2,
			etag: "e4",
		});

		await act(async () =>
			root.render(<ModelWorkbenchDialog canMaintain dialog="advanced" model={model} onClose={vi.fn()} />),
		);
		await flush();
		expect(apiMocks.getRepresentation).toHaveBeenNthCalledWith(1, model.id, {
			modelRevision: model.revision,
			implementationRevision: implementation.implementationRevision,
			representationScope: "TECHNICAL",
		});
		await act(async () => button("创建高级草稿")?.click());
		await flush();
		await act(async () => button("校验")?.click());
		await flush();
		await act(async () => button("提交实现")?.click());
		await flush();

		expect(button("创建新草稿")).toBeDefined();
		expect(
			(container.querySelector('textarea[aria-label="编辑 models/budget.sql"]') as HTMLTextAreaElement).disabled,
		).toBe(true);
		expect(container.textContent).toContain("状态：COMMITTED");
		await act(async () => button("创建新草稿")?.click());
		await flush();
		expect(apiMocks.getRepresentation).toHaveBeenLastCalledWith(model.id, {
			modelRevision: 4,
			implementationRevision: 3,
			representationScope: "TECHNICAL",
		});
		expect(apiMocks.createDbtDraft).toHaveBeenLastCalledWith(
			model.id,
			expect.objectContaining({ baseModelRevision: 4, baseModelChecksum: "model-4" }),
		);
	});
});
