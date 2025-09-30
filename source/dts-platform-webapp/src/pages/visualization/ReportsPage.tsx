import { useEffect, useMemo, useState } from "react";
import { Icon } from "@/components/icon";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Input } from "@/ui/input";
import { Label } from "@/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/ui/select";
import reportsService, { type PublishedReport } from "@/api/services/reportsService";

export default function ReportsPage() {
	const [reports, setReports] = useState<PublishedReport[]>([]);
	const [loading, setLoading] = useState(false);
	const [keyword, setKeyword] = useState("");
	const [tool, setTool] = useState<string>("all");

	const tools = useMemo(() => {
		const set = new Set(reports.map((r) => r.biTool));
		return Array.from(set);
	}, [reports]);

	const fetchReports = async () => {
		setLoading(true);
		try {
			const data = await reportsService.getPublishedReports();
			setReports(data);
		} finally {
			setLoading(false);
		}
	};

	useEffect(() => {
		void fetchReports();
	}, []);

	const filtered = useMemo(() => {
		const kw = keyword.trim().toLowerCase();
		return reports.filter((r) => {
			const kwMatch = kw
				? r.title.toLowerCase().includes(kw) ||
					r.owner.toLowerCase().includes(kw) ||
					(r.domain || "").toLowerCase().includes(kw)
				: true;
			const toolMatch = tool === "all" ? true : r.biTool === tool;
			return kwMatch && toolMatch;
		});
	}, [reports, keyword, tool]);

	return (
		<div className="space-y-4">
			<Card>
				<CardHeader className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
					<CardTitle className="text-base">数据报表</CardTitle>
					<div className="flex flex-wrap items-center gap-2">
						<Label className="text-xs text-muted-foreground">关键词</Label>
						<Input
							className="w-[220px]"
							placeholder="搜索标题/负责人/数据域"
							value={keyword}
							onChange={(e) => setKeyword(e.target.value)}
							onKeyDown={(e) => e.key === "Enter" && fetchReports()}
						/>
						<Label className="ml-2 text-xs text-muted-foreground">BI 工具</Label>
						<Select value={tool} onValueChange={setTool}>
							<SelectTrigger className="w-[160px]">
								<SelectValue />
							</SelectTrigger>
							<SelectContent>
								<SelectItem value="all">全部</SelectItem>
								{tools.map((t) => (
									<SelectItem key={t} value={t}>
										{t}
									</SelectItem>
								))}
							</SelectContent>
						</Select>
						<Button variant="outline" onClick={fetchReports} disabled={loading}>
							<Icon icon="solar:refresh-bold" /> 刷新
						</Button>
					</div>
				</CardHeader>
				<CardContent className="overflow-x-auto">
					<table className="w-full min-w-[960px] table-fixed border-collapse text-sm">
						<thead className="bg-muted/40 text-left text-xs uppercase text-muted-foreground">
							<tr>
								<th className="px-3 py-2 font-medium w-[32px]">#</th>
								<th className="px-3 py-2 font-medium">报表标题</th>
								<th className="px-3 py-2 font-medium w-[120px]">BI 工具</th>
								<th className="px-3 py-2 font-medium w-[140px]">数据域</th>
								<th className="px-3 py-2 font-medium w-[160px]">负责人</th>
								<th className="px-3 py-2 font-medium w-[200px]">标签</th>
								<th className="px-3 py-2 font-medium w-[160px]">最近更新</th>
								<th className="px-3 py-2 font-medium w-[120px]">操作</th>
							</tr>
						</thead>
						<tbody>
							{filtered.map((r, idx) => (
								<tr key={r.id} className="border-b last:border-b-0">
									<td className="px-3 py-2 text-xs text-muted-foreground">{idx + 1}</td>
									<td className="px-3 py-2 font-medium">
										<div className="flex items-center gap-2">
											<span>{r.title}</span>
											<a
												className="text-primary hover:underline inline-flex items-center gap-1"
												href={r.url}
												target="_blank"
												rel="noopener noreferrer"
												title="在新窗口打开"
											>
												<Icon icon="solar:link-circle-bold-duotone" /> 打开
											</a>
										</div>
									</td>
									<td className="px-3 py-2">{r.biTool}</td>
									<td className="px-3 py-2">{r.domain || "-"}</td>
									<td className="px-3 py-2">{r.owner}</td>
									<td className="px-3 py-2">
										<div className="flex flex-wrap gap-1">
											{(r.tags || []).map((t) => (
												<Badge key={t} variant="secondary">
													{t}
												</Badge>
											))}
										</div>
									</td>
									<td className="px-3 py-2 text-xs text-muted-foreground">{new Date(r.updatedAt).toLocaleString()}</td>
									<td className="px-3 py-2">
										<a
											href={r.url}
											target="_blank"
											rel="noopener noreferrer"
											className="inline-flex items-center gap-1 text-primary hover:underline"
										>
											<Icon icon="solar:external-drive-bold-duotone" /> 访问报表
										</a>
									</td>
								</tr>
							))}
							{!filtered.length && (
								<tr>
									<td colSpan={8} className="px-3 py-8 text-center text-xs text-muted-foreground">
										{loading ? "加载中…" : "暂无符合条件的报表"}
									</td>
								</tr>
							)}
						</tbody>
					</table>
				</CardContent>
			</Card>
		</div>
	);
}
