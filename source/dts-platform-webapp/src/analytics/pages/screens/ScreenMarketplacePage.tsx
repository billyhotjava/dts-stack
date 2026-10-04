/**
 * ScreenMarketplacePage — 组件/模板市场
 *
 * Browse, search, inspect and install shared components/templates.
 */
import { useCallback, useEffect, useMemo, useState } from 'react';
import { analyticsApi, type MarketplaceCatalogItem } from '../../api/analyticsApi';
import {
	filterMarketplaceItems,
	resolveMarketplaceEmptyState,
	resolveMarketplaceInstallMessage,
} from './ScreenMarketplacePage.helpers';
import { notifyScreenPluginManifestsUpdated } from './plugins/manifestLoader';

type TabKey = 'components' | 'templates';
type CategoryKey = 'all' | 'chart' | 'decoration' | 'container' | 'metric' | 'other' | 'retail' | 'business';

type ActionNotice = {
	tone: 'success' | 'error';
	text: string;
};

const CATEGORIES: Array<{ key: CategoryKey; label: string }> = [
	{ key: 'all', label: '全部' },
	{ key: 'chart', label: '图表' },
	{ key: 'decoration', label: '装饰' },
	{ key: 'container', label: '容器' },
	{ key: 'metric', label: '指标' },
	{ key: 'business', label: '业务' },
	{ key: 'retail', label: '零售' },
	{ key: 'other', label: '其他' },
];

function cardAccent(activeTab: TabKey): string {
	return activeTab === 'components' ? 'rgba(14, 165, 233, 0.18)' : 'rgba(16, 185, 129, 0.18)';
}

function formatTime(value?: string): string | null {
	if (!value) return null;
	const date = new Date(value);
	if (Number.isNaN(date.getTime())) return null;
	return date.toLocaleString('zh-CN', {
		month: '2-digit',
		day: '2-digit',
		hour: '2-digit',
		minute: '2-digit',
	});
}

export default function ScreenMarketplacePage() {
	const [activeTab, setActiveTab] = useState<TabKey>('components');
	const [search, setSearch] = useState('');
	const [category, setCategory] = useState<CategoryKey>('all');
	const [items, setItems] = useState<MarketplaceCatalogItem[]>([]);
	const [selectedId, setSelectedId] = useState<string | null>(null);
	const [loading, setLoading] = useState(false);
	const [installing, setInstalling] = useState<string | null>(null);
	const [error, setError] = useState<string | null>(null);
	const [notice, setNotice] = useState<ActionNotice | null>(null);

	const loadItems = useCallback(async () => {
		setLoading(true);
		setError(null);
		try {
			const params = {
				search: search || undefined,
				category: category !== 'all' ? category : undefined,
			};
			const result = activeTab === 'components'
				? await analyticsApi.listMarketplaceComponents(params)
				: await analyticsApi.listMarketplaceTemplates(params);
			const nextItems = Array.isArray(result) ? result : [];
			setItems(nextItems);
			setSelectedId((current) => {
				if (current && nextItems.some((item) => item.id === current)) {
					return current;
				}
				return nextItems[0]?.id || null;
			});
		} catch (loadError) {
			console.error('Failed to load marketplace items:', loadError);
			setItems([]);
			setSelectedId(null);
			setError(loadError instanceof Error ? loadError.message : '市场加载失败');
		} finally {
			setLoading(false);
		}
	}, [activeTab, category, search]);

	useEffect(() => {
		void loadItems();
	}, [loadItems]);

	useEffect(() => {
		setNotice(null);
	}, [activeTab, search, category]);

	const filteredItems = useMemo(() => filterMarketplaceItems(items, search), [items, search]);
	const selectedItem = useMemo(
		() => filteredItems.find((item) => item.id === selectedId) || filteredItems[0] || null,
		[filteredItems, selectedId],
	);
	const emptyState = resolveMarketplaceEmptyState({
		hasError: Boolean(error),
		hasRemoteItems: items.length > 0,
		hasFilteredItems: filteredItems.length > 0,
		activeTab,
		search,
	});

	const handleInstall = useCallback(async (item: MarketplaceCatalogItem) => {
		if (installing) return;
		setInstalling(item.id);
		setNotice(null);
		try {
			const installed = activeTab === 'components'
				? await analyticsApi.installMarketplaceComponent(item.id)
				: await analyticsApi.installMarketplaceTemplate(item.id);
			setItems((current) => current.map((entry) => entry.id === item.id ? { ...entry, ...installed, installed: true } : entry));
			setNotice({
				tone: 'success',
				text: resolveMarketplaceInstallMessage(activeTab, item.name || item.id, true),
			});
			if (activeTab === 'components') {
				notifyScreenPluginManifestsUpdated();
			}
			await loadItems();
		} catch (installError) {
			console.error('Failed to install marketplace item:', installError);
			const detail = installError instanceof Error ? installError.message : '';
			setNotice({
				tone: 'error',
				text: detail
					? `${resolveMarketplaceInstallMessage(activeTab, item.name || item.id, false)} ${detail}`
					: resolveMarketplaceInstallMessage(activeTab, item.name || item.id, false),
			});
		} finally {
			setInstalling(null);
		}
	}, [activeTab, installing, loadItems]);

	return (
		<div className="w-full max-w-none p-6 text-inherit">
			<div className="flex justify-between gap-6 items-start mb-5">
				<div>
					<h1 className="text-2xl font-bold m-0 mb-2">组件与模板市场</h1>
					<p className="text-[13px] opacity-[0.68] m-0 max-w-[640px]">
						市场页已经接通真实后端，可按分类浏览可安装组件与模板，并将安装结果同步回组件库和模板资产中心。
					</p>
				</div>
				<div className="min-w-[280px] p-3.5 rounded-[14px] text-[#e2e8f0] shadow-[0_18px_40px_rgba(15,23,42,0.18)]" style={{ background: 'linear-gradient(135deg, rgba(15,23,42,0.88), rgba(30,41,59,0.82))' }}>
					<div className="text-xs tracking-wide opacity-[0.72] uppercase">Market Snapshot</div>
					<div className="grid grid-cols-3 gap-2.5 mt-3">
						<div>
							<div className="text-[22px] font-bold">{items.length}</div>
							<div className="text-[11px] opacity-[0.68]">远端条目</div>
						</div>
						<div>
							<div className="text-[22px] font-bold">{filteredItems.length}</div>
							<div className="text-[11px] opacity-[0.68]">当前结果</div>
						</div>
						<div>
							<div className="text-[22px] font-bold">{items.filter((item) => item.installed).length}</div>
							<div className="text-[11px] opacity-[0.68]">已安装</div>
						</div>
					</div>
				</div>
			</div>

			{notice && (
				<div
					data-testid="analytics-marketplace-notice"
					className={`mb-4 px-3.5 py-3 rounded-xl text-[13px] ${
						notice.tone === 'success'
							? 'border border-success/25 bg-success/10 text-[#047857]'
							: 'border border-error/25 bg-error/10 text-[#b91c1c]'
					}`}
				>
					{notice.text}
				</div>
			)}

			<div className="flex gap-6 items-start">
				<div className="flex-1 min-w-0">
					<div className="flex gap-1 mb-4 border-b border-white/10">
						{(['components', 'templates'] as TabKey[]).map((tab) => (
							<button
								key={tab}
								type="button"
								onClick={() => setActiveTab(tab)}
								className="bg-transparent border-none px-4 py-2 text-[13px] cursor-pointer"
								style={{
									borderBottom: activeTab === tab ? '2px solid var(--color-primary, #3b82f6)' : '2px solid transparent',
									color: activeTab === tab ? 'var(--color-primary, #3b82f6)' : 'inherit',
									fontWeight: activeTab === tab ? 600 : 400,
								}}
							>
								{tab === 'components' ? '组件市场' : '模板市场'}
							</button>
						))}
					</div>

					<div className="flex gap-2.5 mb-4 items-center">
						<input
							data-testid="analytics-marketplace-search"
							type="text"
							placeholder={activeTab === 'components' ? '搜索组件、标签或描述...' : '搜索模板、标签或描述...'}
							value={search}
							onChange={(event) => setSearch(event.target.value)}
							className="flex-1 px-3 py-2.5 border border-white/20 rounded-[10px] bg-white/5 text-inherit text-[13px] outline-none"
						/>
						<div className="flex gap-1 flex-wrap justify-end">
							{CATEGORIES.map((cat) => (
								<button
									key={cat.key}
									type="button"
									onClick={() => setCategory(cat.key)}
									className="px-3 py-1.5 text-xs rounded-full cursor-pointer"
									style={{
										border: category === cat.key ? '1px solid var(--color-primary, #3b82f6)' : '1px solid rgba(148,163,184,0.2)',
										background: category === cat.key ? 'rgba(59,130,246,0.12)' : 'transparent',
										color: category === cat.key ? 'var(--color-primary, #3b82f6)' : 'inherit',
									}}
								>
									{cat.label}
								</button>
							))}
						</div>
					</div>

					{loading ? (
						<div className="text-center p-12 rounded-[18px] bg-[rgba(15,23,42,0.03)] border border-white/10 text-[13px] opacity-[0.68]">
							正在读取 {activeTab === 'components' ? '组件' : '模板'} 市场...
						</div>
					) : filteredItems.length === 0 ? (
						<div className="p-10 rounded-[18px] bg-[rgba(15,23,42,0.03)] border border-white/10">
							<div className="text-base font-semibold mb-1.5">{emptyState.title}</div>
							<div className="text-[13px] opacity-[0.68] leading-relaxed">{emptyState.description}</div>
							{(error || items.length === 0) && (
								<button
									type="button"
									onClick={() => void loadItems()}
									className="mt-4 px-3.5 py-2 rounded-[10px] border border-brand/25 bg-brand/10 text-brand cursor-pointer"
								>
									重新加载
								</button>
							)}
						</div>
					) : (
						<div
							data-testid="analytics-marketplace-grid"
							className="grid gap-4"
							style={{ gridTemplateColumns: 'repeat(auto-fill, minmax(260px, 1fr))' }}
						>
							{filteredItems.map((item) => {
								const isSelected = selectedItem?.id === item.id;
								return (
									<button
										key={item.id}
										type="button"
										onClick={() => setSelectedId(item.id)}
										data-testid={`analytics-marketplace-card-${item.id}`}
										className="text-left rounded-2xl overflow-hidden p-0 cursor-pointer text-inherit transition-all duration-200"
										style={{
											border: isSelected ? '1px solid rgba(59,130,246,0.32)' : '1px solid rgba(148,163,184,0.18)',
											background: isSelected ? 'rgba(59,130,246,0.06)' : 'rgba(148,163,184,0.04)',
											boxShadow: isSelected ? '0 18px 36px rgba(59,130,246,0.12)' : '0 8px 18px rgba(15,23,42,0.05)',
										}}
									>
										<div className="h-[124px] flex items-center justify-between p-4" style={{ background: `linear-gradient(135deg, ${cardAccent(activeTab)}, rgba(15,23,42,0.06))` }}>
											<div>
												<div className="text-xs tracking-wide opacity-[0.68] uppercase">
													{item.category || (activeTab === 'components' ? 'component' : 'template')}
												</div>
												<div className="mt-2.5 text-2xl font-bold">{item.name || item.id}</div>
											</div>
											<div
												className="px-2.5 py-1.5 rounded-full text-[11px] font-semibold"
												style={{
													background: item.installed ? 'rgba(16,185,129,0.16)' : 'rgba(255,255,255,0.46)',
													color: item.installed ? '#047857' : 'rgba(15,23,42,0.65)',
												}}
											>
												{item.installed ? '已安装' : '可安装'}
											</div>
										</div>
										<div className="p-4">
											<div className="text-[13px] leading-relaxed min-h-[42px] opacity-[0.74]">
												{item.description || '暂无详细说明'}
											</div>
											<div className="flex gap-1.5 flex-wrap mt-3">
												{(item.tags || []).slice(0, 4).map((tag) => (
													<span
														key={tag}
														className="text-[10px] px-2 py-0.5 rounded-full border border-white/15 opacity-[0.72]"
													>
														{tag}
													</span>
												))}
											</div>
											<div className="flex justify-between items-center mt-3.5">
												<span className="text-[11px] opacity-[0.58]">
													{item.author || '系统'}{item.downloads != null ? ` · ${item.downloads} 次安装` : ''}
												</span>
												<span className="text-[11px] opacity-[0.48]">
													{formatTime(item.updatedAt || item.createdAt) || '刚刚更新'}
												</span>
											</div>
										</div>
									</button>
								);
							})}
						</div>
					)}
				</div>

				<aside
					data-testid="analytics-marketplace-detail"
					className="w-[340px] sticky top-6 self-start rounded-[18px] border border-white/10 p-[18px] shadow-[0_18px_48px_rgba(15,23,42,0.10)]"
					style={{ background: 'linear-gradient(180deg, rgba(255,255,255,0.96), rgba(248,250,252,0.96))' }}
				>
					{selectedItem ? (
						<>
							<div className="flex justify-between items-center gap-3">
								<div>
									<div className="text-xs tracking-wide opacity-[0.58] uppercase">
										{activeTab === 'components' ? 'Component Detail' : 'Template Detail'}
									</div>
									<h2 className="mt-2 mb-0 text-xl">{selectedItem.name || selectedItem.id}</h2>
								</div>
								<div
									className="px-2.5 py-1.5 rounded-full text-[11px] font-bold"
									style={{
										background: selectedItem.installed ? 'rgba(16,185,129,0.12)' : 'rgba(59,130,246,0.10)',
										color: selectedItem.installed ? '#047857' : '#2563eb',
									}}
								>
									{selectedItem.installed ? '已安装' : '待安装'}
								</div>
							</div>
							<div className="mt-3.5 text-[13px] leading-[1.72] opacity-[0.74]">
								{selectedItem.description || '当前条目尚未提供更多说明。'}
							</div>
							<div className="mt-[18px] grid gap-3">
								<div className="p-3 rounded-[14px] bg-[rgba(15,23,42,0.03)]">
									<div className="text-[11px] uppercase opacity-[0.52]">发布信息</div>
									<div className="mt-1.5 text-[13px]">
										作者：{selectedItem.author || '系统'}
										<br />
										版本：{selectedItem.version || '-'}
										<br />
										分类：{selectedItem.category || '-'}
									</div>
								</div>
								<div className="p-3 rounded-[14px] bg-[rgba(15,23,42,0.03)]">
									<div className="text-[11px] uppercase opacity-[0.52]">标签与轨迹</div>
									<div className="mt-2 flex gap-1.5 flex-wrap">
										{(selectedItem.tags || []).length > 0 ? (selectedItem.tags || []).map((tag) => (
											<span
												key={tag}
												className="text-[10px] px-2 py-[3px] rounded-full bg-brand/10 text-[#2563eb]"
											>
												{tag}
											</span>
										)) : <span className="text-xs opacity-[0.58]">暂无标签</span>}
									</div>
									<div className="mt-2.5 text-xs opacity-[0.62]">
										最近更新：{formatTime(selectedItem.updatedAt || selectedItem.createdAt) || '未知'}
									</div>
								</div>
							</div>
							<button
								type="button"
								onClick={() => void handleInstall(selectedItem)}
								disabled={selectedItem.installed || installing === selectedItem.id}
								data-testid="analytics-marketplace-install"
								className="mt-[18px] w-full px-3.5 py-3 rounded-xl border-none text-[13px] font-bold cursor-pointer disabled:cursor-default"
								style={{
									background: selectedItem.installed
										? 'rgba(148,163,184,0.18)'
										: 'linear-gradient(135deg, #2563eb, #0ea5e9)',
									color: selectedItem.installed ? 'rgba(15,23,42,0.62)' : '#fff',
								}}
							>
								{installing === selectedItem.id ? '安装中...' : selectedItem.installed ? '已安装，可直接使用' : '安装到当前空间'}
							</button>
						</>
					) : (
						<div className="text-[13px] opacity-[0.64] leading-[1.7]">
							选择左侧卡片后，这里会显示更完整的安装说明、作者、标签与状态信息。
						</div>
					)}
				</aside>
			</div>
		</div>
	);
}
