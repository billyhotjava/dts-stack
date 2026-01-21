import { EmptyState } from "@/components/empty-state";
import { PageHeader } from "@/components/page-header";
import { Button } from "@/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/ui/card";

export default function Page() {
	return (
		<div className="space-y-6">
			<PageHeader
				title="BI 可视化 / 自助分析"
				description="拖拽式分析，支持维度筛选与多指标对比。"
				actions={
					<div className="flex items-center gap-2">
						<Button variant="default">新建分析</Button>
						<Button variant="outline">保存为看板</Button>
					</div>
				}
			/>

			<Card>
				<CardHeader>
					<CardTitle>分析主题</CardTitle>
					<CardDescription>按业务场景组织的分析模板</CardDescription>
				</CardHeader>
				<CardContent>
					<EmptyState title="暂无分析主题" description="从数据集管理创建语义模型后可生成主题。" />
				</CardContent>
			</Card>

			<Card>
				<CardHeader>
					<CardTitle>我的分析</CardTitle>
					<CardDescription>草稿与已发布分析</CardDescription>
				</CardHeader>
				<CardContent>
					<EmptyState title="暂无分析记录" description="创建分析后将在此处展示。" />
				</CardContent>
			</Card>
		</div>
	);
}
