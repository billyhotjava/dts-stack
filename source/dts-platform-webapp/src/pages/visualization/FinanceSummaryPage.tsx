import { useEffect, useMemo, useState } from "react";
import { Button } from "@/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/ui/card";
import { getFinanceSummary } from "@/api/platformApi";

type Metric = { value?: number; unit?: string; growth?: number };
type Segment = { name: string; value: number };

export default function FinanceSummaryPage() {
	const [data, setData] = useState<any>(null);
	const [loading, setLoading] = useState(false);
	const [error, setError] = useState<string | null>(null);

	const load = async () => {
		setLoading(true);
		setError(null);
		try {
			const resp = await getFinanceSummary();
			setData(resp ?? null);
		} catch (e) {
			setError("加载财务概览失败");
			setData(null);
		} finally {
			setLoading(false);
		}
	};

	useEffect(() => {
		void load();
	}, []);

	const segments = useMemo(
		() => (Array.isArray(data?.topSegments) ? (data.topSegments as Segment[]) : []),
		[data],
	);

	const renderMetric = (label: string, metric?: Metric) => {
		if (!metric) {
			return "-";
		}
		return (
			<div>
				<div className="text-2xl font-semibold">
					{typeof metric.value === "number" ? metric.value.toLocaleString() : "-"}
					{metric.unit ? <span className="ml-1 text-sm text-muted-foreground">{metric.unit}</span> : null}
				</div>
				{typeof metric.growth === "number" ? (
					<div className={`text-xs ${metric.growth >= 0 ? "text-emerald-600" : "text-red-500"}`}>
						环比 {metric.growth >= 0 ? "+" : ""}{(metric.growth * 100).toFixed(1)}%
					</div>
				) : null}
			</div>
		);
	};

	return (
		<div className="space-y-4">
			<Card>
				<CardHeader className="flex flex-col gap-2 md:flex-row md:items-center md:justify-between">
					<div>
						<CardTitle className="text-base">财务表现</CardTitle>
						<CardDescription>
							{data?.generatedAt ? `数据时间 ${new Date(data.generatedAt).toLocaleString()}` : "核心财务指标与行业构成"}
						</CardDescription>
					</div>
					<Button variant="outline" size="sm" onClick={load} disabled={loading}>
						{loading ? "刷新中" : "刷新"}
					</Button>
				</CardHeader>
				<CardContent className="space-y-4">
					{error ? <div className="rounded-md border border-destructive/40 bg-destructive/5 px-3 py-2 text-sm text-destructive">{error}</div> : null}
					<div className="grid gap-3 md:grid-cols-3">
						<Card className="border-dashed bg-muted/30">
							<CardHeader className="pb-2">
								<CardTitle className="text-sm font-medium text-muted-foreground">收入</CardTitle>
							</CardHeader>
							<CardContent>{renderMetric("收入", data?.revenue)}</CardContent>
						</Card>
						<Card className="border-dashed bg-muted/30">
							<CardHeader className="pb-2">
								<CardTitle className="text-sm font-medium text-muted-foreground">成本</CardTitle>
							</CardHeader>
							<CardContent>{renderMetric("成本", data?.cost)}</CardContent>
						</Card>
						<Card className="border-dashed bg-muted/30">
							<CardHeader className="pb-2">
								<CardTitle className="text-sm font-medium text-muted-foreground">利润</CardTitle>
							</CardHeader>
							<CardContent>{renderMetric("利润", data?.profit)}</CardContent>
						</Card>
					</div>
					<div className="space-y-2">
						<div className="text-sm font-medium">重点行业贡献</div>
						{segments.length ? (
							<div className="grid gap-2 md:grid-cols-3">
								{segments.map((segment) => (
									<Card key={segment.name} className="border p-3">
										<div className="text-xs text-muted-foreground">{segment.name}</div>
										<div className="mt-1 text-lg font-semibold">{segment.value.toLocaleString()} 万元</div>
									</Card>
								))}
							</div>
						) : (
							<div className="rounded-md border border-dashed p-4 text-center text-sm text-muted-foreground">
								暂无行业分布。
							</div>
						)}
					</div>
				</CardContent>
			</Card>
			{data?.notes ? (
				<Card>
					<CardHeader>
						<CardTitle className="text-sm">备注</CardTitle>
					</CardHeader>
					<CardContent className="text-sm text-muted-foreground whitespace-pre-line">{data.notes}</CardContent>
				</Card>
			) : null}
		</div>
	);
}
