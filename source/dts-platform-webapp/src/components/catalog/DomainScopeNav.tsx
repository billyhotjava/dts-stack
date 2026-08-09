import { ApartmentOutlined, DownOutlined, SearchOutlined, WarningOutlined } from "@ant-design/icons";
import { useMemo, useState } from "react";
import { UNASSIGNED_DOMAIN_KEY } from "@/pages/catalog/assets/assetPageShared";

// 资产范围导航（Sprint-88 ADR-88-01/02）：单层扁平列表，不呈现建模期层级。
// 「全部资产」是顶部概览行，「未归域」是独立的待治理分区，每行 = 名称 + 数量 + 待处置圆点。

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

/** 域数超过该阈值才渲染搜索框——扁平化后行数即域总数，12 行 ≈ 一屏可见上限 */
export const SCOPE_SEARCH_THRESHOLD = 12;

/** Top N 展示上限；折叠区收纳其余域 */
export const SCOPE_TOP_N = 6;

/**
 * 递归展开为一层，丢弃 children，保留原 stats。
 * 上游不变量（账本 #7）：父域 stats 不含子域资产，扁平后各行数字之和 = 全部域资产数。
 */
export function flattenScopeNodes(nodes: DomainScopeNode[]): DomainScopeNode[] {
	return nodes.flatMap((node) => [
		{ ...node, children: undefined },
		...(node.children ? flattenScopeNodes(node.children) : []),
	]);
}

/**
 * 按 stats.total 倒序取 Top N；total 缺省视为 0；id === null 的节点恒排在末尾且不计入 Top N。
 * 返回 { visible: 前 topN 个可渲染节点, overflow: 剩余节点 }。
 */
export function rankScopeNodes(
	flat: DomainScopeNode[],
	topN: number,
): { visible: DomainScopeNode[]; overflow: DomainScopeNode[] } {
	const ranked = [...flat].sort((a, b) => {
		const aTotal = a.stats?.total ?? 0;
		const bTotal = b.stats?.total ?? 0;
		if (bTotal !== aTotal) return bTotal - aTotal;
		return String(a.name).localeCompare(String(b.name));
	});
	const identified = ranked.filter((node) => node.id !== null);
	const unidentified = ranked.filter((node) => node.id === null);
	return { visible: identified.slice(0, topN), overflow: [...identified.slice(topN), ...unidentified] };
}

const countText = (value: number | undefined, truncated: boolean) => {
	if (value === undefined) return "—";
	return `${truncated ? "≥" : ""}${value}`;
};

const matches = (node: DomainScopeNode, keyword: string) => {
	const needle = keyword.trim().toLowerCase();
	if (!needle) return true;
	return node.name.toLowerCase().includes(needle);
};

type RowProps = {
	testId: string;
	label: string;
	stats?: DomainScopeStats;
	selected: boolean;
	disabled?: boolean;
	title?: string;
	tone?: "default" | "attention";
	truncated: boolean;
	onSelect?: () => void;
	leading?: React.ReactNode;
};

function ScopeRow({
	testId,
	label,
	stats,
	selected,
	disabled,
	title,
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
				selected
					? "border-l-blue-500 bg-slate-100 font-semibold text-slate-900"
					: "border-l-transparent text-slate-700",
				disabled ? "cursor-not-allowed opacity-40" : "hover:bg-slate-50",
				!disabled && isEmpty ? "opacity-40" : "",
			].join(" ")}
		>
			<span className="flex w-4 shrink-0 justify-center text-[10px] text-slate-400">{leading}</span>
			<span className="min-w-0 flex-1 truncate">{label}</span>
			{stats && stats.attention > 0 ? (
				<span
					data-testid={`domain-scope-attention-${testId}`}
					aria-hidden
					title={`含 ${stats.attention} 个待处置资产`}
					className="mr-1 inline-block h-1.5 w-1.5 shrink-0 rounded-full bg-amber-500"
				/>
			) : (
				<span className="mr-1 w-1.5 shrink-0" aria-hidden />
			)}
			<span data-testid={`domain-scope-total-${testId}`} className="shrink-0 tabular-nums text-xs text-slate-600">
				{countText(stats?.total, truncated)}
			</span>
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
	const [moreOpen, setMoreOpen] = useState(false);

	const flatNodes = useMemo(() => flattenScopeNodes(nodes), [nodes]);
	const flatCount = flatNodes.length;
	const showSearch = flatCount > SCOPE_SEARCH_THRESHOLD;
	const filtered = useMemo(
		() => (showSearch && keyword.trim() ? flatNodes.filter((node) => matches(node, keyword)) : flatNodes),
		[flatNodes, keyword, showSearch],
	);
	// 过滤态不做 Top N 截断：用户明确在搜索时希望看到全部命中
	const { visible, overflow } = useMemo(() => rankScopeNodes(filtered, SCOPE_TOP_N), [filtered]);
	const searchActive = Boolean(showSearch && keyword.trim());
	const shownNodes = searchActive ? filtered : visible;

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

			<div className="mt-3 px-2 text-[11px] font-medium uppercase tracking-wide text-slate-400">主题域</div>
			<div className="mt-1">
				{shownNodes.length ? (
					<div className="space-y-0.5">
						{shownNodes.map((node, index) => (
							<ScopeRow
								key={node.id ?? `unidentified-${index}`}
								testId={node.id ?? `unidentified-${index}`}
								label={node.name}
								stats={node.stats}
								selected={node.id !== null && node.id === value}
								disabled={node.id === null}
								title={node.id === null ? "该主题域缺少标识，无法作为筛选条件" : undefined}
								truncated={truncated}
								onSelect={node.id === null ? undefined : () => onChange(node.id ?? undefined)}
							/>
						))}
						{!searchActive && overflow.length > 0 ? (
							<button
								type="button"
								data-testid="domain-scope-more"
								aria-expanded={moreOpen}
								onClick={() => setMoreOpen((open) => !open)}
								className="flex w-full items-center gap-2 rounded-md py-1.5 pl-6 pr-2 text-left text-xs text-slate-500 transition hover:bg-slate-50"
							>
								<span className="flex w-4 shrink-0 justify-center text-[10px]">
									<DownOutlined className={`transition-transform ${moreOpen ? "rotate-180" : ""}`} />
								</span>
								{moreOpen ? "收起" : `更多 ${overflow.length} 个域`}
							</button>
						) : null}
						{moreOpen && !searchActive
							? overflow.map((node, index) => (
									<ScopeRow
										key={node.id ?? `overflow-${index}`}
										testId={node.id ?? `overflow-${index}`}
										label={node.name}
										stats={node.stats}
										selected={node.id !== null && node.id === value}
										disabled={node.id === null}
										title={node.id === null ? "该主题域缺少标识，无法作为筛选条件" : undefined}
										truncated={truncated}
										onSelect={node.id === null ? undefined : () => onChange(node.id ?? undefined)}
									/>
								))
							: null}
					</div>
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
			</div>

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
