import { useEffect, useMemo, useState } from "react";
import { Button } from "@/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/ui/card";
import { Progress } from "@/ui/progress";
import { getProjectsSummary } from "@/api/platformApi";

type HealthStage = { stage: string; completed?: number; total?: number };

export default function ProjectsSummaryPage() {
	const [data, setData] = useState<any>(null);
	const [loading, setLoading] = useState(false);
	const [error, setError] = useState<string | null>(null);

	const load = async () => {
		setLoading(true);
		setError(null);
		try {
			const resp = await getProjectsSummary();
			setData(resp ?? null);
		} catch (e) {
			setError("加载项目概览失败");
			setData(null);
		} finally {
			setLoading(false);
		}
	};

	useEffect(() => {
		void load();
	}, []);

	const stages = useMemo(
		() => (Array.isArray(data?.health) ? (data.health as HealthStage[]) : []),
		[data],
	);

	return (
		<div className="space-y-4">
			<Card>
				<CardHeader className="flex flex-col gap-2 md:flex-row md:items-center md:justify-between">
					<div>
						<CardTitle className="text-base">项目执行概览</CardTitle>
						<CardDescription>
							{data?.generatedAt ? `数据时间 ${new Date(data.generatedAt).toLocaleString()}` : "关注项目进度与风险"}
						</CardDescription>
					</div>
					<Button variant="outline" size="sm" onClick={load} disabled={loading}>
						{loading ? "刷新中" : "刷新"}
					</Button>
				</CardHeader>
				<CardContent className="space-y-4">
					{error ? <div className="rounded-md border border-destructive/40 bg-destructive/5 px-3 py-2 text-sm text-destructive">{error}</div> : null}
					<div className="grid gap-3 md:grid-cols-3">
						<div className="rounded-md border p-3">
							<div className="text-xs text-muted-foreground">项目总数</div>
							<div className="mt-1 text-2xl font-semibold">{data?.count ?? "-"}</div>
						</div>
						<div className="rounded-md border p-3">
							<div className="text-xs text-muted-foreground">进行中</div>
							<div className="mt-1 text-2xl font-semibold">{data?.active ?? "-"}</div>
						</div>
						<div className="rounded-md border p-3">
							<div className="text-xs text-muted-foreground">延期</div>
							<div className="mt-1 text-2xl font-semibold text-orange-600">{data?.delayed ?? "-"}</div>
						</div>
					</div>
					<div className="space-y-3">
						<div className="text-sm font-medium">阶段完成度</div>
						{stages.length ? (
							<div className="space-y-2">
								{stages.map((stage) => {
									const total = stage.total ?? 0;
									const completed = stage.completed ?? 0;
									const percent = total > 0 ? Math.min(100, Math.round((completed / total) * 100)) : 0;
									return (
										<div key={stage.stage} className="rounded-md border px-3 py-2">
											<div className="flex items-center justify-between text-xs text-muted-foreground">
												<span>{stage.stage}</span>
												<span>
													{completed}/{total} · {percent}%
												</span>
											</div>
											<Progress value={percent} className="mt-2 h-2" />
										</div>
									);
								})}
							</div>
						) : (
							<div className="rounded-md border border-dashed p-4 text-center text-sm text-muted-foreground">
								暂无阶段数据。
							</div>
						)}
					</div>
				</CardContent>
			</Card>
		</div>
	);
}
