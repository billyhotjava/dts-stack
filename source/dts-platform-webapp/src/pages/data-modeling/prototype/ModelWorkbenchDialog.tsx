import { useCallback, useEffect, useState } from "react";
import {
	commitDbtImplementationDraft,
	createDbtImplementationDraft,
	type DbtDraftCommit,
	type DbtDraftFile,
	type DbtDraftValidation,
	type DbtImplementationDraft,
	saveDbtImplementationDraftFiles,
	validateDbtImplementationDraft,
} from "@/api/dbtImplementationDraftApi";
import {
	getModelPhysicalPreview,
	getModelPhysicalStructure,
	type ModelPhysicalPreview,
	type PhysicalPreviewPageSize,
} from "@/api/modelPhysicalPreviewApi";
import { getModelRepresentation } from "@/api/modelRepresentationApi";
import {
	createReleaseCandidate,
	getModelLifecycle,
	getModelSpecDependencies,
	getModelSpecStageGates,
	getReleaseCandidateWorkbench,
	lockReleaseCandidate,
	type ModelLifecycleTimeline,
	type ModelSpecDependencyGraph,
	type ModelSpecStageGate,
	publishReleaseCandidate,
	type ReleaseCandidateWorkbench,
	retryReleaseCandidate,
	startModelBuildIntent,
	startModelPublicationIntent,
} from "@/api/modelSpecApi";
import type {
	ModelRepresentationView,
	PhysicalPreviewReference,
	PhysicalPreviewScope,
} from "@/features/modeling/contracts/modelRepresentationContract";
import type { CanonicalModelSpecView, ModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
import { Button, Modal, RequestState, Status } from "./PrototypePrimitives";
import { normalizeModelingRequestFailure } from "./services/planningProjectionService";

export type WorkbenchDialog =
	| "association"
	| "publish"
	| "versions"
	| "releases"
	| "logs"
	| "quality"
	| "advanced"
	| "preview"
	| "gates"
	| null;

type DialogPayload = {
	gates?: ModelSpecStageGate[];
	dependencies?: ModelSpecDependencyGraph;
	lifecycle?: ModelLifecycleTimeline;
};

const canonical = (model: ModelSpecView): model is CanonicalModelSpecView =>
	model.compatibilityMode === "CANONICAL" && model.contractVersion === 2;

export function ModelWorkbenchDialog({
	dialog,
	onClose,
	model,
	canMaintain,
}: {
	dialog: WorkbenchDialog;
	onClose: () => void;
	model: ModelSpecView | null;
	canMaintain: boolean;
}) {
	const [payload, setPayload] = useState<DialogPayload | null>(null);
	const [loading, setLoading] = useState(false);
	const [failure, setFailure] = useState<{ kind: "permission" | "request"; message: string } | null>(null);

	const load = useCallback(async () => {
		if (!dialog || !model || dialog === "publish" || dialog === "preview" || dialog === "advanced") return;
		setLoading(true);
		setFailure(null);
		try {
			if (dialog === "association") setPayload({ dependencies: await getModelSpecDependencies(model.id) });
			else if (dialog === "gates" || dialog === "quality")
				setPayload({ gates: await getModelSpecStageGates(model.id) });
			else if (dialog === "versions" || dialog === "releases" || dialog === "logs")
				setPayload({ lifecycle: await getModelLifecycle(model.id) });
		} catch (error) {
			setPayload(null);
			setFailure(normalizeModelingRequestFailure(error, "模型附加信息读取失败。"));
		} finally {
			setLoading(false);
		}
	}, [dialog, model]);

	useEffect(() => {
		setPayload(null);
		setFailure(null);
		void load();
	}, [load]);

	if (!dialog || !model) return null;
	if (dialog === "publish") return <PublishDialog canMaintain={canMaintain} model={model} onClose={onClose} />;
	if (dialog === "preview") return <PhysicalPreviewDialog canMaintain={canMaintain} model={model} onClose={onClose} />;
	if (dialog === "advanced") return <AdvancedDbtDialog canMaintain={canMaintain} model={model} onClose={onClose} />;

	const title = {
		association: "模型关联关系",
		versions: "版本证据",
		releases: "发布记录",
		logs: "生命周期日志",
		quality: "质量与发布门禁",
		gates: "提交检查",
	}[dialog];
	return (
		<Modal
			footer={
				<Button primary onClick={onClose}>
					关闭
				</Button>
			}
			onClose={onClose}
			title={title}
			wide
		>
			{loading ? (
				<RequestState description="正在读取服务端事实。" kind="loading" title="正在加载" />
			) : failure ? (
				<RequestState
					description={failure.message}
					kind={failure.kind === "permission" ? "permission" : "error"}
					onRetry={failure.kind === "request" ? () => void load() : undefined}
					title="加载失败"
				/>
			) : (
				<DialogContent dialog={dialog} model={model} payload={payload} />
			)}
		</Modal>
	);
}

function DialogContent({
	dialog,
	model,
	payload,
}: {
	dialog: Exclude<WorkbenchDialog, "publish" | "preview" | null>;
	model: ModelSpecView;
	payload: DialogPayload | null;
}) {
	if (!payload) return <RequestState description="服务端未返回该能力的数据。" kind="empty" title="暂无数据" />;
	if (dialog === "association" && payload.dependencies) {
		const graph = payload.dependencies;
		return (
			<>
				<p className="dmx-capability-note">关联关系来自当前模型的固定版本依赖，不在客户端推断。</p>
				<div className="dmx-table-scroll">
					<table className="dmx-table">
						<thead>
							<tr>
								<th>上游模型</th>
								<th>固定版本</th>
								<th>当前版本</th>
								<th>状态</th>
							</tr>
						</thead>
						<tbody>
							{graph.edges.map((edge) => (
								<tr key={`${edge.fromModelSpecId}-${edge.toModelSpecId}`}>
									<td>
										{graph.nodes.find((node) => node.modelSpecId === edge.toModelSpecId)?.name || edge.toModelSpecId}
									</td>
									<td>r{edge.pinnedRevision}</td>
									<td>{edge.currentRevision ? `r${edge.currentRevision}` : "—"}</td>
									<td>
										<Status tone={edge.state === "CURRENT" ? "success" : "warning"}>{edge.state}</Status>
									</td>
								</tr>
							))}
						</tbody>
					</table>
				</div>
				{!graph.edges.length ? (
					<RequestState description="当前模型没有固定上游模型依赖。" kind="empty" title="暂无关联" />
				) : null}
			</>
		);
	}
	if ((dialog === "gates" || dialog === "quality") && payload.gates) return <GateTable gates={payload.gates} />;
	if ((dialog === "versions" || dialog === "releases" || dialog === "logs") && payload.lifecycle) {
		const events =
			dialog === "releases"
				? payload.lifecycle.events.filter((event) => event.eventType === "RELEASE" || event.eventType === "ROLLBACK")
				: payload.lifecycle.events;
		return (
			<>
				<p className="dmx-capability-note">
					{dialog === "versions"
						? "当前接口提供生命周期事件和制品修订证据，不伪造完整版本清单。"
						: "记录来自模型生命周期审计事实。"}
				</p>
				<div className="dmx-table-scroll">
					<table className="dmx-table">
						<thead>
							<tr>
								<th>事件</th>
								<th>模型版本</th>
								<th>状态</th>
								<th>操作人</th>
								<th>时间</th>
								<th>外部引用</th>
							</tr>
						</thead>
						<tbody>
							{events.map((event) => (
								<tr key={event.id}>
									<td>{event.eventType}</td>
									<td>r{event.revision}</td>
									<td>
										<Status tone={event.status.includes("FAIL") ? "danger" : "info"}>{event.status}</Status>
									</td>
									<td>{event.actorId || "—"}</td>
									<td>{event.createdAt}</td>
									<td>{event.externalRef || "—"}</td>
								</tr>
							))}
						</tbody>
					</table>
				</div>
				{!events.length ? (
					<RequestState description="当前没有符合条件的生命周期记录。" kind="empty" title="暂无记录" />
				) : null}
				{dialog === "versions" && payload.lifecycle.artifacts.length ? (
					<div className="dmx-table-scroll">
						<table className="dmx-table">
							<thead>
								<tr>
									<th>制品</th>
									<th>实现版本</th>
									<th>物化方式</th>
									<th>状态</th>
									<th>校验和</th>
								</tr>
							</thead>
							<tbody>
								{payload.lifecycle.artifacts.map((item) => (
									<tr key={item.id}>
										<td>{item.path}</td>
										<td>r{item.implementationRevision}</td>
										<td>{item.materialization}</td>
										<td>{item.status}</td>
										<td>{item.checksum}</td>
									</tr>
								))}
							</tbody>
						</table>
					</div>
				) : null}
			</>
		);
	}
	return <RequestState description={`模型 ${model.name} 当前没有可展示的服务端事实。`} kind="empty" title="暂无数据" />;
}

function GateTable({ gates }: { gates: ModelSpecStageGate[] }) {
	return (
		<div className="dmx-table-scroll">
			<table className="dmx-table">
				<thead>
					<tr>
						<th>阶段</th>
						<th>状态</th>
						<th>阻断项</th>
						<th>修复入口</th>
					</tr>
				</thead>
				<tbody>
					{gates.map((gate) => (
						<tr key={gate.stage}>
							<td>{gate.stage}</td>
							<td>
								<Status tone={gate.status === "READY" ? "success" : "danger"}>{gate.status}</Status>
							</td>
							<td>
								{gate.blockers.length
									? gate.blockers.map((blocker) => (
											<div key={`${blocker.code}-${blocker.field}`}>
												<b>{blocker.code}</b>：{blocker.message}
											</div>
										))
									: "无"}
							</td>
							<td>
								{gate.blockers
									.map((blocker) => blocker.repairRoute)
									.filter(Boolean)
									.join("；") || "—"}
							</td>
						</tr>
					))}
				</tbody>
			</table>
		</div>
	);
}

function PublishDialog({
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
								服务端根据模型修订、实现修订和执行目标生成 dbt 选择器与目标关系；前端不拼接 SQL 或表名。
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

function AdvancedDbtDialog({
	model,
	onClose,
	canMaintain,
}: {
	model: ModelSpecView;
	onClose: () => void;
	canMaintain: boolean;
}) {
	const [representation, setRepresentation] = useState<ModelRepresentationView | null>(null);
	const [draft, setDraft] = useState<DbtImplementationDraft | null>(null);
	const [files, setFiles] = useState<DbtDraftFile[]>([]);
	const [selectedPath, setSelectedPath] = useState("");
	const [newPath, setNewPath] = useState("");
	const [validation, setValidation] = useState<DbtDraftValidation | null>(null);
	const [commit, setCommit] = useState<DbtDraftCommit | null>(null);
	const [dirty, setDirty] = useState(false);
	const [conflict, setConflict] = useState(false);
	const [busy, setBusy] = useState<"load" | "create" | "save" | "validate" | "commit" | "">("load");
	const [failure, setFailure] = useState("");
	useEffect(() => {
		let active = true;
		setFailure("");
		setRepresentation(null);
		if (!canMaintain) {
			setBusy("");
			return () => {
				active = false;
			};
		}
		setBusy("load");
		void getModelRepresentation(model.id, { modelRevision: model.revision, representationScope: "TECHNICAL" })
			.then((value) => {
				if (active) setRepresentation(value);
			})
			.catch((error) => {
				if (active) setFailure(normalizeModelingRequestFailure(error, "高级实现视图读取失败。").message);
			})
			.finally(() => {
				if (active) setBusy("");
			});
		return () => {
			active = false;
		};
	}, [canMaintain, model.id, model.revision]);
	const canOpenAdvanced = Boolean(representation?.allowedActions.includes("OPEN_ADVANCED_DBT"));
	const installDraft = (created: DbtImplementationDraft) => {
		const sourceFiles = created.sourceBundle?.files.map(({ path, content }) => ({ path, content })) || [];
		setDraft(created);
		setFiles(sourceFiles);
		setSelectedPath(sourceFiles[0]?.path || "");
		setDirty(false);
		setConflict(false);
		setCommit(null);
		setValidation(null);
	};
	const recordFailure = (error: unknown, fallback: string) => {
		const status = Number((error as { response?: { status?: unknown } } | null)?.response?.status ?? 0);
		setConflict(status === 409);
		setFailure(normalizeModelingRequestFailure(error, fallback).message);
	};
	const create = async () => {
		if (!canonical(model) || !canMaintain || !canOpenAdvanced) return;
		setBusy("create");
		setFailure("");
		setConflict(false);
		setCommit(null);
		setValidation(null);
		try {
			const created = await createDbtImplementationDraft(model.id, {
				planId: model.planId,
				baseModelRevision: model.revision,
				baseModelChecksum: model.checksum,
				baseImplementationRevision: representation?.implementationRevision || null,
				baseImplementationChecksum: representation?.implementationChecksum || null,
				idempotencyKey: crypto.randomUUID(),
			});
			installDraft(created);
		} catch (error) {
			recordFailure(error, "dbt 高级草稿创建失败。");
		} finally {
			setBusy("");
		}
	};
	const persist = async () => {
		if (!canMaintain) throw new Error("当前账号无高级实现维护权限");
		if (!draft) throw new Error("请先创建高级草稿");
		if (draft.state === "COMMITTED") throw new Error("已提交草稿不可继续编辑，请创建新草稿");
		const saved = await saveDbtImplementationDraftFiles(model.id, draft.draftId, { expectedEtag: draft.etag, files });
		const next: DbtImplementationDraft = { ...draft, state: "DRAFT", etag: saved.etag, expiresAt: saved.expiresAt };
		setDraft(next);
		setDirty(false);
		setConflict(false);
		return next;
	};
	const save = async () => {
		setBusy("save");
		setFailure("");
		setConflict(false);
		try {
			await persist();
			setValidation(null);
		} catch (error) {
			recordFailure(error, "dbt 文件保存失败。");
		} finally {
			setBusy("");
		}
	};
	const validate = async () => {
		setBusy("validate");
		setFailure("");
		setConflict(false);
		setValidation(null);
		try {
			const saved = await persist();
			const checked = await validateDbtImplementationDraft(model.id, saved.draftId, { expectedEtag: saved.etag });
			setValidation(checked);
			setDraft({ ...saved, state: "VALIDATED", etag: checked.etag, expiresAt: checked.expiresAt });
		} catch (error) {
			recordFailure(error, "dbt 草稿校验失败。");
		} finally {
			setBusy("");
		}
	};
	const commitDraft = async () => {
		if (!draft || !validation) return;
		setBusy("commit");
		setFailure("");
		setConflict(false);
		try {
			const receipt = await commitDbtImplementationDraft(model.id, draft.draftId, {
				expectedEtag: validation.etag,
				validatedChecksum: validation.validatedChecksum,
				idempotencyKey: crypto.randomUUID(),
			});
			setCommit(receipt);
			setDraft((current) => (current ? { ...current, state: "COMMITTED", etag: receipt.etag } : current));
			setDirty(false);
		} catch (error) {
			recordFailure(error, "dbt 实现提交失败。");
		} finally {
			setBusy("");
		}
	};
	const startNewDraft = async () => {
		if (!canonical(model) || !canMaintain) return;
		const baseModelRevision = commit?.modelRevision || model.revision;
		const baseModelChecksum = commit?.modelChecksum || model.checksum;
		setBusy("create");
		setFailure("");
		setConflict(false);
		try {
			const latest = await getModelRepresentation(model.id, {
				modelRevision: baseModelRevision,
				representationScope: "TECHNICAL",
			});
			setRepresentation(latest);
			if (!latest.allowedActions.includes("OPEN_ADVANCED_DBT"))
				throw new Error(latest.capabilityReasons.join("；") || "当前表示不允许创建新的高级 dbt 草稿");
			installDraft(
				await createDbtImplementationDraft(model.id, {
					planId: model.planId,
					baseModelRevision,
					baseModelChecksum,
					baseImplementationRevision: latest.implementationRevision || null,
					baseImplementationChecksum: latest.implementationChecksum || null,
					idempotencyKey: crypto.randomUUID(),
				}),
			);
		} catch (error) {
			recordFailure(error, "新 dbt 高级草稿创建失败。");
		} finally {
			setBusy("");
		}
	};
	const selectedFile = files.find((file) => file.path === selectedPath) || null;
	const draftCommitted = draft?.state === "COMMITTED" || Boolean(commit);
	return (
		<Modal onClose={onClose} title="高级 dbt 实现" wide>
			{!canMaintain ? (
				<RequestState
					description="当前账号可查看实现摘要，但不能创建或提交高级 dbt 草稿。"
					kind="permission"
					title="无高级实现维护权限"
				/>
			) : null}
			{failure ? (
				<div className="dmx-inline-error" role="alert">
					{failure}
				</div>
			) : null}
			{conflict ? (
				<RequestState
					description="草稿 ETag 已变化，为避免覆盖他人修改，请关闭后重新打开高级实现。"
					kind="error"
					title="检测到并发冲突"
				/>
			) : null}
			{!canonical(model) ? (
				<RequestState description="历史只读模型不能创建高级 dbt 草稿。" kind="empty" title="当前模型不可维护" />
			) : !draft ? (
				<>
					<p className="dmx-capability-note">
						SQL/Jinja 只在本高级弹层可见；业务可视化页面保持纯结构。草稿基于固定模型和实现修订创建。
					</p>
					<dl className="dmx-summary-list">
						<dt>模型</dt>
						<dd>
							{model.name} · r{model.revision}
						</dd>
						<dt>实现所有权</dt>
						<dd>{representation?.ownershipMode || model.implementationMode}</dd>
						<dt>实现修订</dt>
						<dd>{representation?.implementationRevision ? `r${representation.implementationRevision}` : "尚无"}</dd>
						<dt>能力限制</dt>
						<dd>{representation?.capabilityReasons.join("；") || "无"}</dd>
					</dl>
					<div className="dmx-dialog-actions">
						<Button onClick={onClose}>取消</Button>
						<Button
							disabled={!canMaintain || !canOpenAdvanced || Boolean(busy)}
							primary
							title={
								canOpenAdvanced
									? undefined
									: representation?.capabilityReasons.join("；") || "当前表示不允许高级 dbt 实现"
							}
							onClick={() => void create()}
						>
							{busy === "create" ? "创建中…" : "创建高级草稿"}
						</Button>
					</div>
				</>
			) : (
				<div className="dmx-dbt-editor">
					<aside>
						<strong>草稿文件</strong>
						{files.map((file) => (
							<button
								className={selectedPath === file.path ? "active" : ""}
								key={file.path}
								onClick={() => setSelectedPath(file.path)}
								type="button"
							>
								{file.path}
							</button>
						))}
						<div>
							<input
								disabled={!canMaintain || draftCommitted}
								onChange={(event) => setNewPath(event.target.value)}
								placeholder="models/example.sql"
								value={newPath}
							/>
							<Button
								disabled={
									!canMaintain ||
									draftCommitted ||
									!newPath.trim() ||
									files.some((file) => file.path === newPath.trim())
								}
								onClick={() => {
									const path = newPath.trim();
									setFiles((current) => [...current, { path, content: "" }]);
									setSelectedPath(path);
									setNewPath("");
									setValidation(null);
									setCommit(null);
									setDirty(true);
								}}
							>
								新增
							</Button>
						</div>
					</aside>
					<section>
						{selectedFile ? (
							<>
								<header>
									<strong>{selectedFile.path}</strong>
									<Button
										danger
										disabled={!canMaintain || draftCommitted}
										onClick={() => {
											setFiles((current) => current.filter((file) => file.path !== selectedFile.path));
											setSelectedPath(files.find((file) => file.path !== selectedFile.path)?.path || "");
											setValidation(null);
											setCommit(null);
											setDirty(true);
										}}
									>
										删除文件
									</Button>
								</header>
								<textarea
									aria-label={`编辑 ${selectedFile.path}`}
									disabled={!canMaintain || draftCommitted}
									onChange={(event) => {
										const content = event.target.value;
										setFiles((current) =>
											current.map((file) => (file.path === selectedFile.path ? { ...file, content } : file)),
										);
										setValidation(null);
										setCommit(null);
										setDirty(true);
									}}
									value={selectedFile.content}
								/>
							</>
						) : (
							<RequestState description="选择已有文件或新增文件。" kind="empty" title="暂无选中文件" />
						)}
					</section>
					<footer>
						<span>
							状态：
							{conflict ? "CONFLICT" : dirty ? "DIRTY" : commit ? "COMMITTED" : validation ? "VALIDATED" : draft.state}{" "}
							· 到期：{draft.expiresAt}
						</span>
						{draftCommitted ? (
							<Button disabled={!canMaintain || Boolean(busy)} primary onClick={() => void startNewDraft()}>
								{busy === "create" ? "创建中…" : "创建新草稿"}
							</Button>
						) : (
							<>
								<Button disabled={!canMaintain || Boolean(busy)} onClick={() => void save()}>
									{busy === "save" ? "保存中…" : "保存文件"}
								</Button>
								<Button disabled={!canMaintain || Boolean(busy) || !files.length} onClick={() => void validate()}>
									{busy === "validate" ? "校验中…" : "校验"}
								</Button>
								<Button
									disabled={!canMaintain || Boolean(busy) || !validation}
									primary
									onClick={() => void commitDraft()}
								>
									{busy === "commit" ? "提交中…" : "提交实现"}
								</Button>
							</>
						)}
					</footer>
					{validation ? (
						<div className="dmx-dbt-diagnostics">
							<h3>校验结果</h3>
							{validation.diagnostics.length ? (
								validation.diagnostics.map((item, index) => (
									<div key={`${item.code}-${index}`}>
										<Status tone={item.severity === "ERROR" ? "danger" : "warning"}>{item.severity}</Status>
										<b>{item.code}</b>
										<span>{item.path || item.modelUniqueId || "—"}</span>
										<p>{item.message}</p>
									</div>
								))
							) : (
								<Status tone="success">校验通过</Status>
							)}
						</div>
					) : null}
					{commit ? (
						<div className="dmx-capability-note">
							实现已提交：模型 r{commit.modelRevision}，实现 r{commit.implementationRevision}，制品{" "}
							{commit.artifactCount} 个。
						</div>
					) : null}
				</div>
			)}
		</Modal>
	);
}

function PhysicalPreviewDialog({
	model,
	onClose,
	canMaintain,
}: {
	model: ModelSpecView;
	onClose: () => void;
	canMaintain: boolean;
}) {
	const [representation, setRepresentation] = useState<ModelRepresentationView | null>(null);
	const [scope, setScope] = useState<PhysicalPreviewScope>("SERVING");
	const [limit, setLimit] = useState<PhysicalPreviewPageSize>(100);
	const [preview, setPreview] = useState<ModelPhysicalPreview | null>(null);
	const [busy, setBusy] = useState(true);
	const [failure, setFailure] = useState("");
	useEffect(() => {
		let active = true;
		setBusy(true);
		setFailure("");
		void getModelRepresentation(model.id, { modelRevision: model.revision, representationScope: "BUSINESS" })
			.then((value) => {
				if (active) setRepresentation(value);
			})
			.catch((error) => {
				if (active) setFailure(normalizeModelingRequestFailure(error, "物理预览能力读取失败。").message);
			})
			.finally(() => {
				if (active) setBusy(false);
			});
		return () => {
			active = false;
		};
	}, [model.id, model.revision]);
	const reference = (
		scope === "SERVING" ? representation?.physicalPreview?.serving : representation?.physicalPreview?.candidate
	) as PhysicalPreviewReference | null | undefined;
	const read = async (mode: "STRUCTURE" | "SAMPLE") => {
		if (!reference) return;
		setBusy(true);
		setFailure("");
		try {
			setPreview(
				mode === "STRUCTURE"
					? await getModelPhysicalStructure(reference)
					: await getModelPhysicalPreview(reference, limit),
			);
		} catch (error) {
			setFailure(normalizeModelingRequestFailure(error, "物理预览读取失败。").message);
		} finally {
			setBusy(false);
		}
	};
	const columns = preview?.columns || [];
	const rowOccurrences = new Map<string, number>();
	const previewRows = (preview?.maskedRows || []).map((row) => {
		const signature = columns.map((column) => String(row[column.name] ?? "")).join("\u001f") || "__empty_row__";
		const occurrence = rowOccurrences.get(signature) || 0;
		rowOccurrences.set(signature, occurrence + 1);
		return { key: `${signature}\u001f${occurrence}`, row };
	});
	return (
		<Modal
			footer={
				<Button primary onClick={onClose}>
					关闭
				</Button>
			}
			onClose={onClose}
			title="物理结构与数据预览"
			wide
		>
			{failure ? (
				<RequestState description={failure} kind="error" title="预览失败" />
			) : busy && !representation ? (
				<RequestState description="正在读取服务端签发的关系证据。" kind="loading" title="正在加载预览能力" />
			) : (
				<>
					<p className="dmx-capability-note">
						查询只使用服务端签发的 relation evidence，前端不接受 manifest 名称或手工 relation 标识符。
					</p>
					<div className="dmx-preview-toolbar">
						<label>
							范围
							<select
								onChange={(event) => {
									setScope(event.target.value as PhysicalPreviewScope);
									setPreview(null);
								}}
								value={scope}
							>
								<option value="SERVING">现行服务版本</option>
								<option disabled={!canMaintain} value="CANDIDATE">
									候选版本（维护者）
								</option>
							</select>
						</label>
						<label>
							样本行数
							<select
								onChange={(event) => setLimit(Number(event.target.value) as PhysicalPreviewPageSize)}
								value={limit}
							>
								<option value={20}>20</option>
								<option value={50}>50</option>
								<option value={100}>100</option>
								<option value={500}>500</option>
							</select>
						</label>
						<Button disabled={!reference || busy} onClick={() => void read("STRUCTURE")}>
							读取结构
						</Button>
						<Button disabled={!reference || busy} primary onClick={() => void read("SAMPLE")}>
							读取脱敏样本
						</Button>
					</div>
					{!reference ? (
						<RequestState
							description={
								(representation?.previewCapability.reasons || []).join("；") ||
								`当前${scope === "SERVING" ? "现行" : "候选"}版本没有可用关系证据。`
							}
							kind="empty"
							title="暂不可预览"
						/>
					) : null}
					{preview ? (
						<>
							<dl className="dmx-summary-list">
								<dt>证据状态</dt>
								<dd>{preview.driftStatus}</dd>
								<dt>观测时间</dt>
								<dd>{preview.observedAt}</dd>
								<dt>返回行数</dt>
								<dd>
									{preview.returnedRows}
									{preview.truncated ? "（已截断）" : ""}
								</dd>
								<dt>脱敏摘要</dt>
								<dd>
									允许 {preview.maskingSummary.allowedColumnCount}，掩码 {preview.maskingSummary.maskedColumnCount}
									，拒绝 {preview.maskingSummary.deniedColumnCount}
								</dd>
							</dl>
							<div className="dmx-table-scroll">
								<table className="dmx-table">
									<thead>
										<tr>
											{columns.map((column) => (
												<th key={column.name}>
													{column.name}
													<small>
														{column.dataType} · {column.policy}
													</small>
												</th>
											))}
										</tr>
									</thead>
									<tbody>
										{previewRows.map(({ key, row }) => (
											<tr key={key}>
												{columns.map((column) => (
													<td key={column.name}>{String(row[column.name] ?? "")}</td>
												))}
											</tr>
										))}
									</tbody>
								</table>
							</div>
						</>
					) : null}
				</>
			)}
		</Modal>
	);
}
