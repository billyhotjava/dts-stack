import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/ui/card";

export default function AdminOtherConfigView() {
	return (
		<div className="mx-auto max-w-4xl space-y-6">
			<Card>
				<CardHeader>
					<CardTitle>其他配置</CardTitle>
					<CardDescription>后续需求明确后将补充具体内容。</CardDescription>
				</CardHeader>
				<CardContent className="text-sm text-muted-foreground">
					当前页面预留用于系统附加配置，暂未提供可操作项。
				</CardContent>
			</Card>
		</div>
	);
}
