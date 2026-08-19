// @vitest-environment jsdom

import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
import type {
	MaterializationPlanPreview,
	ModelSpecStageGate,
	PlanExecutionWorkspace,
	ReleaseCandidate,
	ReleaseCandidateWorkbench,
} from "@/api/modelSpecApi";
import type { ModelImplementationView } from "@/features/modeling/contracts/modelImplementationContract";
import type { ModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
import { AdvancedDbtWorkspace } from "./AdvancedDbtWorkspace";
import { ModelPublishDialog } from "./ModelPublishDialog";
import { ModelWorkbenchDialog } from "./ModelWorkbenchDialog";

// DbtCodeEditor 经 configureMonaco 运行时引入 monaco-editor，而该包只有 module 字段、
// 没有 main/exports，vitest 的 node 解析会失败。本用例考的是草稿生命周期锁定，
// 与编辑器内部无关，直接桩掉这个惰性加载的组件。
vi.mock("./DbtCodeEditor", () => ({
	// 用一个等价可观测的 textarea 顶替 Monaco：保留 aria-label 与只读态，
	// 这样“已提交草稿不可再编辑”的断言依然成立。
	DbtCodeEditor: ({ path, content, readOnly }: { path: string; content: string; readOnly: boolean }) => (
		<textarea aria-label={`编辑 ${path}`} disabled={readOnly} readOnly={readOnly} value={content} onChange={() => {}} />
	),
	dbtEditorLanguage: "sql",
	isDbtSaveShortcut: () => false,
}));

beforeAll(() => {
	if (!window.matchMedia) {
		Object.defineProperty(window, "matchMedia", {
			writable: true,
			value: (query: string) => ({
				matches: false,
				media: query,
				onchange: null,
				addListener: () => {},
				removeListener: () => {},
				addEventListener: () => {},
				removeEventListener: () => {},
				dispatchEvent: () => false,
			}),
		});
	}
});

const apiMocks = vi.hoisted(() => ({
	cancelCandidate: vi.fn(),
	createCandidate: vi.fn(),
	createReplacementCandidate: vi.fn(),
	getExecutionWorkspace: vi.fn(),
	getModelSpec: vi.fn(),
	getMaterializationStatuses: vi.fn(),
	previewMaterializationPlan: vi.fn(),
	repairExecutionBinding: vi.fn(),
	runExecutionNow: vi.fn(),
	getWorkbench: vi.fn(),
	lockCandidate: vi.fn(),
	runQualityCandidate: vi.fn(),
	submitReviewCandidate: vi.fn(),
	approveCandidate: vi.fn(),
	rejectCandidate: vi.fn(),
	publishCandidate: vi.fn(),
	retryRegistrationCandidate: vi.fn(),
	rollbackCandidate: vi.fn(),
	refreshCandidate: vi.fn(),
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
	cancelReleaseCandidate: apiMocks.cancelCandidate,
	createReleaseCandidate: apiMocks.createCandidate,
	createReplacementReleaseCandidate: apiMocks.createReplacementCandidate,
	getPlanExecutionWorkspace: apiMocks.getExecutionWorkspace,
	getModelSpec: apiMocks.getModelSpec,
	getModelMaterializationStatuses: apiMocks.getMaterializationStatuses,
	previewMaterializationPlan: apiMocks.previewMaterializationPlan,
	repairPlanExecutionBinding: apiMocks.repairExecutionBinding,
	runPlanExecutionNow: apiMocks.runExecutionNow,
	getReleaseCandidateWorkbench: apiMocks.getWorkbench,
	lockReleaseCandidate: apiMocks.lockCandidate,
	runReleaseCandidateQuality: apiMocks.runQualityCandidate,
	submitReleaseCandidateReview: apiMocks.submitReviewCandidate,
	approveReleaseCandidateReview: apiMocks.approveCandidate,
	rejectReleaseCandidateReview: apiMocks.rejectCandidate,
	publishReleaseCandidate: apiMocks.publishCandidate,
	retryReleaseCandidateRegistration: apiMocks.retryRegistrationCandidate,
	rollbackReleaseCandidate: apiMocks.rollbackCandidate,
	refreshReleaseCandidate: apiMocks.refreshCandidate,
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

const materializationPreview = (requestedModelSpecIds: string[]): MaterializationPlanPreview => ({
	planId: model.planId,
	environment: "dev",
	strategy: "WITH_MISSING_UPSTREAMS",
	planChecksum: "a".repeat(64),
	canStart: true,
	requestedModelSpecIds,
	orderedEntries: requestedModelSpecIds.map((modelSpecId, topologyLevel) => ({
		modelSpecId,
		modelName: modelSpecId === model.id ? model.name : secondModel.name,
		modelRevision: 3,
		modelChecksum: "b".repeat(64),
		implementationRevision: 1,
		implementationChecksum: "c".repeat(64),
		dependencyChecksum: "d".repeat(64),
		layer: "DWD",
		dependencyRole: "ROOT",
		topologyLevel,
		action: "BUILD",
		reasonCode: "REQUESTED_MODEL",
	})),
	blockers: [],
});

let container: HTMLDivElement;
let root: Root;

const flush = async () => {
	await act(async () => {
		await Promise.resolve();
		await Promise.resolve();
	});
};

const button = (label: string) => {
	const normalize = (text: string) => text.replace(/\s+/g, "");
	return Array.from(container.querySelectorAll("button")).find(
		(item) => normalize(item.textContent ?? "") === normalize(label),
	);
};

beforeEach(() => {
	(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
	container = document.createElement("div");
	document.body.appendChild(container);
	root = createRoot(container);
	Object.values(apiMocks).forEach((mock) => mock.mockReset());
	apiMocks.getExecutionWorkspace.mockResolvedValue({
		planId: model.planId,
		state: "NOT_DEPLOYED",
		bindings: [],
	} satisfies PlanExecutionWorkspace);
	apiMocks.getMaterializationStatuses.mockResolvedValue([]);
	apiMocks.getModelSpec.mockImplementation((id: string) =>
		Promise.resolve(id === secondModel.id ? secondModel : model),
	);
	apiMocks.previewMaterializationPlan.mockImplementation(
		(_planId: string, request: { requestedModelSpecIds: string[] }) =>
			Promise.resolve(materializationPreview(request.requestedModelSpecIds)),
	);
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
				materializationPlanChecksum: "a".repeat(64),
				strategy: "WITH_MISSING_UPSTREAMS",
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

	it("renders the server-owned BUILD and REUSE dependency plan before candidate creation", async () => {
		apiMocks.getWorkbench.mockResolvedValue(workspace(["CREATE_CANDIDATE"], null));
		apiMocks.previewMaterializationPlan.mockResolvedValue({
			...materializationPreview([model.id]),
			orderedEntries: [
				{
					...materializationPreview([secondModel.id]).orderedEntries[0],
					modelName: secondModel.name,
					dependencyRole: "DIMENSION",
					action: "REUSE",
					reasonCode: "EXACT_VERIFIED_RELATION",
					targetRelation: "public.dim_budget_subject",
				},
				materializationPreview([model.id]).orderedEntries[0],
			],
		});

		await act(async () =>
			root.render(<ModelWorkbenchDialog canMaintain dialog="publish" model={model} onClose={vi.fn()} />),
		);
		await flush();

		expect(container.textContent).toContain("依赖物化计划");
		expect(container.textContent).toContain("REUSE");
		expect(container.textContent).toContain("BUILD");
		expect(container.textContent).toContain("public.dim_budget_subject");
		expect(container.textContent).toContain("aaaaaaaaaaaa…");
	});

	it("compiles every server-planned BUILD node including an automatically expanded upstream", async () => {
		const created = candidate("BATCH_WORKBENCH");
		apiMocks.getWorkbench.mockResolvedValue(workspace(["CREATE_CANDIDATE"], null));
		apiMocks.createCandidate.mockResolvedValue({ candidate: created });
		apiMocks.lockCandidate.mockResolvedValue({ candidate: created });
		apiMocks.previewMaterializationPlan.mockResolvedValue({
			...materializationPreview([model.id]),
			orderedEntries: [
				{
					...materializationPreview([secondModel.id]).orderedEntries[0],
					dependencyRole: "UPSTREAM",
					reasonCode: "UPSTREAM_MATERIALIZATION_REQUIRED",
				},
				materializationPreview([model.id]).orderedEntries[0],
			],
		});

		await act(async () =>
			root.render(<ModelWorkbenchDialog canMaintain dialog="publish" model={model} onClose={vi.fn()} />),
		);
		await flush();
		await act(async () => button("创建并运行")?.click());

		expect(apiMocks.getModelSpec).toHaveBeenCalledWith(secondModel.id);
		expect(apiMocks.compileLifecycle).toHaveBeenCalledTimes(2);
		expect(apiMocks.compileLifecycle).toHaveBeenCalledWith(secondModel, implementation, "idem-1");
		expect(apiMocks.compileLifecycle).toHaveBeenCalledWith(model, implementation, "idem-1");
		expect(apiMocks.createCandidate).toHaveBeenCalled();
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
				materializationPlanChecksum: "a".repeat(64),
			}),
		);
	});

	it("matches an expanded candidate by its roots and previews only those roots before rematerializing", async () => {
		const built = {
			...candidate("BATCH_WORKBENCH", "BUILT"),
			entries: [
				{ modelSpecId: secondModel.id, selectedReason: "AUTO_DEPENDENCY" },
				{ modelSpecId: model.id, selectedReason: "MATERIALIZATION_ROOT" },
			],
		} as ReleaseCandidate;
		apiMocks.getWorkbench.mockResolvedValue(workspace(["RUN_QUALITY", "REMATERIALIZE"], built));
		apiMocks.rematerializeCandidate.mockResolvedValue({ candidate: built });

		await act(async () =>
			root.render(<ModelWorkbenchDialog canMaintain dialog="publish" model={model} onClose={vi.fn()} />),
		);
		await flush();
		await act(async () => button("重新物化")?.click());

		expect(apiMocks.previewMaterializationPlan).toHaveBeenLastCalledWith(model.planId, {
			environment: "dev",
			requestedModelSpecIds: [model.id],
			strategy: "WITH_MISSING_UPSTREAMS",
		});
		expect(apiMocks.rematerializeCandidate).toHaveBeenCalledWith(
			model.planId,
			built,
			"idem-1",
			expect.objectContaining({
				entries: [{ modelSpecId: model.id, sortOrder: 0, selectedReason: "从模型工作台选择" }],
			}),
		);
	});

	it("shows only the selected model materialization when the plan workspace points at another model", async () => {
		const foreign = {
			...candidate("BATCH_WORKBENCH", "PUBLISHED"),
			version: 7,
			entries: [{ modelSpecId: secondModel.id }],
		} as ReleaseCandidate;
		apiMocks.getWorkbench.mockResolvedValue({
			...workspace(["CREATE_CANDIDATE", "ROLLBACK"], foreign),
			entryEvidence: [
				{
					candidateEntryId: "foreign-entry",
					modelSpecId: secondModel.id,
					modelName: "其他计划模型",
					modelRevision: 1,
					targetRelation: "public.other_model",
					runStatus: "BUILT",
					relationState: "VERIFIED",
				},
			],
		});
		apiMocks.getMaterializationStatuses.mockResolvedValue([
			{
				modelSpecId: model.id,
				candidateId: "selected-candidate",
				candidateVersion: 8,
				environment: "dev",
				candidateStatus: "STALE",
				candidateUpdatedAt: "2026-08-17T00:00:00Z",
				currentImplementationRevision: 1,
				evidence: {
					candidateEntryId: "selected-entry",
					modelSpecId: model.id,
					modelName: model.name,
					modelRevision: model.revision,
					targetRelation: "public.dim_budget_date",
					runStatus: "BUILT",
					relationState: "VERIFIED",
					attempt: 2,
				},
			},
		]);

		await act(async () =>
			root.render(<ModelWorkbenchDialog canMaintain dialog="publish" model={model} onClose={vi.fn()} />),
		);
		await flush();

		expect(apiMocks.getMaterializationStatuses).toHaveBeenCalledWith(model.planId, [model.id]);
		expect(container.textContent).toContain("STALE · v8");
		expect(container.textContent).toContain("public.dim_budget_date");
		expect(container.textContent).not.toContain("public.other_model");
	});

	it("cancels an unpublished candidate and creates a fresh candidate when the selected scope changed", async () => {
		const built = candidate("BATCH_WORKBENCH", "BUILT");
		const cancelled = { ...built, status: "CANCELLED", version: 5 } as ReleaseCandidate;
		const replacement = {
			...candidate("BATCH_WORKBENCH"),
			id: "30000000-0000-0000-0000-000000000002",
			version: 1,
			entries: [{ modelSpecId: secondModel.id }],
		} as ReleaseCandidate;
		apiMocks.getWorkbench.mockResolvedValue(workspace(["RUN_QUALITY", "CANCEL_CANDIDATE", "REMATERIALIZE"], built));
		apiMocks.cancelCandidate.mockResolvedValue({ candidate: cancelled });
		apiMocks.createCandidate.mockResolvedValue({ candidate: replacement });
		apiMocks.lockCandidate.mockResolvedValue({ candidate: replacement });

		await act(async () => root.render(<ModelPublishDialog canMaintain models={[secondModel]} onClose={vi.fn()} />));
		await flush();
		await act(async () => button("替换候选并物化")?.click());

		expect(apiMocks.cancelCandidate).toHaveBeenCalledWith(
			model.planId,
			built,
			"idem-1",
			"所选模型范围已变化，关闭旧候选",
		);
		expect(apiMocks.createCandidate).toHaveBeenCalledWith(
			model.planId,
			"idem-1",
			expect.objectContaining({
				entries: [{ modelSpecId: secondModel.id, sortOrder: 0, selectedReason: "从模型工作台选择" }],
			}),
		);
		expect(apiMocks.rematerializeCandidate).not.toHaveBeenCalled();
		expect(apiMocks.createReplacementCandidate).not.toHaveBeenCalled();
		expect(apiMocks.lockCandidate).toHaveBeenCalledWith(
			model.planId,
			replacement,
			"idem-1",
			"从模型工作台启动新范围构建",
		);
	});

	it("refreshes a drifted candidate and creates a fresh candidate when the selected scope is different", async () => {
		const reviewPending = candidate("BATCH_WORKBENCH", "REVIEW_PENDING");
		const stale = { ...reviewPending, status: "STALE", version: 5 } as ReleaseCandidate;
		const created = {
			...candidate("BATCH_WORKBENCH"),
			id: "30000000-0000-0000-0000-000000000002",
			version: 1,
			entries: [{ modelSpecId: secondModel.id }],
		} as ReleaseCandidate;
		apiMocks.getWorkbench.mockResolvedValue(workspace(["REFRESH_CANDIDATE"], reviewPending));
		apiMocks.refreshCandidate.mockResolvedValue({ candidate: stale });
		apiMocks.createCandidate.mockResolvedValue({ candidate: created });
		apiMocks.lockCandidate.mockResolvedValue({ candidate: created });

		await act(async () => root.render(<ModelPublishDialog canMaintain models={[secondModel]} onClose={vi.fn()} />));
		await flush();
		await act(async () => button("按新范围重新物化")?.click());

		expect(apiMocks.refreshCandidate).toHaveBeenCalledWith(
			model.planId,
			reviewPending,
			"idem-1",
			"模型已发生新修订，废弃旧候选",
		);
		expect(apiMocks.createCandidate).toHaveBeenCalledWith(
			model.planId,
			"idem-1",
			expect.objectContaining({
				entries: [{ modelSpecId: secondModel.id, sortOrder: 0, selectedReason: "从模型工作台选择" }],
			}),
		);
		expect(apiMocks.createReplacementCandidate).not.toHaveBeenCalled();
		expect(apiMocks.lockCandidate).toHaveBeenCalledWith(model.planId, created, "idem-1", "从模型工作台启动新范围构建");
	});

	it("supersedes a drifted review candidate and builds a replacement without reviewer approval", async () => {
		const reviewPending = candidate("BATCH_WORKBENCH", "REVIEW_PENDING");
		const stale = { ...reviewPending, status: "STALE", version: 5 } as ReleaseCandidate;
		const replacement = {
			...candidate("BATCH_WORKBENCH"),
			id: "30000000-0000-0000-0000-000000000002",
			version: 1,
		} as ReleaseCandidate;
		apiMocks.getWorkbench.mockResolvedValue(workspace(["REFRESH_CANDIDATE"], reviewPending));
		apiMocks.refreshCandidate.mockResolvedValue({ candidate: stale });
		apiMocks.createReplacementCandidate.mockResolvedValue({ candidate: replacement });
		apiMocks.lockCandidate.mockResolvedValue({ candidate: replacement });

		await act(async () =>
			root.render(<ModelWorkbenchDialog canMaintain dialog="publish" model={model} onClose={vi.fn()} />),
		);
		await flush();
		await act(async () => button("按新修订重新物化")?.click());

		expect(apiMocks.refreshCandidate).toHaveBeenCalledWith(
			model.planId,
			reviewPending,
			"idem-1",
			"模型已发生新修订，废弃旧候选",
		);
		expect(apiMocks.createReplacementCandidate).toHaveBeenCalledWith(
			model.planId,
			stale,
			"idem-1",
			expect.objectContaining({
				entries: [{ modelSpecId: model.id, sortOrder: 0, selectedReason: "从模型工作台选择" }],
			}),
		);
		expect(apiMocks.lockCandidate).toHaveBeenCalledWith(
			model.planId,
			replacement,
			"idem-1",
			"从模型工作台启动新修订构建",
		);
		expect(apiMocks.refreshCandidate.mock.invocationCallOrder[0]).toBeLessThan(
			apiMocks.createReplacementCandidate.mock.invocationCallOrder[0],
		);
		expect(apiMocks.createReplacementCandidate.mock.invocationCallOrder[0]).toBeLessThan(
			apiMocks.lockCandidate.mock.invocationCallOrder[0],
		);
		expect(apiMocks.rematerializeCandidate).not.toHaveBeenCalled();
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
		await act(async () => button("发布上线")?.click());

		expect(apiMocks.publishCandidate).toHaveBeenCalledWith(model.planId, approved, "idem-1", "从模型工作台发布");
		expect(apiMocks.startPublicationIntent).not.toHaveBeenCalled();
	});

	it("starts the strict single-model publication intent at the quality boundary", async () => {
		const built = candidate("SINGLE_MODEL_INTENT", "BUILT");
		apiMocks.getWorkbench.mockResolvedValue(workspace(["RUN_QUALITY"], built));
		apiMocks.startPublicationIntent.mockResolvedValue({ candidateId: built.id });

		await act(async () =>
			root.render(<ModelWorkbenchDialog canMaintain dialog="publish" model={model} onClose={vi.fn()} />),
		);
		await flush();
		await act(async () => button("发布模型")?.click());
		await act(async () => button("运行工程验证")?.click());

		expect(apiMocks.startPublicationIntent).toHaveBeenCalledWith(model.id, built, "idem-1", "从模型工作台发布");
		expect(apiMocks.runQualityCandidate).not.toHaveBeenCalled();
	});

	it("shows the data-owner self-service handoff without a review gate", async () => {
		const reviewPending = {
			...candidate("BATCH_WORKBENCH", "REVIEW_PENDING"),
			audit: {
				createdBy: "model-owner",
				createdAt: "2026-08-12T01:00:00Z",
				submittedBy: "model-owner",
				submittedAt: "2026-08-12T01:10:00Z",
				approvedBy: null,
				approvedAt: null,
				publishedBy: null,
				publishedAt: null,
			},
		} as ReleaseCandidate;
		apiMocks.getWorkbench.mockResolvedValue(workspace(["PUBLISH"], reviewPending));

		await act(async () =>
			root.render(<ModelWorkbenchDialog canMaintain dialog="publish" model={model} onClose={vi.fn()} />),
		);
		await flush();
		await act(async () => button("发布模型")?.click());

		expect(container.textContent).toContain("候选发布流程");
		expect(container.textContent).toContain("工程验证");
		expect(container.textContent).toContain("治理数据质量");
		expect(container.textContent).toContain("发布登记");
		expect(container.textContent).toContain("上线就绪");
		expect(container.textContent).toContain("无需另行审批");
		expect(container.textContent).not.toContain("发布评审");
		expect(container.textContent).toContain("提交人：model-owner");
		expect(button("发布上线")?.disabled).toBe(false);
	});

	it("does not present a published candidate as online while its plan binding is deploying", async () => {
		const published = {
			...candidate("BATCH_WORKBENCH", "PUBLISHED"),
			environment: "dev",
			executionTargetKey: "postgres-primary",
		} as ReleaseCandidate;
		apiMocks.getWorkbench.mockResolvedValue(workspace(["ROLLBACK"], published));
		apiMocks.getExecutionWorkspace.mockResolvedValue({
			planId: model.planId,
			state: "READY",
			bindings: [
				{
					id: "binding-1",
					version: 1,
					environment: "dev",
					state: "DEPLOYING",
					deploymentStatus: "DEPLOYING",
					scheduleMode: "MANUAL_ONLY",
					desiredDeploymentChecksum: "desired",
					airflowDagId: "dts_plan_dev",
					airflowState: "NOT_REGISTERED",
					latestOperationalRun: {},
					latestRelation: { verified: true, exists: true },
					allowedActions: [],
				},
			],
		} as PlanExecutionWorkspace);

		await act(async () =>
			root.render(<ModelWorkbenchDialog canMaintain dialog="publish" model={model} onClose={vi.fn()} />),
		);
		await flush();
		await act(async () => button("发布模型")?.click());

		expect(apiMocks.getExecutionWorkspace).toHaveBeenCalledWith(model.planId);
		expect(container.textContent).toContain("发布登记已完成");
		expect(container.textContent).toContain("运行计划部署中");
		expect(container.textContent).not.toContain("上线完成");
	});

	it("shows online completion only for an online binding with a healthy relation", async () => {
		const published = {
			...candidate("BATCH_WORKBENCH", "PUBLISHED"),
			environment: "dev",
		} as ReleaseCandidate;
		apiMocks.getWorkbench.mockResolvedValue(workspace(["ROLLBACK"], published));
		apiMocks.getExecutionWorkspace.mockResolvedValue({
			planId: model.planId,
			state: "READY",
			bindings: [
				{
					id: "binding-1",
					version: 2,
					environment: "dev",
					state: "ONLINE",
					deploymentStatus: "ACTIVE",
					scheduleMode: "MANUAL_ONLY",
					desiredDeploymentChecksum: "deployed",
					deployedChecksum: "deployed",
					airflowDagId: "dts_plan_dev",
					airflowState: "OBSERVED",
					latestOperationalRun: {},
					latestRelation: { verified: true, exists: true },
					allowedActions: ["RUN_NOW"],
				},
			],
		} as PlanExecutionWorkspace);

		await act(async () =>
			root.render(<ModelWorkbenchDialog canMaintain dialog="publish" model={model} onClose={vi.fn()} />),
		);
		await flush();
		await act(async () => button("发布模型")?.click());

		expect(container.textContent).toContain("上线完成");
		expect(container.textContent).toContain("关系健康");
	});

	it("lets the data owner run an online manual binding to produce operational relation evidence", async () => {
		const published = {
			...candidate("BATCH_WORKBENCH", "PUBLISHED"),
			environment: "dev",
		} as ReleaseCandidate;
		apiMocks.getWorkbench.mockResolvedValue(workspace(["ROLLBACK"], published));
		apiMocks.getExecutionWorkspace.mockResolvedValue({
			planId: model.planId,
			state: "READY",
			bindings: [
				{
					id: "binding-1",
					version: 1,
					environment: "dev",
					state: "ONLINE",
					deploymentStatus: "ACTIVE",
					scheduleMode: "MANUAL_ONLY",
					desiredDeploymentChecksum: "deployed",
					deployedChecksum: "deployed",
					airflowDagId: "dts_plan_dev",
					airflowState: "OBSERVED",
					latestOperationalRun: {},
					latestRelation: {},
					allowedActions: ["RUN_NOW"],
				},
			],
		} as PlanExecutionWorkspace);
		apiMocks.runExecutionNow.mockResolvedValue({
			pipelineRunGroupId: "run-group-1",
			bindingId: "binding-1",
			bindingVersion: 1,
			triggerType: "MANUAL",
			airflowDagId: "dts_plan_dev",
			airflowRunId: "airflow-run-1",
			status: "DISPATCHED",
			replayed: false,
		});

		await act(async () =>
			root.render(<ModelWorkbenchDialog canMaintain dialog="publish" model={model} onClose={vi.fn()} />),
		);
		await flush();
		await act(async () => button("发布模型")?.click());
		await act(async () => button("立即运行并核验")?.click());

		expect(apiMocks.runExecutionNow).toHaveBeenCalledWith(model.planId, "binding-1", "idem-1");
	});

	it("keeps the published execution binding operable when a newer draft candidate occupies the plan workspace", async () => {
		const draft = {
			...candidate("BATCH_WORKBENCH", "DRAFT"),
			environment: "dev",
		} as ReleaseCandidate;
		const publishedModel = { ...model, status: "PUBLISHED" } as ModelSpecView;
		apiMocks.getWorkbench.mockResolvedValue(workspace(["START_BUILD"], draft));
		apiMocks.getMaterializationStatuses.mockResolvedValue([
			{
				modelSpecId: model.id,
				candidateId: draft.id,
				candidateVersion: draft.version,
				environment: "dev",
				candidateStatus: "DRAFT",
				candidateUpdatedAt: "2026-08-17T04:07:03Z",
				currentImplementationRevision: 1,
				evidence: {
					candidateEntryId: draft.entries[0]?.id || "draft-entry",
					modelSpecId: model.id,
					modelName: model.name,
					modelRevision: model.revision,
					targetRelation: null,
					runStatus: null,
					relationState: "NOT_STARTED",
				},
			},
		]);
		apiMocks.getExecutionWorkspace.mockResolvedValue({
			planId: model.planId,
			state: "READY",
			bindings: [
				{
					id: "binding-1",
					version: 10,
					environment: "dev",
					state: "DEPLOYING",
					deploymentStatus: "DEPLOYING",
					scheduleMode: "MANUAL_ONLY",
					desiredDeploymentChecksum: "desired",
					deployedChecksum: "previous",
					airflowDagId: "dts_plan_dev",
					airflowState: "NOT_REGISTERED",
					latestOperationalRun: {},
					latestRelation: { verified: true, exists: true },
					allowedActions: ["REPAIR_DEPLOYMENT"],
				},
			],
		} as PlanExecutionWorkspace);
		apiMocks.repairExecutionBinding.mockResolvedValue({
			bindingId: "binding-1",
			bindingVersion: 11,
			deploymentStatus: "DEPLOYING",
		});

		await act(async () =>
			root.render(<ModelWorkbenchDialog canMaintain dialog="publish" model={publishedModel} onClose={vi.fn()} />),
		);
		await flush();

		expect(apiMocks.getExecutionWorkspace).toHaveBeenCalledWith(model.planId);
		expect(button("修复部署")?.disabled).toBe(false);
		await act(async () => button("修复部署")?.click());

		expect(apiMocks.repairExecutionBinding).toHaveBeenCalledWith(model.planId, "binding-1", 10);
		expect(apiMocks.compileLifecycle).not.toHaveBeenCalled();
		expect(apiMocks.createCandidate).not.toHaveBeenCalled();
	});

	it("runs an unchanged published model from its existing binding instead of creating another candidate", async () => {
		const publishedModel = { ...model, status: "PUBLISHED" } as ModelSpecView;
		apiMocks.getWorkbench.mockResolvedValue(workspace(["CREATE_CANDIDATE"], null));
		apiMocks.getMaterializationStatuses.mockResolvedValue([
			{
				modelSpecId: model.id,
				candidateId: "published-candidate",
				candidateVersion: 8,
				environment: "dev",
				candidateStatus: "PUBLISHED",
				candidateUpdatedAt: "2026-08-17T03:30:19Z",
				currentImplementationRevision: 1,
				evidence: {
					candidateEntryId: "published-entry",
					modelSpecId: model.id,
					modelName: model.name,
					modelRevision: model.revision,
					targetRelation: "public.biz_ads_budget_kpi_v2",
					runStatus: "BUILT",
					relationState: "VERIFIED",
				},
			},
		]);
		apiMocks.getExecutionWorkspace.mockResolvedValue({
			planId: model.planId,
			state: "READY",
			bindings: [
				{
					id: "binding-1",
					version: 11,
					environment: "dev",
					state: "ONLINE",
					deploymentStatus: "ACTIVE",
					scheduleMode: "MANUAL_ONLY",
					desiredDeploymentChecksum: "deployed",
					deployedChecksum: "deployed",
					airflowDagId: "dts_plan_dev",
					airflowState: "OBSERVED",
					latestOperationalRun: {},
					latestRelation: { verified: true, exists: true },
					allowedActions: ["RUN_NOW"],
				},
			],
		} as PlanExecutionWorkspace);
		apiMocks.runExecutionNow.mockResolvedValue({
			pipelineRunGroupId: "run-group-2",
			bindingId: "binding-1",
			bindingVersion: 11,
			triggerType: "MANUAL",
			airflowDagId: "dts_plan_dev",
			airflowRunId: "airflow-run-2",
			status: "DISPATCHED",
			replayed: false,
		});

		await act(async () =>
			root.render(<ModelWorkbenchDialog canMaintain dialog="publish" model={publishedModel} onClose={vi.fn()} />),
		);
		await flush();
		await act(async () => button("再次运行并核验")?.click());

		expect(apiMocks.runExecutionNow).toHaveBeenCalledWith(model.planId, "binding-1", "idem-1");
		expect(apiMocks.compileLifecycle).not.toHaveBeenCalled();
		expect(apiMocks.createCandidate).not.toHaveBeenCalled();
	});

	it("allows a release reviewer to act from server duties without model-maintainer permission", async () => {
		const reviewPending = candidate("BATCH_WORKBENCH", "REVIEW_PENDING");
		apiMocks.getWorkbench.mockResolvedValue(workspace(["APPROVE", "REJECT"], reviewPending));
		apiMocks.approveCandidate.mockResolvedValue({ candidate: reviewPending });

		await act(async () =>
			root.render(<ModelWorkbenchDialog canMaintain={false} dialog="publish" model={model} onClose={vi.fn()} />),
		);
		await flush();
		await act(async () => button("发布模型")?.click());
		expect(button("审核通过")?.disabled).toBe(false);
		await act(async () => button("审核通过")?.click());

		expect(apiMocks.approveCandidate).toHaveBeenCalledWith(model.planId, reviewPending, "idem-1", "从模型工作台发布");
	});

	it.each([
		["BUILT", "RUN_QUALITY", "运行工程验证", "runQualityCandidate"],
		["QUALITY_PASSED", "SUBMIT_REVIEW", "提交发布评审", "submitReviewCandidate"],
		["REVIEW_PENDING", "APPROVE", "审核通过", "approveCandidate"],
		["REVIEW_PENDING", "REJECT", "驳回", "rejectCandidate"],
		["PARTIAL", "RETRY_PUBLICATION", "重试发布", "retryRegistrationCandidate"],
		["PUBLISHED", "ROLLBACK", "回滚发布", "rollbackCandidate"],
	] as const)(
		"dispatches %s candidate action %s from the release workflow",
		async (status, action, label, mockName) => {
			const current = candidate("BATCH_WORKBENCH", status);
			apiMocks.getWorkbench.mockResolvedValue(workspace([action], current));
			apiMocks[mockName].mockResolvedValue({ candidate: current });

			await act(async () =>
				root.render(<ModelWorkbenchDialog canMaintain dialog="publish" model={model} onClose={vi.fn()} />),
			);
			await flush();
			await act(async () => button("发布模型")?.click());
			await act(async () => button(label)?.click());

			expect(apiMocks[mockName]).toHaveBeenCalledWith(model.planId, current, "idem-1", "从模型工作台发布");
		},
	);
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
		expect(container.querySelectorAll("tbody tr.ant-table-row")).toHaveLength(1);
		expect(container.querySelector("tbody")?.textContent).toContain("DESIGNED");
		expect(container.textContent).not.toContain("MODEL_SPEC_PERMISSION_EVIDENCE_STALE");
	});
});

describe("advanced dbt draft lifecycle", () => {
	it("keeps generated dependency files read-only and commits the validated dependency checksum", async () => {
		apiMocks.getRepresentation.mockResolvedValue({
			allowedActions: ["OPEN_ADVANCED_DBT"],
			capabilityReasons: [],
			implementationRevision: 2,
			implementationChecksum: "impl-2",
			ownershipMode: "DBT_MANAGED",
		});
		apiMocks.createDbtDraft.mockResolvedValue({
			draftId: "draft-dependencies",
			planId: model.planId,
			modelSpecId: model.id,
			baseModelRevision: model.revision,
			baseModelChecksum: model.checksum,
			state: "DRAFT",
			etag: "e1",
			expiresAt: "2026-08-04T00:00:00Z",
			sourceBundle: {
				dependencyChecksum: "dependency-checksum",
				files: [
					{ path: "models/.dts_dependencies/sources.yml", content: "version: 2" },
					{ path: "models/budget.sql", content: "select 1" },
				],
			},
		});
		apiMocks.saveDbtFiles.mockResolvedValue({ etag: "e2", expiresAt: "2026-08-04T00:00:00Z" });
		apiMocks.validateDbtDraft.mockResolvedValue({
			draftId: "draft-dependencies",
			state: "VALIDATED",
			etag: "e3",
			expiresAt: "2026-08-04T00:00:00Z",
			validatedChecksum: "validated-checksum",
			diagnostics: [],
			proposedStructure: [],
			dependencyValidation: {
				dependencyChecksum: "dependency-checksum",
				matched: ["source.system.binding"],
				missing: [],
				undeclared: [],
			},
		});
		apiMocks.commitDbtDraft.mockResolvedValue({
			draftId: "draft-dependencies",
			modelSpecId: model.id,
			modelRevision: model.revision,
			modelChecksum: model.checksum,
			implementationId: "impl-1",
			implementationRevision: 3,
			implementationChecksum: "impl-3",
			artifactCount: 1,
			etag: "e4",
			dependencyChecksum: "dependency-checksum",
		});

		await act(async () => root.render(<AdvancedDbtWorkspace canMaintain model={model} onBack={vi.fn()} />));
		await flush();
		await act(async () => button("创建高级草稿")?.click());
		await flush();

		expect(container.textContent).toContain("系统依赖");
		expect(
			(
				container.querySelector(
					'textarea[aria-label="编辑 models/.dts_dependencies/sources.yml"]',
				) as HTMLTextAreaElement
			).disabled,
		).toBe(true);
		expect(button("删除文件")?.disabled).toBe(true);

		await act(async () => button("校验")?.click());
		await flush();
		expect(container.textContent).toContain("依赖关系已匹配 1 项");
		await act(async () => button("提交实现")?.click());
		await flush();
		expect(apiMocks.commitDbtDraft).toHaveBeenCalledWith(
			model.id,
			"draft-dependencies",
			expect.objectContaining({ dependencyChecksum: "dependency-checksum" }),
		);
	});

	it("does not request the technical representation for a read-only account", async () => {
		await act(async () => root.render(<AdvancedDbtWorkspace canMaintain={false} model={model} onBack={vi.fn()} />));
		await flush();

		expect(apiMocks.getRepresentation).not.toHaveBeenCalled();
		expect(container.textContent).toContain("无高级实现维护权限");
	});

	it("allows a new advanced draft from the prior implementation after the logical model advances", async () => {
		const priorImplementation = {
			...implementation,
			revision: model.revision - 1,
			modelChecksum: "prior-model-checksum",
		};
		apiMocks.getLifecycle.mockResolvedValue({ implementation: priorImplementation, artifacts: [], events: [] });
		apiMocks.getRepresentation.mockResolvedValue({
			allowedActions: ["OPEN_ADVANCED_DBT"],
			capabilityReasons: [],
			implementationRevision: null,
			implementationChecksum: null,
			ownershipMode: "DBT_MANAGED",
		});
		apiMocks.createDbtDraft.mockResolvedValue({
			draftId: "draft-forward",
			planId: model.planId,
			modelSpecId: model.id,
			baseModelRevision: model.revision,
			baseModelChecksum: model.checksum,
			baseImplementationRevision: priorImplementation.implementationRevision,
			baseImplementationChecksum: priorImplementation.implementationChecksum,
			state: "DRAFT",
			etag: "e1",
			expiresAt: "2026-08-04T00:00:00Z",
			sourceBundle: { files: [{ path: "models/budget.sql", content: "select 1" }] },
		});

		await act(async () => root.render(<AdvancedDbtWorkspace canMaintain model={model} onBack={vi.fn()} />));
		await flush();

		expect(apiMocks.getRepresentation).toHaveBeenCalledWith(model.id, {
			modelRevision: model.revision,
			representationScope: "TECHNICAL",
		});
		expect(button("创建高级草稿")?.disabled).toBe(false);
		await act(async () => button("创建高级草稿")?.click());
		await flush();
		expect(apiMocks.createDbtDraft).toHaveBeenCalledWith(
			model.id,
			expect.objectContaining({
				baseModelRevision: model.revision,
				baseImplementationRevision: priorImplementation.implementationRevision,
				baseImplementationChecksum: priorImplementation.implementationChecksum,
			}),
		);
	});

	it("uses the explicit target physical name when creating the first dbt implementation", async () => {
		apiMocks.getLifecycle.mockResolvedValue({ implementation: null, artifacts: [], events: [] });
		apiMocks.getRepresentation.mockResolvedValue({
			allowedActions: ["OPEN_ADVANCED_DBT"],
			capabilityReasons: [],
			implementationRevision: null,
			implementationChecksum: null,
			ownershipMode: "DBT_MANAGED",
		});
		apiMocks.createDbtDraft.mockResolvedValue({
			draftId: "draft-first",
			planId: model.planId,
			modelSpecId: model.id,
			baseModelRevision: model.revision,
			baseModelChecksum: model.checksum,
			state: "DRAFT",
			etag: "e1",
			expiresAt: "2026-08-04T00:00:00Z",
			sourceBundle: { files: [{ path: "models/biz_dwd_project_follow_up_v2.sql", content: "select 1" }] },
		});

		await act(async () =>
			root.render(
				<AdvancedDbtWorkspace
					canMaintain
					initialTargetPhysicalName="biz_dwd_project_follow_up_v2"
					model={model}
					onBack={vi.fn()}
				/>,
			),
		);
		await flush();

		const input = container.querySelector('input[aria-label="目标物理表名"]') as HTMLInputElement;
		expect(input.value).toBe("biz_dwd_project_follow_up_v2");
		expect(button("创建高级草稿")?.disabled).toBe(false);
		await act(async () => button("创建高级草稿")?.click());
		await flush();

		expect(apiMocks.createDbtDraft).toHaveBeenCalledWith(
			model.id,
			expect.objectContaining({
				baseImplementationRevision: null,
				targetPhysicalName: "biz_dwd_project_follow_up_v2",
			}),
		);
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

		await act(async () => root.render(<AdvancedDbtWorkspace canMaintain model={model} onBack={vi.fn()} />));
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
		// 草稿状态已从裸枚举改为本地化标签（dbtDraftStatusLabel）；JSX 换行会插入空白，先归一化。
		expect((container.textContent ?? "").replace(/\s/g, "")).toContain("状态：实现已提交");
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
