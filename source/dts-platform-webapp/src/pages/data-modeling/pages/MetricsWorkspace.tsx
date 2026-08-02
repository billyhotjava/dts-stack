import {
	CalendarDays,
	ChevronDown,
	Download,
	Gauge,
	LayoutList,
	Plus,
	RefreshCw,
	Search,
	Sigma,
	Sparkles,
	Upload,
} from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router";
import { MetricEditor, type MetricSelection, type MetricType } from "../components/MetricEditor";
import { UiStageNotice, WorkspacePage } from "../components/WorkspacePage";
import { dataModelingPath } from "../navigation";
import type { WorkspacePageProps } from "../types";
import "./modeling-metrics.css";
import "./modeling-dialogs.css";
import "./modeling-metrics-extended.css";

const TYPE_CONFIG: Array<{
	type: MetricType;
	view: string;
	count: number;
	icon: typeof Sigma;
}> = [
	{ type: "复合指标", view: "composite", count: 2, icon: Sparkles },
	{ type: "派生指标", view: "derived", count: 5, icon: Gauge },
	{ type: "原子指标", view: "atomic", count: 6, icon: Sigma },
	{ type: "修饰词", view: "modifiers", count: 4, icon: LayoutList },
	{ type: "时间周期", view: "periods", count: 4, icon: CalendarDays },
];

const METRIC_CATALOG: Record<MetricType, MetricSelection[]> = {
	原子指标: [
		{ domain: "默认", code: "budget_amount", name: "预算金额" },
		{ domain: "财务域", code: "executed_amount", name: "已执行金额" },
		{ domain: "财务域", code: "payable_amount", name: "应付金额" },
		{ domain: "项目域", code: "milestone_count", name: "节点数量" },
	],
	派生指标: [
		{ domain: "财务域", code: "monthly_execution_rate", name: "月度预算执行率" },
		{ domain: "财务域", code: "project_budget_variance", name: "项目预算偏差" },
		{ domain: "项目域", code: "weekly_completion_rate", name: "周节点完成率" },
	],
	复合指标: [
		{ domain: "财务域", code: "budget_health_score", name: "预算健康度" },
		{ domain: "项目域", code: "project_health_score", name: "项目健康度" },
	],
	修饰词: [
		{ domain: "默认", code: "current", name: "当期" },
		{ domain: "默认", code: "cumulative", name: "累计" },
		{ domain: "默认", code: "year_on_year", name: "同比" },
		{ domain: "默认", code: "month_on_month", name: "环比" },
	],
	时间周期: [
		{ domain: "默认", code: "day", name: "日" },
		{ domain: "默认", code: "week", name: "周" },
		{ domain: "默认", code: "month", name: "月" },
		{ domain: "默认", code: "year", name: "年" },
	],
};

const typeFromView = (view: string): MetricType => TYPE_CONFIG.find((item) => item.view === view)?.type ?? "原子指标";
const viewFromType = (type: MetricType) => TYPE_CONFIG.find((item) => item.type === type)?.view ?? "atomic";

export function MetricsWorkspace({ route }: WorkspacePageProps) {
	const navigate = useNavigate();
	const [type, setType] = useState<MetricType>(() => typeFromView(route.view));
	const [selection, setSelection] = useState<MetricSelection>(() => METRIC_CATALOG[typeFromView(route.view)][0]);
	const [domain, setDomain] = useState("全部数据域");
	const [query, setQuery] = useState("");
	const [compact, setCompact] = useState(false);
	const [expandedDomains, setExpandedDomains] = useState(() => new Set(["默认", "财务域", "项目域"]));

	useEffect(() => {
		const nextType = typeFromView(route.view);
		setType(nextType);
		setSelection(METRIC_CATALOG[nextType][0]);
	}, [route.view]);

	const groupedCatalog = useMemo(() => {
		const groups = new Map<string, MetricSelection[]>();
		for (const item of METRIC_CATALOG[type]) {
			if (
				(domain === "全部数据域" || item.domain === domain) &&
				(!query || item.code.toLowerCase().includes(query.toLowerCase()) || item.name.includes(query))
			) {
				groups.set(item.domain, [...(groups.get(item.domain) ?? []), item]);
			}
		}
		return Array.from(groups.entries());
	}, [domain, query, type]);

	const changeType = (nextType: MetricType) => {
		setType(nextType);
		setSelection(METRIC_CATALOG[nextType][0]);
		setDomain("全部数据域");
		setQuery("");
		navigate(dataModelingPath("metrics", viewFromType(nextType)));
	};

	const createMetric = () => {
		setSelection({
			domain: domain === "全部数据域" ? "默认" : domain,
			code: "",
			name: "",
			isNew: true,
		});
	};

	return (
		<WorkspacePage
			description="按指标类型和数据域维护统一口径，保留原型中的目录筛选与差异化编辑表单。"
			eyebrow="统一指标目录"
			title="数据指标"
		>
			<UiStageNotice />
			<div className={`dm-metric-workspace ${compact ? "is-compact" : ""}`}>
				<nav aria-label="指标类型" className="dm-metric-type-rail">
					{TYPE_CONFIG.map((item) => {
						const Icon = item.icon;
						return (
							<button
								className={type === item.type ? "is-active" : ""}
								key={item.type}
								onClick={() => changeType(item.type)}
								type="button"
							>
								<Icon aria-hidden="true" size={17} />
								<span>{item.type}</span>
								<small>{item.count}</small>
							</button>
						);
					})}
				</nav>

				<aside className="dm-metric-catalog">
					<header>
						<strong>{type}</strong>
						<div>
							<button aria-label={`新建${type}`} className="dm-icon-button" onClick={createMetric} type="button">
								<Plus aria-hidden="true" size={17} />
							</button>
							<button aria-label="导入指标" className="dm-icon-button" disabled title="后台阶段接入" type="button">
								<Download aria-hidden="true" size={16} />
							</button>
							<button aria-label="导出指标" className="dm-icon-button" disabled title="后台阶段接入" type="button">
								<Upload aria-hidden="true" size={16} />
							</button>
							<button
								aria-label="切换目录密度"
								className={`dm-icon-button ${compact ? "is-active" : ""}`}
								onClick={() => setCompact((current) => !current)}
								title="切换目录密度"
								type="button"
							>
								<LayoutList aria-hidden="true" size={16} />
							</button>
							<button
								aria-label="刷新目录"
								className="dm-icon-button"
								onClick={() => {
									setDomain("全部数据域");
									setQuery("");
								}}
								type="button"
							>
								<RefreshCw aria-hidden="true" size={16} />
							</button>
						</div>
					</header>
					<div className="dm-metric-layer">公共层</div>
					<div className="dm-catalog-filter">
						<select
							aria-label="选择数据域"
							className="dm-select"
							onChange={(event) => setDomain(event.target.value)}
							value={domain}
						>
							<option>全部数据域</option>
							<option>默认</option>
							<option>财务域</option>
							<option>项目域</option>
						</select>
						<label>
							<Search aria-hidden="true" size={14} />
							<input
								aria-label={`搜索${type}`}
								onChange={(event) => setQuery(event.target.value)}
								placeholder={`搜索${type}编码或名称`}
								type="search"
								value={query}
							/>
						</label>
					</div>
					<div className="dm-metric-tree">
						<div className="dm-metric-tree__root">
							<ChevronDown aria-hidden="true" size={14} />
							<Sigma aria-hidden="true" size={14} />
							<strong>{type}</strong>
							<span>{METRIC_CATALOG[type].length}</span>
						</div>
						{groupedCatalog.length === 0 ? (
							<div className="dm-object-tree__empty">当前筛选条件下暂无指标</div>
						) : (
							groupedCatalog.map(([group, items]) => {
								const expanded = expandedDomains.has(group);
								return (
									<div className="dm-metric-tree__group" key={group}>
										<button
											onClick={() =>
												setExpandedDomains((current) => {
													const next = new Set(current);
													if (expanded) next.delete(group);
													else next.add(group);
													return next;
												})
											}
											type="button"
										>
											<ChevronDown aria-hidden="true" className={expanded ? "" : "is-collapsed"} size={13} />
											<span>◎</span>
											<strong>{group}</strong>
											<small>{items.length}</small>
										</button>
										{expanded
											? items.map((item) => (
													<button
														className={`dm-metric-node ${selection.code === item.code && !selection.isNew ? "is-active" : ""}`}
														key={item.code}
														onClick={() => setSelection(item)}
														type="button"
													>
														<Sigma aria-hidden="true" size={13} />
														<span>
															<strong>{item.code}</strong>
															<small>{item.name}</small>
														</span>
													</button>
												))
											: null}
									</div>
								);
							})
						)}
					</div>
					<footer>
						<span>共 {METRIC_CATALOG[type].length} 个目录对象</span>
						<small>演示数据</small>
					</footer>
				</aside>

				<MetricEditor selection={selection} type={type} />
			</div>
		</WorkspacePage>
	);
}
