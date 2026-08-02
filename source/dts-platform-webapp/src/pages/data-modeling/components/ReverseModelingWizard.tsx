import { ArrowLeft, Check, Database, FileArchive, RefreshCw, Upload, WandSparkles } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import {
	applyModelSpecImport,
	type DbtArchiveInspection,
	forwardUndoModelSpecImport,
	getModelSpecImportApplyResult,
	getModelSpecImportPreviewRun,
	inspectDbtModelArchive,
	type ModelPackageJson,
	type ModelSpecImportApplyResult,
	type ModelSpecImportConflictResolution,
	type ModelSpecImportIssue,
	type ModelSpecImportPreview,
	type ModelSpecImportSemanticOverride,
	previewModelSpecImport,
	retryModelSpecImport,
} from "@/api/modelSpecImportApi";
import { listWarehousePlans, type WarehousePlanHeader } from "@/api/warehousePlanApi";
import { ActionButton, StatusTag } from "./WorkspacePage";

const STEPS = ["上传检查", "语义与范围", "导入预检", "导入结果"];
const LAST_RUN_KEY = "dts.modeling.reverse.lastRunId";
const MODEL_TYPES = ["FACT", "DIMENSION", "SUMMARY", "APPLICATION"];
const LAYERS = ["DWD", "DWS", "ADS"];
const IMPORTABLE_PLAN_STATUSES = new Set(["DRAFT", "BASELINE_READY", "DESIGNING", "VALIDATING", "READY_TO_PUBLISH"]);

type SemanticDraft = {
	businessName: string;
	businessDefinition: string;
	modelType: string;
	layer: string;
	grainStatement: string;
	grainKeys: string;
	consumptionScenario: string;
};

const requestKey = (prefix: string) => {
	const random =
		typeof crypto !== "undefined" && typeof crypto.randomUUID === "function"
			? crypto.randomUUID()
			: `${Date.now()}-${Math.random().toString(36).slice(2)}`;
	return `${prefix}-${random}`;
};

const errorMessage = (error: unknown) => (error instanceof Error ? error.message : "请求失败，请稍后重试");

const formatIssue = (issue: ModelSpecImportIssue) => {
	const classification = [issue.stage, issue.category].filter(Boolean).join("/");
	const context = [
		classification ? `阶段 ${classification}` : "",
		issue.dependencyUniqueId ? `依赖 ${issue.dependencyUniqueId}` : "",
		issue.correlationId ? `关联号 ${issue.correlationId}` : "",
		issue.retryable ? "可重试" : "不可重试",
	]
		.filter(Boolean)
		.join("，");
	return `${issue.code}：${issue.message}（${issue.recoveryAction || "NONE"}${context ? `；${context}` : ""}）`;
};

const draftFrom = (model: ModelPackageJson["models"][number]): SemanticDraft => ({
	businessName: model.name || "",
	businessDefinition: model.description || "",
	modelType: model.semantics?.modelType || "",
	layer: model.semantics?.layer || "",
	grainStatement: model.semantics?.grain?.statement || "",
	grainKeys: (model.semantics?.grain?.keys || []).join(", "),
	consumptionScenario: model.semantics?.consumptionScenarios?.[0] || "",
});

const semanticOverride = (modelUniqueId: string, draft: SemanticDraft): ModelSpecImportSemanticOverride => ({
	modelUniqueId,
	businessName: draft.businessName.trim() || undefined,
	businessDefinition: draft.businessDefinition.trim() || undefined,
	modelType: draft.modelType || undefined,
	layer: draft.layer || undefined,
	grain:
		draft.grainStatement.trim() || draft.grainKeys.trim()
			? {
					statement: draft.grainStatement.trim() || undefined,
					keys: draft.grainKeys
						.split(",")
						.map((item) => item.trim())
						.filter(Boolean),
				}
			: undefined,
	consumptionScenarios: draft.consumptionScenario.trim() ? [draft.consumptionScenario.trim()] : undefined,
});

export function ReverseModelingWizard() {
	const [started, setStarted] = useState(false);
	const [step, setStep] = useState(0);
	const [plans, setPlans] = useState<WarehousePlanHeader[]>([]);
	const [planId, setPlanId] = useState("");
	const [archive, setArchive] = useState<File | null>(null);
	const [inspection, setInspection] = useState<DbtArchiveInspection | null>(null);
	const [selectedModels, setSelectedModels] = useState<Set<string>>(new Set());
	const [drafts, setDrafts] = useState<Record<string, SemanticDraft>>({});
	const [renameSources, setRenameSources] = useState<Record<string, string>>({});
	const [conflictResolutions, setConflictResolutions] = useState<Record<string, ModelSpecImportConflictResolution>>({});
	const [preview, setPreview] = useState<ModelSpecImportPreview | null>(null);
	const [applyResult, setApplyResult] = useState<ModelSpecImportApplyResult | null>(null);
	const [undoResult, setUndoResult] = useState<ModelSpecImportApplyResult | null>(null);
	const [undoSelected, setUndoSelected] = useState<Set<string>>(new Set());
	const [busy, setBusy] = useState(false);
	const [error, setError] = useState("");
	const [resumeRunId, setResumeRunId] = useState(() => sessionStorage.getItem(LAST_RUN_KEY) || "");

	useEffect(() => {
		let active = true;
		void listWarehousePlans()
			.then((items) => {
				if (!active) return;
				const writable = items.filter((item) => IMPORTABLE_PLAN_STATUSES.has(item.lifecycleStatus));
				setPlans(writable);
				setPlanId((current) => current || writable[0]?.id || "");
			})
			.catch((cause) => active && setError(errorMessage(cause)));
		return () => {
			active = false;
		};
	}, []);

	const selectedIds = useMemo(() => Array.from(selectedModels).sort(), [selectedModels]);
	const readyIds = useMemo(
		() =>
			preview?.items
				.filter(
					(item) =>
						item.action !== "BLOCKED" &&
						(item.action !== "CONFLICT" ||
							["KEEP_CURRENT", "ACCEPT_INCOMING"].includes(conflictResolutions[item.dbtUniqueId] || "")),
				)
				.map((item) => item.dbtUniqueId) || [],
		[conflictResolutions, preview],
	);
	const retryableItems = useMemo(
		() => applyResult?.items.filter((item) => item.issues.some((issue) => issue.retryable)) || [],
		[applyResult],
	);
	const undoableItems = useMemo(
		() =>
			applyResult?.items.filter(
				(item) =>
					["CREATED", "UPDATED"].includes(item.status) &&
					Boolean(item.revision && item.modelChecksum && item.implementationRevision && item.implementationChecksum),
			) || [],
		[applyResult],
	);

	const inspectArchive = async () => {
		if (!archive || !planId) return;
		setBusy(true);
		setError("");
		try {
			const result = await inspectDbtModelArchive(archive);
			setInspection(result);
			const ids = result.package.models.map((model) => model.dbtUniqueId);
			setSelectedModels(new Set(ids));
			setDrafts(Object.fromEntries(result.package.models.map((model) => [model.dbtUniqueId, draftFrom(model)])));
			setRenameSources({});
			setConflictResolutions({});
			setPreview(null);
			setApplyResult(null);
			setUndoResult(null);
			setUndoSelected(new Set());
			setStep(1);
		} catch (cause) {
			setError(errorMessage(cause));
		} finally {
			setBusy(false);
		}
	};

	const runPreview = async () => {
		if (!inspection || !planId || selectedIds.length === 0) return;
		setBusy(true);
		setError("");
		try {
			const result = await previewModelSpecImport({
				package: inspection.package,
				inspectionProof: inspection.inspectionProof,
				context: { planId, domainMappings: {}, sourceMappings: {} },
				selectedUniqueIds: selectedIds,
				semanticOverrides: selectedIds.map((uniqueId) => semanticOverride(uniqueId, drafts[uniqueId])),
				renameMappings: selectedIds.flatMap((uniqueId) => {
					const oldUniqueId = renameSources[uniqueId]?.trim();
					return oldUniqueId ? [{ oldUniqueId, newUniqueId: uniqueId }] : [];
				}),
			});
			setPreview(result);
			sessionStorage.setItem(LAST_RUN_KEY, result.runId);
			setResumeRunId(result.runId);
			setStep(2);
		} catch (cause) {
			setError(errorMessage(cause));
		} finally {
			setBusy(false);
		}
	};

	const runApply = async () => {
		if (!preview || readyIds.length === 0) return;
		setBusy(true);
		setError("");
		try {
			const result = await applyModelSpecImport({
				runId: preview.runId,
				previewHash: preview.previewHash,
				selectedUniqueIds: readyIds,
				idempotencyKey: requestKey("dbt-import-apply"),
				conflictResolutions: Object.fromEntries(
					readyIds.flatMap((uniqueId) => {
						const resolution = conflictResolutions[uniqueId];
						return resolution ? [[uniqueId, resolution]] : [];
					}),
				),
			});
			setApplyResult(result);
			setStep(3);
		} catch (cause) {
			setError(errorMessage(cause));
		} finally {
			setBusy(false);
		}
	};

	const runForwardUndo = async () => {
		if (!applyResult || undoSelected.size === 0) return;
		const selected = undoableItems.filter((item) => undoSelected.has(item.dbtUniqueId));
		setBusy(true);
		setError("");
		try {
			setUndoResult(
				await forwardUndoModelSpecImport({
					targetAttemptId: applyResult.attemptId,
					selectedItemIds: selected.map((item) => item.dbtUniqueId),
					expectedCurrentRevisions: Object.fromEntries(
						selected.map((item) => [
							item.dbtUniqueId,
							{
								modelRevision: item.revision as number,
								modelChecksum: item.modelChecksum as string,
								implementationRevision: item.implementationRevision as number,
								implementationChecksum: item.implementationChecksum as string,
							},
						]),
					),
					idempotencyKey: requestKey("dbt-import-forward-undo"),
				}),
			);
			setUndoSelected(new Set());
		} catch (cause) {
			setError(errorMessage(cause));
		} finally {
			setBusy(false);
		}
	};

	const retryApply = async () => {
		if (!preview) return;
		setBusy(true);
		setError("");
		try {
			setApplyResult(
				await retryModelSpecImport(preview.runId, {
					previewHash: preview.previewHash,
					idempotencyKey: requestKey("dbt-import-retry"),
				}),
			);
		} catch (cause) {
			setError(errorMessage(cause));
		} finally {
			setBusy(false);
		}
	};

	const resumeLastRun = async () => {
		const runId = resumeRunId.trim();
		if (!runId) return;
		setStarted(true);
		setBusy(true);
		setError("");
		try {
			const restoredPreview = await getModelSpecImportPreviewRun(runId);
			setPreview(restoredPreview);
			setPlanId(restoredPreview.planId || "");
			sessionStorage.setItem(LAST_RUN_KEY, runId);
			try {
				setApplyResult(await getModelSpecImportApplyResult(runId));
				setStep(3);
			} catch (cause) {
				const status = (cause as { response?: { status?: number } })?.response?.status;
				if (status !== 404) throw cause;
				setApplyResult(null);
				setStep(2);
			}
		} catch (cause) {
			setError(errorMessage(cause));
		} finally {
			setBusy(false);
		}
	};

	const updateDraft = (uniqueId: string, patch: Partial<SemanticDraft>) => {
		setDrafts((current) => ({ ...current, [uniqueId]: { ...current[uniqueId], ...patch } }));
	};

	const toggleModel = (uniqueId: string, checked: boolean) => {
		setSelectedModels((current) => {
			const next = new Set(current);
			if (checked) next.add(uniqueId);
			else next.delete(uniqueId);
			return next;
		});
	};

	if (!started) {
		return (
			<section className="dm-reverse-intro">
				<div className="dm-reverse-intro__visual">
					<Database aria-hidden="true" size={31} />
					<WandSparkles aria-hidden="true" size={24} />
				</div>
				<div>
					<StatusTag tone="info">dbt ZIP</StatusTag>
					<h2>把外部 dbt 项目导入可视化建模</h2>
					<p>上传 dbt ZIP，完成安全检查、业务语义补齐和预检后，再创建 canonical 模型草稿。</p>
					<div className="dm-page__actions">
						<ActionButton kind="primary" onClick={() => setStarted(true)}>
							上传 dbt ZIP
						</ActionButton>
					</div>
					<label>
						<span>恢复已有导入</span>
						<input
							aria-label="导入运行 ID"
							onChange={(event) => setResumeRunId(event.target.value)}
							placeholder="输入预检返回的 runId"
							value={resumeRunId}
						/>
					</label>
					<ActionButton disabled={busy || !resumeRunId.trim()} onClick={() => void resumeLastRun()}>
						{busy ? "恢复中…" : "恢复导入"}
					</ActionButton>
				</div>
				<aside>
					<strong>反向建模流程</strong>
					<ol>
						{STEPS.map((item, index) => (
							<li key={item}>
								<span>{index + 1}</span>
								{item}
							</li>
						))}
					</ol>
				</aside>
			</section>
		);
	}

	return (
		<section className="dm-reverse-wizard">
			<header className="dm-reverse-wizard__header">
				<ActionButton kind="quiet" onClick={() => (step === 0 ? setStarted(false) : setStep((current) => current - 1))}>
					<ArrowLeft aria-hidden="true" size={15} />
					返回
				</ActionButton>
				<div>
					<h2>dbt 反向建模</h2>
					<p>技术事实来自已检查 ZIP；业务补充通过白名单进入预检。</p>
				</div>
			</header>
			<ol className="dm-wizard-steps" aria-label="dbt 反向建模步骤">
				{STEPS.map((item, index) => (
					<li className={`${index === step ? "is-active" : ""} ${index < step ? "is-done" : ""}`} key={item}>
						<span>{index < step ? <Check aria-hidden="true" size={13} /> : index + 1}</span>
						<strong>{item}</strong>
					</li>
				))}
			</ol>

			{error ? (
				<div className="dm-stage-notice" role="alert">
					{error}
				</div>
			) : null}
			<div className="dm-wizard-content">
				{step === 0 ? (
					<div className="dm-reverse-form">
						<label>
							<span>
								建模计划 <b>*</b>
							</span>
							<select className="dm-select" onChange={(event) => setPlanId(event.target.value)} value={planId}>
								<option value="">请选择建模计划</option>
								{plans.map((plan) => (
									<option key={plan.id} value={plan.id}>
										{plan.code} · {plan.name}
									</option>
								))}
							</select>
						</label>
						<label className="dm-reverse-form__wide">
							<span>
								dbt ZIP <b>*</b>
							</span>
							<div className="dm-input-with-icon">
								<FileArchive aria-hidden="true" size={14} />
								<input
									accept=".zip,application/zip"
									aria-label="选择 dbt ZIP"
									onChange={(event) => setArchive(event.target.files?.[0] || null)}
									type="file"
								/>
							</div>
						</label>
						<div>
							<small>支持包含 manifest/catalog 的 artifact-rich ZIP；安全检查不会创建模型。</small>
						</div>
						<ActionButton disabled={busy || !archive || !planId} kind="primary" onClick={() => void inspectArchive()}>
							<Upload aria-hidden="true" size={15} />
							{busy ? "检查中…" : "上传并检查"}
						</ActionButton>
					</div>
				) : null}

				{step === 1 && inspection ? (
					<div className="dm-reverse-confirm">
						<div className="dm-reverse-summary">
							<div>
								<span>项目</span>
								<strong>{inspection.package.dbt.projectName}</strong>
							</div>
							<div>
								<span>安全检查</span>
								<strong>{inspection.compatibility.inspection}</strong>
							</div>
							<div>
								<span>导入投影</span>
								<strong>{inspection.compatibility.importProjection}</strong>
							</div>
							<div>
								<span>物化认证</span>
								<strong>{inspection.compatibility.materialization}</strong>
							</div>
							<div>
								<span>凭据有效期</span>
								<strong>{new Date(inspection.proofExpiresAt).toLocaleString()}</strong>
							</div>
						</div>
						{inspection.compatibility.issues.map((issue) => (
							<div className="dm-stage-notice" key={issue.code}>
								{issue.code}：{issue.message}
							</div>
						))}
						<div className="dm-field-table-wrap">
							<table className="dm-field-table dm-reverse-table">
								<thead>
									<tr>
										<th>选择</th>
										<th>dbt uniqueId</th>
										<th>原 uniqueId（重命名时）</th>
										<th>业务名称</th>
										<th>业务定义</th>
										<th>模型类型</th>
										<th>分层</th>
										<th>粒度</th>
										<th>粒度字段</th>
										<th>消费场景</th>
									</tr>
								</thead>
								<tbody>
									{inspection.package.models.map((model) => {
										const draft = drafts[model.dbtUniqueId] || draftFrom(model);
										return (
											<tr key={model.dbtUniqueId}>
												<td>
													<input
														aria-label={`选择 ${model.dbtUniqueId}`}
														checked={selectedModels.has(model.dbtUniqueId)}
														onChange={(event) => toggleModel(model.dbtUniqueId, event.target.checked)}
														type="checkbox"
													/>
												</td>
												<td>
													<code>{model.dbtUniqueId}</code>
												</td>
												<td>
													<input
														aria-label={`${model.dbtUniqueId} 原 uniqueId`}
														onChange={(event) =>
															setRenameSources((current) => ({ ...current, [model.dbtUniqueId]: event.target.value }))
														}
														placeholder="例如 model.pkg.old_name"
														value={renameSources[model.dbtUniqueId] || ""}
													/>
												</td>
												<td>
													<input
														aria-label={`${model.dbtUniqueId} 业务名称`}
														onChange={(event) => updateDraft(model.dbtUniqueId, { businessName: event.target.value })}
														value={draft.businessName}
													/>
												</td>
												<td>
													<input
														aria-label={`${model.dbtUniqueId} 业务定义`}
														onChange={(event) =>
															updateDraft(model.dbtUniqueId, { businessDefinition: event.target.value })
														}
														value={draft.businessDefinition}
													/>
												</td>
												<td>
													<select
														onChange={(event) => updateDraft(model.dbtUniqueId, { modelType: event.target.value })}
														value={draft.modelType}
													>
														<option value="">请选择</option>
														{MODEL_TYPES.map((value) => (
															<option key={value}>{value}</option>
														))}
													</select>
												</td>
												<td>
													<select
														onChange={(event) => updateDraft(model.dbtUniqueId, { layer: event.target.value })}
														value={draft.layer}
													>
														<option value="">请选择</option>
														{LAYERS.map((value) => (
															<option key={value}>{value}</option>
														))}
													</select>
												</td>
												<td>
													<input
														aria-label={`${model.dbtUniqueId} 粒度`}
														onChange={(event) => updateDraft(model.dbtUniqueId, { grainStatement: event.target.value })}
														value={draft.grainStatement}
													/>
												</td>
												<td>
													<input
														aria-label={`${model.dbtUniqueId} 粒度字段`}
														onChange={(event) => updateDraft(model.dbtUniqueId, { grainKeys: event.target.value })}
														value={draft.grainKeys}
													/>
												</td>
												<td>
													<input
														aria-label={`${model.dbtUniqueId} 消费场景`}
														onChange={(event) =>
															updateDraft(model.dbtUniqueId, { consumptionScenario: event.target.value })
														}
														value={draft.consumptionScenario}
													/>
												</td>
											</tr>
										);
									})}
								</tbody>
							</table>
						</div>
					</div>
				) : null}

				{step === 2 && preview ? (
					<div className="dm-reverse-confirm">
						<div className="dm-reverse-summary">
							<div>
								<span>运行 ID</span>
								<strong>{preview.runId}</strong>
							</div>
							<div>
								<span>候选</span>
								<strong>{preview.summary.total}</strong>
							</div>
							<div>
								<span>可执行</span>
								<strong>{preview.summary.ready}</strong>
							</div>
							<div>
								<span>阻断</span>
								<strong>{preview.summary.blocked}</strong>
							</div>
							<div>
								<span>冲突</span>
								<strong>{preview.summary.conflict}</strong>
							</div>
						</div>
						<div className="dm-field-table-wrap">
							<table className="dm-field-table">
								<thead>
									<tr>
										<th>模型</th>
										<th>动作</th>
										<th>表示能力</th>
										<th>冲突决策</th>
										<th>问题与恢复动作</th>
									</tr>
								</thead>
								<tbody>
									{preview.items.map((item) => (
										<tr key={item.dbtUniqueId}>
											<td>
												<code>{item.dbtUniqueId}</code>
											</td>
											<td>
												<StatusTag
													tone={item.action === "BLOCKED" || item.action === "CONFLICT" ? "danger" : "success"}
												>
													{item.action}
												</StatusTag>
											</td>
											<td>{item.conversionMode}</td>
											<td>
												{item.action === "CONFLICT" ? (
													<select
														aria-label={`${item.dbtUniqueId} 冲突决策`}
														onChange={(event) =>
															setConflictResolutions((current) => ({
																...current,
																[item.dbtUniqueId]: event.target.value as ModelSpecImportConflictResolution,
															}))
														}
														value={conflictResolutions[item.dbtUniqueId] || ""}
													>
														<option value="">请选择</option>
														<option value="KEEP_CURRENT">保留当前</option>
														<option value="ACCEPT_INCOMING">接受导入</option>
														<option value="CANCEL">取消该项</option>
													</select>
												) : (
													"—"
												)}
											</td>
											<td>{item.issues.length ? item.issues.map(formatIssue).join("；") : "—"}</td>
										</tr>
									))}
								</tbody>
							</table>
						</div>
					</div>
				) : null}

				{step === 3 && applyResult ? (
					<div className="dm-generation-preview">
						{applyResult.status === "SUCCESS" ? (
							<Check aria-hidden="true" size={30} />
						) : (
							<RefreshCw aria-hidden="true" size={30} />
						)}
						<h3>最新尝试状态：{applyResult.status}</h3>
						<p>运行 ID：{preview?.runId || "—"}</p>
						<p>
							选中 {applyResult.summary.selected}，处理中 {applyResult.summary.pending}，成功{" "}
							{applyResult.summary.succeeded}，跳过 {applyResult.summary.skipped}，失败 {applyResult.summary.failed}
							，阻断 {applyResult.summary.blocked}。
						</p>
						<h3>整体导入状态：{applyResult.overallRun.status}</h3>
						<p>
							根尝试 {applyResult.overallRun.rootAttemptId}；选中 {applyResult.overallRun.summary.selected}，成功{" "}
							{applyResult.overallRun.summary.succeeded}，跳过 {applyResult.overallRun.summary.skipped}，失败{" "}
							{applyResult.overallRun.summary.failed}，阻断 {applyResult.overallRun.summary.blocked}。
						</p>
						<div className="dm-field-table-wrap">
							<table className="dm-field-table">
								<thead>
									<tr>
										<th>撤销</th>
										<th>模型</th>
										<th>状态</th>
										<th>模型 ID</th>
										<th>问题与恢复动作</th>
									</tr>
								</thead>
								<tbody>
									{applyResult.overallRun.items.map((item) => (
										<tr key={item.dbtUniqueId}>
											<td>
												{undoableItems.some((candidate) => candidate.dbtUniqueId === item.dbtUniqueId) ? (
													<input
														aria-label={`撤销 ${item.dbtUniqueId}`}
														checked={undoSelected.has(item.dbtUniqueId)}
														onChange={(event) =>
															setUndoSelected((current) => {
																const next = new Set(current);
																if (event.target.checked) next.add(item.dbtUniqueId);
																else next.delete(item.dbtUniqueId);
																return next;
															})
														}
														type="checkbox"
													/>
												) : (
													"—"
												)}
											</td>
											<td>
												<code>{item.dbtUniqueId}</code>
											</td>
											<td>{item.status}</td>
											<td>{item.modelSpecId || "—"}</td>
											<td>{item.issues.length ? item.issues.map(formatIssue).join("；") : "—"}</td>
										</tr>
									))}
								</tbody>
							</table>
						</div>
						{retryableItems.length > 0 ? (
							<ActionButton disabled={busy} onClick={() => void retryApply()}>
								<RefreshCw aria-hidden="true" size={15} />
								{busy ? "重试中…" : `重试 ${retryableItems.length} 个可恢复项`}
							</ActionButton>
						) : null}
						{undoSelected.size > 0 ? (
							<ActionButton disabled={busy} onClick={() => void runForwardUndo()}>
								{busy ? "撤销中…" : `前向撤销 ${undoSelected.size} 个模型`}
							</ActionButton>
						) : null}
						{undoResult ? (
							<output className="dm-stage-notice">
								前向撤销状态：{undoResult.status}；成功 {undoResult.summary.succeeded}，失败 {undoResult.summary.failed}
								，阻断 {undoResult.summary.blocked}。
							</output>
						) : null}
					</div>
				) : null}
			</div>

			<footer className="dm-wizard-footer">
				<span>ZIP、SQL/Jinja 与 inspectionProof 不写入普通日志；apply 仅创建 DRAFT。</span>
				<div>
					{step === 1 ? <ActionButton onClick={() => setStep(0)}>重新上传</ActionButton> : null}
					{step === 1 ? (
						<ActionButton disabled={busy || selectedIds.length === 0} kind="primary" onClick={() => void runPreview()}>
							{busy ? "预检中…" : "生成预检"}
						</ActionButton>
					) : null}
					{step === 2 ? (
						<ActionButton disabled={busy || readyIds.length === 0} kind="primary" onClick={() => void runApply()}>
							{busy ? "导入中…" : `导入 ${readyIds.length} 个可执行模型`}
						</ActionButton>
					) : null}
				</div>
			</footer>
		</section>
	);
}
