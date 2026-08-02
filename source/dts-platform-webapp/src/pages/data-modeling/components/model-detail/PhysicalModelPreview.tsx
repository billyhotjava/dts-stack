import { AlertTriangle, Eye, LoaderCircle, ShieldAlert, TableProperties } from "lucide-react";
import { useEffect, useState } from "react";
import {
	getModelPhysicalPreview,
	getModelPhysicalStructure,
	type ModelPhysicalPreview,
	type PhysicalPreviewEvidenceState,
	type PhysicalPreviewPageSize,
	type PhysicalPreviewScope,
} from "@/api/modelPhysicalPreviewApi";
import type {
	ModelRepresentationView,
	PhysicalPreviewReadModel,
} from "@/features/modeling/contracts/modelRepresentationContract";
import { ActionButton, StatusTag } from "../WorkspacePage";
import { ModelRepresentationState } from "./ModelRepresentationState";

const PAGE_SIZES: PhysicalPreviewPageSize[] = [20, 50, 100, 500];

export type { PhysicalPreviewReadModel };
export type PhysicalModelRepresentation = ModelRepresentationView;

export type PhysicalSurfaceState =
	| "READY"
	| "NO_SERVING_EVIDENCE"
	| "MATERIALIZING"
	| "FAILED_STALE"
	| "CANDIDATE_UNAVAILABLE"
	| "HISTORICAL_ONLY";

type PreviewFailureState = "ACCESS_DENIED" | "POLICY_BLOCKED" | "FAILED_STALE" | "CANDIDATE_UNAVAILABLE" | "ERROR";

export type PhysicalPreviewFailure = {
	state: PreviewFailureState;
	code: string;
	correlationId: string | null;
};

export function resolvePhysicalSurfaceState({
	scope,
	status,
	hasReference,
	canPreviewCandidate = false,
	historical = false,
}: {
	scope: PhysicalPreviewScope;
	status?: PhysicalPreviewEvidenceState;
	hasReference: boolean;
	canPreviewCandidate?: boolean;
	historical?: boolean;
}): PhysicalSurfaceState {
	if (historical) return "HISTORICAL_ONLY";
	if (scope === "CANDIDATE" && (!canPreviewCandidate || !hasReference)) return "CANDIDATE_UNAVAILABLE";
	if (status === "MATERIALIZING") return "MATERIALIZING";
	if (status === "FAILED_STALE") return "FAILED_STALE";
	if (!hasReference) return "NO_SERVING_EVIDENCE";
	return "READY";
}

const stableFailureCodes = new Set([
	"PHYSICAL_PREVIEW_NOT_MATERIALIZED",
	"PHYSICAL_PREVIEW_CANDIDATE_NOT_SUCCEEDED",
	"PHYSICAL_PREVIEW_EVIDENCE_MISMATCH",
	"PHYSICAL_PREVIEW_STALE_RELATION",
	"PHYSICAL_PREVIEW_HISTORICAL_ROWS_NOT_REPRODUCIBLE",
	"PHYSICAL_PREVIEW_RELATION_NOT_FOUND",
	"PHYSICAL_PREVIEW_IDENTIFIER_INVALID",
	"PHYSICAL_PREVIEW_ACCESS_DENIED",
	"PHYSICAL_PREVIEW_CLASSIFICATION_UNRESOLVED",
	"PHYSICAL_PREVIEW_MASKING_UNAVAILABLE",
	"PHYSICAL_PREVIEW_LIMIT_EXCEEDED",
	"PHYSICAL_PREVIEW_TIMEOUT",
]);

export function mapPhysicalPreviewFailure(error: unknown): PhysicalPreviewFailure {
	const response = error && typeof error === "object" ? (error as any).response?.data : undefined;
	const rawCode = typeof response?.code === "string" ? response.code.trim() : "";
	const code = stableFailureCodes.has(rawCode) ? rawCode : "PHYSICAL_PREVIEW_FAILED";
	const rawMessage = typeof response?.message === "string" ? response.message : "";
	const correlationId = rawMessage.match(/correlationId=([A-Za-z0-9_-]{1,128})/)?.[1] || null;
	if (code === "PHYSICAL_PREVIEW_ACCESS_DENIED") return { state: "ACCESS_DENIED", code, correlationId };
	if (code === "PHYSICAL_PREVIEW_CLASSIFICATION_UNRESOLVED" || code === "PHYSICAL_PREVIEW_MASKING_UNAVAILABLE") {
		return { state: "POLICY_BLOCKED", code, correlationId };
	}
	if (code === "PHYSICAL_PREVIEW_CANDIDATE_NOT_SUCCEEDED") {
		return { state: "CANDIDATE_UNAVAILABLE", code, correlationId };
	}
	if (
		code === "PHYSICAL_PREVIEW_EVIDENCE_MISMATCH" ||
		code === "PHYSICAL_PREVIEW_STALE_RELATION" ||
		code === "PHYSICAL_PREVIEW_HISTORICAL_ROWS_NOT_REPRODUCIBLE"
	) {
		return { state: "FAILED_STALE", code, correlationId };
	}
	return { state: "ERROR", code, correlationId };
}

const surfaceMessages: Record<Exclude<PhysicalSurfaceState, "READY">, string> = {
	NO_SERVING_EVIDENCE: "当前模型尚无可消费的 serving evidence，不能读取实时样例。",
	MATERIALIZING: "模型正在物化，旧 serving 保持可用；新候选形成完整证据后才可预览。",
	FAILED_STALE: "最近物化结果失败或证据已过期，未切换 serving，禁止按成功结果预览。",
	CANDIDATE_UNAVAILABLE: "当前没有可预览的成功候选，或当前账号不具备候选预览权限。",
	HISTORICAL_ONLY: "历史修订只展示固定结构，不使用当前物理关系冒充历史样例。",
};

const failureMessages: Record<PreviewFailureState, string> = {
	ACCESS_DENIED: "当前账号无权读取该物理资产样例。",
	POLICY_BLOCKED: "密级或字段脱敏策略无法安全确定，本次样例已整体阻断。",
	FAILED_STALE: "固定证据与当前物理关系不一致，本次样例已阻断。",
	CANDIDATE_UNAVAILABLE: "候选尚未成功或已不可用，不能读取样例。",
	ERROR: "物理样例读取失败，请使用关联编号联系管理员。",
};

function displayValue(value: unknown) {
	if (value === null || value === undefined) return "NULL";
	if (typeof value === "object") {
		try {
			return JSON.stringify(value);
		} catch {
			return "[VALUE]";
		}
	}
	return String(value);
}

export function PhysicalModelPreview({
	representation,
	canPreviewCandidate,
}: {
	representation: PhysicalModelRepresentation;
	canPreviewCandidate: boolean;
}) {
	const readModel = representation.physicalPreview;
	const initialScope: PhysicalPreviewScope = readModel?.serving
		? "SERVING"
		: canPreviewCandidate && readModel?.candidate
			? "CANDIDATE"
			: "SERVING";
	const [scope, setScope] = useState<PhysicalPreviewScope>(initialScope);
	const [limit, setLimit] = useState<PhysicalPreviewPageSize>(100);
	const [structure, setStructure] = useState<ModelPhysicalPreview | null>(null);
	const [preview, setPreview] = useState<ModelPhysicalPreview | null>(null);
	const [structureFailure, setStructureFailure] = useState<PhysicalPreviewFailure | null>(null);
	const [sampleFailure, setSampleFailure] = useState<PhysicalPreviewFailure | null>(null);
	const [structureLoading, setStructureLoading] = useState(false);
	const [loading, setLoading] = useState(false);
	const reference = scope === "SERVING" ? readModel?.serving : readModel?.candidate;
	const evidenceStatus =
		(scope === "SERVING" ? readModel?.servingStatus : readModel?.candidateStatus) || "UNAVAILABLE";
	const surfaceState = resolvePhysicalSurfaceState({
		scope,
		status: representation.driftStatus === "STALE" ? "FAILED_STALE" : evidenceStatus,
		hasReference: Boolean(reference),
		canPreviewCandidate,
		historical: false,
	});
	const observedColumns = structure?.columns || [];

	useEffect(() => {
		let cancelled = false;
		setStructure(null);
		setStructureFailure(null);
		setPreview(null);
		setSampleFailure(null);
		if (!reference || surfaceState !== "READY") {
			setStructureLoading(false);
			return () => {
				cancelled = true;
			};
		}
		setStructureLoading(true);
		void getModelPhysicalStructure(reference)
			.then((result) => {
				if (!cancelled) setStructure(result);
			})
			.catch((cause) => {
				if (!cancelled) setStructureFailure(mapPhysicalPreviewFailure(cause));
			})
			.finally(() => {
				if (!cancelled) setStructureLoading(false);
			});
		return () => {
			cancelled = true;
		};
	}, [reference, surfaceState]);

	const clearRows = () => {
		setPreview(null);
		setSampleFailure(null);
	};

	const changeScope = (next: PhysicalPreviewScope) => {
		setScope(next);
		clearRows();
	};

	const changeLimit = (next: PhysicalPreviewPageSize) => {
		setLimit(next);
		clearRows();
	};

	const loadSamples = async () => {
		if (!reference || surfaceState !== "READY") return;
		setLoading(true);
		setSampleFailure(null);
		setPreview(null);
		try {
			setPreview(await getModelPhysicalPreview(reference, limit));
		} catch (cause) {
			setSampleFailure(mapPhysicalPreviewFailure(cause));
		} finally {
			setLoading(false);
		}
	};

	return (
		<div className="dm-model-editor__scroll dm-physical-preview">
			<section className="dm-editor-section">
				<div className="dm-physical-preview__heading">
					<div>
						<h2>物理结构</h2>
						<p>结构来自固定 relation evidence；不会用模型名或客户端关系名推断查询目标。</p>
					</div>
					<label className="dm-physical-preview__scope">
						<span>证据范围</span>
						<select
							aria-label="预览范围"
							onChange={(event) => changeScope(event.target.value as PhysicalPreviewScope)}
							value={scope}
						>
							<option disabled={!readModel?.serving} value="SERVING">当前 serving</option>
							<option disabled={!canPreviewCandidate || !readModel?.candidate} value="CANDIDATE">
								成功候选
							</option>
						</select>
					</label>
				</div>
				{scope === "CANDIDATE" ? (
					<div className="dm-physical-preview__candidate" role="note">
						<AlertTriangle aria-hidden="true" size={15} />
						候选结果，非正式资产
					</div>
				) : null}
				{scope === "SERVING" && canPreviewCandidate && !readModel?.candidate ? (
					<p className="dm-physical-preview__candidate-note">当前没有具备完整证据的成功候选可供预览。</p>
				) : null}
				{surfaceState !== "READY" ? (
					<ModelRepresentationState message={surfaceMessages[surfaceState]} state={surfaceState === "FAILED_STALE" ? "error" : "empty"} />
				) : null}
				{structureLoading ? (
					<ModelRepresentationState message="正在校验固定证据并加载物理结构。" state="loading" />
				) : null}
				{structureFailure ? (
					<div className="dm-physical-preview__failure" role="alert">
						<ShieldAlert aria-hidden="true" size={17} />
						<div><strong>{failureMessages[structureFailure.state]}</strong><span>{structureFailure.code}</span>
							{structureFailure.correlationId ? <code>关联编号：{structureFailure.correlationId}</code> : null}
						</div>
					</div>
				) : null}
				{observedColumns.length ? (
					<div className="dm-table-wrap">
						<table aria-label="固定证据物理结构" className="dm-table">
							<thead>
								<tr><th>序号</th><th>字段</th><th>物理类型</th><th>允许为空</th><th>策略</th></tr>
							</thead>
							<tbody>
								{observedColumns.map((column) => (
									<tr key={`${column.ordinalPosition}-${column.name}`}>
										<td>{column.ordinalPosition}</td><td>{column.name}</td><td>{column.dataType}</td>
										<td>{column.nullable ? "是" : "否"}</td><td>{column.policy === "MASK" ? "脱敏" : "允许"}</td>
									</tr>
								))}
							</tbody>
						</table>
					</div>
				) : surfaceState === "READY" && !structureLoading && !structureFailure ? (
					<ModelRepresentationState message="固定证据中没有可展示的物理字段。" state="empty" />
				) : null}
			</section>

			<section className="dm-editor-section dm-physical-preview__samples">
				<div className="dm-physical-preview__heading">
					<div>
						<h2>受控样例</h2>
						<p>实时样例，不保证排序，不是历史快照；页面不保存样例，也不提供批量操作。</p>
					</div>
					{surfaceState === "READY" && Boolean(structure) && !structureFailure ? (
						<div className="dm-physical-preview__actions">
							<label><span>行数</span>
								<select
									aria-label="样例行数"
									onChange={(event) => changeLimit(Number(event.target.value) as PhysicalPreviewPageSize)}
									value={limit}
								>
									{PAGE_SIZES.map((size) => <option key={size} value={size}>{size}</option>)}
								</select>
							</label>
							<ActionButton disabled={loading} kind="primary" onClick={() => void loadSamples()}>
								{loading ? <LoaderCircle aria-hidden="true" size={14} /> : <Eye aria-hidden="true" size={14} />}
								{loading ? "正在加载" : "加载样例"}
							</ActionButton>
						</div>
					) : null}
				</div>
				{sampleFailure ? (
					<div className="dm-physical-preview__failure" role="alert">
						<ShieldAlert aria-hidden="true" size={17} />
						<div><strong>{failureMessages[sampleFailure.state]}</strong><span>{sampleFailure.code}</span>
							{sampleFailure.correlationId ? <code>关联编号：{sampleFailure.correlationId}</code> : null}
						</div>
					</div>
				) : null}
				{preview ? (
					<>
						<div className="dm-physical-preview__summary">
							<TableProperties aria-hidden="true" size={15} />
							<span>{preview.returnedRows} 行</span>
							<StatusTag tone="info">脱敏 {preview.maskingSummary.maskedColumnCount} 列</StatusTag>
							<StatusTag tone="warning">隐藏 {preview.maskingSummary.deniedColumnCount} 列</StatusTag>
							<span>查询时间 {preview.queriedAt}</span>
							{preview.truncated ? <StatusTag tone="warning">结果已截断</StatusTag> : null}
						</div>
						<div className="dm-table-wrap">
							<table aria-label="受控实时样例" className="dm-table">
								<thead><tr>{preview.columns.map((column) => <th key={column.name}>{column.name}</th>)}</tr></thead>
								<tbody>
									{preview.maskedRows.map((row, rowIndex) => (
										<tr key={`${preview.correlationId}-${rowIndex}`}>
											{preview.columns.map((column) => <td key={column.name}>{displayValue(row[column.name])}</td>)}
										</tr>
									))}
								</tbody>
							</table>
						</div>
					</>
				) : null}
			</section>
		</div>
	);
}
