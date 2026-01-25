import { Button, Card, Space, Typography } from "antd";
import { PageHeader } from "@/components/page-header";
import { useRouter } from "@/routes/hooks";

const { Paragraph } = Typography;

export default function DataSourceCreatePage() {
	const router = useRouter();

	return (
		<div className="flex flex-col gap-6">
			<PageHeader
				title="新建数据源"
				description="数据源配置已并入 Addax 入湖任务。"
				actions={
					<Space>
						<Button onClick={() => router.push("/foundation/data-sources")}>返回列表</Button>
						<Button type="primary" onClick={() => router.push("/explore/etl/transform/new")}>创建入湖任务</Button>
					</Space>
				}
			/>
			<Card>
				<Paragraph>
					请在创建入湖任务时填写 Reader/Writer 配置。平台不再单独维护数据源连接。
				</Paragraph>
			</Card>
		</div>
	);
}
