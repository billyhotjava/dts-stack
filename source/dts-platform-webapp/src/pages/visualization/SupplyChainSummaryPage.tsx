import { useEffect, useMemo, useState } from "react";
import { Button } from "@/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/ui/card";
import { getSupplySummary } from "@/api/platformApi";

type AlertItem = { supplier?: string; severity?: string; message?: string };

const severityTone: Record<string, string> = {
	high: "bg-red-100 text-red-700",
	medium: "bg-amber-100 text-amber-700",
	low: "bg-emerald-100 text-emerald-700",
};

export default function SupplyChainSummaryPage() {
	const [data, setData] = useState<any>(null);
	const [loading, setLoading] = useState(false);
	const [error, setError] = useState<string | null>(null);

	const load = async () => {
		setLoading(true);
		setError(null);
		try {
			const resp = await getSupplySummary();
			setData(resp ?? null);
		} catch (e) {
			setError("加载供应链概览失败");
			setData(null);
		} finally {
			setLoading(false);
		}
	};

	useEffect(() => {
		void load();
	}, []);

	const alerts = useMemo(() => (Array.isArray(data?.alerts) ? (data.alerts as AlertItem[]) : []), [data]);

	const onTimePercent = useMemo(() => {
		const rate = typeof data?.onTimeRate === "number" ? data.onTimeRate : 0;
		return (rate * 100).toFixed(1);
	}, [data]);

	return (
		<div className="space-y-4">
			<Card>
				<CardHeader className="flex flex-col gap-2 md:flex-row md:items-center md:justify-between">
					<div>
						<CardTitle className="text-base">供应链健康度</CardTitle>
						<CardDescription>
							{data?.generatedAt ? `数据时间 ${new Date(data.generatedAt).toLocaleString()}` : "交付效率与异常提醒"}
						</CardDescription>
					</div>
					<Button variant="outline" size="sm" onClick={load} disabled={loading}>
						{loading ? "刷新中" : "刷新"}
					</Button>
				</CardHeader>
				<CardContent className="space-y-4">
					{error ? <div className="rounded-md border border-destructive/40 bg-destructive/5 px-3 py-2 text-sm text-destructive">{error}</div> : null}
					<div className="grid gap-3 md:grid-cols-2">
						<Card className="border-dashed bg-muted/30">
							<CardHeader className="pb-2">
								<CardTitle className="text-sm font-medium text-muted-foreground">准时到货率</CardTitle>
							</CardHeader>
							<CardContent className="text-2xl font-semibold">{onTimePercent}%</CardContent>
						</Card>
						<Card className="border-dashed bg-muted/30">
							<CardHeader className="pb-2">
								<CardTitle className="text-sm font-medium text-muted-foreground">平均交付周期</CardTitle>
							</CardHeader>
							<CardContent className="text-2xl font-semibold">{typeof data?.leadTimeDays === "number" ? data.leadTimeDays.toFixed(1) : "-"} 天</CardContent>
						</Card>
					</div>
					<div className="space-y-2">
						<div className="text-sm font-medium">异常提醒</div>
						{alerts.length ? (
							<ul className="space-y-2">
								{alerts.map((alert, idx) => {
									const tone = alert.severity ? severityTone[alert.severity.toLowerCase()] ?? "bg-slate-100 text-slate-700" : "bg-slate-100 text-slate-700";
									return (
										<li key={`${alert.supplier ?? idx}`} className="rounded-md border px-3 py-2 text-sm">
											<div className="flex items-center justify-between gap-2">
												<div className="font-medium text-muted-foreground">供应商 {alert.supplier ?? "未知"}</div>
												{alert.severity ? <span className={`rounded px-2 py-0.5 text-xs font-medium ${tone}`}>{alert.severity}</span> : null}
											</div>
											<div className="mt-1 text-sm">{alert.message ?? "未提供描述"}</div>
										</li>
									);
								})}
							</ul>
						) : (
							<div className="rounded-md border border-dashed p-4 text-center text-sm text-muted-foreground">
								暂无异常提醒。
							</div>
						)}
					</div>
				</CardContent>
			</Card>
		</div>
	);
}
