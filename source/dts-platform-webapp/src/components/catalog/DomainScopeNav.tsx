import { ApartmentOutlined, DownOutlined, RightOutlined, SearchOutlined, WarningOutlined } from "@ant-design/icons";
import { useMemo, useState } from "react";
import { UNASSIGNED_DOMAIN_KEY } from "@/pages/catalog/assets/assetPageShared";

// 资产范围导航：分区式，不是文件树。
// 「全部资产」是顶部概览行而非根节点，「未归域」是独立的待治理分区而非并列的主题域，
// 每行右侧带体量与待处置数，使「哪个域有货」无需逐个点击即可看出。

export type DomainScopeStats = { total: number; attention: number };

export type DomainScopeNode = {
	/** null 表示该主题域缺少标识，不能作为筛选条件 */
	id: string | null;
	name: string;
	code?: string;
	stats?: DomainScopeStats;
	children?: DomainScopeNode[];
};

export interface DomainScopeNavProps {
	nodes: DomainScopeNode[];
	allStats?: DomainScopeStats;
	unassignedStats?: DomainScopeStats;
	/** undefined 表示「全部资产」 */
	value?: string;
	onChange: (next: string | undefined) => void;
	loading?: boolean;
	/** 统计被扫描上限截断时，数字加 ≥ 前缀 */
	truncated?: boolean;
}

/** 域数超过该阈值才渲染搜索框——少量域时搜索框只是多余控件 */
const SEARCH_THRESHOLD = 8;

const countText = (value: number | undefined, truncated: boolean) => {
	if (value === undefined) return "—";
	return `${truncated ? "≥" : ""}${value}`;
};

const flatten = (nodes: DomainScopeNode[]): DomainScopeNode[] =>
	nodes.flatMap((node) => [node, ...(node.children ? flatten(node.children) : [])]);

const matches = (node: DomainScopeNode, keyword: string) => {
	const needle = keyword.trim().toLowerCase();
	if (!needle) return true;
	return `${node.name} ${node.code ?? ""}`.toLowerCase().includes(needle);
};

/** 过滤时保留命中节点，父节点只要有子节点命中就一并保留 */
const filterTree = (nodes: DomainScopeNode[], keyword: string): DomainScopeNode[] => {
	const kept: DomainScopeNode[] = [];
	for (const node of nodes) {
		const children = node.children ? filterTree(node.children, keyword) : undefined;
		if (matches(node, keyword) || (children && children.length > 0)) {
			kept.push({ ...node, children });
		}
	}
	return kept;
};

type RowProps = {
	testId: string;
	label: string;
	code?: string;
	stats?: DomainScopeStats;
	selected: boolean;
	disabled?: boolean;
	title?: string;
	indent?: number;
	tone?: "default" | "attention";
	truncated: boolean;
	onSelect?: () => void;
	leading?: React.ReactNode;
};

function ScopeRow({
	testId,
	label,
	code,
	stats,
	selected,
	disabled,
	title,
	indent = 0,
	tone = "default",
	truncated,
	onSelect,
	leading,
}: RowProps) {
	const isEmpty = stats !== undefined && stats.total === 0;
	return (
		<button
			type="button"
			data-testid={`domain-scope-row-${testId}`}
			data-empty={stats === undefined ? undefined : String(isEmpty)}
			aria-current={selected ? "true" : undefined}
			disabled={disabled}
			title={title}
			onClick={disabled ? undefined : onSelect}
			className={[
				"flex w-full items-center gap-2 rounded-md py-1.5 pr-2 text-left text-sm transition",
				"border-l-[3px]",
				selected ? "border-l-blue-500 bg-slate-100 font-semibold text-slate-900" : "border-l-transparent text-slate-700",
				disabled ? "cursor-not-allowed opacity-40" : "hover:bg-slate-50",
				!disabled && isEmpty ? "opacity-40" : "",
			].join(" ")}
			style={{ paddingLeft: 8 + indent * 12 }}
		>
			<span className="flex w-4 shrink-0 justify-center text-[10px] text-slate-400">{leading}</span>
			<span className="min-w-0 flex-1 truncate">{label}</span>
			{code ? <span className="shrink-0 text-[11px] text-slate-400">{code}</span> : null}
			<span
				data-testid={`domain-scope-total-${testId}`}
				className="shrink-0 tabular-nums text-xs text-slate-600"
			>
				{countText(stats?.total, truncated)}
			</span>
			{stats && stats.attention > 0 ? (
				<span
					data-testid={`domain-scope-attention-${testId}`}
					className="shrink-0 rounded bg-amber-50 px-1 text-[11px] tabular-nums text-amber-700"
				>
					{countText(stats.attention, truncated)}
				</span>
			) : (
				<span className="w-0 shrink-0" aria-hidden />
			)}
			{tone === "attention" ? <span className="sr-only">待治理</span> : null}
		</button>
	);
}

export function DomainScopeNav({
	nodes,
	allStats,
	unassignedStats,
	value,
	onChange,
	loading,
	truncated = false,
}: DomainScopeNavProps) {
	const [keyword, setKeyword] = useState("");
	const [collapsed, setCollapsed] = useState<Set<string>>(new Set());

	const flatCount = useMemo(() => flatten(nodes).length, [nodes]);
	const showSearch = flatCount > SEARCH_THRESHOLD;
	const visibleNodes = useMemo(() => (showSearch && keyword ? filterTree(nodes, keyword) : nodes), [nodes, keyword, showSearch]);

	const toggle = (key: string) =>
		setCollapsed((prev) => {
			const next = new Set(prev);
			if (next.has(key)) next.delete(key);
			else next.add(key);
			return next;
		});

	const renderNodes = (list: DomainScopeNode[], depth: number, path: string): React.ReactNode[] =>
		list.flatMap((node, index) => {
			const key = node.id ?? `${path}-${index}`;
			const hasChildren = Boolean(node.children?.length);
			const isCollapsed = collapsed.has(key);
			const row = (
				<ScopeRow
					key={key}
					testId={node.id ?? `unidentified-${index}`}
					label={node.name}
					code={node.code}
					stats={node.stats}
					selected={node.id !== null && node.id === value}
					disabled={node.id === null}
					title={node.id === null ? "该主题域缺少标识，无法作为筛选条件" : undefined}
					indent={depth}
					truncated={truncated}
					onSelect={node.id === null ? undefined : () => onChange(node.id ?? undefined)}
					leading={
						hasChildren ? (
							<span
								role="presentation"
								onClick={(event) => {
									event.stopPropagation();
									toggle(key);
								}}
							>
								{isCollapsed ? <RightOutlined /> : <DownOutlined />}
							</span>
						) : null
					}
				/>
			);
			if (!hasChildren || isCollapsed) return [row];
			return [row, ...renderNodes(node.children ?? [], depth + 1, key)];
		});

	if (loading) {
		return (
			<div className="space-y-2" aria-busy="true">
				<div className="h-4 w-20 animate-pulse rounded bg-slate-200" />
				{[0, 1, 2, 3].map((i) => (
					<div key={i} className="h-7 animate-pulse rounded bg-slate-100" />
				))}
			</div>
		);
	}

	return (
		<nav aria-label="资产范围">
			<div className="mb-2 flex items-center gap-2 px-2 text-sm font-semibold text-slate-900">
				<ApartmentOutlined />
				资产范围
			</div>

			{showSearch ? (
				<div className="mb-2 flex items-center gap-1 rounded-md border border-slate-200 px-2 py-1">
					<SearchOutlined className="text-xs text-slate-400" />
					<input
						data-testid="domain-scope-search"
						value={keyword}
						onChange={(event) => setKeyword(event.target.value)}
						placeholder="搜索主题域…"
						aria-label="搜索主题域"
						className="w-full border-0 text-xs outline-none placeholder:text-slate-400"
					/>
				</div>
			) : null}

			<ScopeRow
				testId="all"
				label="全部资产"
				stats={allStats}
				selected={value === undefined}
				truncated={truncated}
				onSelect={() => onChange(undefined)}
			/>

			<div className="mt-3 flex items-center justify-between px-2 text-[11px] font-medium uppercase tracking-wide text-slate-400">
				<span>业务主题域</span>
				<span className="tabular-nums">{flatCount}</span>
			</div>
			{visibleNodes.length ? (
				<div className="mt-1">{renderNodes(visibleNodes, 0, "root")}</div>
			) : (
				<div className="px-2 py-3 text-xs text-slate-400">
					{keyword ? (
						"没有匹配的主题域"
					) : (
						<>
							尚未创建主题域，请先在
							<a href="/governance/subjects" className="mx-1 text-blue-600 hover:underline">
								治理主题域
							</a>
							中创建。
						</>
					)}
				</div>
			)}

			<div className="mt-3 px-2 text-[11px] font-medium uppercase tracking-wide text-slate-400">待治理</div>
			<div className="mt-1">
				<ScopeRow
					testId="unassigned"
					label="未归域"
					stats={unassignedStats}
					selected={value === UNASSIGNED_DOMAIN_KEY}
					tone="attention"
					truncated={truncated}
					onSelect={() => onChange(UNASSIGNED_DOMAIN_KEY)}
					leading={<WarningOutlined className="text-amber-500" />}
				/>
			</div>
		</nav>
	);
}
