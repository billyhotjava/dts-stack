import { Alert, Button, Card, Empty, Space, Tag, Typography } from "antd";
import { useNavigate } from "react-router";
import type { ModelSpecStage, ModelSpecStageGate } from "@/api/modelSpecApi";

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
	return (
		<Card size="small" title="分阶段门禁" className="mb-3" loading={loading}>
			{error ? (
				<Alert
					type="warning"
					showIcon
					message={error}
					action={onReload ? <Button size="small" onClick={onReload}>重新检查</Button> : undefined}
				/>
			) : gates.length === 0 ? (
				<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无门禁结果" />
			) : (
				<Space direction="vertical" size={12} className="w-full">
					{gates.map((gate) => (
						<div key={gate.stage} data-testid={`model-spec-gate-${gate.stage.toLowerCase()}`}>
							<Space wrap className="mb-1">
								<Text strong>{STAGE_LABELS[gate.stage]}</Text>
								<Tag color={gate.status === "READY" ? "success" : "warning"}>
									{gate.status === "READY" ? "已满足" : `${gate.blockers.length} 项待处理`}
								</Tag>
								<Text type="secondary">r{gate.revision}</Text>
							</Space>
							{gate.blockers.map((blocker) => (
								<div key={`${blocker.code}:${blocker.field}`} className="flex items-center justify-between gap-3 py-1">
									<Text>{blocker.message}</Text>
									<Button size="small" type="link" onClick={() => navigate(blocker.repairRoute)}>
										去修复
									</Button>
								</div>
							))}
						</div>
					))}
				</Space>
			)}
		</Card>
	);
}
