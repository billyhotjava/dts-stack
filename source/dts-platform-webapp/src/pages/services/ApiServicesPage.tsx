import { useEffect, useMemo, useState } from "react";
import { Icon } from "@/components/icon";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Input } from "@/ui/input";
import { Label } from "@/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/ui/select";
import { useRouter } from "@/routes/hooks";
import apiService, {
	type ApiServiceStatus,
	type ApiHttpMethod,
	type ApiServiceSummary,
} from "@/api/services/apiServicesService";
import { Chart } from "@/components/chart/chart";
import { useChart } from "@/components/chart/useChart";
import SensitiveNotice from "@/components/security/SensitiveNotice";


function LevelBadge({ level }: { level: string }) {
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

function StatusBadge({ status }: { status: ApiServiceStatus }) {
	const map: Record<ApiServiceStatus, string> = {
		PUBLISHED: "bg-emerald-100 text-emerald-700",
		OFFLINE: "bg-slate-200 text-slate-700",
	};
	const text: Record<ApiServiceStatus, string> = {
		PUBLISHED: "发布",
		OFFLINE: "下线",
	};
	return <Badge className={map[status]}>{text[status]}</Badge>;
}

export default function ApiServicesPage() {
	const router = useRouter();
	const [list, setList] = useState<ApiServiceSummary[]>([]);
	const [loading, setLoading] = useState(false);
	const [keyword, setKeyword] = useState("");
	const [method, setMethod] = useState<"all" | ApiHttpMethod>("all");
	const [status, setStatus] = useState<"all" | ApiServiceStatus>("all");

	const fetchList = async () => {
		setLoading(true);
		try {
			const data = await apiService.listApiServices();
			setList(data);
		} finally {
			setLoading(false);
		}
	};

	const gotoDetail = (id: string, tab?: string) => {
		const q = tab ? `?tab=${tab}` : "";
		router.push(`/services/apis/${id}${q}`);
	};

	useEffect(() => {
		void fetchList();
	}, []);

	const filtered = useMemo(() => {
		const kw = keyword.trim().toLowerCase();
		return list.filter((it) => {
			const datasetLabel = it.datasetName ?? "";
			const kwMatch = kw ? `${it.name}${datasetLabel}${it.path}`.toLowerCase().includes(kw) : true;
			const methodMatch = method === "all" ? true : it.method === method;
			const statusMatch = status === "all" ? true : it.status === status;
			return kwMatch && methodMatch && statusMatch;
		});
	}, [list, keyword, method, status]);

	// Chart options must be created via hook at top-level (not in loops)
	const sparkOptions = useChart({
		chart: { sparkline: { enabled: true } },
		stroke: { width: 2, curve: "smooth" },
		tooltip: { enabled: false },
	});

	return (
		<div className="space-y-4">
			<SensitiveNotice />
			<Card>
				<CardHeader className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
					<CardTitle className="text-base">API 服务</CardTitle>
					<div className="flex flex-wrap items-center gap-2">
						<Label className="text-xs text-muted-foreground">关键词</Label>
						<Input
							className="w-[220px]"
							placeholder="搜索名称/数据集/路径"
							value={keyword}
							onChange={(e) => setKeyword(e.target.value)}
							onKeyDown={(e) => e.key === "Enter" && fetchList()}
						/>
						<Label className="ml-2 text-xs text-muted-foreground">方法</Label>
						<Select value={method} onValueChange={(v) => setMethod(v as any)}>
							<SelectTrigger className="w-[120px]">
								<SelectValue />
							</SelectTrigger>
							<SelectContent>
								<SelectItem value="all">全部</SelectItem>
								<SelectItem value="GET">GET</SelectItem>
								<SelectItem value="POST">POST</SelectItem>
								<SelectItem value="PUT">PUT</SelectItem>
								<SelectItem value="DELETE">DELETE</SelectItem>
							</SelectContent>
						</Select>
						<Label className="ml-2 text-xs text-muted-foreground">状态</Label>
						<Select value={status} onValueChange={(v) => setStatus(v as any)}>
							<SelectTrigger className="w-[140px]">
								<SelectValue />
							</SelectTrigger>
							<SelectContent>
								<SelectItem value="all">全部</SelectItem>
								<SelectItem value="PUBLISHED">发布</SelectItem>
								<SelectItem value="OFFLINE">下线</SelectItem>
							</SelectContent>
						</Select>
						<Button variant="outline" onClick={fetchList} disabled={loading}>
							<Icon icon="solar:refresh-bold" /> 刷新
						</Button>
					</div>
				</CardHeader>
				<CardContent className="overflow-x-auto">
					<table className="w-full min-w-[1200px] table-fixed border-collapse text-sm">
						<thead className="bg-muted/40 text-left text-xs uppercase text-muted-foreground">
							<tr>
								<th className="px-3 py-2 font-medium">API 名称</th>
								<th className="px-3 py-2 font-medium">绑定数据集/视图</th>
								<th className="px-3 py-2 font-medium w-[90px]">方法</th>
								<th className="px-3 py-2 font-medium">路径</th>
								<th className="px-3 py-2 font-medium w-[110px]">最低密级</th>
								<th className="px-3 py-2 font-medium w-[140px]">QPS 配额</th>
								<th className="px-3 py-2 font-medium w-[160px]">最近调用量</th>
								<th className="px-3 py-2 font-medium w-[90px]">状态</th>
								<th className="px-3 py-2 font-medium w-[160px]">操作</th>
							</tr>
						</thead>
						<tbody>
							{filtered.map((it) => (
								<tr key={it.id} className="border-b last:border-b-0">
									<td className="px-3 py-2 font-medium">
										<div className="flex items-center gap-2">
											<Icon icon="solar:server-bold-duotone" className="text-primary" />
											<span>{it.name}</span>
											<Badge variant="outline">{it.method}</Badge>
										</div>
									</td>
									<td className="px-3 py-2">
										{it.datasetName ? (
											<a
												className="text-primary hover:underline"
												href={it.datasetId ? `/catalog/assets/${it.datasetId}` : "/catalog/assets"}
												onClick={(e) => {
													e.preventDefault();
													router.push(it.datasetId ? `/catalog/assets/${it.datasetId}` : "/catalog/assets");
												}}
											>
												{it.datasetName}
											</a>
										) : (
											<span className="text-xs text-muted-foreground">未绑定</span>
										)}
									</td>
									<td className="px-3 py-2">
										<Badge variant="outline">{it.method}</Badge>
									</td>
									<td className="px-3 py-2 font-mono text-xs">{it.path}</td>
									<td className="px-3 py-2">
										<LevelBadge level={it.classification} />
									</td>
									<td className="px-3 py-2">
										{it.qps} / {it.qpsLimit}
									</td>
									<td className="px-3 py-2">
										<Chart height={40} series={[{ name: "calls", data: it.sparkline }]} options={sparkOptions} />
									</td>
									<td className="px-3 py-2">
										<StatusBadge status={it.status} />
									</td>
									<td className="px-3 py-2">
										<div className="flex items-center gap-2">
											<Button size="sm" variant="outline" onClick={() => gotoDetail(it.id)}>
												查看详情
											</Button>
											<Button size="sm" onClick={() => gotoDetail(it.id, "try")}>
												<Icon icon="solar:bolt-bold-duotone" /> 在线试调
											</Button>
										</div>
									</td>
								</tr>
							))}
							{!filtered.length && (
								<tr>
									<td colSpan={9} className="px-3 py-8 text-center text-xs text-muted-foreground">
										{loading ? "加载中…" : "暂无符合条件的 API 服务"}
									</td>
								</tr>
							)}
						</tbody>
					</table>
				</CardContent>
			</Card>

			{/* 详情改为独立路由 /services/apis/:id */}
		</div>
	);
}
