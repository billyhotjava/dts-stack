import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { archiveIndicator, listIndicators, publishIndicator, validateIndicator } from "@/api/platformApi";
import { useRouter } from "@/routes/hooks";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Input } from "@/ui/input";
import { Label } from "@/ui/label";
import { ScrollArea } from "@/ui/scroll-area";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/ui/select";

type IndicatorRow = {
	id: string;
	code: string;
	name: string;
	ownerDept?: string | null;
	status?: string | null;
	dataLevel?: string | null;
	datasetId?: string | null;
	expressionSql?: string | null;
	lastValidationStatus?: string | null;
	lastValidationMessage?: string | null;
	lastValidatedAt?: string | null;
	lastValidationSignature?: string | null;
};

const PAGE_SIZE = 10;

export default function IndicatorPublishPage() {
	const router = useRouter();
	const [loading, setLoading] = useState(false);
	const [items, setItems] = useState<IndicatorRow[]>([]);
	const [total, setTotal] = useState(0);
	const [page, setPage] = useState(0);
	const [keyword, setKeyword] = useState("");
	const [status, setStatus] = useState<string>("ALL");

	const statusOptions = useMemo(
		() => [
			{ value: "ALL", label: "全部" },
			{ value: "DRAFT", label: "草稿" },
			{ value: "PUBLISHED", label: "已发布" },
			{ value: "DEPRECATED", label: "已废止" },
		],
		[],
	);

	const load = useCallback(async () => {
		setLoading(true);
		try {
			const params: any = { page, size: PAGE_SIZE };
			if (keyword.trim()) params.keyword = keyword.trim();
			if (status !== "ALL") params.status = status;
			const resp = (await listIndicators(params)) as any;
			const content = Array.isArray(resp?.content) ? resp.content : [];
			setItems(
				content.map((it: any) => ({
					id: String(it?.id ?? ""),
					code: String(it?.code ?? ""),
					name: String(it?.name ?? ""),
					ownerDept: it?.ownerDept ?? "",
					status: it?.status ?? "",
					dataLevel: it?.dataLevel ?? "",
					datasetId: it?.datasetId ?? "",
					expressionSql: it?.expressionSql ?? "",
					lastValidationStatus: it?.lastValidationStatus ?? "",
					lastValidationMessage: it?.lastValidationMessage ?? "",
					lastValidatedAt: it?.lastValidatedAt ?? "",
					lastValidationSignature: it?.lastValidationSignature ?? "",
				})),
			);
			setTotal(Number(resp?.total ?? 0));
		} catch (e: any) {
			console.error(e);
			toast.error(e?.message ?? "加载失败");
		} finally {
			setLoading(false);
		}
	}, [keyword, page, status]);

	useEffect(() => {
		void load();
	}, [load]);

	const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));
	const pageLabel = `${page + 1}/${totalPages}`;

	const formatValidation = (row: IndicatorRow) => {
		const st = String(row.lastValidationStatus || "").toUpperCase();
		if (!st) return "未校验";
		if (st === "SUCCESS") return "通过";
		if (st === "FAILED") return "失败";
		return st;
	};

	const validationBadgeVariant = (row: IndicatorRow) => {
		const st = String(row.lastValidationStatus || "").toUpperCase();
		if (st === "SUCCESS") return "default";
		if (st === "FAILED") return "destructive";
		return "secondary";
	};

	const configComplete = (row: IndicatorRow) => {
		return !!(row.datasetId && String(row.datasetId).trim() && row.expressionSql && String(row.expressionSql).trim());
	};

	const goComputeRules = () => {
		router.push("/governance/indicators/compute-rules");
	};

	const runValidate = useCallback(
		async (row: IndicatorRow) => {
			if (!row?.id) return;
			try {
				const res = (await validateIndicator(row.id)) as any;
				const st = String(res?.status || "").toUpperCase();
				if (st === "SUCCESS") toast.success("校验通过");
				else toast.error(res?.message || "校验失败");
				await load();
			} catch (e: any) {
				console.error(e);
				toast.error(e?.message ?? "校验失败");
			}
		},
		[load],
	);

	const runPublish = useCallback(
		async (row: IndicatorRow) => {
			if (!row?.id) return;
			try {
				await publishIndicator(row.id);
				toast.success("已发布");
				await load();
			} catch (e: any) {
				console.error(e);
				toast.error(e?.message ?? "发布失败");
			}
		},
		[load],
	);

	const runArchive = useCallback(
		async (row: IndicatorRow) => {
			if (!row?.id) return;
			try {
				await archiveIndicator(row.id);
				toast.success("已废止");
				await load();
			} catch (e: any) {
				console.error(e);
				toast.error(e?.message ?? "废止失败");
			}
		},
		[load],
	);

	return (
		<div className="flex flex-col gap-6">
			<Card>
				<CardHeader className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
					<CardTitle>发布与一致性校验</CardTitle>
					<div className="flex flex-col gap-2 md:flex-row md:items-center">
						<div className="w-full md:w-64">
							<Label className="text-xs text-muted-foreground">搜索</Label>
							<Input
								value={keyword}
								onChange={(e) => {
									setKeyword(e.target.value);
									setPage(0);
								}}
								placeholder="搜索编码/名称/分类"
							/>
						</div>
						<div className="w-full md:w-40">
							<Label className="text-xs text-muted-foreground">状态</Label>
							<Select
								value={status}
								onValueChange={(v) => {
									setStatus(v);
									setPage(0);
								}}
							>
								<SelectTrigger>
									<SelectValue />
								</SelectTrigger>
								<SelectContent>
									{statusOptions.map((opt) => (
										<SelectItem key={opt.value} value={opt.value}>
											{opt.label}
										</SelectItem>
									))}
								</SelectContent>
							</Select>
						</div>
						<Button variant="outline" onClick={goComputeRules}>
							去配置计算规则
						</Button>
						<Button variant="secondary" onClick={load} disabled={loading}>
							刷新
						</Button>
					</div>
				</CardHeader>
				<CardContent>
					<div className="overflow-x-auto">
						<table className="min-w-full text-sm">
							<thead className="text-left text-muted-foreground border-b">
								<tr>
									<th className="py-2 pr-4 font-medium">编码</th>
									<th className="py-2 pr-4 font-medium">名称</th>
									<th className="py-2 pr-4 font-medium">部门</th>
									<th className="py-2 pr-4 font-medium">配置</th>
									<th className="py-2 pr-4 font-medium">校验</th>
									<th className="py-2 pr-4 font-medium">状态</th>
									<th className="py-2 pr-0 font-medium text-right">操作</th>
								</tr>
							</thead>
							<tbody>
								{items.map((row) => (
									<tr key={row.id} className="border-b last:border-none align-top">
										<td className="py-2 pr-4 font-mono">{row.code}</td>
										<td className="py-2 pr-4 font-medium">{row.name}</td>
										<td className="py-2 pr-4 text-muted-foreground">{row.ownerDept || "-"}</td>
										<td className="py-2 pr-4">
											{configComplete(row) ? <Badge>完整</Badge> : <Badge variant="secondary">缺失</Badge>}
										</td>
										<td className="py-2 pr-4">
											<div className="flex items-center gap-2">
												<Badge variant={validationBadgeVariant(row)}>{formatValidation(row)}</Badge>
												{row.lastValidationStatus && String(row.lastValidationStatus).toUpperCase() === "FAILED" ? (
													<span className="max-w-[320px] truncate text-xs text-muted-foreground" title={String(row.lastValidationMessage || "")}>
														{String(row.lastValidationMessage || "")}
													</span>
												) : null}
											</div>
										</td>
										<td className="py-2 pr-4">
											<Badge
												variant={
													String(row.status).toUpperCase() === "PUBLISHED"
														? "default"
														: String(row.status).toUpperCase() === "DEPRECATED"
															? "secondary"
															: "outline"
												}
											>
												{String(row.status || "-")}
											</Badge>
										</td>
										<td className="py-2 pr-0">
											<div className="flex justify-end gap-2">
												<Button size="sm" variant="outline" onClick={() => runValidate(row)} disabled={loading}>
													校验
												</Button>
												<Button size="sm" variant="secondary" onClick={() => runPublish(row)} disabled={loading}>
													发布
												</Button>
												<Button size="sm" variant="secondary" onClick={() => runArchive(row)} disabled={loading}>
													废止
												</Button>
											</div>
										</td>
									</tr>
								))}
								{items.length === 0 ? (
									<tr>
										<td colSpan={7} className="py-10 text-center text-muted-foreground">
											{loading ? "加载中..." : "暂无数据"}
										</td>
									</tr>
								) : null}
							</tbody>
						</table>
					</div>

					<div className="mt-4 flex items-center justify-between text-sm">
						<div className="text-muted-foreground">共 {total} 条</div>
						<div className="flex items-center gap-2">
							<Button size="sm" variant="outline" disabled={page <= 0} onClick={() => setPage((p) => Math.max(0, p - 1))}>
								上一页
							</Button>
							<span className="text-muted-foreground">{pageLabel}</span>
							<Button
								size="sm"
								variant="outline"
								disabled={page + 1 >= totalPages}
								onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
							>
								下一页
							</Button>
						</div>
					</div>
				</CardContent>
			</Card>

			<Card>
				<CardHeader>
					<CardTitle className="text-base">说明</CardTitle>
				</CardHeader>
				<CardContent>
					<ScrollArea className="h-[140px] pr-2 text-sm text-muted-foreground">
						<div className="space-y-2">
							<div>1) “校验”会在当前部门上下文与密级策略下，执行一次 LIMIT 1 的 SQL 探测。</div>
							<div>2) “发布”会强制要求：已绑定数据集 + 已配置 SQL + 最近一次校验通过且未被修改；否则会自动触发校验并拦截失败原因。</div>
							<div>3) 若提示“部门上下文不可访问该数据集”，请先切换顶部部门上下文或调整数据集授权。</div>
						</div>
					</ScrollArea>
				</CardContent>
			</Card>
		</div>
	);
}

