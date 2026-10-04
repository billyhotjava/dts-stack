import { Button, Card, Empty, Space, Typography } from "antd";
import { Link, useNavigate } from "react-router";

const MODEL_WORKBENCH_PATH = "/data-modeling/dimensions/workbench";

type Props = {
	title?: string;
	compact?: boolean;
};

export default function SemanticModelingEmptyState({
	title = "还没有可用于可视化建模的语义模型",
	compact = false,
}: Props) {
	const navigate = useNavigate();

	return (
		<Card title={title}>
			<div className="space-y-4">
				<Empty description="当前页依赖已发布到 Analytics 且 exposed_to_modeler=true 的语义模型；现在返回结果是 0 个模型。" />

				<div className="space-y-2">
					<Typography.Text strong>如何开始</Typography.Text>
					<div className="text-sm text-secondary">1. 进入数据建模工作台，打开需要交付到分析侧的模型。</div>
					<div className="text-sm text-secondary">2. 当前界面重构阶段只提供交付预览；发布能力将在后台重构后接入。</div>
					<div className="text-sm text-secondary">3. 接入后确认模型对分析师开放，且模型密级没有高于当前账号可见级别。</div>
					<div className="text-sm text-secondary">
						4. 回到这里后，先选择基础模型，再从左侧指标树和维度树开始组装卡片。
					</div>
				</div>

				<Space wrap>
					<Button type="primary" onClick={() => navigate(MODEL_WORKBENCH_PATH, { flushSync: true })}>
						去模型工作台
					</Button>
					{!compact && (
						<Link to="/bi/explore">
							<Button>刷新语义探索</Button>
						</Link>
					)}
				</Space>

				<Typography.Text type="secondary">
					如果明明已经发布过仍然为空，优先检查 `exposed_to_modeler`、模型密级和当前账号的数据分级。
				</Typography.Text>
			</div>
		</Card>
	);
}
