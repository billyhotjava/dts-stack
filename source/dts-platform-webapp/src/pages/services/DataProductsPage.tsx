import { useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { Icon } from "@/components/icon";
import { listDataProducts, getDataProductDetail, type DataProductDetail, type DataProductSummary, type DataProductVersion } from "@/api/services/dataProductsService";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Input } from "@/ui/input";
import { Label } from "@/ui/label";
import { ScrollArea } from "@/ui/scroll-area";
import { Tabs, TabsList, TabsTrigger } from "@/ui/tabs";
import SensitiveNotice from "@/components/security/SensitiveNotice";

function StatusBadge({ status }: { status?: string }) {
	if (!status) return <Badge variant="outline">未设置</Badge>;
	const map: Record<string, string> = {
		"启用": "bg-emerald-100 text-emerald-700",
		"停用": "bg-slate-200 text-slate-600",
		"灰度": "bg-amber-100 text-amber-700",
	};
	const cls = map[status] ?? "bg-slate-100 text-slate-700";
	return <Badge className={cls}>{status}</Badge>;
}

function ProductCard({ item, active, onSelect }: { item: DataProductSummary; active: boolean; onSelect: (id: string) => void }) {
	return (
		<button
			onClick={() => onSelect(item.id)}
			className={`w-full rounded-lg border px-4 py-3 text-left transition ${active ? "border-primary bg-primary/5" : "hover:border-primary/60"}`}
		>
			<div className="flex items-center justify-between">
				<div className="space-y-1">
					<div className="flex items-center gap-2">
						<span className="text-sm font-semibold">{item.name}</span>
						<Badge variant="outline">{item.productType || "未分类"}</Badge>
					</div>
					<div className="text-xs text-muted-foreground">编码：{item.code}</div>
					<div className="text-xs text-muted-foreground">绑定数据集：{item.datasets.join("、") || "-"}</div>
				</div>
				<div className="flex flex-col items-end gap-1 text-xs text-muted-foreground">
					<StatusBadge status={item.status} />
					{item.currentVersion && <span>当前版本：{item.currentVersion}</span>}
					<span>订阅：{item.subscriptions}</span>
				</div>
			</div>
		</button>
	);
}

function VersionBlock({ version }: { version: DataProductVersion }) {
	return (
		<div className="rounded-md border p-4">
			<div className="flex flex-wrap items-center justify-between gap-2">
				<div className="flex items-center gap-2 text-sm font-medium">
					<Icon icon="solar:bookmark-bold-duotone" className="text-primary" />
					{version.version}
					{version.status && <Badge variant="outline">{version.status}</Badge>}
				</div>
				<div className="text-xs text-muted-foreground">
					{version.releasedAt ? new Date(version.releasedAt).toLocaleString() : "未发布"}
				</div>
			</div>
			{version.diffSummary && <div className="mt-2 text-xs text-muted-foreground">变更说明：{version.diffSummary}</div>}
			<div className="mt-3 space-y-2">
				<Label className="text-xs text-muted-foreground">字段列表</Label>
				<div className="overflow-x-auto">
					<table className="w-full min-w-[480px] table-fixed border-collapse text-xs">
						<thead className="bg-muted/40">
							<tr>
								<th className="px-2 py-2 text-left font-medium">字段</th>
								<th className="px-2 py-2 text-left font-medium">类型</th>
								<th className="px-2 py-2 text-left font-medium">术语</th>
								<th className="px-2 py-2 text-left font-medium">脱敏</th>
								<th className="px-2 py-2 text-left font-medium">描述</th>
							</tr>
						</thead>
						<tbody>
							{version.fields.map((f) => (
								<tr key={f.name} className="border-b last:border-b-0">
									<td className="px-2 py-2 font-medium">{f.name}</td>
									<td className="px-2 py-2">{f.type || "-"}</td>
									<td className="px-2 py-2">{f.term || "-"}</td>
									<td className="px-2 py-2">{f.masked ? "是" : "否"}</td>
									<td className="px-2 py-2 text-muted-foreground">{f.description || "-"}</td>
								</tr>
							))}
							{!version.fields.length && (
								<tr>
									<td colSpan={5} className="px-2 py-4 text-center text-muted-foreground">
										暂无字段信息
									</td>
								</tr>
							)}
						</tbody>
					</table>
				</div>
			</div>
			<div className="mt-3 grid gap-3 md:grid-cols-3 text-xs">
				<div className="rounded-md border p-3">
					<div className="text-muted-foreground">REST 接入</div>
					<div className="mt-1 font-mono text-[11px] text-muted-foreground break-all">
						{version.consumption?.rest?.endpoint || "-"}
					</div>
				</div>
				<div className="rounded-md border p-3">
					<div className="text-muted-foreground">JDBC</div>
					<div className="mt-1 font-mono text-[11px] text-muted-foreground break-all">
						{version.consumption?.jdbc?.url || "-"}
					</div>
				</div>
				<div className="rounded-md border p-3">
					<div className="text-muted-foreground">文件分发</div>
					<div className="mt-1 text-muted-foreground">
						{version.consumption?.file?.objectStorePath || "-"}
					</div>
				</div>
			</div>
		</div>
	);
}

export default function DataProductsPage() {
	const [products, setProducts] = useState<DataProductSummary[]>([]);
	const [loadingList, setLoadingList] = useState(false);
	const [loadingDetail, setLoadingDetail] = useState(false);
	const [keyword, setKeyword] = useState("");
	const [typeFilter, setTypeFilter] = useState<string>("all");
	const [selectedId, setSelectedId] = useState<string | null>(null);
	const [detail, setDetail] = useState<DataProductDetail | null>(null);

	useEffect(() => {
		const fetchList = async () => {
			setLoadingList(true);
			try {
				const data = await listDataProducts();
				setProducts(data);
				if (data.length) {
					setSelectedId(data[0].id);
				}
			} catch (error) {
				console.error(error);
				toast.error("数据产品列表加载失败");
			} finally {
				setLoadingList(false);
			}
		};
		void fetchList();
	}, []);

	useEffect(() => {
		if (!selectedId) {
			setDetail(null);
			return;
		}
		const fetchDetail = async () => {
			setLoadingDetail(true);
			try {
				const data = await getDataProductDetail(selectedId);
				setDetail(data);
			} catch (error) {
				console.error(error);
				toast.error("加载数据产品详情失败");
			} finally {
				setLoadingDetail(false);
			}
		};
		void fetchDetail();
	}, [selectedId]);

	const filteredProducts = useMemo(() => {
		const kw = keyword.trim().toLowerCase();
		return products.filter((item) => {
			const kwMatch = kw
				? `${item.name}${item.code}${item.datasets.join(" ")}`.toLowerCase().includes(kw)
				: true;
			const typeMatch = typeFilter === "all" ? true : (item.productType || "").toLowerCase() === typeFilter.toLowerCase();
			return kwMatch && typeMatch;
		});
	}, [products, keyword, typeFilter]);

	const typeOptions = useMemo(() => {
		const set = new Set<string>();
		products.forEach((p) => {
			if (p.productType) set.add(p.productType);
		});
		return ["all", ...Array.from(set)];
	}, [products]);

	return (
		<div className="space-y-4">
			<SensitiveNotice />
			<Card>
				<CardHeader className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
					<CardTitle className="text-base">数据产品目录</CardTitle>
					<div className="flex flex-wrap items-center gap-2">
						<Label className="text-xs text-muted-foreground">关键词</Label>
						<Input
							className="w-[220px]"
							placeholder="名称/编码/数据集"
							value={keyword}
							onChange={(e) => setKeyword(e.target.value)}
						/>
						<Label className="ml-2 text-xs text-muted-foreground">类型</Label>
						<Tabs value={typeFilter} onValueChange={setTypeFilter} className="max-w-full">
							<TabsList>
								{typeOptions.map((opt) => (
									<TabsTrigger key={opt} value={opt} className="capitalize">
										{opt === "all" ? "全部" : opt}
									</TabsTrigger>
								))}
							</TabsList>
						</Tabs>
						<Button variant="outline" size="sm" onClick={() => void setSelectedId(filteredProducts[0]?.id || null)} disabled={loadingList}>
							刷新
						</Button>
					</div>
				</CardHeader>
				<CardContent>
					<div className="grid gap-4 lg:grid-cols-[360px,1fr]">
						<ScrollArea className="max-h-[520px] pr-4">
							<div className="space-y-3">
								{loadingList && <div className="text-xs text-muted-foreground">加载中…</div>}
								{!loadingList && filteredProducts.map((item) => (
									<ProductCard key={item.id} item={item} active={item.id === selectedId} onSelect={setSelectedId} />
								))}
								{!loadingList && !filteredProducts.length && (
									<div className="rounded-md border border-dashed p-6 text-center text-xs text-muted-foreground">
										暂无匹配的数据产品
									</div>
								)}
							</div>
						</ScrollArea>
						<div className="min-h-[400px]">
							{loadingDetail && <div className="text-xs text-muted-foreground">详情加载中…</div>}
							{!loadingDetail && detail && (
								<div className="space-y-4">
									<div className="flex flex-wrap items-center gap-3">
										<h3 className="text-lg font-semibold">{detail.name}</h3>
										<Badge variant="outline">{detail.productType || "未分类"}</Badge>
										<StatusBadge status={detail.status} />
									</div>
									<div className="grid gap-3 md:grid-cols-3 text-xs">
										<div className="rounded-md border p-3">
											<div className="text-muted-foreground">SLA</div>
											<div className="mt-1 font-medium">{detail.sla || "-"}</div>
										</div>
										<div className="rounded-md border p-3">
											<div className="text-muted-foreground">刷新频率</div>
											<div className="mt-1 font-medium">{detail.refreshFrequency || "-"}</div>
										</div>
										<div className="rounded-md border p-3">
											<div className="text-muted-foreground">订阅数</div>
											<div className="mt-1 font-medium">{detail.subscriptions}</div>
										</div>
									</div>
									<div className="rounded-md border p-4 text-sm">
										<div className="text-xs text-muted-foreground">绑定数据集</div>
										<div className="mt-1 flex flex-wrap gap-2">
											{detail.datasets.length ? detail.datasets.map((ds) => <Badge key={ds} variant="secondary">{ds}</Badge>) : <span className="text-xs text-muted-foreground">无</span>}
										</div>
									</div>
									{detail.description && (
										<Card>
											<CardHeader>
												<CardTitle className="text-base">说明</CardTitle>
											</CardHeader>
											<CardContent className="text-sm text-muted-foreground">{detail.description}</CardContent>
										</Card>
									)}
									<Card>
										<CardHeader>
											<CardTitle className="text-base">版本列表</CardTitle>
										</CardHeader>
										<CardContent className="space-y-3">
											{detail.versions.map((v) => (
												<VersionBlock key={v.version} version={v} />
											))}
											{!detail.versions.length && <div className="text-xs text-muted-foreground">暂无版本信息</div>}
										</CardContent>
									</Card>
								</div>
							)}
							{!loadingDetail && !detail && <div className="text-xs text-muted-foreground">请选择左侧数据产品查看详情</div>}
						</div>
					</div>
				</CardContent>
			</Card>
		</div>
	);
}
