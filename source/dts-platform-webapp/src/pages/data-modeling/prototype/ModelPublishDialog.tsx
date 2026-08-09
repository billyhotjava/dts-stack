import { useCallback, useEffect, useState } from "react";
import {
	compileModelLifecycle,
	createReleaseCandidate,
	getModelLifecycle,
	getReleaseCandidateWorkbench,
	lockReleaseCandidate,
	publishReleaseCandidate,
	type ReleaseCandidateWorkbench,
	retryReleaseCandidate,
	startModelBuildIntent,
	startModelPublicationIntent,
} from "@/api/modelSpecApi";
import type { CanonicalModelSpecView, ModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
import { Button, Modal, RequestState } from "./PrototypePrimitives";
import { normalizeModelingRequestFailure } from "./services/planningProjectionService";

const canonical = (model: ModelSpecView): model is CanonicalModelSpecView =>
	model.compatibilityMode === "CANONICAL" && model.contractVersion === 2;

export function ModelPublishDialog({
	model,
	onClose,
	canMaintain,
}: {
	model: ModelSpecView;
	onClose: () => void;
	canMaintain: boolean;
}) {
	const [tab, setTab] = useState<"materialize" | "publish">("materialize");
	const [environment, setEnvironment] = useState("dev");
	const [reason, setReason] = useState("从模型工作台发布");
	const [workspace, setWorkspace] = useState<ReleaseCandidateWorkbench | null>(null);
	const [busy, setBusy] = useState<"load" | "build" | "publish" | "">("load");
	const [failure, setFailure] = useState<string>("");
	const load = useCallback(async () => {
		if (!model.planId) return;
		setBusy("load");
		setFailure("");
		try {
			setWorkspace(await getReleaseCandidateWorkbench(model.planId));
		} catch (error) {
			setFailure(normalizeModelingRequestFailure(error, "发布候选读取失败。").message);
		} finally {
			setBusy("");
		}
	}, [model.planId]);
	useEffect(() => {
		void load();
	}, [load]);
	const candidate = workspace?.candidate || null;
	const candidateContainsModel = Boolean(candidate?.entries.some((entry) => entry.modelSpecId === model.id));
	const buildAction = workspace?.allowedActions.includes("CREATE_CANDIDATE")
		? "CREATE_CANDIDATE"
		: candidateContainsModel && workspace?.allowedActions.includes("RETRY_BUILD")
			? "RETRY_BUILD"
			: candidateContainsModel && workspace?.allowedActions.includes("START_BUILD")
				? "START_BUILD"
				: null;
	const canBuild = Boolean(buildAction);
	const canPublish = Boolean(candidateContainsModel && workspace?.allowedActions.includes("PUBLISH"));
	const build = async () => {
		if (!canonical(model) || !canMaintain || !buildAction) return;
		setBusy("build");
		setFailure("");
		try {
			const lifecycle = await getModelLifecycle(model.id);
			if (!lifecycle.implementation) throw new Error("当前模型尚未保存可编译的数据实现，请先保存数据实现后重试。");
			await compileModelLifecycle(model, lifecycle.implementation, crypto.randomUUID());
			if (buildAction === "CREATE_CANDIDATE") {
				const created = await createReleaseCandidate(model.planId, crypto.randomUUID(), {
					environment,
					entries: [{ modelSpecId: model.id, sortOrder: 0, selectedReason: "从模型工作台选择" }],
					reason: "从模型工作台创建单模型候选",
				});
				await lockReleaseCandidate(model.planId, created.candidate, crypto.randomUUID(), "从模型工作台启动构建");
			} else if (buildAction === "RETRY_BUILD" && candidate) {
				await retryReleaseCandidate(model.planId, candidate, crypto.randomUUID(), "从模型工作台重试构建");
			} else if (candidate?.origin === "BATCH_WORKBENCH") {
				await lockReleaseCandidate(model.planId, candidate, crypto.randomUUID(), "从模型工作台启动构建");
			} else {
				await startModelBuildIntent(model, crypto.randomUUID(), { planId: model.planId, environment });
			}
			await load();
			setTab("publish");
		} catch (error) {
			setFailure(normalizeModelingRequestFailure(error, "物化构建未能启动。").message);
		} finally {
			setBusy("");
		}
	};
	const publish = async () => {
		if (!canonical(model) || !candidate || !canMaintain || !canPublish) return;
		setBusy("publish");
		setFailure("");
		try {
			const publishReason = reason.trim() || "从模型工作台发布";
			if (candidate.origin === "BATCH_WORKBENCH")
				await publishReleaseCandidate(model.planId, candidate, crypto.randomUUID(), publishReason);
			else await startModelPublicationIntent(model.id, candidate, crypto.randomUUID(), publishReason);
			await load();
		} catch (error) {
			setFailure(normalizeModelingRequestFailure(error, "发布意图未能启动。").message);
		} finally {
			setBusy("");
		}
	};
	return (
		<Modal onClose={onClose} title="发布与物化" wide>
			{!canMaintain ? (
				<RequestState description="当前账号只有查看权限，不能启动构建或发布。" kind="permission" title="无发布权限" />
			) : null}
			<div className="dmx-publish-grid">
				<nav>
					<button className={tab === "materialize" ? "active" : ""} onClick={() => setTab("materialize")} type="button">
						生成物化任务
					</button>
					<button className={tab === "publish" ? "active" : ""} onClick={() => setTab("publish")} type="button">
						发布模型
					</button>
				</nav>
				<section>
					{failure ? (
						<div className="dmx-inline-error" role="alert">
							{failure}
						</div>
					) : null}
					{!canonical(model) ? (
						<RequestState description="历史只读模型不能进入发布与物化链路。" kind="empty" title="当前模型不可操作" />
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
								<dt>模型</dt>
								<dd>
									{model.name} · r{model.revision}
								</dd>
								<dt>物化方式</dt>
								<dd>{model.materialization || "由实现策略决定"}</dd>
								<dt>当前候选</dt>
								<dd>
									{workspace?.candidate ? `${workspace.candidate.status} · v${workspace.candidate.version}` : "尚无"}
								</dd>
								<dt>主要阻断</dt>
								<dd>
									{workspace?.primaryBlocker
										? `${workspace.primaryBlocker.code}：${workspace.primaryBlocker.message}`
										: "无"}
								</dd>
							</dl>
							<div className="dmx-dialog-actions">
								<Button onClick={onClose}>取消</Button>
								<Button
									disabled={!canMaintain || !canBuild || Boolean(busy)}
									primary
									title={canBuild ? undefined : workspace?.primaryBlocker?.message || "当前候选不允许启动构建"}
									onClick={() => void build()}
								>
									{busy === "build"
										? "处理中…"
										: buildAction === "RETRY_BUILD"
											? "重试构建"
											: buildAction === "START_BUILD"
												? "开始构建"
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
								<dd>{workspace?.candidate?.status || "尚无候选"}</dd>
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
								<Button disabled={!canMaintain || !canPublish || Boolean(busy)} primary onClick={() => void publish()}>
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
