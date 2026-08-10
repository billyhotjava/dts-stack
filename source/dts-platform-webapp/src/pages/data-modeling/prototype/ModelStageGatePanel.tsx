import { useMemo } from "react";
import type { ModelSpecStageGate } from "@/api/modelSpecApi";
import { type CompactColumns, CompactTable } from "@/components/table";
import { RequestState, Status } from "./PrototypePrimitives";

type TargetStage = "DESIGNED" | "RELEASE_READY";

const stageCopy: Record<
	TargetStage,
	{
		note: string;
		ready: string;
		blocked: string;
	}
> = {
	DESIGNED: {
		note: "本次提交只校验当前逻辑设计。构建、测试、标准、质量、权限和字段分类分级属于发布门禁，不会阻断逻辑设计提交。",
		ready: "逻辑设计提交检查已通过，可以继续配置或验证数据实现。",
		blocked: "逻辑设计仍有阻断项，请按修复入口补齐后重新检查。",
	},
	RELEASE_READY: {
		note: "发布门禁独立校验当前版本的构建、测试、标准、质量、权限和字段分类分级证据。",
		ready: "发布门禁检查已通过，可以进入发布流程。",
		blocked: "发布前证据仍需处理；这些阻断项不会回溯影响已完成的逻辑设计提交。",
	},
};

export function ModelStageGatePanel({ gates, targetStage }: { gates: ModelSpecStageGate[]; targetStage: TargetStage }) {
	const gate = gates.find((item) => item.stage === targetStage);
	const columns = useMemo<CompactColumns<ModelSpecStageGate>>(
		() => [
			{ title: "阶段", dataIndex: "stage" },
			{
				title: "状态",
				dataIndex: "status",
				render: (value: string) => <Status tone={value === "READY" ? "success" : "danger"}>{value}</Status>,
			},
			{
				title: "阻断项",
				dataIndex: "blockers",
				render: (blockers: ModelSpecStageGate["blockers"]) =>
					blockers.length
						? blockers.map((blocker) => (
								<div key={`${blocker.code}-${blocker.field}`}>
									<b>{blocker.code}</b>：{blocker.message}
								</div>
							))
						: "无",
			},
			{
				title: "修复入口",
				dataIndex: "blockers",
				render: (blockers: ModelSpecStageGate["blockers"]) =>
					blockers
						.map((blocker) => blocker.repairRoute)
						.filter(Boolean)
						.join("；") || "—",
			},
		],
		[],
	);
	if (!gate) {
		return (
			<RequestState
				description={`服务端未返回 ${targetStage} 门禁，已按失败关闭处理。`}
				kind="error"
				title="门禁数据不完整"
			/>
		);
	}
	const copy = stageCopy[targetStage];
	const ready = gate.status === "READY";
	return (
		<>
			<p className="dmx-capability-note">{copy.note}</p>
			<output className="dmx-capability-note">
				<Status tone={ready ? "success" : "warning"}>{ready ? "检查通过" : "待处理"}</Status>{" "}
				{ready ? copy.ready : copy.blocked}
			</output>
			<CompactTable<ModelSpecStageGate>
				className="dmx-stage-gate-table"
				columns={columns}
				dataSource={[gate]}
				pagination={false}
				rowKey="stage"
			/>
		</>
	);
}
