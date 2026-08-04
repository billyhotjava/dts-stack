import { Check, Database, FileArchive, Play, RefreshCw, RotateCcw } from "lucide-react";
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
	getWarehousePlanCategories,
	getWarehousePlanSources,
	listWarehousePlans,
	type WarehousePlanCategoryBindingView,
	type WarehousePlanHeader,
	type WarehousePlanSourceBindingView,
} from "@/api/warehousePlanApi";
import { dataModelingPath } from "../navigation";
import type { DataModelingRoute } from "../types";
import { Button, PageHeader, RequestState, Status } from "./PrototypePrimitives";
import {
	createRenameMapping,
	defaultImportConflictResolutions,
	type RenameMapping,
	renameMappingRequests,
} from "./services/modelImportUiState";
import { normalizeModelingRequestFailure } from "./services/planningProjectionService";
import { useDataModelingMenuGrant } from "./useDataModelingMenuGrant";

const steps = ["逆向策略", "确认模型信息", "生成模型", "完成"];

type Failure = { kind: "permission" | "request"; message: string };
type PlanContext = {
	domains: WarehousePlanCategoryBindingView[];
	sources: WarehousePlanSourceBindingView[];
};
const issueText = (
	issues: Array<{
		code: string;
		message: string;
		recoveryAction?: string | null;
		stage?: string | null;
		category?: string | null;
		retryable?: boolean;
		correlationId?: string | null;
	}>,
) =>
	issues.length
		? issues
				.map(
					(issue) =>
						`${issue.stage || "UNKNOWN"}/${issue.category || "GENERAL"} · ${issue.code}：${issue.message}${issue.recoveryAction ? `；处理建议：${issue.recoveryAction}` : ""}${issue.retryable == null ? "" : `；可重试：${issue.retryable ? "是" : "否"}`}${issue.correlationId ? `；关联号：${issue.correlationId}` : ""}`,
				)
				.join("\n")
		: "—";

export function ReverseModelingPage({ route }: { route: DataModelingRoute }) {
	const canMaintain = useDataModelingMenuGrant();
	const navigate = useNavigate();
	const [searchParams, setSearchParams] = useSearchParams();
	const restoredRunId = searchParams.get("importRunId") || "";
	const requestEpoch = useRef(0);
	const restoredRunRef = useRef("");
	const [started, setStarted] = useState(false);
	const [step, setStep] = useState(0);
	const [plans, setPlans] = useState<WarehousePlanHeader[]>([]);
	const [planId, setPlanId] = useState("");
	const [planContext, setPlanContext] = useState<PlanContext>({ domains: [], sources: [] });
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
			const next = (await listWarehousePlans()).filter((plan) => plan.lifecycleStatus !== "ARCHIVED");
			if (requestEpoch.current !== epoch) return;
			setPlans(next);
			setPlanId((current) => (next.some((plan) => plan.id === current) ? current : next[0]?.id || ""));
		} catch (error) {
			if (requestEpoch.current !== epoch) return;
			setPlans([]);
			setPlanId("");
			setFailure(normalizeModelingRequestFailure(error, "模型导入上下文读取失败。"));
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
		if (!planId) {
			setPlanContext({ domains: [], sources: [] });
			return;
		}
		const epoch = ++requestEpoch.current;
		void Promise.all([getWarehousePlanCategories(planId), getWarehousePlanSources(planId)])
			.then(([categories, sources]) => {
				if (requestEpoch.current !== epoch) return;
				setPlanContext({
					domains: categories.value.domainBindings.filter(
						(item) => item.confirmationStatus === "CONFIRMED" && item.resolutionStatus === "AVAILABLE",
					),
					sources: sources.bindings.filter(
						(item) => item.confirmationStatus === "CONFIRMED" && item.resolutionStatus === "AVAILABLE",
					),
				});
			})
			.catch((error) => {
				if (requestEpoch.current !== epoch) return;
				setPlanContext({ domains: [], sources: [] });
				setFailure(normalizeModelingRequestFailure(error, "数据域或来源基线读取失败。"));
			});
	}, [planId]);

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
		if (!archive || !planId) return;
		setBusy("inspect");
		setFailure(null);
		try {
			const next = await inspectDbtModelArchive(archive);
			setInspection(next);
			setSelected(
				next.package.models.filter((model) => model.conversion?.mode !== "BLOCKED").map((model) => model.dbtUniqueId),
			);
			setSemanticOverrides(
				Object.fromEntries(
					next.package.models.map((model) => [
						model.dbtUniqueId,
						{
							modelUniqueId: model.dbtUniqueId,
							modelType: model.semantics?.modelType || undefined,
							layer: model.semantics?.layer || undefined,
							businessName: model.name,
							businessDefinition: model.description || undefined,
							grain: model.semantics?.grain?.statement
								? { statement: model.semantics.grain.statement, keys: model.semantics.grain.keys || [] }
								: undefined,
						},
					]),
				),
			);
			const defaultDomain = planContext.domains[0]?.domainId || "";
			setDomainMappings(
				Object.fromEntries(
					next.package.models
						.map((model) => model.semantics?.domainCode?.trim())
						.filter((value): value is string => Boolean(value))
						.map((code) => [code, defaultDomain]),
				),
			);
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
				semanticOverrides: Object.values(semanticOverrides).filter((item) => selected.includes(item.modelUniqueId)),
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
			<PageHeader description={route.description} title="逆向建模" trail="数据建模 / 维度建模" />
			{loading ? (
				<RequestState description="正在准备模型导入上下文。" kind="loading" title="正在准备逆向建模" />
			) : !started ? (
				<section className="dmx-reverse-entry">
					<div className="dmx-reverse-mark">
						<RotateCcw size={35} />
					</div>
					<h2>逆向建模</h2>
					<p>导入外部 dbt 项目 ZIP，经检查与预览后生成可视化模型草稿。</p>
					<Button
						disabled={!canMaintain || !plans.length}
						primary
						onClick={() => setStarted(true)}
						title={canMaintain ? undefined : "当前账号无模型导入权限"}
					>
						<Play size={16} /> 快速开始
					</Button>
					{!plans.length ? (
						<p className="dmx-inline-error">当前环境没有可用的模型导入上下文，请联系管理员初始化。</p>
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
								onDomainMapping={(code, value) => setDomainMappings((current) => ({ ...current, [code]: value }))}
								onRenameMappings={setRenameMappings}
								onSelected={setSelected}
								onSemanticOverride={(id, value) => setSemanticOverrides((current) => ({ ...current, [id]: value }))}
								onSourceMapping={(code, value) => setSourceMappings((current) => ({ ...current, [code]: value }))}
								selected={selected}
								renameMappings={renameMappings}
								semanticOverrides={semanticOverrides}
								sourceMappings={sourceMappings}
								sources={planContext.sources}
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
								result={result}
							/>
						) : (
							<RequestState description="上一步尚未返回有效结果。" kind="empty" title="暂无可展示内容" />
						)}
					</div>
					<footer className="dmx-wizard-actions">
						<Button disabled={Boolean(busy)} onClick={reset}>
							取消
						</Button>
						{step > 0 && step < 3 ? (
							<Button disabled={Boolean(busy)} onClick={() => setStep((current) => current - 1)}>
								上一步
							</Button>
						) : null}
						{step === 0 ? (
							<Button
								disabled={!canMaintain || !archive || !planId || Boolean(busy)}
								primary
								onClick={() => void inspectArchive()}
							>
								{busy === "inspect" ? "正在检查…" : "开始识别"}
							</Button>
						) : null}
						{step === 1 ? (
							<Button
								disabled={
									!canMaintain ||
									!selected.length ||
									packageDomains.some((code) => !domainMappings[code]) ||
									Boolean(busy)
								}
								primary
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
								<RefreshCw size={14} /> {busy === "refresh" ? "刷新中…" : "刷新结果"}
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
								title={canForwardUndo ? "追加恢复修订，不删除历史" : "结果缺少完整修订固定证据，不能安全撤销"}
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

function StrategyStep({
	archive,
	onArchive,
}: {
	archive: File | null;
	onArchive: (file: File | null) => void;
}) {
	return (
		<>
			<div className="dmx-strategy-cards">
				<button disabled title="当前版本尚无数据库结构逆向契约" type="button">
					<Database size={22} />
					<span>
						<strong>从数据源逆向</strong>
						<small>尚未接入，当前不可用</small>
					</span>
				</button>
				<button className="active" disabled type="button">
					<FileArchive size={22} />
					<span>
						<strong>导入 dbt ZIP</strong>
						<small>检查 manifest、catalog 与模型结构证据</small>
					</span>
				</button>
			</div>
			<div className="dmx-dbt-drop">
				<FileArchive size={30} />
				<strong>选择 dbt 项目 ZIP</strong>
				<p>仅支持 ZIP；重新导入按项目身份和文件指纹更新，允许部分成功并逐项返回失败原因。</p>
				<input
					accept=".zip,application/zip"
					aria-label="选择 dbt ZIP"
					onChange={(event) => onArchive(event.target.files?.[0] || null)}
					type="file"
				/>
				{archive ? <small>已选择：{archive.name}</small> : null}
			</div>
		</>
	);
}

function ConfirmStep({
	inspection,
	selected,
	onSelected,
	domains,
	domainMappings,
	onDomainMapping,
	sources,
	sourceMappings,
	onSourceMapping,
	renameMappings,
	onRenameMappings,
	semanticOverrides,
	onSemanticOverride,
}: {
	inspection: DbtArchiveInspection;
	selected: string[];
	onSelected: (ids: string[]) => void;
	domains: WarehousePlanCategoryBindingView[];
	domainMappings: Record<string, string>;
	onDomainMapping: (code: string, value: string) => void;
	sources: WarehousePlanSourceBindingView[];
	sourceMappings: Record<string, string>;
	onSourceMapping: (code: string, value: string) => void;
	renameMappings: RenameMapping[];
	onRenameMappings: (mappings: RenameMapping[]) => void;
	semanticOverrides: Record<string, ModelSpecImportSemanticOverride>;
	onSemanticOverride: (id: string, value: ModelSpecImportSemanticOverride) => void;
}) {
	const packageDomains = Array.from(
		new Set(
			inspection.package.models
				.map((model) => model.semantics?.domainCode?.trim())
				.filter((value): value is string => Boolean(value)),
		),
	);
	const packageSources = Array.from(
		new Map([
			...inspection.package.sources.map((source) => [source.dbtUniqueId, source.name] as const),
			...inspection.package.models
				.flatMap((model) => model.semantics?.sourceRefs || [])
				.filter((source) => Boolean(source.ref))
				.map((source) => [String(source.ref), String(source.ref)] as const),
		]).entries(),
	);
	return (
		<>
			<div className="dmx-wizard-heading">
				<div>
					<h3>确认模型信息</h3>
					<p>检查兼容性，映射已确认的数据域和来源，并选择导入对象。</p>
				</div>
				<Status tone={inspection.compatibility.importProjection === "BLOCKED" ? "danger" : "info"}>
					{inspection.compatibility.importProjection}
				</Status>
			</div>
			{inspection.compatibility.issues.length ? (
				<pre className="dmx-issue-list">{issueText(inspection.compatibility.issues)}</pre>
			) : null}
			<div className="dmx-mapping-grid">
				{packageDomains.map((code) => (
					<label key={code}>
						<span>数据域 {code}</span>
						<select onChange={(event) => onDomainMapping(code, event.target.value)} value={domainMappings[code] || ""}>
							<option value="">请选择已确认数据域</option>
							{domains.map((domain) => (
								<option key={domain.domainId} value={domain.domainId}>
									{domain.name || domain.code || domain.domainId}
								</option>
							))}
						</select>
					</label>
				))}
				{packageSources.map(([sourceId, sourceName]) => (
					<label key={sourceId}>
						<span>来源 {sourceName}</span>
						<select
							onChange={(event) => onSourceMapping(sourceId, event.target.value)}
							value={sourceMappings[sourceId] || ""}
						>
							<option value="">自动匹配（可能阻断）</option>
							{sources.map((binding) => (
								<option key={binding.bindingId} value={binding.bindingId}>
									{binding.displayName || binding.sourceId || binding.bindingId}
								</option>
							))}
						</select>
					</label>
				))}
			</div>
			<section className="dmx-rename-mappings">
				<header>
					<div>
						<strong>重新导入重命名映射</strong>
						<p>只有明确确认 old unique_id → new unique_id，系统才会按重命名处理；留空不会推断删除或改名。</p>
					</div>
					<Button onClick={() => onRenameMappings([...renameMappings, createRenameMapping()])}>新增映射</Button>
				</header>
				{renameMappings.map((mapping, index) => (
					<div className="dmx-rename-mapping-row" key={mapping._clientId}>
						<input
							aria-label={`旧 unique_id ${index + 1}`}
							onChange={(event) =>
								onRenameMappings(
									renameMappings.map((item, row) =>
										row === index ? { ...item, oldUniqueId: event.target.value } : item,
									),
								)
							}
							placeholder="旧 unique_id"
							value={mapping.oldUniqueId}
						/>
						<span>→</span>
						<input
							aria-label={`新 unique_id ${index + 1}`}
							onChange={(event) =>
								onRenameMappings(
									renameMappings.map((item, row) =>
										row === index ? { ...item, newUniqueId: event.target.value } : item,
									),
								)
							}
							placeholder="新 unique_id"
							value={mapping.newUniqueId}
						/>
						<Button danger onClick={() => onRenameMappings(renameMappings.filter((_, row) => row !== index))}>
							删除
						</Button>
					</div>
				))}
			</section>
			<div className="dmx-table-scroll">
				<table className="dmx-table dmx-import-semantics">
					<thead>
						<tr>
							<th>选择</th>
							<th>dbt 对象</th>
							<th>业务名称</th>
							<th>模型类型</th>
							<th>目标分层</th>
							<th>粒度说明</th>
							<th>业务主键</th>
							<th>字段数</th>
						</tr>
					</thead>
					<tbody>
						{inspection.package.models.map((model) => {
							const checked = selected.includes(model.dbtUniqueId);
							const blocked = model.conversion?.mode === "BLOCKED";
							const override = semanticOverrides[model.dbtUniqueId] || { modelUniqueId: model.dbtUniqueId };
							const patch = (next: Partial<ModelSpecImportSemanticOverride>) =>
								onSemanticOverride(model.dbtUniqueId, { ...override, ...next, modelUniqueId: model.dbtUniqueId });
							return (
								<tr key={model.dbtUniqueId}>
									<td>
										<input
											checked={checked}
											disabled={blocked}
											onChange={(event) =>
												onSelected(
													event.target.checked
														? [...selected, model.dbtUniqueId]
														: selected.filter((id) => id !== model.dbtUniqueId),
												)
											}
											type="checkbox"
										/>
									</td>
									<td>
										{model.dbtUniqueId}
										{blocked ? <Status tone="danger">不可导入</Status> : null}
									</td>
									<td>
										<input
											onChange={(event) => patch({ businessName: event.target.value })}
											value={override.businessName || ""}
										/>
									</td>
									<td>
										<select
											onChange={(event) => patch({ modelType: event.target.value || undefined })}
											value={override.modelType || ""}
										>
											<option value="">请选择</option>
											<option value="DIMENSION">维度表</option>
											<option value="FACT">明细表</option>
											<option value="SUMMARY">汇总表</option>
											<option value="APPLICATION">应用表</option>
										</select>
									</td>
									<td>
										<select
											onChange={(event) => patch({ layer: event.target.value || undefined })}
											value={override.layer || ""}
										>
											<option value="">请选择</option>
											<option value="DWD">DWD</option>
											<option value="DWS">DWS</option>
											<option value="ADS">ADS</option>
										</select>
									</td>
									<td>
										<input
											onChange={(event) =>
												patch({ grain: { statement: event.target.value, keys: override.grain?.keys || [] } })
											}
											value={override.grain?.statement || ""}
										/>
									</td>
									<td>
										<input
											onChange={(event) => {
												const keys = event.target.value
													.split(",")
													.map((item) => item.trim())
													.filter(Boolean);
												patch({ businessKeys: keys, grain: { statement: override.grain?.statement, keys } });
											}}
											placeholder="逗号分隔"
											value={(override.businessKeys || override.grain?.keys || []).join(",")}
										/>
									</td>
									<td>{model.columns?.length || 0}</td>
								</tr>
							);
						})}
					</tbody>
				</table>
			</div>
		</>
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
					<p>这是服务端预检查结果；应用后只生成或更新草稿，不自动发布和物化。</p>
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
			<div className="dmx-table-scroll">
				<table className="dmx-table">
					<thead>
						<tr>
							<th>对象</th>
							<th>选择来源</th>
							<th>动作</th>
							<th>转换模式</th>
							<th>冲突处理</th>
							<th>原因与处理建议</th>
						</tr>
					</thead>
					<tbody>
						{preview.items.map((item) => (
							<tr key={item.dbtUniqueId}>
								<td>{item.dbtUniqueId}</td>
								<td>
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
								</td>
								<td>
									<Status tone={item.action === "BLOCKED" ? "danger" : item.action === "CONFLICT" ? "warning" : "info"}>
										{item.action}
									</Status>
								</td>
								<td>{item.conversionMode}</td>
								<td>
									{item.action === "CONFLICT" ? (
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
									)}
								</td>
								<td>
									<pre className="dmx-table-issues">{issueText(item.issues)}</pre>
								</td>
							</tr>
						))}
					</tbody>
				</table>
			</div>
		</>
	);
}

function CompleteStep({
	result,
	onOpenWorkbench,
}: {
	result: ModelSpecImportApplyResult;
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
			{items.length ? (
				<div className="dmx-table-scroll">
					<table className="dmx-table">
						<thead>
							<tr>
								<th>对象</th>
								<th>结果</th>
								<th>模型</th>
								<th>版本</th>
								<th>失败原因</th>
							</tr>
						</thead>
						<tbody>
							{items.map((item) => (
								<tr key={`${item.sequence || 0}-${item.dbtUniqueId}`}>
									<td>{item.dbtUniqueId}</td>
									<td>
										<Status tone={["FAILED", "BLOCKED"].includes(item.status) ? "danger" : "success"}>
											{item.status}
										</Status>
									</td>
									<td>{item.modelSpecId || "—"}</td>
									<td>{item.revision ? `r${item.revision}` : "—"}</td>
									<td>
										<pre className="dmx-table-issues">{issueText(item.issues)}</pre>
									</td>
								</tr>
							))}
						</tbody>
					</table>
				</div>
			) : null}
			<Button disabled={!summary.succeeded} onClick={onOpenWorkbench}>
				打开模型工作台
			</Button>
		</div>
	);
}
