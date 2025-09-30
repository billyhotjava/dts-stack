import { Chart, useChart } from "@/components/chart";
import { GLOBAL_CONFIG } from "@/global-config";
import { useBilingualText } from "@/hooks/useBilingualText";
import { Badge } from "@/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Text, Title } from "@/ui/typography";

const timeline = ["09:00", "09:10", "09:20", "09:30", "09:40", "09:50", "10:00", "10:10", "10:20", "10:30", "10:40", "10:50"];

const resourceSeries = [
	{
		key: "cpu",
		color: "#22c55e",
		seriesName: "CPU",
		values: [48, 52, 49, 58, 62, 65, 61, 67, 63, 60, 58, 55],
	},
	{
		key: "memory",
		color: "#3b82f6",
		seriesName: "Memory",
		values: [56, 59, 61, 64, 67, 69, 70, 73, 75, 74, 72, 70],
	},
	{
		key: "storage",
		color: "#f97316",
		seriesName: "Storage",
		values: [62, 64, 66, 65, 68, 70, 72, 74, 76, 78, 79, 80],
	},
];

const clusterSummary = {
	state: "healthy",
	uptime: "12d 6h",
	nodes: 24,
	jobs: 128,
};

const nodeList = [
	{ name: "主节点-01", role: "master", cpu: 58, memory: 62, status: "healthy" },
	{ name: "计算节点-12", role: "worker", cpu: 71, memory: 69, status: "healthy" },
	{ name: "计算节点-18", role: "worker", cpu: 82, memory: 77, status: "warning" },
	{ name: "计算节点-07", role: "worker", cpu: 64, memory: 59, status: "healthy" },
	{ name: "计算节点-22", role: "worker", cpu: 49, memory: 55, status: "healthy" },
];

export default function Workbench() {
	const t = useBilingualText();
	const translate = (key: string, fallback: string) => {
		const value = t(key).trim();
		return value.length > 0 ? value : fallback;
	};

	const cpuChartOptions = useChart({
		chart: { toolbar: { show: false }, height: 240 },
		stroke: { curve: "smooth", width: 3 },
		colors: [resourceSeries[0].color],
		dataLabels: { enabled: false },
		xaxis: { categories: timeline, labels: { show: true } },
		yaxis: { labels: { formatter: (value: number) => `${value}%` } },
		grid: { strokeDashArray: 4 },
		legend: { show: false },
		tooltip: { y: { formatter: (value: number) => `${value.toFixed(1)}%` } },
	});

	const memoryChartOptions = useChart({
		chart: { toolbar: { show: false }, height: 240 },
		stroke: { curve: "smooth", width: 3 },
		colors: [resourceSeries[1].color],
		dataLabels: { enabled: false },
		xaxis: { categories: timeline, labels: { show: true } },
		yaxis: { labels: { formatter: (value: number) => `${value}%` } },
		grid: { strokeDashArray: 4 },
		legend: { show: false },
		tooltip: { y: { formatter: (value: number) => `${value.toFixed(1)}%` } },
	});

	const storageChartOptions = useChart({
		chart: { toolbar: { show: false }, height: 240 },
		stroke: { curve: "smooth", width: 3 },
		colors: [resourceSeries[2].color],
		dataLabels: { enabled: false },
		xaxis: { categories: timeline, labels: { show: true } },
		yaxis: { labels: { formatter: (value: number) => `${value}%` } },
		grid: { strokeDashArray: 4 },
		legend: { show: false },
		tooltip: { y: { formatter: (value: number) => `${value.toFixed(1)}%` } },
	});

	const chartConfigs = [
		{ ...resourceSeries[0], options: cpuChartOptions },
		{ ...resourceSeries[1], options: memoryChartOptions },
		{ ...resourceSeries[2], options: storageChartOptions },
	];

	return (
		<div className="flex flex-col gap-6">
			<Card>
				<CardHeader className="flex flex-col gap-2 md:flex-row md:items-center md:justify-between">
					<div>
					<Title as="h2" className="text-2xl font-semibold">
						{translate("sys.workbench.title", "集群概览")}
					</Title>
						<Text variant="body2" className="text-muted-foreground">
							{GLOBAL_CONFIG.appName}
						</Text>
					</div>
					<Badge variant={clusterSummary.state === "healthy" ? "success" : "secondary"}>
						{clusterSummary.state === "healthy"
							? translate("sys.workbench.statusHealthy", "运行正常")
							: translate("sys.workbench.statusWarning", "性能预警")}
					</Badge>
				</CardHeader>
				<CardContent className="grid gap-4 md:grid-cols-3">
					<div className="rounded-lg border p-4">
					<Text variant="body2" className="text-muted-foreground">
						{translate("sys.workbench.uptime", "运行时长")}
					</Text>
						<Title as="h3" className="text-xl font-semibold">
							{clusterSummary.uptime}
						</Title>
					</div>
					<div className="rounded-lg border p-4">
					<Text variant="body2" className="text-muted-foreground">
						{translate("sys.workbench.nodes", "在线节点")}
					</Text>
						<Title as="h3" className="text-xl font-semibold">
							{clusterSummary.nodes}
						</Title>
					</div>
					<div className="rounded-lg border p-4">
					<Text variant="body2" className="text-muted-foreground">
						{translate("sys.workbench.jobs", "运行任务")}
					</Text>
						<Title as="h3" className="text-xl font-semibold">
							{clusterSummary.jobs}
						</Title>
					</div>
				</CardContent>
			</Card>

			<div className="grid gap-4 md:grid-cols-3">
				{chartConfigs.map((resource) => (
					<Card key={resource.key} className="flex flex-col">
							<CardHeader>
						<CardTitle className="text-base font-semibold">
							{translate(`sys.workbench.${resource.key}Load`, resource.seriesName)}
						</CardTitle>
						<Text variant="body3" className="text-muted-foreground">
							{translate("sys.workbench.timelineLabel", "时间线")}
						</Text>
						</CardHeader>
							<CardContent className="flex-1">
								<Chart
									type="line"
								height={260}
								series={[{ name: resource.seriesName, data: resource.values }]}
								options={resource.options}
							/>
						</CardContent>
					</Card>
				))}
			</div>

			<Card>
				<CardHeader>
					<CardTitle className="text-base font-semibold">
						{translate("sys.workbench.nodeTableTitle", "节点概览")}
					</CardTitle>
					<Text variant="body3" className="text-muted-foreground">
						{translate("sys.workbench.nodeTableSubtitle", "最近 5 个采样节点负载")}
					</Text>
				</CardHeader>
				<CardContent className="overflow-x-auto">
					<table className="min-w-full text-sm">
						<thead className="text-left text-muted-foreground border-b">
							<tr>
								<th className="py-2 pr-4 font-medium">{translate("sys.workbench.nodeName", "节点")}</th>
								<th className="py-2 pr-4 font-medium">{translate("sys.workbench.nodeRole", "角色")}</th>
								<th className="py-2 pr-4 font-medium">{translate("sys.workbench.nodeCpu", "CPU 使用率")}</th>
								<th className="py-2 pr-4 font-medium">{translate("sys.workbench.nodeMemory", "内存使用率")}</th>
								<th className="py-2 pr-4 font-medium">{translate("sys.workbench.nodeStatus", "状态")}</th>
							</tr>
						</thead>
						<tbody>
							{nodeList.map((node) => (
								<tr key={node.name} className="border-b last:border-none">
									<td className="py-2 pr-4 font-medium">{node.name}</td>
									<td className="py-2 pr-4 text-muted-foreground">
										{node.role === "master"
											? translate("sys.workbench.roleMaster", "主节点")
											: translate("sys.workbench.roleWorker", "工作节点")}
									</td>
									<td className="py-2 pr-4">{node.cpu}%</td>
									<td className="py-2 pr-4">{node.memory}%</td>
									<td className="py-2 pr-4">
										<Badge variant={node.status === "healthy" ? "success" : "warning"}>
											{node.status === "healthy"
												? translate("sys.workbench.statusHealthy", "运行正常")
												: translate("sys.workbench.statusWarning", "性能预警")}
										</Badge>
									</td>
								</tr>
							))}
						</tbody>
					</table>
				</CardContent>
			</Card>
		</div>
	);
}
