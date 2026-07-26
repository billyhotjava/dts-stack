import { Alert, Button, Card, Empty, Space, Tag, Typography } from "antd";
import { useNavigate } from "react-router";
import type { ModelSpecStage, ModelSpecStageGate } from "@/api/modelSpecApi";
import { modelSpecGateGuidance, modelSpecGateRepairPath } from "../modelSpecGateGuidance";

const { Text } = Typography;

const STAGE_LABELS: Record<ModelSpecStage, string> = {
	DRAFT_SAVE: "草稿可保存",
	IMPLEMENTATION_READY: "可进入实现",
	RELEASE_READY: "可发布",
};

type Props = {
	gates: ModelSpecStageGate[];
	loading?: boolean;
	error?: string;
	onReload?: () => void;
};

export function ModelSpecBlockerPanel({ gates, loading = false, error, onReload }: Props) {
	const navigate = useNavigate();
	const seenBlockers = new Set<string>();
	const gateSections = gates.map((gate) => {
		const blockers = gate.blockers.filter((blocker) => {
			const key = `${blocker.code}:${blocker.field}`;
			if (seenBlockers.has(key)) return false;
			seenBlockers.add(key);
			return true;
		});
		return { gate, blockers, inheritedCount: gate.blockers.length - blockers.length };
	});
	return (
		<Card size="small" title="分阶段门禁" className="mb-3" loading={loading}>
			{error ? (
				<Alert
					type="warning"
					showIcon
					message={error}
					action={
						onReload ? (
							<Button size="small" onClick={onReload}>
								重新检查
							</Button>
						) : undefined
					}
				/>
			) : gates.length === 0 ? (
				<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无门禁结果" />
			) : (
				<Space direction="vertical" size={12} className="w-full">
					{gateSections.map(({ gate, blockers, inheritedCount }) => (
						<div key={gate.stage} data-testid={`model-spec-gate-${gate.stage.toLowerCase()}`}>
							<Space wrap className="mb-1">
								<Text strong>{STAGE_LABELS[gate.stage]}</Text>
								<Tag color={gate.status === "READY" ? "success" : "warning"}>
									{gate.status === "READY" ? "已满足" : `${gate.blockers.length} 项待处理`}
								</Tag>
								<Text type="secondary">r{gate.revision}</Text>
							</Space>
							{inheritedCount > 0 ? (
								<div className="mb-1">
									<Text type="secondary" className="text-xs">
										其中 {inheritedCount} 项与前置阶段相同，请先处理上方问题
									</Text>
								</div>
							) : null}
							{blockers.map((blocker) => {
								const guidance = modelSpecGateGuidance(blocker);
								return (
									<div
										key={`${blocker.code}:${blocker.field}`}
										className="flex items-start justify-between gap-3 border-b border-gray-100 py-2 last:border-b-0"
									>
										<div className="min-w-0">
											<Space size={6} wrap>
												<Text>{guidance.message}</Text>
												<Tag>{guidance.location}</Tag>
											</Space>
											<div>
												<Text type="secondary" className="text-xs">
													{guidance.description}
												</Text>
											</div>
										</div>
										<Button
											size="small"
											type="link"
											className="shrink-0"
											onClick={() => navigate(modelSpecGateRepairPath(blocker))}
										>
											{guidance.actionLabel}
										</Button>
									</div>
								);
							})}
						</div>
					))}
				</Space>
			)}
		</Card>
	);
}
