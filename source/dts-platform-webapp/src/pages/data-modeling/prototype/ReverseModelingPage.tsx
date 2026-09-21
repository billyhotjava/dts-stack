import { Check, RotateCcw } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate, useSearchParams } from "react-router";
import {
	applyModelSpecImport,
	type DbtArchiveInspection,
	forwardUndoModelSpecImport,
	getModelSpecImportApplyResult,
	getModelSpecImportPreviewRun,
	inspectDbtModelArchive,
	type ModelSpecImportApplyResult,
	type ModelSpecImportConflictResolution,
	type ModelSpecImportPreview,
	type ModelSpecImportRevisionPins,
	type ModelSpecImportSemanticOverride,
	previewModelSpecImport,
	retryModelSpecImport,
} from "@/api/modelSpecImportApi";
import {
	listModelingImportContexts,
	loadModelingImportContext,
	type ModelingImportContext,
	type ModelingImportContextHeader,
} from "@/api/services/modelingImportContextService";
import { type CompactColumns, CompactTable } from "@/components/table";
import { dataModelingPath } from "../navigation";
import type { DataModelingRoute } from "../types";
import { Button, PageHeader, RequestState, Status } from "./PrototypePrimitives";
import { ConfirmStep, previewReadinessIssues, StrategyStep } from "./ReverseModelingInspectionSteps";
import {
	defaultImportConflictResolutions,
	type RenameMapping,
	renameMappingRequests,
} from "./services/modelImportUiState";
import { normalizeModelingRequestFailure } from "./services/planningProjectionService";
import { isAdvancedDbtImportResult, isInspectionCandidateSelectable } from "./services/reverseModelingInspection";
import { useDataModelingMenuGrant } from "./useDataModelingMenuGrant";

import { issueText } from "./ReverseModelingIssueText";
import { initialImportOverrides, useImportBatchSettings } from "./ReverseImportBatchSettings";

const steps = ["逆向策略", "确认模型信息", "生成模型", "完成"];

type Failure = { kind: "permission" | "request"; message: string };
type PlanContext = ModelingImportContext;
const EMPTY_PLAN_CONTEXT: PlanContext = {
	domains: [],
	sources: [],
	businessProcesses: [],
	dataMarts: [],
	subjectDomains: [],
};

export function ReverseModelingPage({ route }: { route: DataModelingRoute }) {
	const canMaintain = useDataModelingMenuGrant();
	const navigate = useNavigate();
	const [searchParams, setSearchParams] = useSearchParams();
	const restoredRunId = searchParams.get("importRunId") || "";
	const advancedIntent = searchParams.get("intent") === "advanced";
	const requestEpoch = useRef(0);
	const restoredRunRef = useRef("");
	const [started, setStarted] = useState(false);
	const [step, setStep] = useState(0);
	const [plans, setPlans] = useState<ModelingImportContextHeader[]>([]);
	const [planId, setPlanId] = useState("");
	const [planContext, setPlanContext] = useState<PlanContext>(EMPTY_PLAN_CONTEXT);
	const [archive, setArchive] = useState<File | null>(null);
	const [inspection, setInspection] = useState<DbtArchiveInspection | null>(null);
	const [preview, setPreview] = useState<ModelSpecImportPreview | null>(null);
	const [result, setResult] = useState<ModelSpecImportApplyResult | null>(null);
	const [selected, setSelected] = useState<string[]>([]);
	const [requestedSelection, setRequestedSelection] = useState<string[]>([]);
	const [domainMappings, setDomainMappings] = useState<Record<string, string>>({});
	const [sourceMappings, setSourceMappings] = useState<Record<string, string>>({});
	const [renameMappings, setRenameMappings] = useState<RenameMapping[]>([]);
	const [semanticOverrides, setSemanticOverrides] = useState<Record<string, ModelSpecImportSemanticOverride>>({});
	const batch = useImportBatchSettings(inspection, semanticOverrides);
	const [conflictResolutions, setConflictResolutions] = useState<Record<string, ModelSpecImportConflictResolution>>({});
	const [loading, setLoading] = useState(true);
	const [busy, setBusy] = useState<"inspect" | "preview" | "apply" | "refresh" | "retry" | "undo" | "">("");
	const [failure, setFailure] = useState<Failure | null>(null);
	const [undoStarted, setUndoStarted] = useState(false);

	const loadPlans = useCallback(async () => {
		const epoch = ++requestEpoch.current;
		setLoading(true);
		setFailure(null);
		try {
			const next = await listModelingImportContexts();
			if (requestEpoch.current !== epoch) return;
			setPlans(next);
			setPlanId((current) => (next.some((plan) => plan.id === current) ? current : next[0]?.id || ""));
		} catch (error) {
			if (requestEpoch.current !== epoch) return;
			setPlans([]);
			setPlanId("");
			setFailure(normalizeModelingRequestFailure(error, "模型导入环境读取失败。"));
		} finally {
			if (requestEpoch.current === epoch) setLoading(false);
		}
	}, []);

	useEffect(() => {
		void loadPlans();
		return () => {
			requestEpoch.current += 1;
		};
	}, [loadPlans]);

	useEffect(() => {
		if (!restoredRunId || restoredRunRef.current === restoredRunId) return;
		restoredRunRef.current = restoredRunId;
		let active = true;
		setStarted(true);
		setBusy("refresh");
		setFailure(null);
		void getModelSpecImportPreviewRun(restoredRunId)
			.then(async (restoredPreview) => {
				if (!active) return;
				setPreview(restoredPreview);
				setSelected(restoredPreview.items.filter((item) => item.action !== "BLOCKED").map((item) => item.dbtUniqueId));
				setRequestedSelection(restoredPreview.items.map((item) => item.dbtUniqueId));
				setConflictResolutions(defaultImportConflictResolutions(restoredPreview));
				setStep(2);
				try {
					const restoredResult = await getModelSpecImportApplyResult(restoredRunId);
					if (active) {
						setResult(restoredResult);
						setStep(3);
					}
				} catch (error) {
					const status = Number((error as { response?: { status?: unknown } } | null)?.response?.status ?? 0);
					if (active && status !== 404) setFailure(normalizeModelingRequestFailure(error, "导入执行结果恢复失败。"));
				}
			})
			.catch((error) => {
				if (active) setFailure(normalizeModelingRequestFailure(error, "导入运行恢复失败。"));
			})
			.finally(() => {
				if (active) setBusy("");
			});
		return () => {
			active = false;
		};
	}, [restoredRunId]);

	useEffect(() => {
		if (!planId || !inspection) {
			setPlanContext(EMPTY_PLAN_CONTEXT);
			return;
		}
		const epoch = ++requestEpoch.current;
		void loadModelingImportContext(planId)
			.then((context) => {
				if (requestEpoch.current !== epoch) return;
				setPlanContext(context);
			})
			.catch((error) => {
				if (requestEpoch.current !== epoch) return;
				setPlanContext(EMPTY_PLAN_CONTEXT);
				setFailure(normalizeModelingRequestFailure(error, "数据域或来源基线读取失败。"));
			});
	}, [inspection, planId]);

	const packageDomains = useMemo(
		() =>
			Array.from(
				new Set(
					(inspection?.package.models || [])
						.map((model) => model.semantics?.domainCode?.trim())
						.filter((value): value is string => Boolean(value)),
				),
			),
		[inspection],
	);
	const previewIssues = useMemo(
		() => previewReadinessIssues({ inspection, planId, selected, packageDomains, domainMappings, semanticOverrides: batch.effective }),
		[inspection, planId, selected, packageDomains, domainMappings, batch.effective],
	);
	const hasRetryableResult = Boolean(
		(result?.overallRun?.items || result?.items || []).some((item) =>
			item.issues.some((issue) => issue.retryable === true),
		),
	);
	const undoTargets = (result?.overallRun?.items || result?.items || []).filter((item) =>
		["CREATED", "UPDATED", "SKIPPED"].includes(item.status),
	);
	const undoPins = undoTargets.reduce<Record<string, ModelSpecImportRevisionPins>>((pins, item) => {
		if (
			item.revision != null &&
			item.modelChecksum &&
			item.implementationRevision != null &&
			item.implementationChecksum
		) {
			pins[item.dbtUniqueId] = {
				modelRevision: item.revision,
				modelChecksum: item.modelChecksum,
				implementationRevision: item.implementationRevision,
				implementationChecksum: item.implementationChecksum,
			};
		}
		return pins;
	}, {});
	const canForwardUndo = undoTargets.length > 0 && Object.keys(undoPins).length === undoTargets.length;

	const reset = () => {
		requestEpoch.current += 1;
		setStarted(false);
		setStep(0);
		setArchive(null);
		setInspection(null);
		setPreview(null);
		setResult(null);
		setSelected([]);
		setRequestedSelection([]);
		setDomainMappings({});
		setSourceMappings({});
		setRenameMappings([]);
		setSemanticOverrides({});
		batch.reset();
		setConflictResolutions({});
		setFailure(null);
		setBusy("");
		setUndoStarted(false);
		restoredRunRef.current = "";
		const next = new URLSearchParams(searchParams);
		next.delete("importRunId");
		setSearchParams(next, { replace: true });
	};

	const inspectArchive = async () => {
		if (!archive) return;
		setBusy("inspect");
		setFailure(null);
		try {
			const next = await inspectDbtModelArchive(archive);
			setInspection(next);
			setSelected(
				next.package.models
					.filter((model) => isInspectionCandidateSelectable(next, model.dbtUniqueId))
					.map((model) => model.dbtUniqueId),
			);
			setSemanticOverrides(initialImportOverrides(next));
			batch.reset();
			setDomainMappings({});
			setSourceMappings({});
			setStep(1);
		} catch (error) {
			setFailure(normalizeModelingRequestFailure(error, "dbt ZIP 检查失败。"));
		} finally {
			setBusy("");
		}
	};

	const createPreview = async () => {
		if (!inspection || !planId || !selected.length) return;
		setBusy("preview");
		setFailure(null);
		try {
			const next = await previewModelSpecImport({
				package: inspection.package,
				inspectionProof: inspection.inspectionProof,
				context: { planId, domainMappings, sourceMappings },
				selectedUniqueIds: selected,
				semanticOverrides: Object.values(batch.effective).filter((item) => selected.includes(item.modelUniqueId)),
				renameMappings: renameMappingRequests(renameMappings),
			});
			setRequestedSelection(selected);
			setSelected(next.items.filter((item) => item.action !== "BLOCKED").map((item) => item.dbtUniqueId));
			setPreview(next);
			restoredRunRef.current = next.runId;
			const params = new URLSearchParams(searchParams);
			params.set("importRunId", next.runId);
			setSearchParams(params, { replace: true });
			setConflictResolutions(defaultImportConflictResolutions(next));
			setStep(2);
		} catch (error) {
			setFailure(normalizeModelingRequestFailure(error, "导入预览生成失败。"));
		} finally {
			setBusy("");
		}
	};

	const applyPreview = async () => {
		if (!preview) return;
		setBusy("apply");
		setFailure(null);
		try {
			const next = await applyModelSpecImport({
				runId: preview.runId,
				previewHash: preview.previewHash,
				selectedUniqueIds: selected,
				idempotencyKey: crypto.randomUUID(),
				conflictResolutions,
			});
			setResult(next);
			setUndoStarted(false);
			setStep(3);
		} catch (error) {
			setFailure(normalizeModelingRequestFailure(error, "导入执行失败。"));
		} finally {
			setBusy("");
		}
	};

	const forwardUndo = async () => {
		if (!result || !canForwardUndo || undoStarted) return;
		if (!window.confirm("将为本次导入追加前向恢复修订；历史记录不会删除。是否继续？")) return;
		setBusy("undo");
		setFailure(null);
		try {
			setResult(
				await forwardUndoModelSpecImport({
					targetAttemptId: result.attemptId,
					selectedItemIds: [],
					expectedCurrentRevisions: undoPins,
					idempotencyKey: crypto.randomUUID(),
				}),
			);
			setUndoStarted(true);
		} catch (error) {
			setFailure(normalizeModelingRequestFailure(error, "前向撤销未能启动。"));
		} finally {
			setBusy("");
		}
	};

	const refreshResult = async () => {
		if (!preview) return;
		setBusy("refresh");
		setFailure(null);
		try {
			setResult(await getModelSpecImportApplyResult(preview.runId));
		} catch (error) {
			setFailure(normalizeModelingRequestFailure(error, "导入结果刷新失败。"));
		} finally {
			setBusy("");
		}
	};

	const retry = async () => {
		if (!preview) return;
		setBusy("retry");
		setFailure(null);
		try {
			setResult(
				await retryModelSpecImport(preview.runId, {
					previewHash: preview.previewHash,
					idempotencyKey: crypto.randomUUID(),
				}),
			);
		} catch (error) {
			setFailure(normalizeModelingRequestFailure(error, "失败对象重试未能启动。"));
		} finally {
			setBusy("");
		}
	};

	return (
		<main className="dmx-page dmx-reverse-page">
			<PageHeader
				description={
					advancedIntent
						? "导入与逆向建模共用同一 dbt ZIP 检查、预览和应用链路；DBT_BACKED 结果进入模型级高级加工配置。"
						: route.description
				}
				title={advancedIntent ? "高级 dbt 包导入" : "逆向建模"}
				trail="数据建模 / 维度建模"
			/>
			{!started ? (
				<section className="dmx-reverse-entry">
					<div className="dmx-reverse-mark">
						<RotateCcw size={35} />
					</div>
					<h2>逆向建模</h2>
					<p>导入外部 dbt 项目 ZIP，经检查与预览后生成统一模型草稿；导入完成后可继续使用可视化模式或代码模式编辑。</p>
					<Button
						disabled={!canMaintain}
						primary
						onClick={() => setStarted(true)}
						title={canMaintain ? undefined : "当前账号无模型导入权限"}
					>
						快速开始
					</Button>
					{!loading && !plans.length ? (
						<p className="dmx-capability-note">可以先检查 dbt 包；生成导入预览前需初始化数仓规划。</p>
					) : null}
					{!canMaintain ? <p className="dmx-capability-note">当前账号只有查看权限，不能发起 dbt 包导入。</p> : null}
				</section>
			) : (
				<section className="dmx-reverse-wizard">
					<div className="dmx-stepper">
						{steps.map((label, index) => (
							<div className={`${index === step ? "active" : ""}${index < step ? " complete" : ""}`} key={label}>
								<span>{index < step ? <Check size={15} /> : index + 1}</span>
								<strong>{label}</strong>
							</div>
						))}
					</div>
					<div className="dmx-wizard-body">
						{failure ? (
							<RequestState
								description={failure.message}
								kind={failure.kind === "permission" ? "permission" : "error"}
								title="当前操作未完成"
							/>
						) : null}
						{step === 0 ? (
							<StrategyStep archive={archive} onArchive={setArchive} />
						) : step === 1 && inspection ? (
							<ConfirmStep
								domainMappings={domainMappings}
								domains={planContext.domains}
								inspection={inspection}
								onPlanId={(id) => {
									setPlanId(id); setSourceMappings({}); setDomainMappings({}); batch.reset();
									setSemanticOverrides(current => Object.fromEntries(Object.entries(current).map(([key, value]) =>
										[key, { ...value, businessProcessId: undefined, dataMartId: undefined, subjectDomainId: undefined }])));
								}}
								onDomainMapping={(code, value) => setDomainMappings((current) => ({ ...current, [code]: value }))}
								onRenameMappings={setRenameMappings}
								onSelected={setSelected}
								onSemanticOverride={(id, value) => { batch.customize(id); setSemanticOverrides((current) => ({ ...current, [id]: value })); }}
								onSourceMapping={(code, value) => setSourceMappings((current) => ({ ...current, [code]: value }))}
								planId={planId}
								plans={plans}
								plansLoading={loading}
								selected={selected}
								renameMappings={renameMappings}
								semanticOverrides={batch.effective}
								batchControls={{ defaults: batch.defaults, custom: batch.custom, onInherit: batch.inherit, onChange: next => {
									if (next.domainId !== batch.defaults.domainId) {
										setDomainMappings(Object.fromEntries(packageDomains.map(code => [code, next.domainId])));
										setSemanticOverrides(current => Object.fromEntries(Object.entries(current).map(([id, value]) =>
											[id, { ...value, businessProcessId: undefined }])));
									}
									batch.setDefaults(next);
								} }}
								onSourcesChanged={(sources, sourcePlanId) => {
									if (sourcePlanId === planId) setPlanContext(current => ({ ...current, sources }));
								}}
								sourceMappings={sourceMappings}
								sources={planContext.sources}
								businessProcesses={planContext.businessProcesses}
								dataMarts={planContext.dataMarts}
								subjectDomains={planContext.subjectDomains}
							/>
						) : step === 2 && preview ? (
							<GenerateStep
								conflictResolutions={conflictResolutions}
								onConflictResolution={(id, value) => setConflictResolutions((current) => ({ ...current, [id]: value }))}
								preview={preview}
								requestedSelection={requestedSelection}
								selected={selected}
							/>
						) : step === 3 && result ? (
							<CompleteStep
								onOpenWorkbench={() => navigate(dataModelingPath("dimensions", "workbench"))}
								onOpenAdvanced={(modelSpecId) =>
									navigate(
										`${dataModelingPath("dimensions", "workbench")}?modelSpecId=${encodeURIComponent(modelSpecId)}&open=advanced`,
									)
								}
								preview={preview}
								result={result}
							/>
						) : (
							<RequestState description="上一步尚未返回有效结果。" kind="empty" title="暂无可展示内容" />
						)}
					</div>
					{step === 1 && inspection && previewIssues.length ? (
						<section className="dmx-reverse-readiness" aria-label="生成预览前待完成" role="status">
							<strong>生成预览前还需完成</strong>
							<ul>
								{previewIssues.map((issue) => (
									<li key={issue}>{issue}</li>
								))}
							</ul>
						</section>
					) : null}
					<footer className="dmx-wizard-actions">
						<Button disabled={Boolean(busy)} onClick={reset}>
							取消
						</Button>
						{step > 0 && step < 3 ? (
							<Button disabled={Boolean(busy)} onClick={() => { if (step === 2) setSelected(requestedSelection); setStep((current) => current - 1); }}>
								上一步
							</Button>
						) : null}
						{step === 0 ? (
							<Button
								disabled={!canMaintain || !archive || Boolean(busy)}
								primary
								onClick={() => void inspectArchive()}
							>
								{busy === "inspect" ? "正在检查…" : "开始识别"}
							</Button>
						) : null}
						{step === 1 ? (
							<Button
								disabled={!canMaintain || previewIssues.length > 0 || Boolean(busy)}
								primary
								title={previewIssues.length ? previewIssues.join("；") : undefined}
								onClick={() => void createPreview()}
							>
								{busy === "preview" ? "正在预览…" : "生成预览"}
							</Button>
						) : null}
						{step === 2 ? (
							<Button
								disabled={!canMaintain || !preview || !selected.length || preview.status === "EXPIRED" || Boolean(busy)}
								primary
								onClick={() => void applyPreview()}
							>
								{busy === "apply" ? "正在生成…" : "生成模型"}
							</Button>
						) : null}
						{step === 3 && result?.status === "RUNNING" ? (
							<Button disabled={!canMaintain || Boolean(busy)} primary onClick={() => void refreshResult()}>
								{busy === "refresh" ? "刷新中…" : "刷新结果"}
							</Button>
						) : null}
						{step === 3 && result && ["PARTIAL", "FAILED", "BLOCKED"].includes(result.status) ? (
							<Button
								disabled={!canMaintain || Boolean(busy) || !hasRetryableResult}
								primary
								title={
									hasRetryableResult
										? "仅重试服务端判定为可重试的失败对象"
										: "当前失败项均不可重试，请按失败原因修复后重新预览"
								}
								onClick={() => void retry()}
							>
								{busy === "retry" ? "重试中…" : "重试失败对象"}
							</Button>
						) : null}
						{step === 3 && result && result.status !== "RUNNING" && !undoStarted ? (
							<Button
								disabled={!canMaintain || Boolean(busy) || !canForwardUndo}
								title={canForwardUndo ? "追加恢复修订，不删除历史" : "结果缺少完整修订固定记录，不能安全撤销"}
								onClick={() => void forwardUndo()}
							>
								{busy === "undo" ? "撤销中…" : "前向撤销本次导入"}
							</Button>
						) : null}
						{step === 3 && result && result.status !== "RUNNING" ? (
							<Button primary onClick={reset}>
								返回逆向建模
							</Button>
						) : null}
					</footer>
				</section>
			)}
		</main>
	);
}

function GenerateStep({
	preview,
	conflictResolutions,
	onConflictResolution,
	requestedSelection,
	selected,
}: {
	preview: ModelSpecImportPreview;
	conflictResolutions: Record<string, ModelSpecImportConflictResolution>;
	onConflictResolution: (id: string, value: ModelSpecImportConflictResolution) => void;
	requestedSelection: string[];
	selected: string[];
}) {
	return (
		<>
			<div className="dmx-wizard-heading">
				<div>
					<h3>导入预览</h3>
					<p>这是服务端预检查结果；应用后只生成或更新草稿，不自动发布和构建。</p>
				</div>
				<Status tone={preview.summary.blocked ? "danger" : "success"}>
					可处理 {preview.summary.ready} / {preview.summary.total}
				</Status>
			</div>
			<div className="dmx-summary-line">
				<Status tone="success">新建 {preview.summary.create}</Status>
				<Status tone="info">更新 {preview.summary.update}</Status>
				<Status>跳过 {preview.summary.skip}</Status>
				<Status tone={preview.summary.conflict ? "warning" : "neutral"}>冲突 {preview.summary.conflict}</Status>
				<Status tone={preview.summary.blocked ? "danger" : "neutral"}>阻断 {preview.summary.blocked}</Status>
			</div>
			<PreviewItemsTable
				conflictResolutions={conflictResolutions}
				onConflictResolution={onConflictResolution}
				preview={preview}
				requestedSelection={requestedSelection}
				selected={selected}
			/>
		</>
	);
}

function PreviewItemsTable({
	preview,
	selected,
	requestedSelection,
	conflictResolutions,
	onConflictResolution,
}: {
	preview: ModelSpecImportPreview;
	selected: string[];
	requestedSelection: string[];
	conflictResolutions: Record<string, ModelSpecImportConflictResolution>;
	onConflictResolution: (id: string, value: ModelSpecImportConflictResolution) => void;
}) {
	const rows = useMemo(() => preview.items.map((item) => ({ key: item.dbtUniqueId, item })), [preview.items]);
	const columns = useMemo<CompactColumns<(typeof rows)[number]>>(
		() => [
			{ title: "对象", key: "object", render: (_, { item }) => item.dbtUniqueId },
			{
				title: "选择来源",
				key: "selection",
				render: (_, { item }) => (
					<Status
						tone={
							!selected.includes(item.dbtUniqueId)
								? "danger"
								: requestedSelection.includes(item.dbtUniqueId)
									? "info"
									: "warning"
						}
					>
						{!selected.includes(item.dbtUniqueId)
							? "不可应用"
							: requestedSelection.includes(item.dbtUniqueId)
								? "用户选择"
								: "依赖闭包补充"}
					</Status>
				),
			},
			{
				title: "动作",
				key: "action",
				render: (_, { item }) => (
					<Status tone={item.action === "BLOCKED" ? "danger" : item.action === "CONFLICT" ? "warning" : "info"}>
						{item.action}
					</Status>
				),
			},
			{ title: "转换模式", key: "conversionMode", render: (_, { item }) => item.conversionMode },
			{
				title: "冲突处理",
				key: "conflict",
				render: (_, { item }) =>
					item.action === "CONFLICT" ? (
						<select
							onChange={(event) =>
								onConflictResolution(item.dbtUniqueId, event.target.value as ModelSpecImportConflictResolution)
							}
							value={conflictResolutions[item.dbtUniqueId] || "KEEP_CURRENT"}
						>
							<option value="KEEP_CURRENT">保留当前</option>
							<option value="ACCEPT_INCOMING">采用导入版本</option>
							<option value="CANCEL">取消该对象</option>
						</select>
					) : (
						"—"
					),
			},
			{
				title: "原因与处理建议",
				key: "issues",
				render: (_, { item }) => <pre className="dmx-table-issues">{issueText(item.issues)}</pre>,
			},
		],
		[conflictResolutions, onConflictResolution, requestedSelection, selected],
	);
	return <CompactTable columns={columns} dataSource={rows} pagination={false} rowKey="key" />;
}

function CompleteStep({
	preview,
	result,
	onOpenAdvanced,
	onOpenWorkbench,
}: {
	preview: ModelSpecImportPreview | null;
	result: ModelSpecImportApplyResult;
	onOpenAdvanced: (modelSpecId: string) => void;
	onOpenWorkbench: () => void;
}) {
	const summary = result.overallRun?.summary || result.summary;
	const items = result.overallRun?.items || result.items;
	return (
		<div className="dmx-complete-result">
			<span>
				<Check size={28} />
			</span>
			<h3>{result.status === "RUNNING" ? "导入正在执行" : `导入结果：${result.status}`}</h3>
			<div className="dmx-summary-line">
				<Status tone="success">成功 {summary.succeeded}</Status>
				<Status tone="info">新建 {summary.created}</Status>
				<Status tone="info">更新 {summary.updated}</Status>
				<Status>跳过 {summary.skipped}</Status>
				<Status tone={summary.failed ? "danger" : "neutral"}>失败 {summary.failed}</Status>
				<Status tone={summary.blocked ? "danger" : "neutral"}>阻断 {summary.blocked}</Status>
			</div>
			{items.length ? <CompleteItemsTable items={items} onOpenAdvanced={onOpenAdvanced} preview={preview} /> : null}
			<Button disabled={!summary.succeeded} onClick={onOpenWorkbench}>
				打开模型工作台
			</Button>
		</div>
	);
}

function CompleteItemsTable({
	items,
	preview,
	onOpenAdvanced,
}: {
	items: ModelSpecImportApplyResult["items"];
	preview: ModelSpecImportPreview | null;
	onOpenAdvanced: (modelSpecId: string) => void;
}) {
	const rows = useMemo(
		() => items.map((item) => ({ key: `${item.sequence || 0}-${item.dbtUniqueId}`, item })),
		[items],
	);
	const columns = useMemo<CompactColumns<(typeof rows)[number]>>(
		() => [
			{ title: "对象", key: "object", render: (_, { item }) => item.dbtUniqueId },
			{
				title: "结果",
				key: "status",
				render: (_, { item }) => (
					<Status tone={["FAILED", "BLOCKED"].includes(item.status) ? "danger" : "success"}>{item.status}</Status>
				),
			},
			{
				title: "模型",
				key: "modelSpecId",
				render: (_, { item }) => item.modelSpecId || "—",
			},
			{
				title: "版本",
				key: "revision",
				render: (_, { item }) => (item.revision ? `r${item.revision}` : "—"),
			},
			{
				title: "失败原因",
				key: "issues",
				render: (_, { item }) => <pre className="dmx-table-issues">{issueText(item.issues)}</pre>,
			},
			{
				title: "后续操作",
				key: "actions",
				render: (_, { item }) =>
					isAdvancedDbtImportResult(preview, item) && item.modelSpecId ? (
						<Button onClick={() => item.modelSpecId && onOpenAdvanced(item.modelSpecId)}>进入模型代码模式</Button>
					) : (
						"—"
					),
			},
		],
		[onOpenAdvanced, preview],
	);
	return (
		<div className="dmx-table-scroll">
			<CompactTable columns={columns} dataSource={rows} pagination={false} rowKey="key" />
		</div>
	);
}
