import { useEffect, useMemo, useState } from "react";
import { Button } from "@/ui/button";
import { Badge } from "@/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/ui/card";
import { getCockpitMetrics } from "@/api/platformApi";

type KpiItem = { name: string; value: number; unit?: string };
type TrendPoint = { month?: string; value: number };

export default function CockpitPage() {
	const [data, setData] = useState<any>(null);
	const [loading, setLoading] = useState(false);
	const [error, setError] = useState<string | null>(null);

	const load = async () => {
		setLoading(true);
		setError(null);
		try {
			const resp = await getCockpitMetrics();
			setData(resp ?? null);
		} catch (e) {
			setError("加载指标失败");
			setData(null);
		} finally {
			setLoading(false);
		}
	};

	useEffect(() => {
		void load();
	}, []);

	const kpis = useMemo(() => (Array.isArray(data?.kpi) ? (data.kpi as KpiItem[]) : []), [data]);
	const trend = useMemo(
		() => (Array.isArray(data?.trend) ? (data.trend as TrendPoint[]) : []),
		[data],
	);
	const sources = useMemo(() => (Array.isArray(data?.sources) ? (data.sources as string[]) : []), [data]);

	return (
		<div className="space-y-4">
			<Card>
				<CardHeader className="flex flex-col gap-2 md:flex-row md:items-center md:justify-between">
					<div>
						<CardTitle className="text-base">经营驾驶舱</CardTitle>
						<CardDescription>
							{data?.generatedAt ? `数据时间 ${new Date(data.generatedAt).toLocaleString()}` : "展示核心经营 KPI 与趋势"}
						</CardDescription>
					</div>
					<Button variant="outline" size="sm" onClick={load} disabled={loading}>
						{loading ? "刷新中" : "刷新"}
					</Button>
				</CardHeader>
				<CardContent className="space-y-4">
					{error ? <div className="rounded-md border border-destructive/40 bg-destructive/5 px-3 py-2 text-sm text-destructive">{error}</div> : null}
					<div className="grid gap-3 md:grid-cols-3">
						{kpis.map((k) => (
							<Card key={k.name} className="border-dashed bg-muted/30">
								<CardHeader className="pb-2">
									<CardTitle className="text-sm font-medium text-muted-foreground">{k.name}</CardTitle>
								</CardHeader>
								<CardContent className="text-2xl font-semibold">
									{k.value?.toLocaleString()}
									{k.unit ? <span className="ml-1 text-sm text-muted-foreground">{k.unit}</span> : null}
								</CardContent>
							</Card>
						))}
						{!kpis.length && !loading ? (
							<div className="col-span-full rounded-md border border-dashed p-4 text-center text-sm text-muted-foreground">
								暂无 KPI 数据，可稍后重试。
							</div>
						) : null}
					</div>
					<Card className="border-none bg-background shadow-none">
						<CardHeader className="px-0 pt-0">
							<CardTitle className="text-sm">趋势概览</CardTitle>
						</CardHeader>
						<CardContent className="px-0">
							{trend.length ? (
								<div className="grid gap-2 sm:grid-cols-3 md:grid-cols-6">
									{trend.map((point, idx) => (
										<div key={`${point.month ?? idx}`} className="rounded-md border p-3 text-sm">
											<div className="text-xs text-muted-foreground">{point.month ? `${point.month} 月` : `区间 ${idx + 1}`}</div>
											<div className="mt-1 text-lg font-medium">{point.value.toFixed(1)}</div>
										</div>
									))}
								</div>
							) : (
								<div className="rounded-md border border-dashed p-4 text-center text-sm text-muted-foreground">
									暂无趋势数据。
								</div>
							)}
						</CardContent>
					</Card>
					{sources.length ? (
						<div className="flex flex-wrap gap-2 text-xs text-muted-foreground">
							<span>数据来源:</span>
							{sources.map((src) => (
								<Badge key={src} variant="secondary">
									{src}
								</Badge>
							))}
						</div>
					) : null}
				</CardContent>
			</Card>
		</div>
	);
}
