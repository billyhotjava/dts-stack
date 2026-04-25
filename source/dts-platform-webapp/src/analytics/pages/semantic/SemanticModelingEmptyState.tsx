import { Button, Card, Empty, Space, Typography } from "antd";
import { Link } from "react-router";

type Props = {
	title?: string;
	compact?: boolean;
};

export default function SemanticModelingEmptyState({
	title = "还没有可用于可视化建模的语义模型",
	compact = false,
}: Props) {
	return (
		<Card title={title}>
			<div className="space-y-4">
				<Empty description="当前页依赖已发布到 Analytics 且 exposed_to_modeler=true 的语义模型；现在返回结果是 0 个模型。" />

				<div className="space-y-2">
					<Typography.Text strong>如何开始</Typography.Text>
					<div className="text-sm text-secondary">1. 进入 SQL 建模页，打开已经配置好 semantic contract 的模型。</div>
					<div className="text-sm text-secondary">2. 在右侧信息区点击“发布到 Analytics”。</div>
					<div className="text-sm text-secondary">3. 确认模型对分析师开放，且模型密级没有高于当前账号可见级别。</div>
					<div className="text-sm text-secondary">
						4. 回到这里后，先选择基础模型，再从左侧指标树和维度树开始组装卡片。
					</div>
				</div>

				<Space wrap>
					<Link to="/modeling/sql">
						<Button type="primary">去 SQL 建模页</Button>
					</Link>
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
