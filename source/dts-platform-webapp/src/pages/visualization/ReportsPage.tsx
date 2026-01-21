import { EmptyState } from "@/components/empty-state";
import { PageHeader } from "@/components/page-header";
import { Button } from "@/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/ui/card";

export default function Page() {
	return (
		<div className="space-y-6">
			<PageHeader
				title="BI 可视化 / 看板中心"
				description="统一管理可视化看板与外部 BI 入口。"
				actions={
					<div className="flex items-center gap-2">
						<Button variant="default">新建看板</Button>
						<Button variant="outline">同步目录</Button>
					</div>
				}
			/>

			<div className="grid gap-4 md:grid-cols-3">
				<Card>
					<CardHeader>
						<CardTitle>看板总数</CardTitle>
						<CardDescription>统一口径统计</CardDescription>
					</CardHeader>
					<CardContent className="text-2xl font-semibold">0</CardContent>
				</Card>
				<Card>
					<CardHeader>
						<CardTitle>收藏看板</CardTitle>
						<CardDescription>我的关注</CardDescription>
					</CardHeader>
					<CardContent className="text-2xl font-semibold">0</CardContent>
				</Card>
				<Card>
					<CardHeader>
						<CardTitle>共享看板</CardTitle>
						<CardDescription>团队共享</CardDescription>
					</CardHeader>
					<CardContent className="text-2xl font-semibold">0</CardContent>
				</Card>
			</div>

			<Card>
				<CardHeader>
					<CardTitle>看板列表</CardTitle>
					<CardDescription>浏览、分享与权限管理</CardDescription>
				</CardHeader>
				<CardContent>
					<EmptyState title="暂无看板" description="创建或导入看板后将在此处展示。" />
				</CardContent>
			</Card>

			<Card>
				<CardHeader>
					<CardTitle>外部 BI 集成</CardTitle>
					<CardDescription>Tableau、Superset 等入口与单点登录配置</CardDescription>
				</CardHeader>
				<CardContent>
					<EmptyState title="暂无外部 BI" description="配置 BI 链接后可在此处跳转访问。" />
				</CardContent>
			</Card>
		</div>
	);
}
