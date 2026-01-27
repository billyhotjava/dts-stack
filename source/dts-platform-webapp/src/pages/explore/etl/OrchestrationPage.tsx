import { useEffect, useState } from "react";
import { toast } from "sonner";
import { Button, Card, Space, Tag, Typography } from "antd";
import { PageHeader } from "@/components/page-header";
import { EmptyState } from "@/components/empty-state";
import { getExternalLink, visitExternalLink } from "@/api/platformApi";

const { Text } = Typography;

export default function Page() {
	const [link, setLink] = useState<any>(null);
	const [loading, setLoading] = useState(false);

	useEffect(() => {
		const load = async () => {
			setLoading(true);
			try {
				const resp = await getExternalLink("EXPLORE_ETL_ORCHESTRATION");
				setLink(resp || null);
			} catch (error: any) {
				toast.error(error?.message || "入口加载失败");
			} finally {
				setLoading(false);
			}
		};
		void load();
	}, []);

	const handleVisit = async () => {
		if (!link?.url) {
			toast.warning("尚未配置外部编排地址");
			return;
		}
		try {
			await visitExternalLink("EXPLORE_ETL_ORCHESTRATION", { source: "platform" });
			window.location.href = link.url;
		} catch (error: any) {
			toast.error(error?.message || "跳转失败");
		}
	};

	return (
		<div className="space-y-6">
			<PageHeader title="数据开发中心 / 任务编排" description="可视化编排与调度管理。" />
			<Card loading={loading}>
				{link ? (
					<div className="space-y-4">
						<Space align="center">
							<Text strong>{link.name || "外部编排平台"}</Text>
							<Tag color={link.enabled ? "green" : "default"}>{link.enabled ? "已启用" : "未启用"}</Tag>
						</Space>
						<Text type="secondary">{link.description || "通过外部平台完成作业编排与调度。"}</Text>
						<Button type="primary" onClick={handleVisit} disabled={!link.enabled || !link.url}>
							进入编排平台
						</Button>
					</div>
				) : (
					<EmptyState title="暂无外部编排入口" description="请在运维侧配置外部编排平台链接。" />
				)}
			</Card>
		</div>
	);
}
