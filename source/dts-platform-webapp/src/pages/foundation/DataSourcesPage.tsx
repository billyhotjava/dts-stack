import { Button, Card, Space, Typography } from "antd";
import { PageHeader } from "@/components/page-header";
import { useRouter } from "@/routes/hooks";

const { Paragraph } = Typography;

export default function DataSourcesPage() {
	const router = useRouter();

	return (
		<div className="flex flex-col gap-6">
			<PageHeader
				title="数据源管理"
				description="当前版本已切换为 Addax 任务编排，数据源配置统一在入湖任务中维护。"
				actions={
					<Space>
						<Button type="primary" onClick={() => router.push("/explore/etl/transform/new")}>创建入湖任务</Button>
					</Space>
				}
			/>
			<Card>
				<Paragraph>
					入湖任务会携带 Reader/Writer 的连接配置，无需单独维护数据源。若需要复用配置，请在任务中复制对应 JSON。
				</Paragraph>
			</Card>
		</div>
	);
}
