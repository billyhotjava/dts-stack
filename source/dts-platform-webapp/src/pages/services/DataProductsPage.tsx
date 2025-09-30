import { useMemo, useState } from "react";
import clsx from "clsx";
import { Icon } from "@/components/icon";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Input } from "@/ui/input";
import { ScrollArea } from "@/ui/scroll-area";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/ui/tabs";
import { Separator } from "@/ui/separator";

type ProductType = "宽表" | "指标视图" | "快照";

type ProductSummary = {
	id: string;
	name: string;
	type: ProductType;
	datasets: string[];
	version: string;
	sla: string;
	lastProducedAt: string;
	subscriptions: number;
	status: "启用" | "停用" | "灰度";
};

const PRODUCT_SUMMARIES: ProductSummary[] = [
	{
		id: "prd-cust-wide",
		name: "客户 360 宽表",
		type: "宽表",
		datasets: ["dwd_customer_profile", "dwd_customer_behavior"],
		version: "v3.2",
		sla: "小时级",
		lastProducedAt: "2024-12-09 08:05",
		subscriptions: 28,
		status: "启用",
	},
	{
		id: "prd-sales-metrics",
		name: "销售指标视图",
		type: "指标视图",
		datasets: ["dm_sales_order", "dm_sales_target"],
		version: "v2.6",
		sla: "日批",
		lastProducedAt: "2024-12-08 23:10",
		subscriptions: 41,
		status: "启用",
	},
	{
		id: "prd-risk-snapshot",
		name: "风险案件周报快照",
		type: "快照",
		datasets: ["dws_risk_case"],
		version: "v1.4",
		sla: "周批",
		lastProducedAt: "2024-12-06 07:00",
		subscriptions: 12,
		status: "灰度",
	},
];

type ProductField = {
	name: string;
	type: string;
	term?: string;
	masked: boolean;
	description?: string;
};

type ProductDetail = {
	id: string;
	fields: ProductField[];
	bloodlineSummary: string;
	classificationStrategy: string;
	maskingStrategy: string;
	refreshFrequency: string;
	latencyObjective: string;
	failurePolicy: string;
	versions: Array<{
		version: string;
		status: "current" | "gray" | "archived";
		releasedAt: string;
		diffSummary: string;
	}>;
	consumption: {
		rest: {
			endpoint: string;
			auth: string;
		};
		jdbc: {
			driver: string;
			url: string;
		};
		file: {
			objectStorePath: string;
			sharedPath: string;
			formats: string[];
		};
	};
};

const PRODUCT_DETAILS: ProductDetail[] = [
	{
		id: "prd-cust-wide",
		fields: [
			{ name: "customer_id", type: "STRING", term: "客户ID", masked: false, description: "主键" },
			{ name: "customer_name", type: "STRING", masked: true, term: "客户姓名" },
			{ name: "career_level", type: "STRING", masked: false, term: "职业等级" },
			{ name: "lifecycle_stage", type: "STRING", masked: false },
			{ name: "total_asset", type: "DECIMAL", masked: true, description: "加总金融资产" },
			{ name: "avg_monthly_active_days", type: "DECIMAL", masked: false },
		],
		bloodlineSummary: "汇聚 ODS 客户主数据、交易行为明细及 CRM 互动记录并进行标准化融合",
		classificationStrategy: "字段按密级标注：姓名/资产为秘密，其余为内部。",
		maskingStrategy: "对 PII 字段默认使用字符替换，对资产字段按区间脱敏。",
		refreshFrequency: "T+1 05:30 完成产出",
		latencyObjective: "延迟不超过 30 分钟",
		failurePolicy: "连续失败 2 次触发钉钉/邮箱告警，支持手动补数",
		versions: [
			{ version: "v3.2", status: "current", releasedAt: "2024-12-08", diffSummary: "新增职业等级字段" },
			{ version: "v3.1", status: "archived", releasedAt: "2024-11-20", diffSummary: "优化资产脱敏策略" },
			{ version: "v3.0", status: "archived", releasedAt: "2024-11-01", diffSummary: "切换血缘到新版数据域" },
		],
		consumption: {
			rest: {
				endpoint: "https://api.data-platform.local/products/customer-360",
				auth: "OAuth2 Client Credentials",
			},
			jdbc: {
				driver: "com.platform.jdbc.Driver",
				url: "jdbc:platform://gateway.local:8443/customer360",
			},
			file: {
				objectStorePath: "oss://datalake/prod/customer360/daily/",
				sharedPath: "/mnt/share/customer360/",
				formats: ["parquet", "csv"],
			},
		},
	},
	{
		id: "prd-sales-metrics",
		fields: [
			{ name: "date", type: "DATE", masked: false },
			{ name: "channel", type: "STRING", masked: false },
			{ name: "gmv", type: "DECIMAL", masked: false },
			{ name: "orders", type: "INTEGER", masked: false },
			{ name: "target_completion", type: "DECIMAL", masked: false },
		],
		bloodlineSummary: "按渠道汇总订单宽表并关联目标视图，输出 GMV/订单/完成率指标",
		classificationStrategy: "指标均为内部数据，订单字段脱敏至聚合层",
		maskingStrategy: "聚合后无需额外脱敏，明细字段不可导出",
		refreshFrequency: "每日 06:00",
		latencyObjective: "+60 分钟",
		failurePolicy: "失败立即短信告警负责人，并自动回滚上一版本",
		versions: [
			{ version: "v2.6", status: "current", releasedAt: "2024-12-07", diffSummary: "新增目标完成率字段" },
			{ version: "v2.5", status: "archived", releasedAt: "2024-11-15", diffSummary: "GMV 口径同步销售平台" },
		],
		consumption: {
			rest: {
				endpoint: "https://api.data-platform.local/products/sales-metrics",
				auth: "API Key",
			},
			jdbc: {
				driver: "com.platform.jdbc.Driver",
				url: "jdbc:platform://gateway.local:8443/salesmetrics",
			},
			file: {
				objectStorePath: "oss://datalake/prod/sales-metrics/",
				sharedPath: "/mnt/share/sales-metrics/",
				formats: ["parquet"],
			},
		},
	},
	{
		id: "prd-risk-snapshot",
		fields: [
			{ name: "report_week", type: "STRING", masked: false },
			{ name: "risk_category", type: "STRING", masked: false },
			{ name: "case_count", type: "INTEGER", masked: false },
			{ name: "high_risk_ratio", type: "DECIMAL", masked: false },
			{ name: "insight", type: "STRING", masked: false },
		],
		bloodlineSummary: "从风控案件数据集按周快照汇总，结合风险标签体系生成洞察",
		classificationStrategy: "默认密级为内部，洞察字段含敏感文本时升级至秘密",
		maskingStrategy: "洞察字段包含个人信息时自动正则脱敏",
		refreshFrequency: "每周一 07:00",
		latencyObjective: "2 小时",
		failurePolicy: "失败后自动重试 3 次，仍失败转人工排查",
		versions: [
			{ version: "v1.4", status: "gray", releasedAt: "2024-12-02", diffSummary: "新增风险洞察描述" },
			{ version: "v1.3", status: "current", releasedAt: "2024-11-18", diffSummary: "引入风险标签维度" },
		],
		consumption: {
			rest: {
				endpoint: "https://api.data-platform.local/products/risk-weekly",
				auth: "OAuth2 Client Credentials",
			},
			jdbc: {
				driver: "com.platform.jdbc.Driver",
				url: "jdbc:platform://gateway.local:8443/risk-weekly",
			},
			file: {
				objectStorePath: "oss://datalake/prod/risk-weekly/",
				sharedPath: "/mnt/share/risk-weekly/",
				formats: ["csv", "xlsx"],
			},
		},
	},
];

const STATUS_VARIANTS: Record<
	ProductSummary["status"],
	{ label: string; variant: "default" | "secondary" | "outline" | "destructive" }
> = {
	启用: { label: "启用", variant: "default" },
	停用: { label: "停用", variant: "secondary" },
	灰度: { label: "灰度", variant: "outline" },
};

export default function DataProductsPage() {
	const [search, setSearch] = useState("");
	const [selectedId, setSelectedId] = useState(PRODUCT_SUMMARIES[0]?.id ?? "");

	const filteredProducts = useMemo(() => {
		return PRODUCT_SUMMARIES.filter((product) => {
			if (!search.trim()) return true;
			const lower = search.toLowerCase();
			return (
				product.name.toLowerCase().includes(lower) ||
				product.datasets.some((dataset) => dataset.toLowerCase().includes(lower))
			);
		});
	}, [search]);

	const activeProduct = useMemo(() => PRODUCT_DETAILS.find((detail) => detail.id === selectedId), [selectedId]);
	const activeSummary = useMemo(() => PRODUCT_SUMMARIES.find((summary) => summary.id === selectedId), [selectedId]);

	return (
		<div className="space-y-4">
			<div className="flex flex-wrap items-center gap-2 rounded-md border border-amber-300/60 bg-amber-50 px-4 py-3 text-sm text-amber-700">
				<Icon icon="solar:shield-keyhole-bold" className="text-amber-500" size={18} />
				数据产品统一依赖策略中心的密级与订阅审批，导出需走导出中心。
				{activeSummary ? (
					<Badge variant="outline" className="border-amber-400 text-amber-700">
						当前：{activeSummary.name} · 状态 {activeSummary.status}
					</Badge>
				) : null}
			</div>

			<div className="grid gap-4 xl:grid-cols-[380px,1fr]">
				<Card className="h-[calc(100vh-260px)]">
					<CardHeader className="space-y-3">
						<CardTitle className="text-base">数据产品</CardTitle>
						<Input placeholder="搜索产品 / 数据集" value={search} onChange={(event) => setSearch(event.target.value)} />
					</CardHeader>
					<CardContent className="p-0">
						<ScrollArea className="h-[calc(100vh-360px)]">
							<table className="w-full text-sm">
								<thead className="sticky top-0 bg-muted/20 text-left text-xs uppercase text-muted-foreground">
									<tr>
										<th className="px-4 py-2">产品名</th>
										<th className="px-4 py-2">类型</th>
										<th className="px-4 py-2">绑定数据集</th>
										<th className="px-4 py-2">版本</th>
										<th className="px-4 py-2">SLA</th>
										<th className="px-4 py-2">最近产出</th>
										<th className="px-4 py-2">订阅数</th>
										<th className="px-4 py-2 text-right">状态</th>
									</tr>
								</thead>
								<tbody>
									{filteredProducts.map((product) => (
										<tr
											key={product.id}
											className={clsx("cursor-pointer border-b border-border/40 last:border-none", {
												"bg-primary/10": product.id === selectedId,
											})}
											onClick={() => setSelectedId(product.id)}
										>
											<td className="px-4 py-3 font-medium">{product.name}</td>
											<td className="px-4 py-3">
												<Badge variant="secondary">{product.type}</Badge>
											</td>
											<td className="px-4 py-3 text-xs text-muted-foreground">{product.datasets.join(", ")}</td>
											<td className="px-4 py-3 text-xs">{product.version}</td>
											<td className="px-4 py-3 text-xs">{product.sla}</td>
											<td className="px-4 py-3 text-xs text-muted-foreground">{product.lastProducedAt}</td>
											<td className="px-4 py-3 text-xs">{product.subscriptions}</td>
											<td className="px-4 py-3 text-right">
												<Badge variant={STATUS_VARIANTS[product.status].variant}>
													{STATUS_VARIANTS[product.status].label}
												</Badge>
											</td>
										</tr>
									))}
								</tbody>
							</table>
						</ScrollArea>
					</CardContent>
				</Card>

				{activeProduct && activeSummary ? (
					<Card className="h-[calc(100vh-260px)]">
						<CardHeader className="flex flex-col gap-2 md:flex-row md:justify-between md:items-center">
							<div>
								<CardTitle className="text-xl font-semibold">{activeSummary.name}</CardTitle>
								<p className="text-sm text-muted-foreground">
									{activeSummary.type} · 当前版本 {activeSummary.version} · SLA {activeSummary.sla}
								</p>
							</div>
							<div className="flex gap-2">
								<Button variant="outline">
									<Icon icon="mdi:bell-ring-outline" className="mr-1" size={16} />
									订阅动态
								</Button>
								<Button>
									<Icon icon="mdi:rocket-launch" className="mr-1" size={16} />
									发起订阅
								</Button>
							</div>
						</CardHeader>
						<CardContent className="flex h-full flex-col">
							<Tabs defaultValue="schema" className="flex h-full flex-col">
								<TabsList className="w-full justify-start">
									<TabsTrigger value="schema">Schema</TabsTrigger>
									<TabsTrigger value="sla">SLA</TabsTrigger>
									<TabsTrigger value="versions">版本</TabsTrigger>
									<TabsTrigger value="consume">消费方式</TabsTrigger>
								</TabsList>
								<TabsContent value="schema" className="flex-1 overflow-hidden">
									<ScrollArea className="h-full">
										<div className="space-y-4 py-4">
											<div className="space-y-2">
												<h3 className="text-sm font-semibold">字段定义</h3>
												<table className="w-full text-sm">
													<thead className="bg-muted/40 text-left text-xs uppercase text-muted-foreground">
														<tr>
															<th className="px-3 py-2">字段</th>
															<th className="px-3 py-2">类型</th>
															<th className="px-3 py-2">业务术语</th>
															<th className="px-3 py-2">脱敏</th>
														</tr>
													</thead>
													<tbody>
														{activeProduct.fields.map((field) => (
															<tr key={field.name} className="border-b border-border/40 last:border-none">
																<td className="px-3 py-2">
																	<div className="font-medium">{field.name}</div>
																	{field.description ? (
																		<div className="text-xs text-muted-foreground">{field.description}</div>
																	) : null}
																</td>
																<td className="px-3 py-2 text-xs text-muted-foreground">{field.type}</td>
																<td className="px-3 py-2 text-xs">{field.term ?? "--"}</td>
																<td className="px-3 py-2">
																	<Badge variant={field.masked ? "secondary" : "outline"}>
																		{field.masked ? "已脱敏" : "原始"}
																	</Badge>
																</td>
															</tr>
														))}
													</tbody>
												</table>
											</div>
											<Separator />
											<div className="grid gap-4 md:grid-cols-2">
												<Card>
													<CardHeader>
														<CardTitle className="text-sm">血缘摘要</CardTitle>
													</CardHeader>
													<CardContent className="text-sm text-muted-foreground">
														{activeProduct.bloodlineSummary}
													</CardContent>
												</Card>
												<Card>
													<CardHeader>
														<CardTitle className="text-sm">分级 / 脱敏策略</CardTitle>
													</CardHeader>
													<CardContent className="space-y-2 text-sm text-muted-foreground">
														<p>分级：{activeProduct.classificationStrategy}</p>
														<p>脱敏：{activeProduct.maskingStrategy}</p>
													</CardContent>
												</Card>
											</div>
										</div>
									</ScrollArea>
								</TabsContent>
								<TabsContent value="sla" className="flex-1 overflow-auto py-4">
									<div className="grid gap-4 md:grid-cols-3">
										<Card>
											<CardHeader>
												<CardTitle className="text-sm">刷新周期</CardTitle>
											</CardHeader>
											<CardContent className="text-sm text-muted-foreground">
												{activeProduct.refreshFrequency}
											</CardContent>
										</Card>
										<Card>
											<CardHeader>
												<CardTitle className="text-sm">时延目标</CardTitle>
											</CardHeader>
											<CardContent className="text-sm text-muted-foreground">
												{activeProduct.latencyObjective}
											</CardContent>
										</Card>
										<Card>
											<CardHeader>
												<CardTitle className="text-sm">失败策略</CardTitle>
											</CardHeader>
											<CardContent className="text-sm text-muted-foreground">{activeProduct.failurePolicy}</CardContent>
										</Card>
									</div>
								</TabsContent>

								<TabsContent value="versions" className="flex-1 overflow-hidden py-4">
									<ScrollArea className="h-full">
										<div className="space-y-3">
											{activeProduct.versions.map((version) => (
												<Card
													key={version.version}
													className={clsx({ "border-primary": version.status === "current" })}
												>
													<CardHeader className="flex flex-row items-center justify-between">
														<div>
															<CardTitle className="text-sm">版本 {version.version}</CardTitle>
															<p className="text-xs text-muted-foreground">发布于 {version.releasedAt}</p>
														</div>
														<div className="flex gap-2">
															{version.status === "gray" ? (
																<Button size="sm" variant="outline">
																	灰度发布
																</Button>
															) : null}
															{version.status !== "current" ? (
																<Button size="sm" variant="ghost">
																	回滚到此版本
																</Button>
															) : null}
														</div>
													</CardHeader>
													<CardContent className="text-sm text-muted-foreground">{version.diffSummary}</CardContent>
												</Card>
											))}
										</div>
									</ScrollArea>
								</TabsContent>

								<TabsContent value="consume" className="flex-1 overflow-auto py-4">
									<div className="grid gap-4 md:grid-cols-3">
										<Card>
											<CardHeader>
												<CardTitle className="flex items-center gap-2 text-sm">
													<Icon icon="mdi:cloud" size={16} /> REST API
												</CardTitle>
											</CardHeader>
											<CardContent className="space-y-2 text-sm text-muted-foreground">
												<p>Endpoint：{activeProduct.consumption.rest.endpoint}</p>
												<p>认证：{activeProduct.consumption.rest.auth}</p>
											</CardContent>
										</Card>
										<Card>
											<CardHeader>
												<CardTitle className="flex items-center gap-2 text-sm">
													<Icon icon="mdi:database" size={16} /> JDBC / SQL
												</CardTitle>
											</CardHeader>
											<CardContent className="space-y-2 text-sm text-muted-foreground">
												<p>Driver：{activeProduct.consumption.jdbc.driver}</p>
												<p>URL：{activeProduct.consumption.jdbc.url}</p>
											</CardContent>
										</Card>
										<Card>
											<CardHeader>
												<CardTitle className="flex items-center gap-2 text-sm">
													<Icon icon="mdi:folder" size={16} /> 文件投递
												</CardTitle>
											</CardHeader>
											<CardContent className="space-y-2 text-sm text-muted-foreground">
												<p>对象存储：{activeProduct.consumption.file.objectStorePath}</p>
												<p>共享目录：{activeProduct.consumption.file.sharedPath}</p>
												<p>格式：{activeProduct.consumption.file.formats.join(" / ")}</p>
											</CardContent>
										</Card>
									</div>
								</TabsContent>
							</Tabs>
						</CardContent>
					</Card>
				) : (
					<Card className="flex h-[calc(100vh-260px)] items-center justify-center text-sm text-muted-foreground">
						请选择左侧数据产品查看详情
					</Card>
				)}
			</div>
		</div>
	);
}
