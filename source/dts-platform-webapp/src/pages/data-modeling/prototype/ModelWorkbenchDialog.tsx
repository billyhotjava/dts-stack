import { useCallback, useEffect, useState } from "react";
import {
	getModelPhysicalPreview,
	getModelPhysicalStructure,
	type ModelPhysicalPreview,
	type PhysicalPreviewPageSize,
} from "@/api/modelPhysicalPreviewApi";
import { getModelRepresentation } from "@/api/modelRepresentationApi";
import {
	getModelLifecycle,
	getModelSpecDependencies,
	getModelSpecStageGates,
	type ModelLifecycleTimeline,
	type ModelSpecDependencyGraph,
	type ModelSpecStageGate,
} from "@/api/modelSpecApi";
import { type CompactColumns, CompactTable } from "@/components/table";
import type {
	ModelRepresentationView,
	PhysicalPreviewReference,
	PhysicalPreviewScope,
} from "@/features/modeling/contracts/modelRepresentationContract";
import type { ModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
import { statusLabel } from "@/utils/customerDisplayLabels";
import { ModelLifecycleArtifactsTable } from "./ModelLifecycleArtifactsTable";
import { ModelPublishDialog } from "./ModelPublishDialog";
import { ModelQualityConstraintPanel } from "./ModelQualityConstraintPanel";
import { ModelStageGatePanel } from "./ModelStageGatePanel";
import { Button, Modal, RequestState, Status } from "./PrototypePrimitives";
import { normalizeModelingRequestFailure } from "./services/planningProjectionService";

export type WorkbenchDialog =
	| "association"
	| "build"
	| "release"
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
		if (!dialog || !model || dialog === "build" || dialog === "release" || dialog === "preview" || dialog === "advanced") return;
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
	if (dialog === "build" || dialog === "release")
		return <ModelPublishDialog canMaintain={canMaintain} mode={dialog} models={[model]} onClose={onClose} />;
	if (dialog === "preview") return <PhysicalPreviewDialog canMaintain={canMaintain} model={model} onClose={onClose} />;
	if (dialog === "advanced") return null;

	const title = {
		association: "模型关联关系",
		versions: "版本记录",
		releases: "发布记录",
		logs: "生命周期日志",
		quality: "质量检查",
		gates: "设计提交检查",
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
	dialog: Exclude<WorkbenchDialog, "build" | "release" | "preview" | null>;
	model: ModelSpecView;
	payload: DialogPayload | null;
}) {
	if (!payload) return <RequestState description="服务端未返回该能力的数据。" kind="empty" title="暂无数据" />;
	if (dialog === "association" && payload.dependencies) {
		const graph = payload.dependencies;
		const dependencyColumns: CompactColumns<ModelSpecDependencyGraph["edges"][number]> = [
			{
				title: "上游模型",
				key: "upstream",
				render: (_, edge) =>
					graph.nodes.find((node) => node.modelSpecId === edge.toModelSpecId)?.name || edge.toModelSpecId,
			},
			{
				title: "固定版本",
				dataIndex: "pinnedRevision",
				render: (value: number) => `r${value}`,
			},
			{
				title: "当前版本",
				dataIndex: "currentRevision",
				render: (value: number | null) => (value ? `r${value}` : "—"),
			},
			{
				title: "状态",
				dataIndex: "state",
				render: (value: string) => <Status tone={value === "CURRENT" ? "success" : "warning"}>{statusLabel(value)}</Status>,
			},
		];
		return (
			<>
				<p className="dmx-capability-note">关联关系来自当前模型的固定版本依赖，不在客户端推断。</p>
				{!graph.edges.length ? (
					<RequestState description="当前模型没有固定上游模型依赖。" kind="empty" title="暂无关联" />
				) : null}
				<CompactTable<ModelSpecDependencyGraph["edges"][number]>
					columns={dependencyColumns}
					dataSource={graph.edges}
					pagination={false}
					rowKey={(edge) => `${edge.fromModelSpecId}-${edge.toModelSpecId}`}
				/>
			</>
		);
	}
	if (dialog === "quality" && payload.gates)
		return <ModelQualityConstraintPanel gates={payload.gates} modelName={model.name} />;
	if (dialog === "gates" && payload.gates)
		return (
			<ModelStageGatePanel gates={payload.gates} targetStage="DESIGNED" />
		);
	if ((dialog === "versions" || dialog === "releases" || dialog === "logs") && payload.lifecycle) {
		const events =
			dialog === "releases"
				? payload.lifecycle.events.filter((event) => event.eventType === "RELEASE" || event.eventType === "ROLLBACK")
				: payload.lifecycle.events;
		const lifecycleColumns: CompactColumns<ModelLifecycleTimeline["events"][number]> = [
			{ title: "事件", dataIndex: "eventType" },
			{
				title: "模型版本",
				dataIndex: "revision",
				render: (value: number) => `r${value}`,
			},
			{
				title: "状态",
				dataIndex: "status",
				render: (value: string) => <Status tone={value.includes("FAIL") ? "danger" : "info"}>{value}</Status>,
			},
			{
				title: "操作人",
				dataIndex: "actorId",
				render: (value: string | null) => value || "—",
			},
			{ title: "时间", dataIndex: "createdAt" },
			{
				title: "外部引用",
				dataIndex: "externalRef",
				render: (value: string | null) => value || "—",
			},
		];
		return (
			<>
				<p className="dmx-capability-note">
					{dialog === "versions"
						? "当前接口提供生命周期事件和制品修订记录，不伪造完整版本清单。"
						: "记录来自模型生命周期审计事实。"}
				</p>
				{!events.length ? (
					<RequestState description="当前没有符合条件的生命周期记录。" kind="empty" title="暂无记录" />
				) : null}
				<CompactTable<ModelLifecycleTimeline["events"][number]>
					columns={lifecycleColumns}
					dataSource={events}
					pagination={false}
					rowKey="id"
				/>
				{dialog === "versions" && payload.lifecycle.artifacts.length ? (
					<ModelLifecycleArtifactsTable artifacts={payload.lifecycle.artifacts} />
				) : null}
			</>
		);
	}
	return <RequestState description={`模型 ${model.name} 当前没有可展示的服务端事实。`} kind="empty" title="暂无数据" />;
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
	const previewColumns: CompactColumns<(typeof previewRows)[number]> = columns.map((column) => ({
		title: (
			<>
				{column.name}
				<small>
					{column.dataType} · {column.policy}
				</small>
			</>
		),
		key: column.name,
		render: (_, { row }) => String(row[column.name] ?? ""),
	}));
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
				<RequestState description="正在读取服务端签发的关系数据。" kind="loading" title="正在加载预览能力" />
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
									待发布版本（维护者）
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
								`当前${scope === "SERVING" ? "现行" : "待发布"}版本没有可用关系数据。`
							}
							kind="empty"
							title="暂不可预览"
						/>
					) : null}
					{preview ? (
						<>
							<dl className="dmx-summary-list">
								<dt>结构状态</dt>
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
							<CompactTable<(typeof previewRows)[number]>
								columns={previewColumns}
								dataSource={previewRows}
								pagination={false}
								rowKey="key"
							/>
						</>
					) : null}
				</>
			)}
		</Modal>
	);
}
