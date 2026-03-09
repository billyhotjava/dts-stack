import { Button, Card, Typography } from "antd";
import { PageHeader } from "@/components/page-header";

const { Text } = Typography;

export default function Page() {
	return (
		<div className="space-y-6">
			<PageHeader title="BI 可视化 / 自助分析" />
			<Card>
				<Text type="secondary">自助分析由独立分析服务提供。</Text>
				<div className="mt-4">
					<Button type="primary" onClick={() => (window.location.href = "/analytics")}>进入分析服务</Button>
				</div>
			</Card>
		</div>
	);
}
