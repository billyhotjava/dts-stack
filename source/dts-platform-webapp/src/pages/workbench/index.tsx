import { useEffect, useMemo, useState } from "react";
import { Button, Card, Col, Row, Space, Tag } from "antd";
import { Database, FileCheck, ListTodo, RefreshCw, TrendingUp, Workflow } from "lucide-react";
import { toast } from "sonner";
import workbenchService, {
	type WorkbenchOverview,
	type WorkbenchTodoItem,
} from "@/api/services/workbenchService";
import { useRouter } from "@/routes/hooks";

/* ── helpers ── */

const relativeTime = (value?: string) => {
	if (!value) return "";
	const diff = Date.now() - new Date(value).getTime();
	if (diff < 0 || Number.isNaN(diff)) return value;
	const mins = Math.floor(diff / 60000);
	if (mins < 1) return "刚刚";
	if (mins < 60) return `${mins} 分钟前`;
	const hours = Math.floor(mins / 60);
	if (hours < 24) return `${hours} 小时前`;
	const days = Math.floor(hours / 24);
	return `${days} 天前`;
};

const TODO_COLORS: Record<string, string> = {
	ACCESS_APPROVAL: "#faad14",
	QUALITY: "#1677ff",
	SCHEMA_DRIFT: "#52c41a",
};

const TODO_LABELS: Record<string, string> = {
	ACCESS_APPROVAL: "权限审批",
	QUALITY: "质量工单",
	SCHEMA_DRIFT: "结构漂移",
};

const TODO_TAG_COLORS: Record<string, string> = {
	ACCESS_APPROVAL: "orange",
	QUALITY: "blue",
	SCHEMA_DRIFT: "green",
};

/* ── Mini SVG area chart (pure CSS + SVG, no library) ── */

function MiniAreaChart({ data, color = "#4f6ef7", height = 180 }: { data: number[]; color?: string; height?: number }) {
	if (!data.length) return null;
	const max = Math.max(...data, 1);
	const w = 600;
	const h = height;
	const pad = 20;
	const chartW = w - pad * 2;
	const chartH = h - pad * 2;
	const points = data.map((v, i) => ({
		x: pad + (i / Math.max(data.length - 1, 1)) * chartW,
		y: pad + chartH - (v / max) * chartH,
	}));
	const lineD = points.map((p, i) => `${i === 0 ? "M" : "L"} ${p.x} ${p.y}`).join(" ");
	const areaD = `${lineD} L ${points[points.length - 1].x} ${h - pad} L ${points[0].x} ${h - pad} Z`;
	const gridLines = [0, 0.25, 0.5, 0.75, 1].map((r) => Math.round(max * r));

	return (
		<svg viewBox={`0 0 ${w} ${h}`} style={{ width: "100%", height }} preserveAspectRatio="none">
			{/* grid lines */}
			{gridLines.map((val, i) => {
				const y = pad + chartH - (val / max) * chartH;
				return (
					<g key={i}>
						<line x1={pad} y1={y} x2={w - pad} y2={y} stroke="#f0f0f0" strokeWidth={1} />
						<text x={pad - 4} y={y + 4} textAnchor="end" fontSize={11} fill="#999">{val}</text>
					</g>
				);
			})}
			{/* area fill */}
			<path d={areaD} fill={color} fillOpacity={0.08} />
			{/* line */}
			<path d={lineD} fill="none" stroke={color} strokeWidth={2.5} strokeLinecap="round" strokeLinejoin="round" />
			{/* dots */}
			{points.map((p, i) => (
				<circle key={i} cx={p.x} cy={p.y} r={3} fill="#fff" stroke={color} strokeWidth={2} />
			))}
		</svg>
	);
}

/* ── Mini bar chart ── */

function MiniBarChart({ items, height = 160 }: { items: { label: string; value: number; color: string }[]; height?: number }) {
	const max = Math.max(...items.map((i) => i.value), 1);
	return (
		<div style={{ display: "flex", alignItems: "flex-end", justifyContent: "space-around", height, padding: "0 8px", gap: 12 }}>
			{items.map((item) => {
				const barH = Math.max(4, (item.value / max) * (height - 30));
				return (
					<div key={item.label} style={{ display: "flex", flexDirection: "column", alignItems: "center", flex: 1 }}>
						<span style={{ fontSize: 12, fontWeight: 600, marginBottom: 4 }}>{item.value}</span>
						<div style={{ width: "100%", maxWidth: 48, height: barH, borderRadius: "4px 4px 0 0", background: item.color, transition: "height 0.4s ease" }} />
						<span style={{ fontSize: 11, color: "#999", marginTop: 6, whiteSpace: "nowrap" }}>{item.label}</span>
					</div>
				);
			})}
		</div>
	);
}

/* ── Mini donut chart (CSS conic-gradient) ── */

function MiniDonutChart({ segments, size = 140 }: { segments: { label: string; value: number; color: string }[]; size?: number }) {
	const total = segments.reduce((s, seg) => s + seg.value, 0) || 1;
	let angle = 0;
	const gradientParts: string[] = [];
	for (const seg of segments) {
		const slice = (seg.value / total) * 360;
		gradientParts.push(`${seg.color} ${angle}deg ${angle + slice}deg`);
		angle += slice;
	}
	return (
		<div style={{ display: "flex", alignItems: "center", gap: 20 }}>
			<div style={{
				width: size, height: size, borderRadius: "50%", flexShrink: 0,
				background: `conic-gradient(${gradientParts.join(", ")})`,
				mask: `radial-gradient(circle ${size * 0.32}px at center, transparent 99%, #000 100%)`,
				WebkitMask: `radial-gradient(circle ${size * 0.32}px at center, transparent 99%, #000 100%)`,
			}} />
			<div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
				{segments.map((seg) => (
					<div key={seg.label} style={{ display: "flex", alignItems: "center", gap: 6, fontSize: 12 }}>
						<span style={{ width: 10, height: 10, borderRadius: "50%", background: seg.color, flexShrink: 0 }} />
						<span style={{ color: "#666" }}>{seg.label}</span>
						<span style={{ fontWeight: 600 }}>{seg.value}</span>
					</div>
				))}
			</div>
		</div>
	);
}

/* ── Main page ── */

export default function Page() {
	const { push } = useRouter();
	const [overview, setOverview] = useState<WorkbenchOverview | null>(null);
	const [todos, setTodos] = useState<WorkbenchTodoItem[]>([]);
	const [loading, setLoading] = useState(false);

	const loadAll = async () => {
		setLoading(true);
		try {
			const [summary, todoList] = await Promise.all([
				workbenchService.overview(),
				workbenchService.todos(),
			]);
			setOverview(summary as WorkbenchOverview);
			setTodos(Array.isArray(todoList) ? (todoList as WorkbenchTodoItem[]) : []);
		} catch (error: any) {
			toast.error(error?.message || "工作台数据加载失败");
		} finally {
			setLoading(false);
		}
	};

	useEffect(() => {
		void loadAll();
	}, []);

	/* derived data */

	const todosByType = useMemo(() => {
		const counts: Record<string, number> = { ACCESS_APPROVAL: 0, QUALITY: 0, SCHEMA_DRIFT: 0 };
		for (const item of todos) counts[item.type] = (counts[item.type] || 0) + 1;
		return counts;
	}, [todos]);

	const recentTodos = useMemo(() => todos.slice(0, 5), [todos]);

	const todoPending = useMemo(() => todos.filter((t) => t.status === "PENDING" || t.status === "OPEN").length, [todos]);

	/* asset trend: simulate 7-day from available data (real API would return history) */
	const assetTrend = useMemo(() => {
		const total = overview?.myAssets ?? 0;
		const todayNew = overview?.todayNewAssets ?? 0;
		const base = Math.max(0, total - todayNew * 3);
		return Array.from({ length: 7 }, (_, i) => Math.round(base + ((total - base) * (i + 1)) / 7));
	}, [overview]);

	const DAYS = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];

	/* ── stat cards config ── */
	const statCards = [
		{
			title: "我创建的资产",
			value: overview?.myAssets ?? 0,
			sub: `累计沉淀`,
			icon: <Database className="h-5 w-5" />,
			iconBg: "#eef2ff",
			iconColor: "#4f6ef7",
		},
		{
			title: "今日新增资产",
			value: overview?.todayNewAssets ?? 0,
			sub: todayGrowthLabel(overview),
			icon: <TrendingUp className="h-5 w-5" />,
			iconBg: "#ecfdf5",
			iconColor: "#10b981",
		},
		{
			title: "待办事项",
			value: todos.length,
			sub: todoPending > 0 ? `${todoPending} 条待处理` : "暂无紧急",
			icon: <ListTodo className="h-5 w-5" />,
			iconBg: "#fff7ed",
			iconColor: "#f59e0b",
		},
		{
			title: "质量检查",
			value: todosByType.QUALITY || 0,
			sub: "质量工单数",
			icon: <FileCheck className="h-5 w-5" />,
			iconBg: "#f0f9ff",
			iconColor: "#0ea5e9",
		},
	];

	return (
		<div className="space-y-4" data-testid="platform-workbench-page">
			{/* Header */}
			<div className="flex items-center justify-between">
				<h1 className="text-xl font-semibold">工作台</h1>
				<Space>
					<Button onClick={() => void loadAll()} loading={loading} icon={<RefreshCw className="h-4 w-4" />}>
						刷新
					</Button>
					<Button onClick={() => push("/dashboard/workbench/workflow-center")} icon={<Workflow className="h-4 w-4" />}>
						待办中心
					</Button>
				</Space>
			</div>

			{/* Row 1: Stat cards */}
			<Row gutter={[16, 16]}>
				{statCards.map((card) => (
					<Col key={card.title} xs={12} lg={6}>
						<Card size="small" styles={{ body: { padding: "16px 20px" } }}>
							<div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
								<div>
									<div style={{ fontSize: 13, color: "#888", marginBottom: 4 }}>{card.title}</div>
									<div style={{ fontSize: 28, fontWeight: 700, lineHeight: 1.2 }}>{card.value}</div>
									<div style={{ fontSize: 12, color: "#aaa", marginTop: 4 }}>{card.sub}</div>
								</div>
								<div style={{
									width: 40, height: 40, borderRadius: 10,
									display: "flex", alignItems: "center", justifyContent: "center",
									background: card.iconBg, color: card.iconColor,
								}}>
									{card.icon}
								</div>
							</div>
						</Card>
					</Col>
				))}
			</Row>

			{/* Row 2: Hero chart + Live activity */}
			<Row gutter={[16, 16]}>
				<Col xs={24} lg={16}>
					<Card
						size="small"
						title="资产增长趋势"
						extra={<Tag>最近 7 天</Tag>}
						styles={{ body: { padding: "12px 16px" } }}
					>
						<MiniAreaChart data={assetTrend} color="#4f6ef7" height={200} />
						<div style={{ display: "flex", justifyContent: "space-between", padding: "4px 20px 0", fontSize: 11, color: "#bbb" }}>
							{DAYS.map((d) => <span key={d}>{d}</span>)}
						</div>
					</Card>
				</Col>
				<Col xs={24} lg={8}>
					<Card
						size="small"
						title="最近动态"
						extra={<span style={{ fontSize: 12, color: "#aaa" }}>待办与变更</span>}
						styles={{ body: { padding: "4px 0" } }}
					>
						{recentTodos.length ? (
							<div>
								{recentTodos.map((todo, i) => (
									<div
										key={`${todo.type}-${todo.taskId || todo.requestId || i}`}
										style={{
											display: "flex", justifyContent: "space-between", alignItems: "center",
											padding: "10px 16px",
											borderBottom: i < recentTodos.length - 1 ? "1px solid #f5f5f5" : "none",
										}}
									>
										<div style={{ flex: 1, minWidth: 0 }}>
											<div style={{ fontSize: 13, fontWeight: 500, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
												{todo.title || todo.message || "待办事项"}
											</div>
											<div style={{ fontSize: 11, color: "#bbb", marginTop: 2 }}>{relativeTime(todo.createdAt)}</div>
										</div>
										<Tag color={TODO_TAG_COLORS[todo.type] || "default"} style={{ marginLeft: 8, flexShrink: 0 }}>
											{TODO_LABELS[todo.type] || todo.type}
										</Tag>
									</div>
								))}
							</div>
						) : (
							<div style={{ textAlign: "center", padding: "40px 0", color: "#ccc", fontSize: 13 }}>暂无动态</div>
						)}
					</Card>
				</Col>
			</Row>

			{/* Row 3: Three chart cards */}
			<Row gutter={[16, 16]}>
				<Col xs={24} md={8}>
					<Card size="small" title="待办分布" extra={<span style={{ fontSize: 12, color: "#aaa" }}>按类型统计</span>}>
						<MiniBarChart
							height={160}
							items={Object.entries(TODO_LABELS).map(([key, label]) => ({
								label,
								value: todosByType[key] || 0,
								color: TODO_COLORS[key] || "#d9d9d9",
							}))}
						/>
					</Card>
				</Col>
				<Col xs={24} md={8}>
					<Card size="small" title="资产构成" extra={<span style={{ fontSize: 12, color: "#aaa" }}>总量分布</span>}>
						<div style={{ display: "flex", justifyContent: "center", padding: "10px 0" }}>
							<MiniDonutChart
								size={130}
								segments={[
									{ label: "已有资产", value: Math.max(0, (overview?.myAssets ?? 0) - (overview?.todayNewAssets ?? 0)), color: "#4f6ef7" },
									{ label: "今日新增", value: overview?.todayNewAssets ?? 0, color: "#a78bfa" },
								]}
							/>
						</div>
					</Card>
				</Col>
				<Col xs={24} md={8}>
					<Card size="small" title="待办趋势" extra={<span style={{ fontSize: 12, color: "#aaa" }}>最近 7 天</span>}>
						<MiniAreaChart
							data={buildTodoTrend(todos)}
							color="#f59e0b"
							height={160}
						/>
						<div style={{ display: "flex", justifyContent: "space-between", padding: "4px 20px 0", fontSize: 11, color: "#bbb" }}>
							{DAYS.map((d) => <span key={d}>{d}</span>)}
						</div>
					</Card>
				</Col>
			</Row>
		</div>
	);
}

/* ── helpers outside component ── */

function todayGrowthLabel(overview: WorkbenchOverview | null): string {
	if (!overview?.myAssets || !overview.todayNewAssets) return "今日暂无新增";
	const pct = ((overview.todayNewAssets / Math.max(1, overview.myAssets - overview.todayNewAssets)) * 100).toFixed(1);
	return `+${pct}%`;
}

function buildTodoTrend(todos: WorkbenchTodoItem[]): number[] {
	const now = Date.now();
	const dayMs = 86400000;
	const buckets = Array.from({ length: 7 }, () => 0);
	for (const item of todos) {
		if (!item.createdAt) continue;
		const age = now - new Date(item.createdAt).getTime();
		const dayIndex = 6 - Math.min(6, Math.floor(age / dayMs));
		if (dayIndex >= 0 && dayIndex < 7) buckets[dayIndex]++;
	}
	return buckets;
}
