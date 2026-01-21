import { EmptyState } from "@/components/empty-state";
import { PageHeader } from "@/components/page-header";
import { Button } from "@/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/ui/card";

export default function Page() {
	return (
		<div className="space-y-6">
			<PageHeader
				title="BI 可视化 / 数据集管理"
				description="将物理表映射为业务语义模型，支持指标与维度配置。"
				actions={
					<div className="flex items-center gap-2">
						<Button variant="default">新建数据集</Button>
						<Button variant="outline">导入 dbt 语义层</Button>
					</div>
				}
			/>

			<div className="grid gap-4 md:grid-cols-2">
				<Card>
					<CardHeader>
						<CardTitle>语义模型</CardTitle>
						<CardDescription>指标、维度与口径定义</CardDescription>
					</CardHeader>
					<CardContent>
						<EmptyState title="暂无语义模型" description="请先绑定物理表与字段。" />
					</CardContent>
				</Card>
				<Card>
					<CardHeader>
						<CardTitle>数据集目录</CardTitle>
						<CardDescription>用于自助分析与看板</CardDescription>
					</CardHeader>
					<CardContent>
						<EmptyState title="暂无数据集" description="创建数据集后可提供给自助分析使用。" />
					</CardContent>
				</Card>
			</div>
		</div>
	);
}
