import { type MutableRefObject, useRef, useState } from "react";
import {
	commitDbtImplementationDraft,
	createDbtImplementationDraft,
	type DbtDraftCommit,
	type DbtDraftFile,
	type DbtDraftSourceBundle,
	type DbtDraftValidation,
	saveDbtImplementationDraftFiles,
	validateDbtImplementationDraft,
} from "@/api/dbtImplementationDraftApi";
import type { ModelTechnicalImplementation } from "@/features/modeling/contracts/modelRepresentationContract";
import { ActionButton, StatusTag } from "../WorkspacePage";

type DraftSession = { draftId: string; etag: string; expiresAt: string };

const idempotencyKey = (action: string) =>
	`advanced-dbt-${action}-${globalThis.crypto?.randomUUID?.() || `${Date.now()}-${Math.random().toString(16).slice(2)}`}`;

type IdempotentAttempt = { fingerprint: string; key: string };

const attemptKey = (
	attempt: MutableRefObject<IdempotentAttempt | null>,
	action: string,
	fingerprint: string,
) => {
	if (attempt.current?.fingerprint !== fingerprint) {
		attempt.current = { fingerprint, key: idempotencyKey(action) };
	}
	return attempt.current.key;
};

const apiFailure = (cause: unknown) => {
	const response = (cause as { response?: { status?: number; data?: { code?: string; message?: string } } })?.response;
	return {
		code:
			response?.data?.code ||
			response?.data?.message ||
			(cause instanceof Error ? cause.message : "DBT_DRAFT_REQUEST_FAILED"),
		status: response?.status,
	};
};

export function AdvancedDbtImplementationView({
	modelSpecId,
	planId,
	baseModelRevision,
	baseModelChecksum,
	baseImplementationRevision,
	baseImplementationChecksum,
	technicalImplementation,
	onCommitted,
}: {
	modelSpecId: string;
	planId: string;
	baseModelRevision: number;
	baseModelChecksum: string;
	baseImplementationRevision?: number | null;
	baseImplementationChecksum?: string | null;
	technicalImplementation?: ModelTechnicalImplementation | null;
	onCommitted?: (result: DbtDraftCommit) => void;
}) {
	const [files, setFiles] = useState<DbtDraftFile[]>([]);
	const [draft, setDraft] = useState<DraftSession | null>(null);
	const [validation, setValidation] = useState<DbtDraftValidation | null>(null);
	const [commitResult, setCommitResult] = useState<DbtDraftCommit | null>(null);
	const [sourceProvenance, setSourceProvenance] = useState<Pick<DbtDraftSourceBundle, "sourceKind" | "lossless"> | null>(null);
	const [nonLosslessAcknowledged, setNonLosslessAcknowledged] = useState(false);
	const [dirty, setDirty] = useState(false);
	const [conflict, setConflict] = useState(false);
	const [busy, setBusy] = useState(false);
	const [error, setError] = useState<string | null>(null);
	const createAttempt = useRef<IdempotentAttempt | null>(null);
	const commitAttempt = useRef<IdempotentAttempt | null>(null);

	const handleFailure = (cause: unknown) => {
		const failure = apiFailure(cause);
		setConflict(failure.status === 409 || failure.status === 412);
		setError(failure.code);
	};

	const beginEdit = async () => {
		setBusy(true);
		setError(null);
		setConflict(false);
		try {
			const fingerprint = JSON.stringify([
				modelSpecId,
				planId,
				baseModelRevision,
				baseModelChecksum,
				baseImplementationRevision ?? null,
				baseImplementationChecksum ?? null,
			]);
			const created = await createDbtImplementationDraft(modelSpecId, {
				planId,
				baseModelRevision,
				baseModelChecksum,
				baseImplementationRevision,
				baseImplementationChecksum,
				idempotencyKey: attemptKey(createAttempt, "create", fingerprint),
			});
			if (!created.sourceBundle?.files.length) throw new Error("DBT_DRAFT_SOURCE_BUNDLE_UNAVAILABLE");
			const sourceFiles = created.sourceBundle.files.map(({ path, content }) => ({ path, content }));
			const saved = await saveDbtImplementationDraftFiles(modelSpecId, created.draftId, {
				expectedEtag: created.etag,
				files: sourceFiles,
			});
			setFiles(sourceFiles);
			setSourceProvenance({ sourceKind: created.sourceBundle.sourceKind, lossless: created.sourceBundle.lossless });
			setNonLosslessAcknowledged(created.sourceBundle.lossless);
			setDraft({ draftId: created.draftId, etag: saved.etag, expiresAt: saved.expiresAt });
			setDirty(false);
			createAttempt.current = null;
		} catch (cause) {
			handleFailure(cause);
		} finally {
			setBusy(false);
		}
	};

	const save = async (session: DraftSession) => {
		const saved = await saveDbtImplementationDraftFiles(modelSpecId, session.draftId, {
			expectedEtag: session.etag,
			files,
		});
		const next = { draftId: session.draftId, etag: saved.etag, expiresAt: saved.expiresAt };
		setDraft(next);
		setValidation(null);
		setDirty(false);
		return next;
	};

	const saveCheckpoint = async () => {
		if (!draft) return;
		setBusy(true);
		setError(null);
		setConflict(false);
		try {
			await save(draft);
		} catch (cause) {
			handleFailure(cause);
		} finally {
			setBusy(false);
		}
	};

	const validate = async () => {
		if (!draft) return;
		setBusy(true);
		setError(null);
		setConflict(false);
		try {
			const session = dirty ? await save(draft) : draft;
			const result = await validateDbtImplementationDraft(modelSpecId, session.draftId, {
				expectedEtag: session.etag,
			});
			setDraft({ draftId: session.draftId, etag: result.etag, expiresAt: result.expiresAt });
			setValidation(result);
			setDirty(false);
		} catch (cause) {
			handleFailure(cause);
		} finally {
			setBusy(false);
		}
	};

	const commit = async () => {
		if (!draft || !validation || dirty || (sourceProvenance?.lossless === false && !nonLosslessAcknowledged)) return;
		setBusy(true);
		setError(null);
		setConflict(false);
		try {
			const fingerprint = JSON.stringify([
				modelSpecId,
				draft.draftId,
				draft.etag,
				validation.validatedChecksum,
			]);
			const result = await commitDbtImplementationDraft(modelSpecId, draft.draftId, {
				expectedEtag: draft.etag,
				validatedChecksum: validation.validatedChecksum,
				idempotencyKey: attemptKey(commitAttempt, "commit", fingerprint),
			});
			setDraft({ ...draft, etag: result.etag });
			setCommitResult(result);
			onCommitted?.(result);
			commitAttempt.current = null;
		} catch (cause) {
			handleFailure(cause);
		} finally {
			setBusy(false);
		}
	};

	const updateFile = (path: string, content: string) => {
		setFiles((current) => current.map((file) => (file.path === path ? { ...file, content } : file)));
		setValidation(null);
		setDirty(true);
		setConflict(false);
	};

	return (
		<div className="dm-model-editor__scroll">
			<section className="dm-editor-section">
				<div className="dm-editor-section__title">
					<h2>高级 dbt 实现</h2>
					<StatusTag tone={conflict ? "danger" : dirty ? "warning" : "info"}>
						{conflict ? "版本冲突" : dirty ? "未保存" : draft ? "隔离草稿" : "技术维护视图"}
					</StatusTag>
				</div>
				<div className="dm-model-form">
					<div className="dm-model-form__field">
						<span>项目</span>
						<strong>{technicalImplementation?.projectKey || "由服务端初始化"}</strong>
					</div>
					<div className="dm-model-form__field">
						<span>dbt 节点</span>
						<strong>{technicalImplementation?.dbtUniqueId || "首次提交后生成"}</strong>
					</div>
					<div className="dm-model-form__field">
						<span>物化策略</span>
						<strong>{technicalImplementation?.materialization || "沿用逻辑模型"}</strong>
					</div>
				</div>
				<div className="dm-editor-toolbar">
					{!draft ? <ActionButton onClick={() => void beginEdit()}>开始隔离编辑</ActionButton> : null}
					{draft ? (
						<ActionButton disabled={busy || !dirty} onClick={() => void saveCheckpoint()}>
							保存草稿
						</ActionButton>
					) : null}
					{draft ? (
						<ActionButton disabled={busy} onClick={() => void validate()}>
							静态校验
						</ActionButton>
					) : null}
					{draft && validation ? (
						<ActionButton
							disabled={busy || dirty || Boolean(commitResult) || (sourceProvenance?.lossless === false && !nonLosslessAcknowledged)}
							onClick={() => void commit()}
						>
							提交实施修订
						</ActionButton>
					) : null}
					{busy ? <span>处理中…</span> : null}
				</div>
				{sourceProvenance?.lossless === false ? (
					<div className="dm-model-context" role="note">
						<StatusTag tone="warning">非无损来源</StatusTag>
						<span>{sourceProvenance.sourceKind}</span>
						<label>
							<input
								checked={nonLosslessAcknowledged}
								disabled={Boolean(commitResult)}
								onChange={(event) => setNonLosslessAcknowledged(event.target.checked)}
								type="checkbox"
							/>
							确认以规范化重建内容作为新的技术权威
						</label>
					</div>
				) : null}
				{error ? <p role="alert">{conflict ? "草稿或基础修订已冲突，请刷新后重新开始。" : error}</p> : null}
				{validation ? (
					<p>
						校验通过：{validation.validatedChecksum.slice(0, 12)}；诊断 {validation.diagnostics.length} 条；拟提交节点
						{validation.proposedStructure.length} 个。
					</p>
				) : null}
				{commitResult ? <p>已创建 Implementation r{commitResult.implementationRevision}，未发布、未运行。</p> : null}
			</section>
			{!draft && files.length === 0 ? (
				<p className="dm-object-tree__empty">开始隔离编辑后，服务端将载入来源 bundle；非无损重建会明确标识并要求确认。</p>
			) : null}
			{files.map((file) => (
				<section className="dm-editor-section" key={file.path}>
					<div className="dm-editor-section__title">
						<h2>{file.path}</h2>
						<StatusTag tone="info">{file.content.length} 字符</StatusTag>
					</div>
					<div className="dm-code-editor">
						<textarea
							aria-label={`${file.path} 技术实现`}
							onChange={(event) => updateFile(file.path, event.target.value)}
							readOnly={!draft || Boolean(commitResult)}
							spellCheck={false}
							value={file.content}
						/>
					</div>
				</section>
			))}
		</div>
	);
}
