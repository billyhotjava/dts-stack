import { Database } from "lucide-react";
import { useEffect, useState } from "react";
import { getModelMaterializationStatuses, type ModelMaterializationStatus } from "@/api/modelSpecApi";
import type { ModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
import { Button, Status } from "./PrototypePrimitives";
import { normalizeModelingRequestFailure } from "./services/planningProjectionService";

export type MaterializationPresentation = {
	key: "NOT_MATERIALIZED" | "PENDING" | "MATERIALIZING" | "MATERIALIZED" | "FAILED" | "STALE" | "VERIFYING";
	label: string;
	tone: "neutral" | "info" | "success" | "danger" | "warning";
};

export function resolveMaterializationPresentation(
	status: ModelMaterializationStatus | null,
	currentModelRevision: number,
	currentImplementationRevision?: number | null,
): MaterializationPresentation {
	if (!status) return { key: "NOT_MATERIALIZED", label: "未构建", tone: "neutral" };
	const evidence = status.evidence;
	const effectiveImplementationRevision = currentImplementationRevision ?? status.currentImplementationRevision;
	if (
		evidence.modelRevision !== currentModelRevision ||
		(effectiveImplementationRevision != null && evidence.implementationRevision !== effectiveImplementationRevision) ||
		status.candidateStatus === "STALE"
	)
		return { key: "STALE", label: "待重新构建", tone: "warning" };
	if (
		status.candidateStatus === "BUILDING" ||
		["QUEUED", "RUNNING", "DBT_SUCCEEDED"].includes(evidence.runStatus || "")
	)
		return { key: "MATERIALIZING", label: "构建中", tone: "info" };
	if (
		status.candidateStatus === "BUILD_FAILED" ||
		evidence.relationState === "FAILED" ||
		["FAILED", "BLOCKED"].includes(evidence.runStatus || "")
	)
		return { key: "FAILED", label: "构建失败", tone: "danger" };
	if (evidence.runStatus === "BUILT" && evidence.relationState === "VERIFIED")
		return { key: "MATERIALIZED", label: "已构建", tone: "success" };
	if (status.candidateStatus === "DRAFT" || evidence.relationState === "NOT_STARTED")
		return { key: "PENDING", label: "待构建", tone: "neutral" };
	return { key: "VERIFYING", label: "待核验", tone: "warning" };
}

const formatTime = (value?: string | null) => {
	if (!value) return "—";
	const parsed = new Date(value);
	return Number.isNaN(parsed.getTime()) ? value : parsed.toLocaleString("zh-CN", { hour12: false });
};

export function ModelMaterializationStatusCard({
	model,
	currentImplementationRevision,
	canMaintain,
	onOpen,
}: {
	model: ModelSpecView;
	currentImplementationRevision?: number | null;
	canMaintain: boolean;
	onOpen?: () => void;
}) {
	const [status, setStatus] = useState<ModelMaterializationStatus | null>(null);
	const [loading, setLoading] = useState(true);
	const [failure, setFailure] = useState("");
	useEffect(() => {
		let active = true;
		setLoading(true);
		setFailure("");
		const planId = model.planId;
		if (!planId) {
			setStatus(null);
			setLoading(false);
			return () => {
				active = false;
			};
		}
		void getModelMaterializationStatuses(planId, [model.id])
			.then((items) => {
				if (active) setStatus(items.find((item) => item.modelSpecId === model.id) || null);
			})
			.catch((error) => {
				if (active) setFailure(normalizeModelingRequestFailure(error, "构建状态读取失败。").message);
			})
			.finally(() => {
				if (active) setLoading(false);
			});
		return () => {
			active = false;
		};
	}, [model.id, model.planId]);
	const presentation = resolveMaterializationPresentation(status, model.revision, currentImplementationRevision);
	const evidence = status?.evidence;

	return (
		<section className="dmx-materialization-card" aria-label="构建状态">
			<header>
				<div>
					<Database size={17} />
					<strong>构建状态</strong>
					<Status tone={presentation.tone}>{loading ? "读取中" : presentation.label}</Status>
				</div>
				{onOpen ? (
					<Button disabled={!canMaintain || loading} onClick={onOpen}>
						{presentation.key === "MATERIALIZED" || presentation.key === "STALE" ? "重新构建" : "构建"}
					</Button>
				) : null}
			</header>
			{failure ? <p className="dmx-materialization-card__failure">{failure}</p> : null}
			<dl>
				<dt>目标关系</dt>
				<dd>{evidence?.targetRelation || "—"}</dd>
				<dt>执行环境</dt>
				<dd>{status?.environment || "—"}</dd>
				<dt>发布单</dt>
				<dd>{status ? `${status.candidateStatus} · 发布单 v${status.candidateVersion}` : "尚无"}</dd>
				<dt>实现版本</dt>
				<dd>{evidence?.implementationRevision ? `r${evidence.implementationRevision}` : "—"}</dd>
				<dt>执行尝试</dt>
				<dd>{evidence?.attempt ? `第 ${evidence.attempt} 次` : "—"}</dd>
				<dt>关系核验</dt>
				<dd>{evidence?.relationState === "VERIFIED" ? "关系已核验" : evidence?.relationState || "—"}</dd>
				<dt>完成时间</dt>
				<dd>{formatTime(evidence?.finishedAt)}</dd>
				<dt>核验时间</dt>
				<dd>{formatTime(evidence?.observedAt)}</dd>
			</dl>
		</section>
	);
}
