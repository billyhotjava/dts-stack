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
		note: "本次提交只校验当前逻辑设计。构建、模型测试、标准、权限和字段分类分级属于模型版本交付检查，不会阻断逻辑设计提交。",
		ready: "逻辑设计提交检查已通过，可以继续配置或验证数据实现。",
		blocked: "逻辑设计仍有阻断项，请按修复入口补齐后重新检查。",
	},
	RELEASE_READY: {
		note: "模型版本交付检查校验构建、模型测试、标准、权限和字段分类分级；治理质量将在物理构建后的候选发布流程按资产规则运行证据核验。",
		ready: "模型版本交付检查已通过，可以进入候选发布流程。",
		blocked: "模型版本交付证据仍需处理；这些阻断项不会回溯影响已完成的逻辑设计提交。",
	},
};

export function ModelStageGatePanel({ gates, targetStage }: { gates: ModelSpecStageGate[]; targetStage: TargetStage }) {
	const gate = gates.find((item) => item.stage === targetStage);
	const columns = useMemo<CompactColumns<ModelSpecStageGate>>(
		() => [
			{ title: "阶段", dataIndex: "stage", width: 128 },
			{
				title: "状态",
				dataIndex: "status",
				width: 88,
				render: (value: string) => <Status tone={value === "READY" ? "success" : "danger"}>{value}</Status>,
			},
			{
				title: "阻断项",
				dataIndex: "blockers",
				width: 500,
				render: (blockers: ModelSpecStageGate["blockers"]) =>
					blockers.length ? (
						<div className="dmx-stage-gate-blockers">
							{blockers.map((blocker, index) => (
								<div className="dmx-stage-gate-blocker" key={`${blocker.code}-${blocker.field}-${index}`}>
									<b>{blocker.code}</b>：{blocker.message}
								</div>
							))}
						</div>
					) : (
						"无"
					),
			},
			{
				title: "修复入口",
				dataIndex: "blockers",
				width: 140,
				render: (blockers: ModelSpecStageGate["blockers"]) => {
					const routes = Array.from(
						new Set(blockers.map((blocker) => blocker.repairRoute).filter((route): route is string => Boolean(route))),
					);
					if (!routes.length) return "—";
					return (
						<div className="dmx-stage-gate-repairs">
							{routes.map((route, index) => (
								<a
									aria-label={`打开修复入口：${route}`}
									href={route.startsWith("#") ? route : `#${route}`}
									key={route}
									title={route}
								>
									{routes.length === 1 ? "前往修复" : `修复入口 ${index + 1}`}
								</a>
							))}
						</div>
					);
				},
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
				autoEllipsis={false}
				autoSort={false}
				className="dmx-stage-gate-table"
				compact={false}
				columns={columns}
				dataSource={[gate]}
				pagination={false}
				rowKey="stage"
				scroll={{ x: 856 }}
				size="small"
				tableLayout="fixed"
			/>
		</>
	);
}
