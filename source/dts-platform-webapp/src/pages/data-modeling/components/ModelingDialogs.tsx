import { ListChecks } from "lucide-react";
import type { ReactNode } from "react";
import { useEffect, useRef, useState } from "react";
import {
	getReleaseCandidateWorkbench,
	type ModelPublicationIntentResult,
	type ReleaseCandidate,
	startModelBuildIntent,
	startModelPublicationIntent,
} from "@/api/modelSpecApi";
import type { CanonicalModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
import { Dialog as AccessibleDialog, DialogContent, DialogDescription, DialogTitle } from "@/ui/dialog";
import { ActionButton, StatusTag } from "./WorkspacePage";

export const MODEL_FIELD_DISPLAY_COLUMNS = [
	["sequence", "序号"],
	["code", "字段名称"],
	["dataType", "类型"],
	["displayName", "字段显示名"],
	["primaryKey", "字段作用"],
	["notNull", "非空"],
	["attributeCode", "维度属性编码"],
	["operation", "操作"],
] as const;

export type ModelingDialogKind = "display" | "association" | "release" | null;

type FieldSummary = {
	code: string;
	dataType: string;
	attributeCode?: string;
};

type DialogProps = {
	title: string;
	subtitle?: string;
	children: ReactNode;
	footer?: ReactNode;
	onClose: () => void;
	wide?: boolean;
};

function Dialog({ title, subtitle, children, footer, onClose, wide = false }: DialogProps) {
	const returnFocusRef = useRef<HTMLElement | null>(
		typeof document !== "undefined" && document.activeElement instanceof HTMLElement ? document.activeElement : null,
	);
	useEffect(() => {
		const returnFocusTarget = returnFocusRef.current;
		return () => returnFocusTarget?.focus();
	}, []);

	return (
		<AccessibleDialog
			open
			onOpenChange={(open) => {
				if (!open) onClose();
			}}
		>
			<DialogContent
				className={`dm-dialog ${wide ? "dm-dialog--wide" : ""}`}
				onCloseAutoFocus={(event) => {
					event.preventDefault();
					returnFocusRef.current?.focus();
				}}
			>
				<header className="dm-dialog__header">
					<div>
						<DialogTitle>{title}</DialogTitle>
						{subtitle ? <DialogDescription>{subtitle}</DialogDescription> : null}
					</div>
				</header>
				<div className="dm-dialog__body">{children}</div>
				{footer ? <footer className="dm-dialog__footer">{footer}</footer> : null}
			</DialogContent>
		</AccessibleDialog>
	);
}

const safeErrorCode = (error: unknown, fallback: string) => {
	if (!error || typeof error !== "object") return fallback;
	const candidate = error as { response?: { status?: number; data?: { code?: string; message?: string } } };
	if (candidate.response?.status === 401 || candidate.response?.status === 403) return "MODEL_RELEASE_FORBIDDEN";
	return candidate.response?.data?.code || candidate.response?.data?.message || fallback;
};

export function ModelingDialogs({
	dialog,
	onClose,
	visibleColumns,
	onToggleColumn,
	selectionCode,
	rows,
	model,
}: {
	dialog: ModelingDialogKind;
	onClose: () => void;
	visibleColumns: Set<string>;
	onToggleColumn: (key: string, checked: boolean) => void;
	selectionCode: string;
	rows: FieldSummary[];
	model?: CanonicalModelSpecView | null;
}) {
	const [releaseTab, setReleaseTab] = useState<"publish" | "materialize">("materialize");
	const [environment, setEnvironment] = useState("DEV");
	const [reason, setReason] = useState("");
	const [candidate, setCandidate] = useState<ReleaseCandidate | null>(null);
	const [publication, setPublication] = useState<ModelPublicationIntentResult | null>(null);
	const [busy, setBusy] = useState(false);
	const [error, setError] = useState<string | null>(null);
	const [loaded, setLoaded] = useState(false);
	const buildIntentIdempotencyRef = useRef({ context: "", key: crypto.randomUUID() });
	const publicationIntentIdempotencyRef = useRef({ context: "", key: crypto.randomUUID() });

	useEffect(() => {
		let active = true;
		if (dialog !== "release" || !model) {
			setCandidate(null);
			setPublication(null);
			setError(null);
			setLoaded(false);
			return () => {
				active = false;
			};
		}
		setBusy(true);
		void getReleaseCandidateWorkbench(model.planId)
			.then((workbench) => {
				if (!active) return;
				const current = workbench.candidate;
				setCandidate(
					current?.entries.some(
						(entry) =>
							entry.modelSpecId === model.id && entry.revision === model.revision && entry.checksum === model.checksum,
					)
						? current
						: null,
				);
				setError(workbench.primaryBlocker?.code || null);
			})
			.catch((cause) => {
				if (active) setError(safeErrorCode(cause, "MODEL_RELEASE_WORKBENCH_READ_FAILED"));
			})
			.finally(() => {
				if (active) {
					setBusy(false);
					setLoaded(true);
				}
			});
		return () => {
			active = false;
		};
	}, [dialog, model]);

	const startBuild = async () => {
		if (!model) return;
		const context = `${model.id}:${model.revision}:${model.checksum}:${environment}`;
		if (buildIntentIdempotencyRef.current.context !== context) {
			buildIntentIdempotencyRef.current = { context, key: crypto.randomUUID() };
		}
		setBusy(true);
		setError(null);
		setPublication(null);
		try {
			const result = await startModelBuildIntent(model, buildIntentIdempotencyRef.current.key, {
				planId: model.planId,
				environment,
			});
			setCandidate(result.candidate);
			buildIntentIdempotencyRef.current = { context: "", key: crypto.randomUUID() };
		} catch (cause) {
			setError(safeErrorCode(cause, "MODEL_BUILD_INTENT_FAILED"));
		} finally {
			setBusy(false);
		}
	};

	const startPublication = async () => {
		if (!model || !candidate) return;
		const publicationReason = reason.trim() || "提交当前模型候选进行质量与发布评审";
		const context = `${model.id}:${candidate.id}:${candidate.version}:${publicationReason}`;
		if (publicationIntentIdempotencyRef.current.context !== context) {
			publicationIntentIdempotencyRef.current = { context, key: crypto.randomUUID() };
		}
		setBusy(true);
		setError(null);
		try {
			const result = await startModelPublicationIntent(
				model.id,
				{ id: candidate.id, version: candidate.version },
				publicationIntentIdempotencyRef.current.key,
				publicationReason,
			);
			setPublication(result);
			setCandidate((current) =>
				current ? { ...current, version: result.candidateVersion, status: result.candidateStatus } : current,
			);
			publicationIntentIdempotencyRef.current = { context: "", key: crypto.randomUUID() };
		} catch (cause) {
			setError(safeErrorCode(cause, "MODEL_PUBLICATION_INTENT_FAILED"));
		} finally {
			setBusy(false);
		}
	};

	if (dialog === "display") {
		return (
			<Dialog
				footer={<ActionButton onClick={onClose}>完成</ActionButton>}
				onClose={onClose}
				subtitle="控制字段表格中展示的列，不改变模型定义。"
				title="字段显示设置"
			>
				<div className="dm-check-grid">
					{MODEL_FIELD_DISPLAY_COLUMNS.map(([key, label]) => (
						<label key={key}>
							<input
								checked={visibleColumns.has(key)}
								onChange={(event) => onToggleColumn(key, event.target.checked)}
								type="checkbox"
							/>
							{label}
						</label>
					))}
				</div>
			</Dialog>
		);
	}

	if (dialog === "association") {
		return (
			<Dialog
				footer={<ActionButton onClick={onClose}>关闭</ActionButton>}
				onClose={onClose}
				subtitle="字段作用和维度属性编码随模型保存，无独立的前端关联台账。"
				title="字段关联概览"
				wide
			>
				<div className="dm-field-table-wrap">
					<table className="dm-field-table">
						<thead>
							<tr>
								<th>模型</th>
								<th>字段</th>
								<th>类型</th>
								<th>维度属性编码</th>
							</tr>
						</thead>
						<tbody>
							{rows.map((row) => (
								<tr key={row.code}>
									<td>{selectionCode || "未命名模型"}</td>
									<td>{row.code || "未命名字段"}</td>
									<td>{row.dataType}</td>
									<td>{row.attributeCode || "-"}</td>
								</tr>
							))}
						</tbody>
					</table>
				</div>
			</Dialog>
		);
	}

	if (dialog !== "release") return null;

	return (
		<Dialog
			footer={
				<>
					<ActionButton onClick={onClose}>关闭</ActionButton>
					{releaseTab === "materialize" ? (
						<ActionButton disabled={busy || !model} kind="primary" onClick={() => void startBuild()}>
							{busy ? "处理中…" : "创建物化构建"}
						</ActionButton>
					) : (
						<ActionButton
							disabled={busy || !model || !candidate}
							kind="primary"
							onClick={() => void startPublication()}
						>
							{busy ? "处理中…" : "提交质量与发布评审"}
						</ActionButton>
					)}
				</>
			}
			onClose={onClose}
			subtitle="构建和发布只经 ModelSpec、ReleaseCandidate 与统一 dbt 执行网关；页面不接收 SQL、表名或凭据。"
			title="发布与物化"
			wide
		>
			<div className="dm-dialog-tabs">
				<button
					className={releaseTab === "materialize" ? "is-active" : ""}
					onClick={() => setReleaseTab("materialize")}
					type="button"
				>
					物化构建
				</button>
				<button
					className={releaseTab === "publish" ? "is-active" : ""}
					onClick={() => setReleaseTab("publish")}
					type="button"
				>
					质量与发布
				</button>
			</div>

			{error ? (
				<div className="dm-pending-callout" role="alert">
					{error}
				</div>
			) : null}
			{!model ? <div className="dm-pending-callout">请先保存模型修订。</div> : null}
			{model && !loaded && busy ? <div className="dm-pending-callout">正在读取候选状态…</div> : null}
			{candidate ? (
				<div className="dm-model-context">
					<span>Candidate {candidate.id.slice(0, 8)}</span>
					<StatusTag tone={candidate.status === "PUBLISHED" ? "success" : "info"}>{candidate.status}</StatusTag>
					<span>v{candidate.version}</span>
					<span>{candidate.environment}</span>
				</div>
			) : loaded && !error ? (
				<div className="dm-pending-callout">当前模型修订尚无发布候选，请先创建物化构建。</div>
			) : null}

			{releaseTab === "materialize" ? (
				<div className="dm-release-form dm-release-form--materialize">
					<label>
						执行环境
						<select className="dm-select" onChange={(event) => setEnvironment(event.target.value)} value={environment}>
							<option value="DEV">DEV</option>
							<option value="PROD">PROD</option>
						</select>
					</label>
					<div className="dm-release-checklist">
						<h3>服务端解析范围</h3>
						<p>
							<ListChecks size={15} /> 固定 ModelSpec r{model?.revision ?? "-"} 与 checksum
						</p>
						<p>
							<ListChecks size={15} /> 服务端解析 implementation、target 与 relation
						</p>
						<p>
							<ListChecks size={15} /> runtime 未认证或门禁未通过时返回明确 blocker
						</p>
					</div>
				</div>
			) : (
				<div className="dm-release-form">
					<label>
						提交说明
						<textarea
							className="dm-textarea"
							onChange={(event) => setReason(event.target.value)}
							placeholder="说明本次发布目的"
							rows={3}
							value={reason}
						/>
					</label>
					{publication ? (
						<div className="dm-release-checklist">
							<h3>处理结果</h3>
							<p>
								<ListChecks size={15} /> {publication.outcome}
							</p>
							<p>
								<ListChecks size={15} /> 下一步：{publication.nextHumanAction}
							</p>
							<p>
								<ListChecks size={15} /> 在线就绪：{publication.onlineReadiness}
							</p>
							{publication.blocker ? (
								<p className="is-pending">
									{publication.blocker.code}：{publication.blocker.message}
								</p>
							) : null}
						</div>
					) : null}
				</div>
			)}
		</Dialog>
	);
}
