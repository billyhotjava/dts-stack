import { lazy, Suspense, useEffect, useState } from "react";
import type { DbtDraftFile } from "@/api/dbtImplementationDraftApi";
import type {
	ModelAuthoringCommit,
	ModelAuthoringContext,
	ModelAuthoringProjectionNode,
	ModelAuthoringValidation,
} from "@/api/modelAuthoringApi";
import type { ModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
import type { DbtEditorFocusLocation } from "./DbtCodeEditor";
import { dbtDraftStatusLabel } from "./dbtCodeEditorContract";
import { Button, RequestState, Status } from "./PrototypePrimitives";

const LazyDbtCodeEditor = lazy(() => import("./DbtCodeEditor").then((module) => ({ default: module.DbtCodeEditor })));

const isManagedDependencyPath = (path: string) => path.replace(/\\/g, "/").startsWith("models/.dts_dependencies/");

const provenanceLabel = (context: ModelAuthoringContext | null) => {
	switch (context?.provenance.origin) {
		case "SYSTEM_GENERATED":
			return "平台生成";
		case "MANUAL_CODE":
			return "手工代码";
		case "DBT_ZIP_IMPORT":
			return "dbt ZIP 导入";
		default:
			return "历史模型";
	}
};

export type AdvancedDbtWorkspaceProps = {
	model: ModelSpecView;
	context: ModelAuthoringContext | null;
	files: DbtDraftFile[];
	validation: ModelAuthoringValidation | null;
	commit: ModelAuthoringCommit | null;
	dirty: boolean;
	busy: string;
	conflict: boolean;
	failure: string;
	initialTargetPhysicalName?: string;
	initialFocusNode?: ModelAuthoringProjectionNode | null;
	canMaintain: boolean;
	onBack: () => void;
	onCreate: (targetPhysicalName: string) => void;
	onFilesChange: (files: DbtDraftFile[]) => void;
	onSave: () => void;
	onValidate: () => void;
	onCommit: () => void;
};

export function AdvancedDbtWorkspace({
	model,
	context,
	files,
	validation,
	commit,
	dirty,
	busy,
	conflict,
	failure,
	initialTargetPhysicalName = "",
	initialFocusNode,
	canMaintain,
	onBack,
	onCreate,
	onFilesChange,
	onSave,
	onValidate,
	onCommit,
}: AdvancedDbtWorkspaceProps) {
	const draft = context?.openDraft || null;
	const projection = context?.projection || null;
	const [selectedPath, setSelectedPath] = useState("");
	const [newPath, setNewPath] = useState("");
	const [targetPhysicalName, setTargetPhysicalName] = useState(initialTargetPhysicalName.trim());
	const [diagnosticFocus, setDiagnosticFocus] = useState<DbtEditorFocusLocation | null>(null);

	useEffect(() => {
		if (selectedPath && files.some((file) => file.path === selectedPath)) return;
		setSelectedPath(files[0]?.path || "");
	}, [files, selectedPath]);

	// biome-ignore lint/correctness/useExhaustiveDependencies: switching to another model must reset the target even when its name is identical.
	useEffect(() => {
		setTargetPhysicalName(initialTargetPhysicalName.trim());
	}, [initialTargetPhysicalName, model.id]);

	useEffect(() => {
		if (!initialFocusNode?.sourcePath) return;
		setSelectedPath(initialFocusNode.sourcePath);
		setDiagnosticFocus({
			path: initialFocusNode.sourcePath,
			line: initialFocusNode.line || 1,
			column: initialFocusNode.column || 1,
			token: Date.now(),
		});
	}, [initialFocusNode]);

	const selectedFile = files.find((file) => file.path === selectedPath) || null;
	const selectedFileManaged = Boolean(selectedFile && isManagedDependencyPath(selectedFile.path));
	const canEditImplementation = Boolean(
		canMaintain && context?.allowedActions.includes("EDIT_IMPLEMENTATION") && draft?.state !== "COMMITTED" && !conflict,
	);
	const diagnostics = [
		...(validation?.implementationValidation?.diagnostics || []),
		...(validation?.projectionIssues || []),
	];
	const validationBlocked = Boolean(validation?.modelIssues.length);
	const diagnosticsFor = (path: string) => diagnostics.filter((item) => item.path === path);
	const draftStatus = draft
		? dbtDraftStatusLabel({
				conflict,
				dirty,
				committed: draft.state === "COMMITTED",
				validated: draft.state === "VALIDATED",
			})
		: context?.publishedForkRequired
			? "已发布"
			: busy === "load"
				? "读取中"
				: "准备中";
	const firstImplementation = !context?.implementation;

	return (
		<section aria-label="模型代码视图" className="dmx-advanced-dbt-workspace">
			<header className="dmx-advanced-dbt-header">
				<div>
					{/* biome-ignore lint/a11y/useSemanticElements: this is a styled navigation switch rather than a form fieldset. */}
					<div aria-label="模型表现模式" className="dmx-workbench-mode-switch" role="group">
						<Button onClick={onBack} type="text">
							返回可视化模式
						</Button>
						<Button className="active" type="text">
							代码模式
						</Button>
					</div>
					<div>
						<strong>模型代码</strong>
						<span>
							{model.name} · 模型 r{context?.model.revision || model.revision}
						</span>
					</div>
				</div>
				<Status tone={conflict ? "danger" : dirty ? "warning" : draft?.state === "COMMITTED" ? "success" : "info"}>
					{draftStatus}
				</Status>
			</header>

			{failure ? (
				<div className="dmx-inline-error" role="alert">
					{failure}
				</div>
			) : null}
			{busy === "load" && !context ? (
				<RequestState description="正在读取模型定义、实现和已有草稿。" kind="loading" title="正在打开代码视图" />
			) : !context ? (
				<RequestState description={failure || "服务端未返回模型创作上下文。"} kind="error" title="代码视图读取失败" />
			) : !draft ? (
				<div className="dmx-advanced-dbt-intro">
					<p className="dmx-capability-note">
						可视化与代码使用同一个模型草稿。来源为“{provenanceLabel(context)}”，来源只用于追溯，不限制编辑方式。
					</p>
					{firstImplementation ? (
						<label className="dmx-form-field--wide">
							<span className="required">目标物理表名</span>
							<input
								aria-label="目标物理表名"
								disabled={Boolean(busy) || !canMaintain}
								maxLength={63}
								onChange={(event) => setTargetPhysicalName(event.target.value)}
								placeholder="例如 biz_dwd_project_follow_up"
								value={targetPhysicalName}
							/>
						</label>
					) : null}
					<Button disabled={!canMaintain || Boolean(busy)} onClick={() => onCreate(targetPhysicalName.trim())} primary>
						{busy === "create" ? "创建中…" : context.publishedForkRequired ? "创建新草稿版本" : "开始编辑"}
					</Button>
				</div>
			) : (
				<>
					<div className="dmx-model-context">
						<span>状态 {draft.state}</span>
						<span>来源 {provenanceLabel(context)}</span>
						<span>投影 {projection?.coverage || "UNKNOWN"}</span>
						<span>原始代码节点 {projection?.rawNodes.length || 0}</span>
					</div>
					{projection?.reasons.length ? (
						<div className="dmx-capability-note">{projection.reasons.join("；")}</div>
					) : null}
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
									disabled={!canEditImplementation}
									onChange={(event) => setNewPath(event.target.value)}
									placeholder="models/example.sql"
									value={newPath}
								/>
								<Button
									disabled={
										!canEditImplementation ||
										!newPath.trim() ||
										isManagedDependencyPath(newPath.trim()) ||
										files.some((file) => file.path === newPath.trim())
									}
									onClick={() => {
										const path = newPath.trim();
										onFilesChange([...files, { path, content: "" }]);
										setSelectedPath(path);
										setNewPath("");
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
											disabled={!canEditImplementation || selectedFileManaged}
											onClick={() => onFilesChange(files.filter((file) => file.path !== selectedFile.path))}
										>
											删除文件
										</Button>
									</header>
									<Suspense
										fallback={
											<textarea aria-label={`编辑 ${selectedFile.path}`} disabled value={selectedFile.content} />
										}
									>
										<LazyDbtCodeEditor
											content={selectedFile.content}
											diagnostics={diagnosticsFor(selectedFile.path)}
											focusLocation={diagnosticFocus}
											onChange={(content) =>
												onFilesChange(
													files.map((file) => (file.path === selectedFile.path ? { ...file, content } : file)),
												)
											}
											onSave={onSave}
											path={selectedFile.path}
											readOnly={!canEditImplementation || selectedFileManaged}
										/>
									</Suspense>
								</>
							) : (
								<RequestState description="选择已有文件或新增文件。" kind="empty" title="暂无选中文件" />
							)}
						</section>
						<footer>
							<span>
								状态：{draftStatus} · 到期：{draft.expiresAt}
							</span>
							<Button disabled={!canEditImplementation || Boolean(busy) || !dirty} onClick={onSave}>
								{busy === "save" ? "保存中…" : "保存草稿"}
							</Button>
							<Button disabled={!canMaintain || Boolean(busy) || !files.length} onClick={onValidate}>
								{busy === "validate" ? "校验中…" : "校验"}
							</Button>
							<Button
								disabled={
									!canMaintain || Boolean(busy) || dirty || validationBlocked || !validation?.implementationValidation
								}
								onClick={onCommit}
								primary
							>
								{busy === "commit" ? "提交中…" : "提交实现"}
							</Button>
						</footer>
					</div>
				</>
			)}

			{validation ? (
				<div className="dmx-dbt-diagnostics">
					<h3>校验结果</h3>
					{validation.modelIssues.map((item) => (
						<div key={`${item.code}:${item.field}`}>
							<Status tone="danger">模型定义</Status>
							<b>{item.field}</b>
							<p>{item.message}</p>
						</div>
					))}
					{diagnostics.map((item, index) => (
						<div key={`${item.code}:${index}`}>
							<Status tone={item.severity === "ERROR" ? "danger" : "warning"}>{item.severity}</Status>
							<b>{item.code}</b>
							<span>{item.path || item.modelUniqueId || "—"}</span>
							<p>{item.message}</p>
							{item.path && Number.isInteger(item.line) && Number(item.line) > 0 ? (
								<Button
									onClick={() => {
										setSelectedPath(item.path as string);
										setDiagnosticFocus({
											path: item.path as string,
											line: Number(item.line),
											column: Number(item.column) > 0 ? Number(item.column) : 1,
											token: Date.now(),
										});
									}}
									type="link"
								>
									定位到第 {item.line} 行
								</Button>
							) : null}
						</div>
					))}
					{!validation.modelIssues.length && !diagnostics.length ? <Status tone="success">校验通过</Status> : null}
				</div>
			) : null}
			{commit ? (
				<div className="dmx-capability-note">
					实现已提交：模型 r{commit.receipt.modelRevision}，实现 r{commit.receipt.implementationRevision}，制品{" "}
					{commit.receipt.artifactCount} 个。
				</div>
			) : null}
		</section>
	);
}
