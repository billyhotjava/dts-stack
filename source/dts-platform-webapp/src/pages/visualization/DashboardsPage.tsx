import { useEffect, useMemo, useState } from "react";
import { Button } from "@/ui/button";
import { Badge } from "@/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/ui/card";
import { listDashboards } from "@/api/platformApi";

type DashboardItem = {
	code: string;
	name: string;
	theme?: string;
	level?: string;
	url: string;
	refreshMinutes?: number;
	availability?: number;
	lastUpdatedAt?: string;
};

type DashboardPayload = {
	generatedAt?: string;
	total?: number;
	items: DashboardItem[];
};

const DEFAULT_PAYLOAD: DashboardPayload = { items: [] };

export default function DashboardsPage() {
	const [payload, setPayload] = useState<DashboardPayload>(DEFAULT_PAYLOAD);
	const [loading, setLoading] = useState(false);
	const [error, setError] = useState<string | null>(null);

	const load = async () => {
		setLoading(true);
		setError(null);
		try {
			const raw: any = await listDashboards();
			const normalized: DashboardPayload = Array.isArray(raw)
				? { items: raw }
				: {
					generatedAt: raw?.generatedAt,
					total: typeof raw?.total === "number" ? raw.total : undefined,
					items: Array.isArray(raw?.items) ? raw.items : [],
				};
			setPayload(normalized);
		} catch (e) {
			setError("加载仪表盘目录失败");
			setPayload(DEFAULT_PAYLOAD);
		} finally {
			setLoading(false);
		}
	};

	useEffect(() => {
		void load();
	}, []);

	const items = useMemo(() => payload.items ?? [], [payload]);

	return (
		<div className="space-y-4">
			<Card>
				<CardHeader className="flex flex-col gap-2 md:flex-row md:items-center md:justify-between">
					<div>
						<CardTitle className="text-base">仪表盘目录</CardTitle>
						<CardDescription>
							{payload.generatedAt ? `最近更新 ${new Date(payload.generatedAt).toLocaleString()}` : "展示当前账号可访问的可视化看板"}
						</CardDescription>
					</div>
					<div className="flex items-center gap-2">
						{typeof payload.total === "number" ? (
							<Badge variant="outline">共 {payload.total} 个</Badge>
						) : null}
						<Button variant="outline" size="sm" onClick={load} disabled={loading}>
							{loading ? "刷新中" : "刷新"}
						</Button>
					</div>
				</CardHeader>
				<CardContent>
					{error ? <div className="rounded-md border border-destructive/40 bg-destructive/5 px-3 py-2 text-sm text-destructive">{error}</div> : null}
					<div className="mt-3 grid gap-4 md:grid-cols-2 lg:grid-cols-3">
						{items.map((d) => (
							<a
								key={d.code}
								href={d.url}
								target="_blank"
								rel="noreferrer"
								className="block rounded-md border p-3 transition hover:border-primary hover:bg-muted/40"
							>
								<div className="flex items-center justify-between gap-2">
									<div className="font-medium">{d.name}</div>
									{d.level ? <Badge variant="secondary">{d.level}</Badge> : null}
								</div>
								<div className="mt-1 text-xs text-muted-foreground">
									{d.theme ? `${d.theme} · ` : ""}
									{d.lastUpdatedAt ? `更新 ${new Date(d.lastUpdatedAt).toLocaleString()}` : "更新时刻未知"}
								</div>
								<div className="mt-2 flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
									{typeof d.refreshMinutes === "number" ? <span>刷新周期 {d.refreshMinutes} 分钟</span> : null}
									{typeof d.availability === "number" ? <span>可用性 {(d.availability * 100).toFixed(1)}%</span> : null}
								</div>
								<div className="mt-2 truncate text-xs text-muted-foreground">{d.url}</div>
							</a>
						))}
						{!items.length && !loading && !error ? (
							<div className="col-span-full rounded-md border border-dashed p-6 text-center text-sm text-muted-foreground">
								暂无可见仪表盘，请确认角色权限或联系管理员。
							</div>
						) : null}
					</div>
				</CardContent>
			</Card>
		</div>
	);
}
