import { useEffect, useState } from "react";
import { toast } from "sonner";
import { useParams, useRouter } from "@/routes/hooks";
import { Icon } from "@/components/icon";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Input } from "@/ui/input";
import { Label } from "@/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/ui/select";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/ui/tabs";
import { Chart } from "@/components/chart/chart";
import { useChart } from "@/components/chart/useChart";
import apiService, {
	type ApiMetricsResponse,
	type ApiServiceDetail,
	type TryInvokeResponse,
} from "@/api/services/apiServicesService";
import { apiPublish, apiExecute } from "@/api/platformApi";
import SensitiveNotice from "@/components/security/SensitiveNotice";
import { useActiveDept, useActiveScope } from "@/store/contextStore";


function LevelBadge({ level }: { level?: string | null }) {
	if (!level) {
		return <Badge variant="outline" className="border border-dashed text-muted-foreground">未设置</Badge>;
	}
	const color =
		level === "机密"
			? "bg-rose-100 text-rose-700 border-rose-300"
			: level === "秘密"
				? "bg-red-100 text-red-700 border-red-300"
				: level === "内部"
					? "bg-amber-100 text-amber-800 border-amber-300"
					: "bg-slate-100 text-slate-700 border-slate-300";
	return (
		<Badge variant="outline" className={`border ${color}`}>
			{level}
		</Badge>
	);
}

export default function ApiServiceDetailPage() {
	const params = useParams();
	const router = useRouter();
	const id = params.id as string;
    const activeScope = useActiveScope();
    const activeDept = useActiveDept();
	const [tab, setTab] = useState<"base" | "try" | "monitor">("base");

	const [detail, setDetail] = useState<ApiServiceDetail | null>(null);
	const [metrics, setMetrics] = useState<ApiMetricsResponse | null>(null);
	const [loading, setLoading] = useState(true);

	// Try-out state
	const [formValues, setFormValues] = useState<Record<string, string>>({});
	const [simType, setSimType] = useState<"user" | "role">("user");
	const [simIdentity, setSimIdentity] = useState<string>("");
	const [simLevel, setSimLevel] = useState<string>("内部");
    const [tryRes, setTryRes] = useState<TryInvokeResponse | null>(null);
    const [publishing, setPublishing] = useState(false);
    const [execRes, setExecRes] = useState<any | null>(null);
	const [trying, setTrying] = useState(false);

	useEffect(() => {
		let mounted = true;
		(async () => {
			setLoading(true);
			try {
				const d = await apiService.getApiServiceById(id);
				const m = await apiService.getMetrics(id);
				if (mounted) {
					setDetail(d);
					setMetrics(m);
				}
			} finally {
				if (mounted) setLoading(false);
			}
		})();
		return () => {
			mounted = false;
		};
	}, [id, activeScope, activeDept]);

	// Chart options (hooks must be called at top-level, not conditionally)
	const lineOptions = useChart({ xaxis: { type: "datetime" }, stroke: { width: 2 } });
	const pieOptions = useChart({ chart: { type: "pie" } });

	const onTry = async () => {
		setTrying(true);
		try {
			const data = await apiService.tryInvoke(id, {
				params: formValues,
				identity: { type: simType, id: simIdentity, level: simLevel },
			});
			setTryRes(data);
		} finally {
			setTrying(false);
		}
	};

	if (loading || !detail) {
		return <div className="text-sm text-muted-foreground p-4">加载中…</div>;
	}

	return (
		<div className="space-y-4">
			<SensitiveNotice />
			<div className="flex items-center justify-between">
				<div className="space-y-1">
					<h2 className="text-lg font-semibold">{detail.name}</h2>
					<div className="text-xs text-muted-foreground font-mono">
						{detail.method} {detail.path}
					</div>
					<div className="text-xs text-muted-foreground">
						绑定数据集：{detail.datasetName || "-"}
					</div>
					<div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
						{detail.latestVersion && <Badge variant="outline">版本 {detail.latestVersion}</Badge>}
						{detail.lastPublishedAt && <span>最近发布：{new Date(detail.lastPublishedAt).toLocaleString()}</span>}
					</div>
				</div>
                <div className="flex items-center gap-3">
                    <LevelBadge level={detail.classification} />
                    <Badge variant="outline">
                        QPS {detail.qps}/{detail.qpsLimit}
                    </Badge>
                    <Button variant="outline" onClick={() => router.push("/services/api")}>
                        返回列表
                    </Button>
                    <Button
                        onClick={async () => {
                            setPublishing(true);
                            try {
                                await apiPublish(id, { version: "v1" });
                                toast.success("已发布版本 v1");
                            } catch (e) {
                                console.error(e);
                                toast.error("发布失败");
                            } finally {
                                setPublishing(false);
                            }
                        }}
                        disabled={publishing}
                    >
                        {publishing ? "发布中…" : "发布版本"}
                    </Button>
                </div>
			</div>

			<Tabs value={tab} onValueChange={(v) => setTab(v as any)}>
				<TabsList>
					<TabsTrigger value="base">基本信息</TabsTrigger>
					<TabsTrigger value="try">在线试调</TabsTrigger>
					<TabsTrigger value="monitor">审计与监控</TabsTrigger>
				</TabsList>

				<TabsContent value="base" className="mt-4 space-y-4">
					<div className="grid gap-4 xl:grid-cols-[1.2fr,1fr]">
						<Card>
							<CardHeader>
								<CardTitle className="text-base">Schema</CardTitle>
							</CardHeader>
							<CardContent className="space-y-4">
								<div>
									<div className="text-xs text-muted-foreground mb-2">入参</div>
									<table className="w-full table-fixed border-collapse text-sm">
										<thead className="bg-muted/40 text-left text-xs uppercase text-muted-foreground">
											<tr>
												<th className="px-3 py-2 font-medium">字段名</th>
												<th className="px-3 py-2 font-medium w-[120px]">类型</th>
												<th className="px-3 py-2 font-medium w-[160px]">脱敏规则</th>
												<th className="px-3 py-2 font-medium">可见条件</th>
											</tr>
										</thead>
										<tbody>
											{detail.input.map((f) => (
												<tr key={`in-${f.name}`} className="border-b last:border-b-0">
													<td className="px-3 py-2 font-medium">{f.name}</td>
													<td className="px-3 py-2">{f.type}</td>
													<td className="px-3 py-2">{f.masked ? "mask_" + f.name : "-"}</td>
													<td className="px-3 py-2 text-xs text-muted-foreground">-</td>
												</tr>
											))}
										</tbody>
									</table>
								</div>
								<div>
									<div className="text-xs text-muted-foreground mb-2">出参</div>
									<table className="w-full table-fixed border-collapse text-sm">
										<thead className="bg-muted/40 text-left text-xs uppercase text-muted-foreground">
											<tr>
												<th className="px-3 py-2 font-medium">字段名</th>
												<th className="px-3 py-2 font-medium w-[120px]">类型</th>
												<th className="px-3 py-2 font-medium w-[160px]">脱敏规则</th>
												<th className="px-3 py-2 font-medium">可见条件</th>
											</tr>
										</thead>
										<tbody>
											{detail.output.map((f) => (
												<tr key={`out-${f.name}`} className="border-b last:border-b-0">
													<td className="px-3 py-2 font-medium">{f.name}</td>
													<td className="px-3 py-2">{f.type}</td>
													<td className="px-3 py-2">{f.masked ? "mask_" + f.name : "-"}</td>
													<td className="px-3 py-2 text-xs text-muted-foreground">-</td>
												</tr>
											))}
										</tbody>
									</table>
								</div>
							</CardContent>
						</Card>
						<div className="space-y-4">
							<Card>
								<CardHeader>
									<CardTitle className="text-base">策略摘要</CardTitle>
								</CardHeader>
							<CardContent className="space-y-2 text-sm">
                        <div className="flex items-center gap-2">
                            <span>最低数据密级（DATA_*）：</span>
                            <LevelBadge level={detail.policy?.minLevel} />
                        </div>
								<div>
									列掩码：
									{detail.policy?.maskedColumns?.length ? detail.policy.maskedColumns.join("、") : "-"}
								</div>
								<div>
									行过滤：{" "}
									{detail.policy?.rowFilter ? (
											<span className="font-mono text-xs">{detail.policy.rowFilter}</span>
										) : (
											<span>-</span>
										)}
								</div>
							</CardContent>
							</Card>
							<Card>
								<CardHeader>
									<CardTitle className="text-base">配额</CardTitle>
								</CardHeader>
								<CardContent className="grid grid-cols-3 gap-3 text-sm">
									<div className="rounded-md border p-3">
										<div className="text-xs text-muted-foreground">QPS</div>
										<div className="font-medium">
											{detail.qps}/{detail.quotas.qpsLimit}
										</div>
									</div>
									<div className="rounded-md border p-3">
										<div className="text-xs text-muted-foreground">日调用上限</div>
										<div className="font-medium">{detail.quotas.dailyLimit}</div>
									</div>
									<div className="rounded-md border p-3">
										<div className="text-xs text-muted-foreground">剩余量</div>
										<div className="font-medium">{detail.quotas.dailyRemaining}</div>
									</div>
								</CardContent>
							</Card>
							<Card>
								<CardHeader>
									<CardTitle className="text-base">审计摘要</CardTitle>
								</CardHeader>
								<CardContent className="grid grid-cols-3 gap-3 text-sm">
									<div className="rounded-md border p-3">
										<div className="text-xs text-muted-foreground">最近 24h 调用量</div>
										<div className="font-medium">{detail.audit.last24hCalls}</div>
									</div>
									<div className="rounded-md border p-3">
										<div className="text-xs text-muted-foreground">命中掩码字段</div>
										<div className="font-medium">{detail.audit.maskedHits}</div>
									</div>
									<div className="rounded-md border p-3">
										<div className="text-xs text-muted-foreground">拒绝次数</div>
										<div className="font-medium">{detail.audit.denies}</div>
									</div>
								</CardContent>
							</Card>
						</div>
					</div>
				</TabsContent>

				<TabsContent value="try" className="mt-4 space-y-4">
					<Card>
						<CardHeader>
							<CardTitle className="text-base">参数与身份</CardTitle>
						</CardHeader>
						<CardContent className="space-y-3">
							<div className="grid gap-3 lg:grid-cols-2">
								<div className="space-y-2">
									<Label className="text-xs text-muted-foreground">参数输入（动态生成）</Label>
									<div className="grid gap-2">
										{detail.input.map((f) => (
											<div key={`param-${f.name}`} className="grid grid-cols-[180px,1fr] items-center gap-2">
												<div className="text-sm text-muted-foreground">{f.name}</div>
												<Input
													value={formValues[f.name] ?? ""}
													onChange={(e) => setFormValues((prev) => ({ ...prev, [f.name]: e.target.value }))}
												/>
											</div>
										))}
									</div>
								</div>
								<div className="space-y-2">
									<Label className="text-xs text-muted-foreground">模拟身份（治理可见）</Label>
									<div className="flex items-center gap-2">
										<Select value={simType} onValueChange={(v) => setSimType(v as any)}>
											<SelectTrigger className="w-[120px]">
												<SelectValue />
											</SelectTrigger>
											<SelectContent>
												<SelectItem value="user">用户</SelectItem>
												<SelectItem value="role">角色</SelectItem>
											</SelectContent>
										</Select>
										<Input
											className="w-[200px]"
											placeholder="标识（如用户名/角色名）"
											value={simIdentity}
											onChange={(e) => setSimIdentity(e.target.value)}
										/>
										<Select value={simLevel} onValueChange={setSimLevel}>
											<SelectTrigger className="w-[120px]">
												<SelectValue />
											</SelectTrigger>
											<SelectContent>
												<SelectItem value="公开">公开</SelectItem>
												<SelectItem value="内部">内部</SelectItem>
												<SelectItem value="秘密">秘密</SelectItem>
												<SelectItem value="机密">机密</SelectItem>
											</SelectContent>
										</Select>
                                <Button onClick={onTry} disabled={trying || !simIdentity}>
                                    {trying ? "试调中…" : "执行"}
                                </Button>
                                <Button
                                    variant="secondary"
                                    onClick={async () => {
                                        try {
                                            const r = (await apiExecute(id, { params: formValues })) as any;
                                            setExecRes(r);
                                        } catch (e) {
                                            console.error(e);
                                            toast.error("在线执行失败");
                                        }
                                    }}
                                >
                                    在线执行
                                </Button>
									</div>
								</div>
							</div>
						</CardContent>
					</Card>

					<div className="grid gap-4 xl:grid-cols-[1fr,380px]">
						<Card>
							<CardHeader>
								<CardTitle className="text-base">返回结果（前 20 行）</CardTitle>
							</CardHeader>
							<CardContent className="overflow-x-auto">
								{tryRes ? (
									<>
										<div className="mb-2 text-xs text-muted-foreground">被过滤掉的行：{tryRes.filteredRowCount}</div>
										<table className="w-full min-w-[760px] table-fixed border-collapse text-sm">
											<thead className="bg-muted/40 text-left text-xs uppercase text-muted-foreground">
												<tr>
													{tryRes.columns.map((c) => (
														<th key={c} className="px-3 py-2 font-medium">
															<div className="inline-flex items-center gap-1">
																{c}
																{tryRes.maskedColumns.includes(c) && (
																	<Icon icon="solar:lock-keyhole-bold-duotone" className="text-slate-500" />
																)}
															</div>
														</th>
													))}
												</tr>
											</thead>
											<tbody>
												{tryRes.rows.map((row, i) => (
													<tr key={i} className="border-b last:border-b-0">
														{tryRes.columns.map((c) => (
															<td
																key={`${i}-${c}`}
																className={`px-3 py-2 ${tryRes.maskedColumns.includes(c) ? "text-slate-400" : ""}`}
															>
																{String(row[c])}
															</td>
														))}
													</tr>
												))}
											</tbody>
										</table>
									</>
								) : (
									<div className="text-sm text-muted-foreground">执行后展示结果</div>
								)}
							</CardContent>
						</Card>
						<Card>
							<CardHeader>
								<CardTitle className="text-base">命中策略说明</CardTitle>
							</CardHeader>
							<CardContent>
								<ul className="list-disc pl-6 text-sm">
									{tryRes?.policyHits.map((h, idx) => (
										<li key={idx}>{h}</li>
									))}
									{!tryRes && <li className="text-muted-foreground">执行后显示</li>}
								</ul>
							</CardContent>
						</Card>
					</div>
				</TabsContent>

				{execRes && (
					<div className="rounded border bg-muted/30 p-2 text-xs">
						<div className="mb-1 font-semibold">执行结果</div>
						<pre className="whitespace-pre-wrap">{JSON.stringify(execRes, null, 2)}</pre>
					</div>
				)}

				<TabsContent value="monitor" className="mt-4 space-y-4">
					<div className="grid gap-4 lg:grid-cols-3">
						<Card className="lg:col-span-2">
							<CardHeader>
								<CardTitle className="text-base">调用量 / QPS 趋势</CardTitle>
							</CardHeader>
							<CardContent>
								{metrics ? (
									<Chart
										height={280}
										series={[
											{ name: "calls", data: metrics.series.map((p) => ({ x: p.timestamp, y: p.calls })) },
											{ name: "qps", data: metrics.series.map((p) => ({ x: p.timestamp, y: p.qps })) },
										]}
										options={lineOptions}
									/>
								) : (
									<div className="text-sm text-muted-foreground">加载中…</div>
								)}
							</CardContent>
						</Card>
						<Card>
							<CardHeader>
                            <CardTitle className="text-base">不同数据密级（DATA_*）用户调用占比</CardTitle>
							</CardHeader>
							<CardContent>
								{metrics ? (
									<Chart
										height={280}
										series={metrics.levelDistribution.map((d) => d.value)}
										options={{ ...pieOptions, labels: metrics.levelDistribution.map((d) => d.label) }}
									/>
								) : (
									<div className="text-sm text-muted-foreground">加载中…</div>
								)}
							</CardContent>
						</Card>
					</div>

					<Card>
						<CardHeader>
							<CardTitle className="text-base">最近 10 次调用</CardTitle>
						</CardHeader>
						<CardContent className="overflow-x-auto">
							{metrics ? (
								<table className="w-full min-w-[760px] table-fixed border-collapse text-sm">
									<thead className="bg-muted/40 text-left text-xs uppercase text-muted-foreground">
										<tr>
											<th className="px-3 py-2 font-medium">用户</th>
                                    <th className="px-3 py-2 font-medium">数据密级（DATA_*）</th>
											<th className="px-3 py-2 font-medium">出行数</th>
											<th className="px-3 py-2 font-medium">策略命中</th>
										</tr>
									</thead>
									<tbody>
										{metrics.recentCalls.map((c, idx) => (
											<tr key={idx} className="border-b last:border-b-0">
												<td className="px-3 py-2">{c.user}</td>
												<td className="px-3 py-2">
													<LevelBadge level={c.level} />
												</td>
												<td className="px-3 py-2">{c.rowCount}</td>
												<td className="px-3 py-2">{c.policy}</td>
											</tr>
										))}
									</tbody>
								</table>
							) : (
								<div className="text-sm text-muted-foreground">加载中…</div>
							)}
						</CardContent>
					</Card>
				</TabsContent>
			</Tabs>
		</div>
	);
}
