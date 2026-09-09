import { useMemo } from "react";
import type { MaterializationPlanEntry, ReleaseCandidateEntryEvidence } from "@/api/modelSpecApi";
import type { CompactColumns } from "@/components/table";
import { Status } from "./PrototypePrimitives";

const formatTime = (value?: string | null) => {
	if (!value) return "—";
	const parsed = new Date(value);
	return Number.isNaN(parsed.getTime()) ? value : parsed.toLocaleString("zh-CN", { hour12: false });
};

export function useModelMaterializationColumns() {
	const materializationPlanColumns = useMemo<CompactColumns<MaterializationPlanEntry>>(
		() => [
			{ title: "顺序", dataIndex: "topologyLevel", render: (value: number) => value + 1 },
			{ title: "模型", dataIndex: "modelName" },
			{ title: "分层", dataIndex: "layer" },
			{ title: "依赖角色", dataIndex: "dependencyRole" },
			{
				title: "计划动作",
				dataIndex: "action",
				render: (value: MaterializationPlanEntry["action"]) => (
					<Status tone={value === "REUSE" ? "success" : "info"}>{value}</Status>
				),
			},
			{
				title: "目标关系",
				dataIndex: "targetRelation",
				render: (value?: string | null) => value || "构建后生成",
			},
		],
		[],
	);
	const evidenceColumns = useMemo<CompactColumns<ReleaseCandidateEntryEvidence>>(
		() => [
			{
				title: "模型",
				dataIndex: "modelName",
				render: (value: string, row: ReleaseCandidateEntryEvidence) => `${value} · r${row.modelRevision}`,
			},
			{ title: "目标关系", dataIndex: "targetRelation", render: (value?: string | null) => value || "—" },
			{ title: "运行", dataIndex: "runStatus", render: (value?: string | null) => value || "未启动" },
			{
				title: "关系核验",
				dataIndex: "relationState",
				render: (value: ReleaseCandidateEntryEvidence["relationState"], row: ReleaseCandidateEntryEvidence) => (
					<Status tone={value === "VERIFIED" ? "success" : "warning"}>
						{value === "VERIFIED"
							? "关系已核验"
							: !row.observedAt && row.runStatus === "BLOCKED"
								? "未完成核验"
								: value}
					</Status>
				),
			},
			{
				title: "原因",
				dataIndex: "repairCode",
				render: (value: string | null | undefined, row: ReleaseCandidateEntryEvidence) => (
					<div style={{ whiteSpace: "pre-wrap", overflowWrap: "anywhere" }}>
						{row.failureMessage || (value === "MODEL_AIRFLOW_DAG_NOT_REGISTERED" ? "执行任务尚未就绪，构建未启动" : value || "—")}
						{row.failureMessage && value ? <div>错误码：{value}</div> : null}
					</div>
				),
			},
			{ title: "尝试", dataIndex: "attempt", render: (value?: number | null) => value ?? "—" },
			{ title: "完成时间", dataIndex: "finishedAt", render: (value?: string | null) => formatTime(value) },
		],
		[],
	);
	return { materializationPlanColumns, evidenceColumns };
}
