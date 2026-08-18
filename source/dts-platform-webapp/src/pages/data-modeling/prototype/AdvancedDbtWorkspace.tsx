import { lazy, Suspense, useEffect, useState } from "react";
import { useNavigate } from "react-router";
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
import { getModelRepresentation } from "@/api/modelRepresentationApi";
import {
	getDbtImplementationPreview,
	transitionDbtOwnership,
	validateDbtOwnershipTransition,
	type DbtImplementationPreview,
	type OwnershipTransitionResult,
	type OwnershipTransitionValidation,
} from "@/api/modelImplementationTransitionApi";
import { getModelLifecycle, type ModelLifecycleTimeline } from "@/api/modelSpecApi";
import type { ModelRepresentationView } from "@/features/modeling/contracts/modelRepresentationContract";
import { toModelImplementationEtag } from "@/features/modeling/contracts/modelImplementationContract";
import { toModelSpecEtag, type CanonicalModelSpecView, type ModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
import { dataModelingPath } from "../navigation";
import { Button, Modal, RequestState, Status } from "./PrototypePrimitives";
import type { DbtEditorFocusLocation } from "./DbtCodeEditor";
import { dbtDraftStatusLabel, isDbtDraftConflictStatus } from "./dbtCodeEditorContract";
import { normalizeModelingRequestFailure } from "./services/planningProjectionService";
import { newModelingIdempotencyKey } from "./modelingIdempotency";
import { modelingCapabilityReasonsText, resolveModelingModeAccess } from "./modelingWorkbenchMode";
import { ownershipTransitionSummary } from "./ownershipTransitionPresentation";

const LazyDbtCodeEditor = lazy(() => import("./DbtCodeEditor").then((module) => ({ default: module.DbtCodeEditor })));

const canonical = (model: ModelSpecView): model is CanonicalModelSpecView =>
	model.compatibilityMode === "CANONICAL" && model.contractVersion === 2;

const isManagedDependencyPath = (path: string) => path.replaceAll("\\", "/").startsWith("models/.dts_dependencies/");

export function AdvancedDbtWorkspace({
	model,
	initialTargetPhysicalName = "",
	onBack,
	onCommitSuccess,
	onDirtyChange,
	canMaintain,
	onTransitionSuccess,
}: {
	model: ModelSpecView;
	initialTargetPhysicalName?: string;
	onBack: () => void;
	onCommitSuccess?: (receipt: DbtDraftCommit) => void;
	onDirtyChange?: (dirty: boolean) => void;
	canMaintain: boolean;
	onTransitionSuccess?: (result: OwnershipTransitionResult) => void;
}) {
	const navigate = useNavigate();
	const [representation, setRepresentation] = useState<ModelRepresentationView | null>(null);
	const [baseImplementation, setBaseImplementation] = useState<ModelLifecycleTimeline["implementation"]>(null);
	const [targetPhysicalName, setTargetPhysicalName] = useState(initialTargetPhysicalName.trim());
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
	const [preview, setPreview] = useState<DbtImplementationPreview | null>(null);
	const [previewFailure, setPreviewFailure] = useState("");
	const [previewAttempt, setPreviewAttempt] = useState(0);
	const [transitioning, setTransitioning] = useState(false);
	const [transitionValidation, setTransitionValidation] = useState<OwnershipTransitionValidation | null>(null);
	const [transitionSuccess, setTransitionSuccess] = useState("");
	const [previewExpandedPaths, setPreviewExpandedPaths] = useState<string[]>([]);
	const [workspaceAttempt, setWorkspaceAttempt] = useState(0);
	const [diagnosticFocus, setDiagnosticFocus] = useState<DbtEditorFocusLocation | null>(null);

	useEffect(() => {
		let active = true;
		setPreview(null);
		setPreviewFailure("");
		setFailure("");
		setRepresentation(null);
		setBaseImplementation(null);
		if (!canMaintain) {
			setBusy("");
			return () => {
				active = false;
			};
		}
		setBusy("load");
		void getModelLifecycle(model.id)
			.then(({ implementation }) => {
				if (active) {
					setBaseImplementation(implementation);
					const persistedName = implementation?.settings?.targetPhysicalName;
					if (typeof persistedName === "string" && persistedName.trim()) setTargetPhysicalName(persistedName.trim());
				}
				const exactImplementation =
					implementation?.revision === model.revision && implementation.modelChecksum === model.checksum;
				return getModelRepresentation(model.id, {
					modelRevision: model.revision,
					implementationRevision: exactImplementation ? implementation.implementationRevision : undefined,
					representationScope: "TECHNICAL",
				});
			})
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
	}, [canMaintain, model.checksum, model.id, model.revision, workspaceAttempt]);
	useEffect(() => {
		setTargetPhysicalName(initialTargetPhysicalName.trim());
	}, [initialTargetPhysicalName, model.id]);

	const canOpenAdvanced = Boolean(representation?.allowedActions.includes("OPEN_ADVANCED_DBT"));
	const canPreviewDesigner = Boolean(representation?.allowedActions.includes("OPEN_DBT_PREVIEW"));
	const codeAccess = resolveModelingModeAccess({
		view: "code",
		canMaintain,
		allowedActions: representation?.allowedActions || [],
		capabilityReasons: representation?.capabilityReasons || [],
	});
	const designerGenerated = representation?.ownershipMode === "DESIGNER_GENERATED";
	useEffect(() => {
		if (!designerGenerated || !canPreviewDesigner || !representation?.implementationRevision) return;
		let active = true;
		void getDbtImplementationPreview(model.id, {
			modelRevision: model.revision,
			implementationRevision: representation.implementationRevision,
		})
			.then((value) => active && setPreview(value))
			.catch((error) => active && setPreviewFailure(normalizeModelingRequestFailure(error, "生成代码预览读取失败。").message));
		return () => {
			active = false;
		};
	}, [canPreviewDesigner, designerGenerated, model.id, model.revision, previewAttempt, representation?.implementationRevision]);
	const validateTakeOver = async () => {
		if (!representation?.implementationRevision || !preview || transitioning) return;
		setTransitioning(true);
		setFailure("");
		try {
			const pins = {
				modelRevision: model.revision,
				implementationRevision: representation.implementationRevision,
				modelEtag: toModelSpecEtag(model),
				implementationEtag: toModelImplementationEtag({
					modelSpecId: model.id,
					implementationRevision: representation.implementationRevision,
					implementationChecksum: representation.implementationChecksum || "",
				}),
			};
			const validation = await validateDbtOwnershipTransition(model.id, pins);
			if (!validation.allowed) throw new Error(validation.reasons.join("；") || "当前版本不能接管代码实现");
			setTransitionValidation(validation);
		} catch (error) {
			setFailure(normalizeModelingRequestFailure(error, "接管代码实现失败。").message);
		} finally {
			setTransitioning(false);
		}
	};
	const confirmTakeOver = async () => {
		if (!representation?.implementationRevision || !transitionValidation || transitioning) return;
		setTransitioning(true);
		setFailure("");
		try {
			const result = await transitionDbtOwnership(model.id, {
				modelRevision: model.revision,
				implementationRevision: representation.implementationRevision,
				modelEtag: toModelSpecEtag(model),
				implementationEtag: toModelImplementationEtag({ modelSpecId: model.id, implementationRevision: representation.implementationRevision, implementationChecksum: representation.implementationChecksum || "" }),
				previewChecksum: transitionValidation.previewChecksum,
				idempotencyKey: newModelingIdempotencyKey(),
			});
			setTransitionValidation(null);
			try {
				installDraft(
					await createDbtImplementationDraft(result.model.id, {
						planId: result.model.planId,
						baseModelRevision: result.model.revision,
						baseModelChecksum: result.model.checksum,
						baseImplementationRevision: result.implementation.implementationRevision,
						baseImplementationChecksum: result.implementation.implementationChecksum,
						idempotencyKey: newModelingIdempotencyKey(),
					}),
				);
				setTransitionSuccess(`已接管并创建高级草稿：模型 r${result.model.revision}，实现 r${result.implementation.implementationRevision}。`);
			} catch (draftError) {
				setTransitionSuccess(`已接管：模型 r${result.model.revision}，实现 r${result.implementation.implementationRevision}。`);
				setFailure(normalizeModelingRequestFailure(draftError, "接管已完成，但高级草稿创建失败，可重新创建。").message);
			}
			onTransitionSuccess?.(result);
		} catch (error) {
			setFailure(normalizeModelingRequestFailure(error, "接管代码实现失败。").message);
		} finally {
			setTransitioning(false);
		}
	};
	function installDraft(created: DbtImplementationDraft) {
		const sourceFiles = created.sourceBundle?.files.map(({ path, content }) => ({ path, content })) || [];
		setDraft(created);
		setFiles(sourceFiles);
		setSelectedPath(sourceFiles[0]?.path || "");
		setDirty(false);
		setConflict(false);
		setCommit(null);
		setValidation(null);
		setDiagnosticFocus(null);
	}
	const recordFailure = (error: unknown, fallback: string) => {
		const status = Number((error as { response?: { status?: unknown } } | null)?.response?.status ?? 0);
		setConflict(isDbtDraftConflictStatus(status));
		setFailure(normalizeModelingRequestFailure(error, fallback).message);
	};
	const create = async () => {
		if (!canonical(model) || !canMaintain || !canOpenAdvanced) return;
		const firstImplementation = !baseImplementation;
		const normalizedTargetPhysicalName = targetPhysicalName.trim();
		if (firstImplementation && !/^[a-z][a-z0-9_]{0,62}$/.test(normalizedTargetPhysicalName)) {
			setFailure("请填写有效的目标物理表名：仅支持小写字母、数字和下划线，且必须以字母开头。");
			return;
		}
		setBusy("create");
		setFailure("");
		setConflict(false);
		setCommit(null);
		setValidation(null);
		try {
			installDraft(
				await createDbtImplementationDraft(model.id, {
					planId: model.planId,
					baseModelRevision: model.revision,
					baseModelChecksum: model.checksum,
					baseImplementationRevision: baseImplementation?.implementationRevision || null,
					baseImplementationChecksum: baseImplementation?.implementationChecksum || null,
					targetPhysicalName: firstImplementation ? normalizedTargetPhysicalName : null,
					idempotencyKey: newModelingIdempotencyKey(),
				}),
			);
		} catch (error) {
			recordFailure(error, "dbt 高级草稿创建失败。");
		} finally {
			setBusy("");
		}
	};
	const persist = async () => {
		if (!canMaintain) throw new Error("当前账号无高级实现维护权限");
		if (!draft) throw new Error("请先创建高级草稿");
		if (conflict) throw new Error("草稿版本已冲突，请放弃本地修改并重新读取");
		if (draft.state === "COMMITTED") throw new Error("已提交草稿不可继续编辑，请创建新草稿");
		const saved = await saveDbtImplementationDraftFiles(model.id, draft.draftId, {
			expectedEtag: draft.etag,
			files,
		});
		const next: DbtImplementationDraft = {
			...draft,
			state: "DRAFT",
			etag: saved.etag,
			expiresAt: saved.expiresAt,
		};
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
			const checked = await validateDbtImplementationDraft(model.id, saved.draftId, {
				expectedEtag: saved.etag,
			});
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
				dependencyChecksum: validation.dependencyValidation?.dependencyChecksum,
				idempotencyKey: newModelingIdempotencyKey(),
			});
			setCommit(receipt);
			setDraft((current) => (current ? { ...current, state: "COMMITTED", etag: receipt.etag } : current));
			setDirty(false);
			onCommitSuccess?.(receipt);
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
				implementationRevision: commit?.implementationRevision,
				representationScope: "TECHNICAL",
			});
			setRepresentation(latest);
			if (!latest.allowedActions.includes("OPEN_ADVANCED_DBT"))
				throw new Error(modelingCapabilityReasonsText(latest.capabilityReasons) || "当前表示不允许创建新的高级 dbt 草稿");
			installDraft(
				await createDbtImplementationDraft(model.id, {
					planId: model.planId,
					baseModelRevision,
					baseModelChecksum,
					baseImplementationRevision: latest.implementationRevision || null,
					baseImplementationChecksum: latest.implementationChecksum || null,
					idempotencyKey: newModelingIdempotencyKey(),
				}),
			);
		} catch (error) {
			recordFailure(error, "新 dbt 高级草稿创建失败。");
		} finally {
			setBusy("");
		}
	};

	const selectedFile = files.find((file) => file.path === selectedPath) || null;
	const selectedFileManaged = Boolean(selectedFile && isManagedDependencyPath(selectedFile.path));
	const draftCommitted = draft?.state === "COMMITTED" || Boolean(commit);
	const draftWriteLocked = draftCommitted || conflict;
	const draftStatus = draft
		? dbtDraftStatusLabel({ conflict, dirty, committed: draftCommitted, validated: Boolean(validation) })
		: busy === "load"
			? "读取中"
			: "准备中";
	const diagnosticsFor = (path: string) => validation?.diagnostics.filter((item) => item.path === path) || [];
	const focusDiagnostic = (item: DbtDraftValidation["diagnostics"][number]) => {
		if (!item.path || !files.some((file) => file.path === item.path) || !Number.isInteger(item.line) || Number(item.line) < 1) return;
		setSelectedPath(item.path);
		setDiagnosticFocus((current) => ({
			path: item.path as string,
			line: Number(item.line),
			column: Number.isInteger(item.column) && Number(item.column) > 0 ? Number(item.column) : 1,
			token: (current?.token || 0) + 1,
		}));
	};
	const resetConflictingDraft = () => {
		setDraft(null);
		setFiles([]);
		setSelectedPath("");
		setNewPath("");
		setValidation(null);
		setCommit(null);
		setDirty(false);
		setConflict(false);
		setFailure("");
		setDiagnosticFocus(null);
		setWorkspaceAttempt((current) => current + 1);
	};
	useEffect(() => {
		onDirtyChange?.(dirty && !draftCommitted);
	}, [dirty, draftCommitted, onDirtyChange]);
	useEffect(
		() => () => {
			onDirtyChange?.(false);
		},
		[onDirtyChange],
	);
	return (
		<section aria-label="高级 dbt 工作区" className="dmx-advanced-dbt-workspace">
			<header className="dmx-advanced-dbt-header">
				<div>
					<div aria-label="模型表现模式" className="dmx-workbench-mode-switch" role="group">
						<Button onClick={onBack} type="text">可视化模式</Button>
						<Button className="active" type="text">代码模式</Button>
					</div>
					<Button className="dmx-table-action" onClick={onBack} type="link">
						返回模型设计
					</Button>
					<div>
						<strong>高级 dbt 工作区</strong>
						<span>
							{model.name} · 模型 r{model.revision}
						</span>
					</div>
				</div>
				<Status tone={conflict ? "danger" : draftCommitted ? "success" : dirty ? "warning" : "info"}>
					{draftStatus}
				</Status>
			</header>
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
				<div>
					<RequestState
						description="草稿 ETag 已变化，当前编辑器已锁定，不会继续覆盖远端版本。"
						kind="error"
						title="检测到并发冲突"
					/>
					<Button onClick={resetConflictingDraft}>放弃本地修改并重新读取</Button>
				</div>
			) : null}
			{!canMaintain ? null : !canonical(model) ? (
				<RequestState description="历史只读模型不能创建高级 dbt 草稿。" kind="empty" title="当前模型不可维护" />
			) : representation && codeAccess.access === "BLOCKED" ? (
				<RequestState description={codeAccess.reason} kind="permission" title="当前代码模式不可用" />
			) : designerGenerated ? (
				<div className="dmx-advanced-dbt-intro">
					<p className="dmx-capability-note">以下三个文件由可视化模型生成，仅供预览；不会自动保存或转换。</p>
					{preview?.files.map((file) => (
						<details key={file.path} onToggle={(event) => {
							const open = event.currentTarget.open;
							setPreviewExpandedPaths((current) => open ? [...new Set([...current, file.path])] : current.filter((path) => path !== file.path));
						}} open={file.nodeKind !== "STG"}>
							<summary>{file.path}{file.nodeKind === "STG" ? " · 系统生成的中间节点" : ""}</summary>
							{file.nodeKind !== "STG" || previewExpandedPaths.includes(file.path) ? (
								<Suspense fallback={<textarea aria-label={`预览 ${file.path}`} disabled value={file.content} />}>
									<LazyDbtCodeEditor content={file.content} diagnostics={[]} onChange={() => undefined} onSave={() => undefined} path={file.path} readOnly />
								</Suspense>
							) : null}
						</details>
					))}
					{previewFailure ? (
						<RequestState description={previewFailure} kind="error" onRetry={() => setPreviewAttempt((current) => current + 1)} title="生成代码预览读取失败" />
					) : preview ? (
						<Button disabled={!canMaintain || transitioning} onClick={() => void validateTakeOver()} primary>
							{transitioning ? "接管中…" : "接管代码实现"}
						</Button>
					) : <RequestState description="正在读取三个生成文件。" kind="loading" title="生成代码预览" />}
					{transitionSuccess ? <Status tone="success">{transitionSuccess}</Status> : null}
				</div>
			) : !draft ? (
				<div className="dmx-advanced-dbt-intro">
					<p className="dmx-capability-note">
						SQL/Jinja 仅在高级 dbt 工作区维护，业务可视化设计仍以模型结构为准。已有 ZIP 请从逆向建模导入，
						完成映射后可直接进入对应模型的本工作区。
					</p>
					<dl className="dmx-summary-list">
						<dt>模型</dt>
						<dd>
							{model.name} · r{model.revision}
						</dd>
						<dt>实现所有权</dt>
						<dd>{representation?.ownershipMode || model.implementationMode}</dd>
						<dt>实现修订</dt>
						<dd>
							{representation?.implementationRevision
								? `r${representation.implementationRevision}`
								: baseImplementation?.implementationRevision
									? `r${baseImplementation.implementationRevision}（上一逻辑版本）`
									: "尚无"}
						</dd>
						<dt>能力限制</dt>
						<dd>{modelingCapabilityReasonsText(representation?.capabilityReasons || []) || "无"}</dd>
					</dl>
					{!baseImplementation ? (
						<div className="dmx-form-grid">
							<label className="dmx-form-field--wide">
								<span className="required">目标物理表名</span>
								<input
									aria-label="目标物理表名"
									disabled={Boolean(busy)}
									maxLength={63}
									onChange={(event) => setTargetPhysicalName(event.target.value)}
									placeholder="例如 biz_dwd_project_follow_up_v2"
									value={targetPhysicalName}
								/>
								<small>首次提交实现后，该名称由实现修订统一持久化，并用于物化目标关系。</small>
							</label>
						</div>
					) : null}
					<div className="dmx-dialog-actions">
						<Button
							disabled={!canMaintain || Boolean(busy)}
							onClick={() => navigate(`${dataModelingPath("dimensions", "reverse")}?intent=advanced`)}
						>
							导入 dbt ZIP
						</Button>
						<Button
							disabled={
								!canMaintain ||
								!canOpenAdvanced ||
								Boolean(busy) ||
								(!baseImplementation && !/^[a-z][a-z0-9_]{0,62}$/.test(targetPhysicalName.trim()))
							}
							onClick={() => void create()}
							primary
							title={
								canOpenAdvanced
									? undefined
									: modelingCapabilityReasonsText(representation?.capabilityReasons || []) || "当前表示不允许高级 dbt 实现"
							}
						>
							{busy === "create" ? "创建中…" : "创建高级草稿"}
						</Button>
					</div>
				</div>
			) : (
				<div className="dmx-dbt-editor">
					<aside>
						<strong>草稿文件</strong>
						{files.map((file) => (
							<Button
								className={selectedPath === file.path ? "active" : ""}
								key={file.path}
								onClick={() => {
									setSelectedPath(file.path);
									setDiagnosticFocus(null);
								}}
								type="text"
								>
									{file.path}
									{isManagedDependencyPath(file.path) ? " · 系统依赖" : ""}
									{diagnosticsFor(file.path).length ? ` · ${diagnosticsFor(file.path).length}` : ""}
							</Button>
						))}
						<div>
							<input
									disabled={!canMaintain || draftWriteLocked}
								onChange={(event) => setNewPath(event.target.value)}
								placeholder="models/example.sql"
								value={newPath}
							/>
							<Button
								disabled={
									!canMaintain ||
										draftWriteLocked ||
									!newPath.trim() ||
									isManagedDependencyPath(newPath.trim()) ||
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
									<strong>
										{selectedFile.path}
										{selectedFileManaged ? " · 系统依赖" : ""}
									</strong>
									<Button
										danger
										disabled={!canMaintain || draftWriteLocked || selectedFileManaged}
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
								<Suspense fallback={<textarea aria-label={`编辑 ${selectedFile.path}`} disabled value={selectedFile.content} />}>
									<LazyDbtCodeEditor
										content={selectedFile.content}
										diagnostics={diagnosticsFor(selectedFile.path)}
										focusLocation={diagnosticFocus}
										onChange={(content) => {
										setFiles((current) =>
											current.map((file) => (file.path === selectedFile.path ? { ...file, content } : file)),
										);
										setValidation(null);
										setCommit(null);
										setDirty(true);
									}}
										onSave={() => void save()}
										path={selectedFile.path}
										readOnly={!canMaintain || draftWriteLocked || selectedFileManaged}
									/>
								</Suspense>
							</>
						) : (
							<RequestState description="选择已有文件或新增文件。" kind="empty" title="暂无选中文件" />
						)}
					</section>
					<footer>
						<span>
							状态：
								{draftStatus}{" "}
							· 到期：{draft.expiresAt}
						</span>
						{draftCommitted ? (
							<Button disabled={!canMaintain || Boolean(busy)} onClick={() => void startNewDraft()} primary>
								{busy === "create" ? "创建中…" : "创建新草稿"}
							</Button>
						) : (
							<>
								<Button disabled={!canMaintain || conflict || Boolean(busy)} onClick={() => void save()}>
									{busy === "save" ? "保存中…" : "保存文件"}
								</Button>
								<Button disabled={!canMaintain || conflict || Boolean(busy) || !files.length} onClick={() => void validate()}>
									{busy === "validate" ? "校验中…" : "校验"}
								</Button>
								<Button
									disabled={!canMaintain || conflict || Boolean(busy) || !validation}
									onClick={() => void commitDraft()}
									primary
								>
									{busy === "commit" ? "提交中…" : "提交实现"}
								</Button>
							</>
						)}
					</footer>
					{validation ? (
						<div className="dmx-dbt-diagnostics">
							<h3>校验结果</h3>
							{validation.dependencyValidation ? (
								<div>
									<Status
										tone={
											validation.dependencyValidation.missing.length ||
											validation.dependencyValidation.undeclared.length
												? "danger"
												: "success"
										}
									>
										依赖校验
									</Status>
									<b>依赖关系已匹配 {validation.dependencyValidation.matched.length} 项</b>
									<span>校验和 {validation.dependencyValidation.dependencyChecksum}</span>
									{validation.dependencyValidation.missing.length ? (
										<p>缺少：{validation.dependencyValidation.missing.join("、")}</p>
									) : null}
									{validation.dependencyValidation.undeclared.length ? (
										<p>未声明：{validation.dependencyValidation.undeclared.join("、")}</p>
									) : null}
								</div>
							) : null}
							{validation.diagnostics.length ? (
								validation.diagnostics.map((item, index) => (
									<div key={`${item.code}-${index}`}>
										<Status tone={item.severity === "ERROR" ? "danger" : "warning"}>{item.severity}</Status>
										<b>{item.code}</b>
										<span>{item.path || item.modelUniqueId || "—"}</span>
										<p>{item.message}</p>
										{item.path && Number.isInteger(item.line) && Number(item.line) > 0 ? (
											<Button onClick={() => focusDiagnostic(item)} type="link">定位到第 {item.line} 行</Button>
										) : null}
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
			{transitionValidation ? (
				<TransitionConfirmation
					onCancel={() => setTransitionValidation(null)}
					onConfirm={() => void confirmTakeOver()}
					summary={ownershipTransitionSummary(transitionValidation, model.revision, representation?.implementationRevision || 0)}
					transitioning={transitioning}
				/>
			) : null}
		</section>
	);
}

function TransitionConfirmation({ summary, onCancel, onConfirm, transitioning }: { summary: ReturnType<typeof ownershipTransitionSummary>; onCancel: () => void; onConfirm: () => void; transitioning: boolean }) {
	return <Modal footer={<><Button disabled={transitioning} onClick={onCancel}>取消</Button><Button disabled={transitioning} onClick={onConfirm} primary>{transitioning ? "接管中…" : "确认接管"}</Button></>} onClose={onCancel} title="确认接管代码实现">
		<dl className="dmx-summary-list"><dt>维护方式</dt><dd>{summary.source} → {summary.target}</dd><dt>模型版本</dt><dd>{summary.modelRevision}</dd><dt>实现版本</dt><dd>{summary.implementationRevision}</dd><dt>预览校验和</dt><dd>{summary.previewChecksum}</dd></dl>
		<p>{summary.reversibility}</p><p>{summary.publish}</p>
	</Modal>;
}
