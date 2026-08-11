import { useCallback, useEffect, useMemo, useState } from "react";
import {
	compileModelLifecycle,
	createReleaseCandidate,
	createReplacementReleaseCandidate,
	getModelLifecycle,
	getReleaseCandidateWorkbench,
	lockReleaseCandidate,
	publishReleaseCandidate,
	type ReleaseCandidateEntryEvidence,
	type ReleaseCandidateWorkbench,
	refreshReleaseCandidate,
	rematerializeReleaseCandidate,
	retryReleaseCandidate,
	startModelBuildIntent,
	startModelPublicationIntent,
} from "@/api/modelSpecApi";
import { type CompactColumns, CompactTable } from "@/components/table";
import type { CanonicalModelSpecView, ModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
import { Button, Modal, RequestState, Status } from "./PrototypePrimitives";
import { normalizeModelingRequestFailure } from "./services/planningProjectionService";

const MAX_MATERIALIZATION_MODELS = 100;
const COMPILE_CONCURRENCY = 5;

const canonical = (model: ModelSpecView): model is CanonicalModelSpecView =>
	model.compatibilityMode === "CANONICAL" && model.contractVersion === 2;

const formatTime = (value?: string | null) => {
	if (!value) return "—";
	const parsed = new Date(value);
	return Number.isNaN(parsed.getTime()) ? value : parsed.toLocaleString("zh-CN", { hour12: false });
};

async function compileSelectedModels(models: CanonicalModelSpecView[]) {
	for (let offset = 0; offset < models.length; offset += COMPILE_CONCURRENCY) {
		await Promise.all(
			models.slice(offset, offset + COMPILE_CONCURRENCY).map(async (model) => {
				const lifecycle = await getModelLifecycle(model.id);
				if (!lifecycle.implementation)
					throw new Error(
						models.length === 1
							? "当前模型尚未保存可编译的数据实现，请先保存数据实现后重试。"
							: `${model.name} 尚未保存可编译的数据实现，请先保存数据实现后重试。`,
					);
				await compileModelLifecycle(model, lifecycle.implementation, crypto.randomUUID());
			}),
		);
	}
}

export function ModelPublishDialog({
	models,
	onClose,
	canMaintain,
}: {
	models: ModelSpecView[];
	onClose: () => void;
	canMaintain: boolean;
}) {
	const [tab, setTab] = useState<"materialize" | "publish">("materialize");
	const [environment, setEnvironment] = useState("dev");
	const [reason, setReason] = useState("从模型工作台发布");
	const [workspace, setWorkspace] = useState<ReleaseCandidateWorkbench | null>(null);
	const [busy, setBusy] = useState<"load" | "build" | "publish" | "">("load");
	const [failure, setFailure] = useState<string>("");
	const selection = useMemo(() => Array.from(new Map(models.map((model) => [model.id, model])).values()), [models]);
	const selectedIds = useMemo(() => new Set(selection.map((model) => model.id)), [selection]);
	const primary = selection[0] || null;
	const batch = selection.length > 1;
	const selectionProblem = !selection.length
		? "请至少选择一个模型。"
		: selection.length > MAX_MATERIALIZATION_MODELS
			? `一次最多物化 ${MAX_MATERIALIZATION_MODELS} 个模型。`
			: selection.some((model) => !canonical(model))
				? "历史只读模型不能进入发布与物化链路。"
				: selection.some((model) => !model.planId)
					? "所选模型尚未归属建模规划，不能启动物化。"
					: new Set(selection.map((model) => model.planId)).size !== 1
						? "批量物化只能选择同一规划下的模型。"
						: "";
	const planId = selectionProblem ? "" : primary?.planId || "";
	const load = useCallback(async () => {
		if (!planId) {
			setBusy("");
			return;
		}
		setBusy("load");
		setFailure("");
		try {
			setWorkspace(await getReleaseCandidateWorkbench(planId));
		} catch (error) {
			setFailure(normalizeModelingRequestFailure(error, "发布候选读取失败。").message);
		} finally {
			setBusy("");
		}
	}, [planId]);
	useEffect(() => {
		void load();
	}, [load]);
	const candidate = workspace?.candidate || null;
	const candidateScopeMatches = Boolean(
		candidate &&
			candidate.entries.length === selectedIds.size &&
			candidate.entries.every((entry) => selectedIds.has(entry.modelSpecId)),
	);
	const buildAction = workspace?.allowedActions.includes("CREATE_CANDIDATE")
		? "CREATE_CANDIDATE"
		: candidate && workspace?.allowedActions.includes("REFRESH_CANDIDATE")
			? "REFRESH_AND_REPLACE"
			: candidate && workspace?.allowedActions.includes("CREATE_REPLACEMENT_CANDIDATE")
				? "CREATE_REPLACEMENT"
				: candidate && workspace?.allowedActions.includes("REMATERIALIZE")
					? "REMATERIALIZE"
					: candidateScopeMatches && workspace?.allowedActions.includes("RETRY_BUILD")
						? "RETRY_BUILD"
						: candidateScopeMatches && workspace?.allowedActions.includes("START_BUILD")
							? "START_BUILD"
							: null;
	const canBuild = Boolean(!selectionProblem && buildAction);
	const canPublish = Boolean(
		!batch && primary && candidateScopeMatches && workspace?.allowedActions.includes("PUBLISH"),
	);
	const entries = selection.map((model, sortOrder) => ({
		modelSpecId: model.id,
		sortOrder,
		selectedReason: "从模型工作台选择",
	}));
	const build = async () => {
		if (!canMaintain || !canBuild || !planId || !primary || !selection.every(canonical)) return;
		setBusy("build");
		setFailure("");
		try {
			await compileSelectedModels(selection);
			if (buildAction === "CREATE_CANDIDATE") {
				const created = await createReleaseCandidate(planId, crypto.randomUUID(), {
					environment,
					entries,
					reason: batch ? "从模型列表创建批量物化候选" : "从模型工作台创建单模型候选",
				});
				await lockReleaseCandidate(planId, created.candidate, crypto.randomUUID(), "从模型工作台启动构建");
			} else if (buildAction === "REMATERIALIZE" && candidate) {
				await rematerializeReleaseCandidate(planId, candidate, crypto.randomUUID(), {
					environment,
					entries,
					reason: batch ? "从模型列表重新物化所选模型" : "从模型工作台重新物化",
				});
			} else if ((buildAction === "REFRESH_AND_REPLACE" || buildAction === "CREATE_REPLACEMENT") && candidate) {
				const source =
					buildAction === "REFRESH_AND_REPLACE"
						? (await refreshReleaseCandidate(planId, candidate, crypto.randomUUID(), "模型已发生新修订，废弃旧候选"))
								.candidate
						: candidate;
				const replacement = await createReplacementReleaseCandidate(planId, source, crypto.randomUUID(), {
					environment,
					entries,
					reason: batch ? "从模型列表按新修订创建替代候选" : "从模型工作台按新修订创建替代候选",
				});
				await lockReleaseCandidate(planId, replacement.candidate, crypto.randomUUID(), "从模型工作台启动新修订构建");
			} else if (buildAction === "RETRY_BUILD" && candidate) {
				await retryReleaseCandidate(planId, candidate, crypto.randomUUID(), "从模型工作台重试构建");
			} else if (candidate?.origin === "BATCH_WORKBENCH") {
				await lockReleaseCandidate(planId, candidate, crypto.randomUUID(), "从模型工作台启动构建");
			} else {
				await startModelBuildIntent(primary, crypto.randomUUID(), { planId, environment });
			}
			await load();
			if (!batch) setTab("publish");
		} catch (error) {
			setFailure(normalizeModelingRequestFailure(error, "物化构建未能启动。").message);
		} finally {
			setBusy("");
		}
	};
	const publish = async () => {
		if (!primary || !canonical(primary) || !candidate || !canMaintain || !canPublish) return;
		setBusy("publish");
		setFailure("");
		try {
			const publishReason = reason.trim() || "从模型工作台发布";
			if (candidate.origin === "BATCH_WORKBENCH")
				await publishReleaseCandidate(planId, candidate, crypto.randomUUID(), publishReason);
			else await startModelPublicationIntent(primary.id, candidate, crypto.randomUUID(), publishReason);
			await load();
		} catch (error) {
			setFailure(normalizeModelingRequestFailure(error, "发布意图未能启动。").message);
		} finally {
			setBusy("");
		}
	};
	const evidenceColumns = useMemo<CompactColumns<ReleaseCandidateEntryEvidence>>(
		() => [
			{ title: "模型", dataIndex: "modelName" },
			{ title: "目标关系", dataIndex: "targetRelation", render: (value?: string | null) => value || "—" },
			{ title: "运行", dataIndex: "runStatus", render: (value?: string | null) => value || "未启动" },
			{
				title: "关系核验",
				dataIndex: "relationState",
				render: (value: ReleaseCandidateEntryEvidence["relationState"]) => (
					<Status tone={value === "VERIFIED" ? "success" : "warning"}>
						{value === "VERIFIED" ? "关系已核验" : value}
					</Status>
				),
			},
			{ title: "尝试", dataIndex: "attempt", render: (value?: number | null) => value ?? "—" },
			{ title: "完成时间", dataIndex: "finishedAt", render: (value?: string | null) => formatTime(value) },
		],
		[],
	);
	return (
		<Modal onClose={onClose} title={batch ? "批量物化" : "发布与物化"} wide>
			{!canMaintain ? (
				<RequestState description="当前账号只有查看权限，不能启动构建或发布。" kind="permission" title="无发布权限" />
			) : null}
			<div className="dmx-publish-grid">
				<nav>
					<button className={tab === "materialize" ? "active" : ""} onClick={() => setTab("materialize")} type="button">
						{batch ? "批量物化" : "生成物化任务"}
					</button>
					{batch ? null : (
						<button className={tab === "publish" ? "active" : ""} onClick={() => setTab("publish")} type="button">
							发布模型
						</button>
					)}
				</nav>
				<section>
					{failure ? (
						<div className="dmx-inline-error" role="alert">
							{failure}
						</div>
					) : null}
					{selectionProblem ? (
						<RequestState description={selectionProblem} kind="empty" title="当前选择不可物化" />
					) : tab === "materialize" ? (
						<>
							<h3>使用 dbt 生成物化任务</h3>
							<p className="dmx-capability-note">
								服务端先生成或校验当前实现制品，再由候选控制面生成 dbt 选择器与目标关系；前端不拼接 SQL 或表名。
							</p>
							<label>
								<span>执行环境</span>
								<select onChange={(event) => setEnvironment(event.target.value)} value={environment}>
									<option value="dev">开发环境</option>
									<option value="test">测试环境</option>
									<option value="prod">生产环境</option>
								</select>
							</label>
							<dl className="dmx-summary-list">
								<dt>模型范围</dt>
								<dd>{selection.map((model) => `${model.name} · r${model.revision}`).join("；")}</dd>
								<dt>物化方式</dt>
								<dd>{batch ? "按各模型实现策略" : primary?.materialization || "由实现策略决定"}</dd>
								<dt>当前候选</dt>
								<dd>{candidate ? `${candidate.status} · v${candidate.version}` : "尚无"}</dd>
								<dt>主要阻断</dt>
								<dd>
									{workspace?.primaryBlocker
										? `${workspace.primaryBlocker.code}：${workspace.primaryBlocker.message}`
										: "无"}
								</dd>
							</dl>
							{workspace?.entryEvidence.length ? (
								<div className="dmx-table-scroll dmx-materialization-evidence">
									<CompactTable<ReleaseCandidateEntryEvidence>
										columns={evidenceColumns}
										dataSource={workspace.entryEvidence}
										pagination={false}
										rowKey="candidateEntryId"
									/>
								</div>
							) : (
								<p className="dmx-capability-note">当前候选尚无逐表执行证据。</p>
							)}
							<div className="dmx-dialog-actions">
								<Button onClick={onClose}>取消</Button>
								<Button
									disabled={!canMaintain || !canBuild || Boolean(busy)}
									onClick={() => void build()}
									primary
									title={canBuild ? undefined : workspace?.primaryBlocker?.message || "当前候选不允许启动构建"}
								>
									{busy === "build"
										? "处理中…"
										: buildAction === "REFRESH_AND_REPLACE" || buildAction === "CREATE_REPLACEMENT"
											? "按新修订重新物化"
											: buildAction === "REMATERIALIZE"
												? "重新物化"
												: buildAction === "RETRY_BUILD"
													? "重试构建"
													: buildAction === "START_BUILD"
														? "开始构建"
														: batch
															? `创建并运行 ${selection.length} 个模型`
															: "创建并运行"}
								</Button>
							</div>
						</>
					) : (
						<>
							<h3>发布模型</h3>
							<p className="dmx-capability-note">只有当前候选包含该模型并且服务端允许 PUBLISH 时才能提交发布。</p>
							<label>
								<span>发布说明</span>
								<input onChange={(event) => setReason(event.target.value)} value={reason} />
							</label>
							<dl className="dmx-summary-list">
								<dt>候选状态</dt>
								<dd>{candidate?.status || "尚无候选"}</dd>
								<dt>在线就绪证据</dt>
								<dd>{workspace?.evidence.map((item) => `${item.type}:${item.state}`).join("；") || "—"}</dd>
								<dt>允许动作</dt>
								<dd>{workspace?.allowedActions.join("、") || "—"}</dd>
								<dt>主要阻断</dt>
								<dd>
									{workspace?.primaryBlocker
										? `${workspace.primaryBlocker.code}：${workspace.primaryBlocker.message}`
										: "无"}
								</dd>
							</dl>
							<div className="dmx-dialog-actions">
								<Button onClick={onClose}>取消</Button>
								<Button disabled={!canMaintain || !canPublish || Boolean(busy)} onClick={() => void publish()} primary>
									{busy === "publish" ? "发布中…" : "发布"}
								</Button>
							</div>
						</>
					)}
				</section>
			</div>
		</Modal>
	);
}
