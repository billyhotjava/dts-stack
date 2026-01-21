import { EmptyState } from "@/components/empty-state";
import { PageHeader } from "@/components/page-header";
import { Button } from "@/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/ui/card";

export default function Page() {
	return (
		<div className="space-y-6">
			<PageHeader
				title="数据服务中心 / 外部 BI 集成"
				description="统一管理 Tableau、Superset 等外部 BI 的入口与单点登录。"
				actions={
					<div className="flex items-center gap-2">
						<Button variant="default">新增 BI 链接</Button>
						<Button variant="outline">配置 SSO</Button>
					</div>
				}
			/>

			<Card>
				<CardHeader>
					<CardTitle>外部 BI 列表</CardTitle>
					<CardDescription>支持跳转访问与统一权限管理</CardDescription>
				</CardHeader>
				<CardContent>
					<EmptyState title="暂无外部 BI" description="添加外部 BI 工具后可在此处统一入口。" />
				</CardContent>
			</Card>
		</div>
	);
}
