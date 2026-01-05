import { RouterLink } from "@/routes/components/router-link";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/ui/card";

export default function WorkbenchPage() {
	return (
		<div className="space-y-4">
			<Card>
				<CardHeader>
					<CardTitle className="text-base">工作台</CardTitle>
					<CardDescription>平台首页，可在这里进入常用功能。</CardDescription>
				</CardHeader>
				<CardContent className="flex flex-wrap gap-2">
					<RouterLink
						href="/catalog"
						className="inline-flex items-center rounded-md border px-3 py-2 text-sm hover:bg-muted/40"
					>
						数据资产
					</RouterLink>
					<RouterLink
						href="/explore/workbench"
						className="inline-flex items-center rounded-md border px-3 py-2 text-sm hover:bg-muted/40"
					>
						数据查询和预览
					</RouterLink>
					<RouterLink
						href="/visualization/reports"
						className="inline-flex items-center rounded-md border px-3 py-2 text-sm hover:bg-muted/40"
					>
						报表与大屏
					</RouterLink>
				</CardContent>
			</Card>
		</div>
	);
}
