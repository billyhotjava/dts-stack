import { CalendarDays, ChevronDown, Gauge, LayoutList, Plus, RefreshCw, Search, Sigma, Sparkles } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate, useSearchParams } from "react-router";
import type { IndicatorDefinition } from "@/features/modeling/indicators/indicatorDefinitionContract";
import { useGovernanceManageAccess } from "@/hooks/useModuleManageAccess";
import { MetricEditor } from "../components/MetricEditor";
import { WorkspacePage } from "../components/WorkspacePage";
import {
	classifyIndicator,
	createIndicatorDraft,
	filterIndicatorCatalog,
	type IndicatorWorkspaceError,
	loadIndicatorCatalog,
	type MetricSelection,
	type MetricType,
	normalizeIndicatorError,
	supportsIndicatorCreation,
	toMetricSelection,
} from "../indicatorWorkspaceAdapter";
import { dataModelingPath } from "../navigation";
import type { WorkspacePageProps } from "../types";
import "./modeling-metrics.css";
import "./modeling-dialogs.css";
import "./modeling-metrics-extended.css";

const METRIC_VIEWS: Array<{ type: MetricType; view: string; icon: typeof Sigma }> = [
	{ type: "复合指标", view: "composite", icon: Sparkles },
	{ type: "派生指标", view: "derived", icon: Gauge },
	{ type: "原子指标", view: "atomic", icon: Sigma },
	{ type: "修饰词", view: "modifiers", icon: LayoutList },
	{ type: "时间周期", view: "periods", icon: CalendarDays },
];

const typeFromView = (view: string): MetricType => METRIC_VIEWS.find((item) => item.view === view)?.type ?? "原子指标";
const viewFromType = (type: MetricType): string => METRIC_VIEWS.find((item) => item.type === type)?.view ?? "atomic";

const domainLabel = (value: unknown): string => String(value ?? "").trim() || "未分域";

export function MetricsWorkspace({ route }: WorkspacePageProps) {
	const navigate = useNavigate();
	const [searchParams, setSearchParams] = useSearchParams();
	const canManage = useGovernanceManageAccess();
	const type = typeFromView(route.view);
	const [catalog, setCatalog] = useState<IndicatorDefinition[]>([]);
	const [selection, setSelection] = useState<MetricSelection | null>(null);
	const [domain, setDomain] = useState("");
	const [query, setQuery] = useState("");
	const [compact, setCompact] = useState(false);
	const [expandedDomains, setExpandedDomains] = useState(() => new Set<string>());
	const [loading, setLoading] = useState(true);
	const [error, setError] = useState<IndicatorWorkspaceError | null>(null);
	const [targetNotice, setTargetNotice] = useState<{ message: string; found: boolean } | null>(null);
	const requestedIndicatorIdRef = useRef(searchParams.get("indicatorId")?.trim() || "");

	const consumeRequestedIndicator = useCallback(() => {
		setSearchParams(
			(current) => {
				const next = new URLSearchParams(current);
				next.delete("indicatorId");
				return next;
			},
			{ replace: true },
		);
	}, [setSearchParams]);

	const refreshCatalog = useCallback(async () => {
		setLoading(true);
		setError(null);
		try {
			const rows = await loadIndicatorCatalog();
			setCatalog(rows);
			setExpandedDomains(new Set(rows.map((item) => domainLabel(item.domain))));
			setSelection((current) => {
				if (current?.isNew) return current;
				const refreshed = current?.id ? rows.find((item) => item.id === current.id) : undefined;
				return refreshed ? toMetricSelection(refreshed) : current;
			});
		} catch (cause) {
			setCatalog([]);
			setSelection(null);
			setError(normalizeIndicatorError(cause));
		} finally {
			setLoading(false);
		}
	}, []);

	useEffect(() => {
		void refreshCatalog();
	}, [refreshCatalog]);

	const domains = useMemo(
		() =>
			Array.from(new Set(catalog.map((item) => String(item.domain ?? "").trim()).filter(Boolean))).sort((left, right) =>
				left.localeCompare(right, "zh-CN"),
			),
		[catalog],
	);

	const visibleCatalog = useMemo(
		() => filterIndicatorCatalog(catalog, { type, domain, query }),
		[catalog, domain, query, type],
	);

	useEffect(() => {
		setSelection((current) => {
			if (current && classifyIndicator(current) === type) return current;
			const first = visibleCatalog[0];
			return first ? toMetricSelection(first) : null;
		});
	}, [type, visibleCatalog]);

	useEffect(() => {
		const requestedIndicatorId = requestedIndicatorIdRef.current;
		if (loading || error || !requestedIndicatorId) return;
		const targetIndicator = catalog.find((item) => item.id === requestedIndicatorId);
		if (!targetIndicator) {
			setTargetNotice({
				found: false,
				message: `目标指标 ${requestedIndicatorId} 不在当前真实指标目录中，请确认指标权限或状态。`,
			});
			requestedIndicatorIdRef.current = "";
			consumeRequestedIndicator();
			return;
		}

		const targetType = classifyIndicator(targetIndicator);
		if (targetType !== type) {
			navigate(
				{ pathname: dataModelingPath("metrics", viewFromType(targetType)), search: window.location.search },
				{ replace: true },
			);
			return;
		}

		setDomain("");
		setQuery("");
		setExpandedDomains((current) => new Set(current).add(domainLabel(targetIndicator.domain)));
		setSelection(toMetricSelection(targetIndicator));
		setTargetNotice({
			found: true,
			message: `已定位指标“${targetIndicator.name || targetIndicator.code || targetIndicator.id}”。`,
		});
		requestedIndicatorIdRef.current = "";
		consumeRequestedIndicator();
	}, [catalog, consumeRequestedIndicator, error, loading, navigate, type]);

	const groupedCatalog = useMemo(() => {
		const groups = new Map<string, IndicatorDefinition[]>();
		for (const item of visibleCatalog) {
			const group = domainLabel(item.domain);
			groups.set(group, [...(groups.get(group) ?? []), item]);
		}
		return Array.from(groups.entries());
	}, [visibleCatalog]);

	const counts = useMemo(() => {
		const result = new Map<MetricType, number>(METRIC_VIEWS.map((item) => [item.type, 0]));
		for (const item of catalog) {
			const metricType = classifyIndicator(item);
			result.set(metricType, (result.get(metricType) ?? 0) + 1);
		}
		return result;
	}, [catalog]);

	const changeType = (nextType: MetricType) => {
		setSelection(null);
		setDomain("");
		setQuery("");
		navigate(dataModelingPath("metrics", viewFromType(nextType)));
	};

	const createDisabledReason = !canManage
		? "无指标维护权限"
		: !supportsIndicatorCreation(type)
			? "现有治理指标契约不支持独立新建修饰词或时间周期"
			: undefined;

	const createMetric = () => {
		if (createDisabledReason) return;
		setSelection(createIndicatorDraft(type, domain || null));
	};

	const applyMutation = (saved: MetricSelection) => {
		setCatalog((current) => {
			const index = saved.id ? current.findIndex((item) => item.id === saved.id) : -1;
			if (index < 0) return [saved, ...current];
			return current.map((item, itemIndex) => (itemIndex === index ? saved : item));
		});
		setSelection(saved);
	};

	const renderCatalogState = () => {
		if (loading) return <div className="dm-object-tree__empty">正在加载指标目录…</div>;
		if (error?.kind === "permission") {
			return (
				<div className="dm-object-tree__empty" role="alert">
					<strong>无权访问指标</strong>
					<p>{error.message}</p>
				</div>
			);
		}
		if (error) {
			return (
				<div className="dm-object-tree__empty" role="alert">
					<p>{error.message}</p>
					<button className="dm-button" onClick={() => void refreshCatalog()} type="button">
						重试
					</button>
				</div>
			);
		}
		if (groupedCatalog.length === 0) {
			return (
				<div className="dm-object-tree__empty">
					<strong>暂无指标</strong>
					<p>当前筛选条件下没有真实指标记录。</p>
				</div>
			);
		}
		return groupedCatalog.map(([group, items]) => {
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
									className={`dm-metric-node ${selection !== null && selection.id === item.id && !selection.isNew ? "is-active" : ""}`}
									key={String(item.id ?? item.code)}
									onClick={() => setSelection(toMetricSelection(item))}
									type="button"
								>
									<Sigma aria-hidden="true" size={13} />
									<span>
										<strong>{item.code || "未编码"}</strong>
										<small>{item.name || "未命名"}</small>
									</span>
								</button>
							))
						: null}
				</div>
			);
		});
	};

	return (
		<WorkspacePage
			description="从 Governance Indicator 权威模型维护指标口径、计算规则、版本和发布状态。"
			eyebrow="统一指标目录"
			title="数据指标"
		>
			{targetNotice ? (
				<output className="dm-stage-notice">
					<strong>{targetNotice.found ? "已定位" : "目标不可见"}</strong>
					{targetNotice.message}
				</output>
			) : null}
			<div className={`dm-metric-workspace ${compact ? "is-compact" : ""}`}>
				<nav aria-label="指标类型" className="dm-metric-type-rail">
					{METRIC_VIEWS.map((item) => {
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
								<small>{counts.get(item.type) ?? 0}</small>
							</button>
						);
					})}
				</nav>

				<aside className="dm-metric-catalog">
					<header>
						<strong>{type}</strong>
						<div>
							<button
								aria-label={`新建${type}`}
								className="dm-icon-button"
								disabled={Boolean(createDisabledReason)}
								onClick={createMetric}
								title={createDisabledReason}
								type="button"
							>
								<Plus aria-hidden="true" size={17} />
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
								disabled={loading}
								onClick={() => void refreshCatalog()}
								title="重新读取指标服务"
								type="button"
							>
								<RefreshCw aria-hidden="true" size={16} />
							</button>
						</div>
					</header>
					<div className="dm-metric-layer">Governance Indicator</div>
					<div className="dm-catalog-filter">
						<select
							aria-label="选择数据域"
							className="dm-select"
							onChange={(event) => setDomain(event.target.value)}
							value={domain}
						>
							<option value="">全部数据域</option>
							{domains.map((item) => (
								<option key={item} value={item}>
									{item}
								</option>
							))}
						</select>
						<label>
							<Search aria-hidden="true" size={14} />
							<input
								aria-label={`搜索${type}`}
								onChange={(event) => setQuery(event.target.value)}
								placeholder={`搜索${type}编码、名称或负责人`}
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
							<span>{visibleCatalog.length}</span>
						</div>
						{renderCatalogState()}
					</div>
					<footer>
						<span>共 {visibleCatalog.length} 个真实目录对象</span>
						<small>{loading ? "刷新中" : "已同步"}</small>
					</footer>
				</aside>

				<MetricEditor canManage={canManage} onChanged={applyMutation} selection={selection} type={type} />
			</div>
		</WorkspacePage>
	);
}
