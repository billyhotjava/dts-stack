import { Chart, useChart } from "@/components/chart";
import { useBilingualText } from "@/hooks/useBilingualText";
import { Badge } from "@/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Text, Title } from "@/ui/typography";

const assetCategories = [
	{ key: "public", value: 320, change: 12 },
	{ key: "internal", value: 540, change: 24 },
	{ key: "confidential", value: 210, change: -3 },
	{ key: "secret", value: 128, change: 5 },
];

const assetList = [
	{ name: "销售订单数据集", owner: "销售分析部", category: "internal", updatedAt: "2024-03-18", records: 128000 },
	{ name: "用户满意度调研", owner: "市场洞察组", category: "public", updatedAt: "2024-03-16", records: 54000 },
	{ name: "风险预警规则库", owner: "风控策略组", category: "confidential", updatedAt: "2024-03-12", records: 8600 },
	{ name: "核心账户台账", owner: "核心账务部", category: "secret", updatedAt: "2024-03-08", records: 4200 },
	{ name: "产品设计蓝图", owner: "硬件创新中心", category: "confidential", updatedAt: "2024-03-05", records: 2600 },
];

export default function DataSecurityPage() {
	const t = useBilingualText();
 	const translate = (key: string, fallback: string) => {
		const value = t(key).trim();
		return value.length > 0 ? value : fallback;
	};

	const donutOptions = useChart({
		chart: { type: "donut", toolbar: { show: false } },
		labels: assetCategories.map((item) => translate(`sys.dataSecurity.category.${item.key}`, item.key)),
		legend: { position: "bottom" },
		dataLabels: { enabled: true, formatter: (val: number) => `${val.toFixed(1)}%` },
		plotOptions: { pie: { donut: { size: "65%" } } },
		colors: ["#22c55e", "#3b82f6", "#f59e0b", "#ef4444"],
	});

	return (
		<div className="flex flex-col gap-6">
			<Card>
				<CardHeader className="flex flex-col gap-2 md:flex-row md:items-center md:justify-between">
					<div>
						<Title as="h2" className="text-2xl font-semibold">
							{translate("sys.dataSecurity.title", "数据安全总览")}
						</Title>
						<Text variant="body2" className="text-muted-foreground">
							{translate("sys.dataSecurity.subtitle", "掌握资产敏感级别与访问态势")}
						</Text>
					</div>
				</CardHeader>
				<CardContent className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
					{assetCategories.map((category) => {
						const valueText = translate(`sys.dataSecurity.category.${category.key}`, category.key);
						const change = category.change;
						const trendColor = change >= 0 ? "text-success-dark" : "text-destructive";
						return (
							<div key={category.key} className="rounded-lg border p-4">
								<Text variant="body2" className="text-muted-foreground">
									{valueText}
								</Text>
								<Title as="h3" className="text-2xl font-semibold">
									{category.value.toLocaleString()}
								</Title>
								<Text variant="body3" className={`${trendColor} font-medium`}>
									{change >= 0 ? "▲" : "▼"} {Math.abs(change)}%
								</Text>
							</div>
						);
					})}
				</CardContent>
			</Card>

			<div className="grid gap-6 lg:grid-cols-[minmax(0,0.45fr)_minmax(0,1fr)]">
				<Card>
					<CardHeader>
						<CardTitle className="text-base font-semibold">
							{translate("sys.dataSecurity.distributionTitle", "资产分布")}
						</CardTitle>
					</CardHeader>
					<CardContent>
						<Chart
							type="donut"
							height={280}
							series={assetCategories.map((item) => item.value)}
							options={donutOptions}
						/>
					</CardContent>
				</Card>

				<Card>
					<CardHeader>
						<CardTitle className="text-base font-semibold">
							{translate("sys.dataSecurity.listTitle", "数据资产列表")}
						</CardTitle>
						<Text variant="body3" className="text-muted-foreground">
							{translate("sys.dataSecurity.listSubtitle", "根据最新同步时间排序")}
						</Text>
					</CardHeader>
					<CardContent className="overflow-x-auto">
						<table className="min-w-full text-sm">
							<thead className="text-left text-muted-foreground border-b">
								<tr>
									<th className="py-2 pr-4 font-medium">{translate("sys.dataSecurity.assetName", "资产名称")}</th>
									<th className="py-2 pr-4 font-medium">{translate("sys.dataSecurity.assetOwner", "负责人")}</th>
									<th className="py-2 pr-4 font-medium">{translate("sys.dataSecurity.assetCategory", "分类")}</th>
									<th className="py-2 pr-4 font-medium text-right">{translate("sys.dataSecurity.assetRecords", "记录数")}</th>
									<th className="py-2 pr-0 font-medium">{translate("sys.dataSecurity.assetUpdatedAt", "最近更新")}</th>
								</tr>
							</thead>
							<tbody>
								{assetList.map((asset) => (
									<tr key={asset.name} className="border-b last:border-none">
										<td className="py-2 pr-4 font-medium">{asset.name}</td>
										<td className="py-2 pr-4 text-muted-foreground">{asset.owner}</td>
									<td className="py-2 pr-4">
										<Badge variant={categoryVariant(asset.category)}>
											{translate(`sys.dataSecurity.category.${asset.category}`, asset.category)}
										</Badge>
									</td>
										<td className="py-2 pr-4 text-right">{asset.records.toLocaleString()}</td>
										<td className="py-2 pr-0 text-muted-foreground">{asset.updatedAt}</td>
									</tr>
								))}
							</tbody>
						</table>
					</CardContent>
				</Card>
			</div>
		</div>
	);
}

function categoryVariant(category: string) {
	switch (category) {
		case "public":
			return "success" as const;
		case "internal":
			return "info" as const;
		case "confidential":
			return "warning" as const;
		case "secret":
			return "destructive" as const;
		default:
			return "secondary" as const;
	}
}
