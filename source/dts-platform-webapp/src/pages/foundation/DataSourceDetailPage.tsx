import { Button, Card, Space, Typography } from "antd";
import { PageHeader } from "@/components/page-header";
import { useRouter } from "@/routes/hooks";

const { Paragraph } = Typography;

export default function DataSourceDetailPage() {
	const router = useRouter();

	return (
		<div className="flex flex-col gap-6">
			<PageHeader
				title="数据源详情"
				description="数据源配置已由 Addax 入湖任务统一维护。"
				actions={
					<Space>
						<Button onClick={() => router.push("/foundation/data-sources")}>返回列表</Button>
						<Button type="primary" onClick={() => router.push("/explore/etl/transform/new")}>创建入湖任务</Button>
					</Space>
				}
			/>
			<Card>
				<Paragraph>
					如需修改连接配置，请在对应入湖任务中更新 Reader/Writer JSON。
				</Paragraph>
			</Card>
		</div>
	);
}
