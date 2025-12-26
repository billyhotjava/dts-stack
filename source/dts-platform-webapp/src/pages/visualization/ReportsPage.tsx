import { useEffect, useMemo, useState } from "react";
import { Icon } from "@/components/icon";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Input } from "@/ui/input";
import { Label } from "@/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/ui/select";
import deptService, { type DeptDto } from "@/api/services/deptService";
import reportsService, { type ReportLink } from "@/api/services/reportsService";

function classificationLabel(level: string | undefined | null) {
	const upper = String(level || "").trim().toUpperCase();
	if (upper === "CONFIDENTIAL") return { label: "机密", variant: "destructive" as const };
	if (upper === "SECRET") return { label: "秘密", variant: "secondary" as const };
	if (upper === "INTERNAL") return { label: "内部", variant: "outline" as const };
	return { label: "公开", variant: "default" as const };
}

function formatDeptNames(codes: string[] | undefined, dict: Map<string, DeptDto>) {
	const list = Array.isArray(codes) ? codes.filter(Boolean) : [];
	if (!list.length) return "全部";
	return list
		.map((c) => dict.get(String(c))?.nameZh || dict.get(String(c))?.nameEn || String(c))
		.join(", ");
}

function normalizeReportUrl(rawUrl: string, engine?: string | null): string {
	const url = String(rawUrl || "").trim();
	if (!url) return "";
	if (url.startsWith("/")) return url;

	// Best-effort normalize Hetu links so we can close 7778 and serve via Traefik on the same domain.
	// Traefik routes already cover PathPrefix(/screen|/dashboards) and /dashboard/hetu.
	const upperEngine = String(engine || "").trim().toUpperCase();
	try {
		const parsed = new URL(url);
		const path = `${parsed.pathname || ""}${parsed.search || ""}${parsed.hash || ""}`;
		const isHetuHostPort = parsed.port === "7778";
		const isHetuPath =
			parsed.pathname?.startsWith("/screen") ||
			parsed.pathname?.startsWith("/dashboards") ||
			parsed.pathname?.startsWith("/dashboard/hetu") ||
			parsed.pathname?.startsWith("/dashboard");
		if (upperEngine === "HETU" && (isHetuHostPort || isHetuPath)) {
			return path || url;
		}
		if (upperEngine === "METABASE") {
			if (!path) return url;
			return `/analytics${path.startsWith("/") ? "" : "/"}${path}`;
		}
	} catch {
		// ignore URL parse failures
	}
	return url;
}

export default function ReportsPage() {
	const [reports, setReports] = useState<ReportLink[]>([]);
	const [loading, setLoading] = useState(false);
	const [keyword, setKeyword] = useState("");
	const [deptCode, setDeptCode] = useState<string>("all");
	const [reportType, setReportType] = useState<string>("all");
	const [departments, setDepartments] = useState<DeptDto[]>([]);

	const reportTypes = useMemo(() => {
		const set = new Set<string>();
		for (const r of reports) {
			const t = String(r.reportType || "").trim();
			if (t) set.add(t);
		}
		return Array.from(set);
	}, [reports]);

	const deptDict = useMemo(() => {
		const m = new Map<string, DeptDto>();
		for (const d of departments) {
			m.set(String(d.code), d);
		}
		return m;
	}, [departments]);

	const fetchReports = async () => {
		setLoading(true);
		try {
			const data = await reportsService.getPublishedReports({
				keyword: keyword.trim() || undefined,
				deptCode: deptCode === "all" ? undefined : deptCode,
				type: reportType === "all" ? undefined : reportType,
			});
			setReports(data);
		} finally {
			setLoading(false);
		}
	};

	useEffect(() => {
		void fetchReports();
	}, []);

	useEffect(() => {
		deptService
			.listDepartments()
			.then((list) => setDepartments(Array.isArray(list) ? list : []))
			.catch(() => setDepartments([]));
	}, []);

	const openReport = (r: ReportLink) => {
		const url = normalizeReportUrl(String(r?.url || ""), r?.engine);
		if (!url) return;
		// Record audit via authorized API call (sendBeacon would miss Authorization in this project).
		reportsService
			.visit({
				id: r.id,
				code: r.code,
				title: r.title,
				url: r.url,
				engine: r.engine,
				classification: r.classification,
			})
			.catch(() => {});

		window.open(url, "_blank", "noopener,noreferrer");
	};

	return (
		<div className="space-y-4">
			<Card>
				<CardHeader className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
					<CardTitle className="text-base">数据报表</CardTitle>
					<div className="flex flex-wrap items-center gap-2">
						<Label className="text-xs text-muted-foreground">关键词</Label>
						<Input
							className="w-[220px]"
							placeholder="搜索标题/编码"
							value={keyword}
							onChange={(e) => setKeyword(e.target.value)}
							onKeyDown={(e) => e.key === "Enter" && fetchReports()}
						/>
						<Label className="ml-2 text-xs text-muted-foreground">部门</Label>
						<Select value={deptCode} onValueChange={setDeptCode}>
							<SelectTrigger className="w-[180px]">
								<SelectValue />
							</SelectTrigger>
							<SelectContent>
								<SelectItem value="all">全部</SelectItem>
								{departments.map((d) => (
									<SelectItem key={String(d.code)} value={String(d.code)}>
										{d.nameZh || d.nameEn || d.code}
									</SelectItem>
								))}
							</SelectContent>
						</Select>
						<Label className="ml-2 text-xs text-muted-foreground">类型</Label>
						<Select value={reportType} onValueChange={setReportType}>
							<SelectTrigger className="w-[160px]">
								<SelectValue />
							</SelectTrigger>
							<SelectContent>
								<SelectItem value="all">全部</SelectItem>
								{reportTypes.map((t) => (
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
								<th className="px-3 py-2 font-medium w-[110px]">引擎</th>
								<th className="px-3 py-2 font-medium w-[120px]">类型</th>
								<th className="px-3 py-2 font-medium w-[200px]">部门范围</th>
								<th className="px-3 py-2 font-medium w-[90px]">密级</th>
								<th className="px-3 py-2 font-medium w-[160px]">最近更新</th>
								<th className="px-3 py-2 font-medium w-[120px]">操作</th>
							</tr>
						</thead>
						<tbody>
							{reports.map((r, idx) => (
								<tr key={r.id} className="border-b last:border-b-0">
									<td className="px-3 py-2 text-xs text-muted-foreground">{idx + 1}</td>
									<td className="px-3 py-2 font-medium">
										<div className="flex items-center gap-2">
											<span>{r.title}</span>
											<button
												type="button"
												className="text-primary hover:underline inline-flex items-center gap-1"
												title="在新窗口打开"
												onClick={() => openReport(r)}
											>
												<Icon icon="solar:link-circle-bold-duotone" /> 打开
											</button>
										</div>
									</td>
									<td className="px-3 py-2">{r.engine}</td>
									<td className="px-3 py-2">{r.reportType || "-"}</td>
									<td className="px-3 py-2">{formatDeptNames(r.deptCodes, deptDict)}</td>
									<td className="px-3 py-2">
										{(() => {
											const v = classificationLabel(r.classification);
											return (
												<Badge variant={v.variant} className="whitespace-nowrap">
													{v.label}
												</Badge>
											);
										})()}
									</td>
									<td className="px-3 py-2 text-xs text-muted-foreground">
										{r.updatedAt ? new Date(r.updatedAt).toLocaleString() : "-"}
									</td>
									<td className="px-3 py-2">
										<button
											type="button"
											onClick={() => openReport(r)}
											className="inline-flex items-center gap-1 text-primary hover:underline"
										>
											<Icon icon="solar:external-drive-bold-duotone" /> 访问报表
										</button>
									</td>
								</tr>
							))}
							{!reports.length && (
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
