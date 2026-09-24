import { describe, expect, it } from "vitest";
import type {
	PlanExecutionBinding,
	ReleaseCandidate,
	ReleaseCandidateEntryEvidence,
	ReleaseCandidateGovernanceQuality,
} from "@/api/modelSpecApi";
import { deriveReleaseJourney, type JourneyStageKey, type ReleaseJourneyInput } from "./releaseJourney";

const candidate = (status: ReleaseCandidate["status"], approvedBy?: string) =>
	({ id: "cand-1", status, audit: approvedBy ? { approvedBy } : {} }) as unknown as ReleaseCandidate;

const governance = (
	state: ReleaseCandidateGovernanceQuality["state"],
	required: boolean,
	message: string | null = null,
): ReleaseCandidateGovernanceQuality => ({
	required,
	state,
	code: message ? "MODEL_SPEC_GOVERNANCE_QUALITY_MISSING" : null,
	message,
	maxAgeSeconds: 86400,
	evidence: [],
});

const failedEntry = {
	candidateEntryId: "e1",
	modelSpecId: "m1",
	modelName: "biz_dws_budget_v2",
	modelRevision: 3,
	relationState: "NOT_STARTED",
	repairCode: "DBT_TARGET_DATASOURCE_NOT_FOUND",
	pipelineRunGroupId: "run-9",
	failureMessage: "target datasource missing",
} as unknown as ReleaseCandidateEntryEvidence;

const base = (overrides: Partial<ReleaseJourneyInput> = {}): ReleaseJourneyInput => ({
	loadState: "ready",
	candidate: null,
	entryEvidence: [],
	governanceQuality: null,
	primaryBlocker: null,
	releaseActions: [],
	plan: { state: "ready", blockers: [] },
	precheckBlocker: null,
	canBuild: true,
	buildLabel: "开始构建",
	registration: null,
	binding: null,
	canRerunGovernanceQuality: false,
	canConfigureQuality: false,
	canCancelBlockingCandidate: false,
	...overrides,
});

const stateOf = (input: ReleaseJourneyInput, key: JourneyStageKey) =>
	deriveReleaseJourney(input).stages.find((stage) => stage.key === key)?.state;

describe("deriveReleaseJourney", () => {
	it("starts at the pre-build check with a single build action when nothing was built yet", () => {
		const journey = deriveReleaseJourney(base());
		expect(journey.current).toBe("precheck");
		expect(journey.primaryAction).toEqual({ key: "BUILD", label: "开始构建", tone: "primary" });
		expect(journey.stages.map((stage) => stage.key)).toEqual([
			"precheck",
			"build",
			"registration",
			"engineering",
			"governance",
			"publish",
			"online",
		]);
	});

	it("stops at the pre-build check when the dependency plan is blocked", () => {
		const journey = deriveReleaseJourney(
			base({ plan: { state: "ready", blockers: [{ code: "UPSTREAM_MISSING", message: "上游模型尚未发布" }] } }),
		);
		expect(journey.current).toBe("precheck");
		expect(journey.stages[0]).toMatchObject({ state: "failed", reason: "上游模型尚未发布" });
		expect(journey.stages[0].technical?.code).toBe("UPSTREAM_MISSING");
	});

	it("offers closing the occupying release candidate before a new build", () => {
		const journey = deriveReleaseJourney(
			base({ precheckBlocker: "所选模型范围被其他发布单占用", canBuild: false, canCancelBlockingCandidate: true }),
		);
		expect(journey.stages[0].state).toBe("failed");
		expect(journey.primaryAction?.key).toBe("CANCEL_BLOCKING_CANDIDATE");
	});

	it("shows the build as in progress without actions while BUILDING", () => {
		const journey = deriveReleaseJourney(base({ candidate: candidate("BUILDING"), canBuild: false }));
		expect(journey.current).toBe("build");
		expect(stateOf(base({ candidate: candidate("BUILDING") }), "precheck")).toBe("passed");
		expect(journey.stages[1].state).toBe("active");
		expect(journey.primaryAction).toBeUndefined();
	});

	it("keeps a build failure on the build stage with the technical detail folded away", () => {
		const journey = deriveReleaseJourney(
			base({
				candidate: candidate("BUILD_FAILED"),
				entryEvidence: [failedEntry],
				primaryBlocker: {
					code: "DBT_TARGET_DATASOURCE_NOT_FOUND",
					message: "目标数仓不存在，请在系统管理中检查数据湖配置",
				},
				buildLabel: "重新构建",
			}),
		);
		expect(journey.current).toBe("build");
		expect(journey.stages[1]).toMatchObject({
			state: "failed",
			reason: "目标数仓不存在，请在系统管理中检查数据湖配置",
			technical: { code: "DBT_TARGET_DATASOURCE_NOT_FOUND", runId: "run-9", detail: "target datasource missing" },
		});
		expect(journey.stages.slice(2).every((stage) => stage.state === "waiting")).toBe(true);
		expect(journey.primaryAction?.label).toBe("重新构建");
	});

	it("puts a failed asset registration on its own stage", () => {
		const journey = deriveReleaseJourney(
			base({ candidate: candidate("BUILT"), registration: { catalog: "FAILED", analysis: "NOT_STARTED" } }),
		);
		expect(journey.current).toBe("registration");
		expect(journey.stages[2].state).toBe("failed");
		expect(journey.primaryAction).toBeUndefined();
	});

	it("runs engineering validation after a successful build", () => {
		const journey = deriveReleaseJourney(
			base({
				candidate: candidate("BUILT"),
				registration: { catalog: "SUCCEEDED", analysis: "SUCCEEDED" },
				releaseActions: ["RUN_QUALITY"],
				canBuild: false,
			}),
		);
		expect(journey.current).toBe("engineering");
		expect(journey.primaryAction).toEqual({ key: "RUN_QUALITY", label: "运行工程验证", tone: "primary" });
	});

	it("reports an engineering validation failure", () => {
		expect(stateOf(base({ candidate: candidate("QUALITY_FAILED") }), "engineering")).toBe("failed");
		expect(stateOf(base({ candidate: candidate("QUALITY_RUNNING") }), "engineering")).toBe("active");
	});

	it("treats missing governance quality as a warning under ADVISORY and lets the release continue", () => {
		const journey = deriveReleaseJourney(
			base({
				candidate: candidate("QUALITY_PASSED"),
				registration: { catalog: "SUCCEEDED", analysis: "SUCCEEDED" },
				governanceQuality: governance("FAILED", false, "缺少质量规则"),
				releaseActions: ["PUBLISH"],
				canConfigureQuality: true,
				canBuild: false,
			}),
		);
		expect(journey.policy).toBe("ADVISORY");
		expect(journey.current).toBe("governance");
		expect(journey.stages[4]).toMatchObject({ state: "warning", reason: "缺少质量规则" });
		expect(journey.primaryAction).toEqual({ key: "PUBLISH", label: "继续发布", tone: "primary" });
		expect(journey.secondaryAction?.key).toBe("CONFIGURE_QUALITY");
	});

	it("blocks on missing governance quality under BLOCKING", () => {
		const journey = deriveReleaseJourney(
			base({
				candidate: candidate("QUALITY_PASSED"),
				registration: { catalog: "SUCCEEDED", analysis: "SUCCEEDED" },
				governanceQuality: governance("FAILED", true, "缺少质量规则"),
				canRerunGovernanceQuality: true,
				canConfigureQuality: true,
				canBuild: false,
			}),
		);
		expect(journey.policy).toBe("BLOCKING");
		expect(journey.stages[4].state).toBe("failed");
		expect(journey.primaryAction?.key).toBe("RERUN_GOVERNANCE_QUALITY");
		expect(journey.secondaryAction?.key).toBe("CONFIGURE_QUALITY");
	});

	it("shows a review stage only when a review is involved", () => {
		const reviewing = deriveReleaseJourney(
			base({ candidate: candidate("REVIEW_PENDING"), releaseActions: ["APPROVE", "REJECT"], canBuild: false }),
		);
		expect(reviewing.stages.map((stage) => stage.key)).toContain("review");
		expect(reviewing.current).toBe("review");
		expect(reviewing.primaryAction?.key).toBe("APPROVE");
		expect(reviewing.secondaryAction).toEqual({ key: "REJECT", label: "驳回", tone: "danger" });
	});

	it("moves to going online after publication and offers running the plan", () => {
		const binding = {
			state: "DEPLOYING",
			allowedActions: ["RUN_NOW"],
			latestRelation: {},
		} as unknown as PlanExecutionBinding;
		const journey = deriveReleaseJourney(
			base({
				candidate: candidate("PUBLISHED"),
				registration: { catalog: "SUCCEEDED", analysis: "SUCCEEDED" },
				governanceQuality: governance("PASSED", true),
				binding,
				releaseActions: ["ROLLBACK"],
				canBuild: false,
			}),
		);
		expect(journey.current).toBe("online");
		expect(journey.stages.find((stage) => stage.key === "publish")?.state).toBe("passed");
		expect(journey.primaryAction?.key).toBe("RUN_NOW");
		expect(journey.secondaryAction).toEqual({ key: "ROLLBACK", label: "回滚发布", tone: "danger" });
	});

	it("marks a rolled-back release on the publish stage", () => {
		expect(stateOf(base({ candidate: candidate("ROLLED_BACK") }), "publish")).toBe("rolled-back");
	});

	it("never claims progress while the state is loading or unreadable", () => {
		const loading = deriveReleaseJourney(base({ loadState: "loading", candidate: candidate("BUILT") }));
		expect(loading.headline).toBe("正在读取发布状态…");
		expect(loading.primaryAction).toBeUndefined();

		const failed = deriveReleaseJourney(base({ loadState: "error", candidate: candidate("BUILT") }));
		expect(failed.headline).toBe("发布状态读取失败");
		expect(failed.primaryAction).toEqual({ key: "RELOAD", label: "重新读取", tone: "primary" });
	});

	it("summarises how many tables of a batch are built", () => {
		const verified = {
			...failedEntry,
			candidateEntryId: "e2",
			relationState: "VERIFIED",
			failureMessage: null,
		} as unknown as ReleaseCandidateEntryEvidence;
		const journey = deriveReleaseJourney(
			base({ candidate: candidate("BUILDING"), entryEvidence: [verified, { ...failedEntry, failureMessage: null }] }),
		);
		expect(journey.stages[1].summary).toBe("1/2 张表完成");
	});

	it("uses a headline that names the stage number and the reason", () => {
		const journey = deriveReleaseJourney(
			base({ candidate: candidate("BUILD_FAILED"), primaryBlocker: { code: "X", message: "目标数仓不可用" } }),
		);
		expect(journey.headline).toBe("第 2 步 构建：目标数仓不可用");
	});
});
