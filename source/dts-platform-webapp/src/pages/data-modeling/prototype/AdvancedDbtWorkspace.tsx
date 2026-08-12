import { useEffect, useState } from "react";
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
import { getModelLifecycle, type ModelLifecycleTimeline } from "@/api/modelSpecApi";
import type { ModelRepresentationView } from "@/features/modeling/contracts/modelRepresentationContract";
import type { CanonicalModelSpecView, ModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
import { dataModelingPath } from "../navigation";
import { Button, RequestState, Status } from "./PrototypePrimitives";
import { normalizeModelingRequestFailure } from "./services/planningProjectionService";

const canonical = (model: ModelSpecView): model is CanonicalModelSpecView =>
	model.compatibilityMode === "CANONICAL" && model.contractVersion === 2;

export function AdvancedDbtWorkspace({
	model,
	onBack,
	onDirtyChange,
	canMaintain,
}: {
	model: ModelSpecView;
	onBack: () => void;
	onDirtyChange?: (dirty: boolean) => void;
	canMaintain: boolean;
}) {
	const navigate = useNavigate();
	const [representation, setRepresentation] = useState<ModelRepresentationView | null>(null);
	const [baseImplementation, setBaseImplementation] = useState<ModelLifecycleTimeline["implementation"]>(null);
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
				if (active) setBaseImplementation(implementation);
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
	}, [canMaintain, model.checksum, model.id, model.revision]);

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
			installDraft(
				await createDbtImplementationDraft(model.id, {
					planId: model.planId,
					baseModelRevision: model.revision,
					baseModelChecksum: model.checksum,
					baseImplementationRevision: baseImplementation?.implementationRevision || null,
					baseImplementationChecksum: baseImplementation?.implementationChecksum || null,
					idempotencyKey: crypto.randomUUID(),
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
				implementationRevision: commit?.implementationRevision,
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
				<Status tone={draftCommitted ? "success" : dirty ? "warning" : "info"}>
					{draftCommitted ? "实现已提交" : dirty ? "有未保存修改" : draft?.state || "准备中"}
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
				<RequestState
					description="草稿 ETag 已变化，为避免覆盖他人修改，请返回模型设计后重新进入工作区。"
					kind="error"
					title="检测到并发冲突"
				/>
			) : null}
			{!canonical(model) ? (
				<RequestState description="历史只读模型不能创建高级 dbt 草稿。" kind="empty" title="当前模型不可维护" />
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
						<dd>{representation?.capabilityReasons.join("；") || "无"}</dd>
					</dl>
					<div className="dmx-dialog-actions">
						<Button
							disabled={!canMaintain || Boolean(busy)}
							onClick={() => navigate(`${dataModelingPath("dimensions", "reverse")}?intent=advanced`)}
						>
							导入 dbt ZIP
						</Button>
						<Button
							disabled={!canMaintain || !canOpenAdvanced || Boolean(busy)}
							onClick={() => void create()}
							primary
							title={
								canOpenAdvanced
									? undefined
									: representation?.capabilityReasons.join("；") || "当前表示不允许高级 dbt 实现"
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
								onClick={() => setSelectedPath(file.path)}
								type="text"
							>
								{file.path}
							</Button>
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
							<Button disabled={!canMaintain || Boolean(busy)} onClick={() => void startNewDraft()} primary>
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
		</section>
	);
}
