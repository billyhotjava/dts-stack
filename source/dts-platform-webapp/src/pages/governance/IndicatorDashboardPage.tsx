import { useEffect, useState, useCallback } from "react";
import {
	Alert,
	Col,
	DatePicker,
	Descriptions,
	Drawer,
	Empty,
	Row,
	Select,
	Spin,
	Table,
	Tabs,
	Tag,
	Typography,
} from "antd";
import { toast } from "sonner";
import {
	getIndicatorDashboard,
	getIndicatorDetail,
	getIndicatorDrilldown,
} from "@/api/platformApi";
import IndicatorCard from "./components/IndicatorCard";
import type { IndicatorCardData } from "./components/IndicatorCard";

const { Title, Text } = Typography;

const DOMAIN_TABS = [
	{ key: "", label: "All" },
	{ key: "FINANCE", label: "Finance" },
	{ key: "PROJECT_MGMT", label: "Project Mgmt" },
	{ key: "PLM", label: "PLM" },
	{ key: "HR", label: "HR" },
];

const ALERT_COLORS: Record<string, string> = {
	RED: "#f5222d",
	YELLOW: "#faad14",
	GREEN: "#52c41a",
};

interface AlertSummary {
	red: number;
	yellow: number;
	green: number;
	noData: number;
}

interface DetailData {
	indicator: Record<string, any>;
	trend: { date: string; value: number; alertLevel?: string }[];
	history: Record<string, any>[];
}

export default function IndicatorDashboardPage() {
	const [domain, setDomain] = useState("");
	const [loading, setLoading] = useState(false);
	const [indicators, setIndicators] = useState<IndicatorCardData[]>([]);
	const [alertSummary, setAlertSummary] = useState<AlertSummary>({
		red: 0,
		yellow: 0,
		green: 0,
		noData: 0,
	});
	const [alertFilterLevel, setAlertFilterLevel] = useState<string | null>(null);

	// Drawer state
	const [drawerOpen, setDrawerOpen] = useState(false);
	const [detailLoading, setDetailLoading] = useState(false);
	const [detailData, setDetailData] = useState<DetailData | null>(null);
	const [selectedId, setSelectedId] = useState<string | null>(null);

	// Drilldown state
	const [drillDimension, setDrillDimension] = useState("");
	const [drillPeriod, setDrillPeriod] = useState<string | undefined>();
	const [drillLoading, setDrillLoading] = useState(false);
	const [drillRows, setDrillRows] = useState<Record<string, any>[]>([]);

	const fetchDashboard = useCallback(async () => {
		setLoading(true);
		try {
			const res: any = await getIndicatorDashboard({
				domain: domain || undefined,
				days: 30,
			});
			const data = res?.data ?? res;
			setIndicators(data.indicators ?? []);
			setAlertSummary(data.alertSummary ?? { red: 0, yellow: 0, green: 0, noData: 0 });
		} catch {
			toast.error("Failed to load indicator dashboard");
		} finally {
			setLoading(false);
		}
	}, [domain]);

	useEffect(() => {
		fetchDashboard();
	}, [fetchDashboard]);

	const openDetail = async (id: string) => {
		setSelectedId(id);
		setDrawerOpen(true);
		setDetailLoading(true);
		setDetailData(null);
		setDrillRows([]);
		setDrillDimension("");
		setDrillPeriod(undefined);
		try {
			const res: any = await getIndicatorDetail(id, { days: 30 });
			setDetailData(res?.data ?? res);
		} catch {
			toast.error("Failed to load indicator detail");
		} finally {
			setDetailLoading(false);
		}
	};

	const runDrilldown = async () => {
		if (!selectedId || !drillDimension) return;
		setDrillLoading(true);
		try {
			const res: any = await getIndicatorDrilldown(selectedId, {
				dimension: drillDimension,
				period: drillPeriod,
			});
			setDrillRows(res?.data ?? res ?? []);
		} catch {
			toast.error("Drilldown query failed");
		} finally {
			setDrillLoading(false);
		}
	};

	// Filter by alert level when user clicks alert summary
	const displayedIndicators = alertFilterLevel
		? indicators.filter((ind) => ind.alertLevel === alertFilterLevel)
		: indicators;

	return (
		<div style={{ padding: 16, height: "100%", overflow: "auto" }}>
			<Title level={4} style={{ marginBottom: 16 }}>
				Indicator Dashboard
			</Title>

			<Tabs
				activeKey={domain}
				onChange={(key) => {
					setDomain(key);
					setAlertFilterLevel(null);
				}}
				items={DOMAIN_TABS.map((t) => ({ key: t.key, label: t.label }))}
				style={{ marginBottom: 12 }}
			/>

			{/* Alert summary bar */}
			<Alert
				type="info"
				showIcon={false}
				style={{ marginBottom: 16, cursor: "pointer" }}
				message={
					<div style={{ display: "flex", gap: 24, alignItems: "center" }}>
						<span style={{ fontWeight: 500 }}>Alert Summary:</span>
						<span
							style={{
								color: "#f5222d",
								cursor: "pointer",
								fontWeight: alertFilterLevel === "RED" ? 700 : 400,
								textDecoration: alertFilterLevel === "RED" ? "underline" : "none",
							}}
							onClick={() =>
								setAlertFilterLevel(alertFilterLevel === "RED" ? null : "RED")
							}
						>
							Red: {alertSummary.red}
						</span>
						<span
							style={{
								color: "#faad14",
								cursor: "pointer",
								fontWeight: alertFilterLevel === "YELLOW" ? 700 : 400,
								textDecoration: alertFilterLevel === "YELLOW" ? "underline" : "none",
							}}
							onClick={() =>
								setAlertFilterLevel(alertFilterLevel === "YELLOW" ? null : "YELLOW")
							}
						>
							Yellow: {alertSummary.yellow}
						</span>
						<span
							style={{
								color: "#52c41a",
								cursor: "pointer",
								fontWeight: alertFilterLevel === "GREEN" ? 700 : 400,
								textDecoration: alertFilterLevel === "GREEN" ? "underline" : "none",
							}}
							onClick={() =>
								setAlertFilterLevel(alertFilterLevel === "GREEN" ? null : "GREEN")
							}
						>
							Green: {alertSummary.green}
						</span>
						<span
							style={{
								color: "#999",
								cursor: "pointer",
								fontWeight: alertFilterLevel === null && false ? 700 : 400,
							}}
							onClick={() => setAlertFilterLevel(null)}
						>
							No Data: {alertSummary.noData}
						</span>
						{alertFilterLevel && (
							<a
								style={{ marginLeft: 8, fontSize: 12 }}
								onClick={(e) => {
									e.stopPropagation();
									setAlertFilterLevel(null);
								}}
							>
								Clear filter
							</a>
						)}
					</div>
				}
			/>

			{loading ? (
				<div style={{ textAlign: "center", padding: 60 }}>
					<Spin />
				</div>
			) : displayedIndicators.length === 0 ? (
				<Empty description="No indicators found" />
			) : (
				<Row gutter={[16, 16]}>
					{displayedIndicators.map((ind) => (
						<Col key={ind.id} xs={24} sm={12} md={8} lg={6} xl={6}>
							<IndicatorCard
								indicator={ind}
								onClick={() => openDetail(ind.id)}
							/>
						</Col>
					))}
				</Row>
			)}

			{/* Detail Drawer */}
			<Drawer
				title={detailData?.indicator?.name ?? "Indicator Detail"}
				open={drawerOpen}
				onClose={() => setDrawerOpen(false)}
				width={720}
				destroyOnClose
			>
				{detailLoading ? (
					<div style={{ textAlign: "center", padding: 40 }}>
						<Spin />
					</div>
				) : detailData ? (
					<div style={{ display: "flex", flexDirection: "column", gap: 24 }}>
						{/* Metadata */}
						<Descriptions
							column={2}
							bordered
							size="small"
							title="Indicator Metadata"
						>
							<Descriptions.Item label="Code">
								{detailData.indicator.code}
							</Descriptions.Item>
							<Descriptions.Item label="Domain">
								{detailData.indicator.domain}
							</Descriptions.Item>
							<Descriptions.Item label="Category">
								{detailData.indicator.category}
							</Descriptions.Item>
							<Descriptions.Item label="Unit">
								{detailData.indicator.unit}
							</Descriptions.Item>
							<Descriptions.Item label="Direction">
								{detailData.indicator.direction}
							</Descriptions.Item>
							<Descriptions.Item label="Status">
								<Tag>{detailData.indicator.status}</Tag>
							</Descriptions.Item>
							<Descriptions.Item label="Current Value">
								<span style={{ fontSize: 18, fontWeight: 700 }}>
									{detailData.indicator.currentValue ?? "--"}
								</span>
							</Descriptions.Item>
							<Descriptions.Item label="Alert Level">
								{detailData.indicator.alertLevel ? (
									<Tag
										color={
											ALERT_COLORS[detailData.indicator.alertLevel] ?? undefined
										}
									>
										{detailData.indicator.alertLevel}
									</Tag>
								) : (
									"--"
								)}
							</Descriptions.Item>
							<Descriptions.Item label="Threshold">
								{[
									detailData.indicator.thresholdMin != null
										? `Min: ${detailData.indicator.thresholdMin}`
										: null,
									detailData.indicator.thresholdMax != null
										? `Max: ${detailData.indicator.thresholdMax}`
										: null,
								]
									.filter(Boolean)
									.join(" / ") || "--"}
							</Descriptions.Item>
							<Descriptions.Item label="Owner">
								{detailData.indicator.owner ?? "--"}
							</Descriptions.Item>
							<Descriptions.Item label="Source Table" span={2}>
								{detailData.indicator.sourceTable ?? "--"}
							</Descriptions.Item>
							{detailData.indicator.definition && (
								<Descriptions.Item label="Definition" span={2}>
									{detailData.indicator.definition}
								</Descriptions.Item>
							)}
						</Descriptions>

						{/* Trend Chart (div bars) */}
						<div>
							<Text strong style={{ display: "block", marginBottom: 8 }}>
								Trend (Last 30 Days)
							</Text>
							{detailData.trend && detailData.trend.length > 0 ? (
								<TrendBarChart trend={detailData.trend} />
							) : (
								<Empty
									description="No trend data"
									image={Empty.PRESENTED_IMAGE_SIMPLE}
								/>
							)}
						</div>

						{/* Drilldown */}
						<div>
							<Text strong style={{ display: "block", marginBottom: 8 }}>
								Dimension Drilldown
							</Text>
							<div
								style={{
									display: "flex",
									gap: 8,
									marginBottom: 8,
									flexWrap: "wrap",
								}}
							>
								<Select
									placeholder="Select dimension"
									style={{ width: 180 }}
									value={drillDimension || undefined}
									onChange={(v) => setDrillDimension(v)}
									options={[
										{ value: "department", label: "Department" },
										{ value: "project", label: "Project" },
										{ value: "category", label: "Category" },
										{ value: "region", label: "Region" },
										{ value: "product", label: "Product" },
									]}
									allowClear
								/>
								<DatePicker
									picker="month"
									onChange={(_d, dateStr) =>
										setDrillPeriod(
											typeof dateStr === "string" && dateStr
												? dateStr
												: undefined,
										)
									}
									style={{ width: 160 }}
								/>
								<a
									style={{
										lineHeight: "32px",
										cursor:
											drillDimension ? "pointer" : "not-allowed",
										color: drillDimension ? "#1677ff" : "#ccc",
									}}
									onClick={() => drillDimension && runDrilldown()}
								>
									Query
								</a>
							</div>
							{drillLoading ? (
								<Spin />
							) : drillRows.length > 0 ? (
								<Table
									size="small"
									dataSource={drillRows}
									rowKey={(_, i) => String(i)}
									pagination={{ pageSize: 10 }}
									columns={Object.keys(drillRows[0]).map((key) => ({
										title: key,
										dataIndex: key,
										key,
										render: (v: any) =>
											v != null ? String(v) : "--",
									}))}
								/>
							) : (
								drillDimension && (
									<Empty
										description="No drilldown data"
										image={Empty.PRESENTED_IMAGE_SIMPLE}
									/>
								)
							)}
						</div>

						{/* Run History */}
						<div>
							<Text strong style={{ display: "block", marginBottom: 8 }}>
								Run History
							</Text>
							{detailData.history && detailData.history.length > 0 ? (
								<Table
									size="small"
									dataSource={detailData.history}
									rowKey="id"
									pagination={{ pageSize: 8 }}
									columns={[
										{
											title: "Run At",
											dataIndex: "runAt",
											width: 170,
											render: (v: string) =>
												v
													? new Date(v).toLocaleString()
													: "--",
										},
										{
											title: "Status",
											dataIndex: "status",
											width: 80,
											render: (v: string) => (
												<Tag
													color={
														v === "SUCCESS"
															? "green"
															: v === "FAILED"
																? "red"
																: "default"
													}
												>
													{v}
												</Tag>
											),
										},
										{
											title: "Value",
											dataIndex: "computedValue",
											width: 100,
											render: (v: any) =>
												v != null ? String(v) : "--",
										},
										{
											title: "Change",
											dataIndex: "changeRate",
											width: 80,
											render: (v: any) =>
												v != null
													? `${(v * 100).toFixed(1)}%`
													: "--",
										},
										{
											title: "Alert",
											dataIndex: "alertLevel",
											width: 70,
											render: (v: string) =>
												v ? (
													<Tag
														color={
															ALERT_COLORS[v] ?? undefined
														}
													>
														{v}
													</Tag>
												) : (
													"--"
												),
										},
										{
											title: "Duration",
											dataIndex: "durationMs",
											width: 80,
											render: (v: number) =>
												v != null ? `${v}ms` : "--",
										},
										{
											title: "Error",
											dataIndex: "errorMessage",
											ellipsis: true,
											render: (v: string) => v ?? "--",
										},
									]}
								/>
							) : (
								<Empty
									description="No run history"
									image={Empty.PRESENTED_IMAGE_SIMPLE}
								/>
							)}
						</div>
					</div>
				) : (
					<Empty description="Failed to load detail" />
				)}
			</Drawer>
		</div>
	);
}

/** Simple div-based bar chart for trend data */
function TrendBarChart({
	trend,
}: {
	trend: { date: string; value: number; alertLevel?: string }[];
}) {
	const values = trend.map((t) => t.value ?? 0);
	const maxVal = Math.max(...values, 1);
	const chartHeight = 120;

	return (
		<div
			style={{
				display: "flex",
				alignItems: "flex-end",
				gap: 2,
				height: chartHeight,
				padding: "0 4px",
				borderBottom: "1px solid #eee",
			}}
		>
			{trend.map((point, i) => {
				const h =
					maxVal > 0
						? Math.max(3, ((point.value ?? 0) / maxVal) * (chartHeight - 10))
						: 3;
				const color =
					ALERT_COLORS[point.alertLevel ?? ""] ?? "#1677ff";
				return (
					<div
						key={i}
						style={{
							flex: 1,
							height: h,
							backgroundColor: color,
							borderRadius: "2px 2px 0 0",
							minWidth: 4,
							maxWidth: 20,
							opacity: 0.8,
							transition: "height 0.2s",
						}}
						title={`${point.date}\nValue: ${point.value}`}
					/>
				);
			})}
		</div>
	);
}
