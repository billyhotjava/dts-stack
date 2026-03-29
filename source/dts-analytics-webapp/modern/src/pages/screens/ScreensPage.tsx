import { useEffect, useState, useCallback, useMemo, useRef } from 'react';
import { useNavigate } from 'react-router';
import { analyticsApi, ScreenListItem, type ScreenAiGenerationResponse } from '../../api/analyticsApi';
import { PageContainer } from '../../components/PageContainer/PageContainer';
import { writeTextToClipboard } from '../../hooks/clipboard';
import { TemplateGallery, type TemplateSelection } from './components';
import { createConfigFromTemplate } from './screenTemplates';
import { buildScreenPayload, normalizeScreenConfig } from './specV2';
const SCREEN_LIST_PREF_KEY = 'dts.analytics.screens.listPref.v1';

export default function ScreensPage() {
	const navigate = useNavigate();
	const [screens, setScreens] = useState<ScreenListItem[]>([]);
	const [loading, setLoading] = useState(true);
	const [error, setError] = useState<string | null>(null);
	const [showTemplateGallery, setShowTemplateGallery] = useState(false);
	const [sharingId, setSharingId] = useState<string | number | null>(null);
	const [savingTemplateId, setSavingTemplateId] = useState<string | number | null>(null);

	const [showAiGenerator, setShowAiGenerator] = useState(false);
	const [aiPrompt, setAiPrompt] = useState('');
	const [aiLoading, setAiLoading] = useState(false);
	const [aiRefining, setAiRefining] = useState(false);
	const [aiCreating, setAiCreating] = useState(false);
	const [aiRefinePrompt, setAiRefinePrompt] = useState('');
	const [aiRefineMode, setAiRefineMode] = useState<'apply' | 'suggest'>('apply');
	const [aiResult, setAiResult] = useState<ScreenAiGenerationResponse | null>(null);
	const [aiContextHistory, setAiContextHistory] = useState<string[]>([]);
	const [activeCardMenuId, setActiveCardMenuId] = useState<string | number | null>(null);
	const [searchKeyword, setSearchKeyword] = useState(() => {
		if (typeof window === 'undefined') return '';
		try {
			const raw = window.localStorage.getItem(SCREEN_LIST_PREF_KEY);
			if (!raw) return '';
			const parsed = JSON.parse(raw) as { searchKeyword?: string };
			return String(parsed.searchKeyword || '');
		} catch {
			return '';
		}
	});
	const [publishFilter, setPublishFilter] = useState<'all' | 'published' | 'draft'>(() => {
		if (typeof window === 'undefined') return 'all';
		try {
			const raw = window.localStorage.getItem(SCREEN_LIST_PREF_KEY);
			if (!raw) return 'all';
			const parsed = JSON.parse(raw) as { publishFilter?: string };
			return parsed.publishFilter === 'published' || parsed.publishFilter === 'draft' ? parsed.publishFilter : 'all';
		} catch {
			return 'all';
		}
	});
	const [sortMode, setSortMode] = useState<'updated-desc' | 'updated-asc' | 'name-asc' | 'name-desc'>(() => {
		if (typeof window === 'undefined') return 'updated-desc';
		try {
			const raw = window.localStorage.getItem(SCREEN_LIST_PREF_KEY);
			if (!raw) return 'updated-desc';
			const parsed = JSON.parse(raw) as { sortMode?: string };
			if (parsed.sortMode === 'updated-asc' || parsed.sortMode === 'name-asc' || parsed.sortMode === 'name-desc') {
				return parsed.sortMode;
			}
			return 'updated-desc';
		} catch {
			return 'updated-desc';
		}
	});
	const searchInputRef = useRef<HTMLInputElement | null>(null);

	useEffect(() => {
		if (activeCardMenuId === null) {
			return;
		}
		const handlePointerDown = (event: MouseEvent) => {
			const node = event.target as HTMLElement | null;
			if (!node?.closest('.screen-card-menu')) {
				setActiveCardMenuId(null);
			}
		};
		const handleEscape = (event: KeyboardEvent) => {
			if (event.key === 'Escape') {
				setActiveCardMenuId(null);
			}
		};
		window.addEventListener('mousedown', handlePointerDown);
		window.addEventListener('keydown', handleEscape);
		return () => {
			window.removeEventListener('mousedown', handlePointerDown);
			window.removeEventListener('keydown', handleEscape);
		};
	}, [activeCardMenuId]);

	useEffect(() => {
		if (typeof window === 'undefined') return;
		window.localStorage.setItem(
			SCREEN_LIST_PREF_KEY,
			JSON.stringify({ searchKeyword, publishFilter, sortMode }),
		);
	}, [publishFilter, searchKeyword, sortMode]);
	useEffect(() => {
		const isTypingTarget = (target: EventTarget | null): boolean => {
			const node = target as HTMLElement | null;
			if (!node) return false;
			const tag = node.tagName;
			if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return true;
			return node.isContentEditable;
		};
		const handleKeyDown = (event: KeyboardEvent) => {
			if (event.ctrlKey || event.metaKey || event.altKey) {
				return;
			}
			if (event.key === '/') {
				if (!isTypingTarget(event.target)) {
					event.preventDefault();
					searchInputRef.current?.focus();
					searchInputRef.current?.select();
				}
				return;
			}
			if (event.key === 'Escape' && searchKeyword) {
				if (!isTypingTarget(event.target)) {
					event.preventDefault();
					setSearchKeyword('');
				}
			}
		};
		window.addEventListener('keydown', handleKeyDown);
		return () => window.removeEventListener('keydown', handleKeyDown);
	}, [searchKeyword]);

	const loadScreens = useCallback(() => {
		setLoading(true);
		analyticsApi.listScreens()
			.then((data) => {
				setScreens(data);
				setLoading(false);
			})
			.catch((err) => {
				console.error('Failed to load screens:', err);
				setError('加载大屏列表失败');
				setLoading(false);
			});
	}, []);

	useEffect(() => {
		loadScreens();
	}, [loadScreens]);

	const publishedCount = useMemo(
		() => screens.filter((item) => Number(item.publishedVersionNo || 0) > 0).length,
		[screens],
	);
	const draftCount = Math.max(0, screens.length - publishedCount);
	const visibleScreens = useMemo(() => {
		const keyword = searchKeyword.trim().toLowerCase();
		const filtered = screens.filter((item) => {
			const published = Number(item.publishedVersionNo || 0) > 0;
			if (publishFilter === 'published' && !published) return false;
			if (publishFilter === 'draft' && published) return false;
			if (!keyword) return true;
			const name = String(item.name || '').toLowerCase();
			const desc = String(item.description || '').toLowerCase();
			return name.includes(keyword) || desc.includes(keyword);
		});
		filtered.sort((a, b) => {
			if (sortMode === 'updated-asc') {
				return (new Date(a.updatedAt || 0).getTime()) - (new Date(b.updatedAt || 0).getTime());
			}
			if (sortMode === 'name-asc') {
				return String(a.name || '').localeCompare(String(b.name || ''), 'zh-CN');
			}
			if (sortMode === 'name-desc') {
				return String(b.name || '').localeCompare(String(a.name || ''), 'zh-CN');
			}
			return (new Date(b.updatedAt || 0).getTime()) - (new Date(a.updatedAt || 0).getTime());
		});
		return filtered;
	}, [publishFilter, screens, searchKeyword, sortMode]);

	const handleCreate = () => {
		setShowTemplateGallery(true);
	};

	const handleOpenAiGenerator = () => {
		setAiPrompt('生成一个面向运营的周报大屏，包含趋势、结构占比、区域排名和明细表');
		setAiRefinePrompt('改成三列布局，增加区域筛选，切换为浅色商务风格，刷新30秒并放大字体');
		setAiRefineMode('apply');
		setAiResult(null);
		setAiContextHistory([]);
		setShowAiGenerator(true);
	};

	const handleTemplateSelect = async (selection: TemplateSelection) => {
		setShowTemplateGallery(false);

		try {
			if (selection.kind === 'asset') {
				const remoteTemplate = selection.template;
				const response = await analyticsApi.createScreenFromTemplate(remoteTemplate.id as string | number, {
					name: (remoteTemplate.name || '未命名模板') + ' 副本',
				});
				navigate(`/screens/${response.id}/edit`);
				return;
			}

			const config = createConfigFromTemplate(selection.template);
			navigate('/screens/new', {
				state: {
					initialConfig: {
						id: '',
						...config,
					},
				},
			});
		} catch (err) {
			console.error('Failed to create screen from template:', err);
			navigate('/screens/new');
		}
	};

	const handleGenerateAi = async () => {
		if (aiLoading) return;
		const prompt = aiPrompt.trim();
		if (!prompt) {
			alert('请输入业务需求描述');
			return;
		}

		setAiLoading(true);
		try {
			const result = await analyticsApi.generateScreenSpec({
				prompt,
				width: 1920,
				height: 1080,
			});
			setAiResult(result);
			setAiContextHistory((prev) => ([...prev, `初始需求: ${prompt}`]).slice(-12));
		} catch (err) {
			console.error('Failed to generate ai screen spec:', err);
			alert('AI 生成失败');
		} finally {
			setAiLoading(false);
		}
	};

	const handleCreateFromAi = async () => {
		if (aiCreating) return;
		const spec = aiResult?.screenSpec;
		if (!spec) {
			alert('请先生成方案');
			return;
		}
		const pendingVariables = aiResult?.nl2sqlDiagnostics?.pendingVariables ?? [];
		if (pendingVariables.length > 0) {
			const preview = pendingVariables.slice(0, 6).join('、');
			const confirmed = window.confirm(
				`当前仍有 ${pendingVariables.length} 个待补参数（${preview}${pendingVariables.length > 6 ? '...' : ''}），继续创建草稿吗？`,
			);
			if (!confirmed) {
				return;
			}
		}

		setAiCreating(true);
		try {
			const normalized = normalizeScreenConfig(spec, { id: '' });
			const created = await analyticsApi.createScreen(buildScreenPayload({
				...normalized.config,
				name: spec.name || normalized.config.name || 'AI生成大屏草稿',
				description: spec.description || normalized.config.description || 'AI自动生成',
			}));
			setShowAiGenerator(false);
			navigate(`/screens/${created.id}/edit`);
		} catch (err) {
			console.error('Failed to create screen from ai spec:', err);
			alert('创建 AI 草稿失败');
		} finally {
			setAiCreating(false);
		}
	};

	const handleRefineAi = async () => {
		if (aiRefining) return;
		const prompt = aiRefinePrompt.trim();
		const screenSpec = aiResult?.screenSpec;
		if (!prompt) {
			alert('请输入优化指令');
			return;
		}
		if (!screenSpec) {
			alert('请先生成初始方案');
			return;
		}
		setAiRefining(true);
		try {
			const result = await analyticsApi.reviseScreenSpec({
				prompt,
				screenSpec: screenSpec as Record<string, unknown>,
				context: aiContextHistory.slice(-8),
				mode: aiRefineMode,
			});
			setAiResult(result);
			setAiContextHistory((prev) => {
				const modeLabel = aiRefineMode === 'suggest' ? '建议模式' : '应用模式';
				const next = [...prev, `优化指令(${modeLabel}): ${prompt}`];
				if (Array.isArray(result.actions) && result.actions.length > 0) {
					next.push(`执行结果: ${result.actions.join('；')}`);
				}
				return next.slice(-12);
			});
		} catch (err) {
			console.error('Failed to refine ai screen spec:', err);
			alert('AI 优化失败');
		} finally {
			setAiRefining(false);
		}
	};

	const handleCopyAiRecommendations = async () => {
		if (!aiResult) {
			alert('请先生成 AI 方案');
			return;
		}
		const payload = {
			engine: aiResult.engine || 'heuristic-v1',
			prompt: aiResult.prompt || aiPrompt.trim(),
			intent: aiResult.intent || {},
			semanticModelHints: aiResult.semanticModelHints || {},
			queryRecommendations: aiResult.queryRecommendations || [],
			sqlBlueprints: aiResult.sqlBlueprints || [],
			vizRecommendations: aiResult.vizRecommendations || [],
			semanticRecall: aiResult.semanticRecall || {},
			metricLensReferences: aiResult.metricLensReferences || [],
			nl2sqlDiagnostics: aiResult.nl2sqlDiagnostics || {},
			quality: aiResult.quality || {},
			actions: aiResult.actions || [],
		};
		const copied = await writeTextToClipboard(JSON.stringify(payload, null, 2));
		if (!copied) {
			alert('复制失败，请稍后重试');
			return;
		}
		alert('AI建议已复制到剪贴板');
	};

	const handleEdit = (id: string | number) => {
		navigate(`/screens/${id}/edit`);
	};

	const handlePreview = (id: string | number) => {
		window.open(`/analytics/screens/${id}/preview`, '_blank', 'noopener,noreferrer');
	};

	const getPreviewUrl = useCallback(
		(id: string | number) => `${window.location.origin}/analytics/screens/${encodeURIComponent(String(id))}/preview`,
		[],
	);

	const handleCopyPreviewUrl = async (id: string | number) => {
		const url = getPreviewUrl(id);
		const copied = await writeTextToClipboard(url);
		alert(copied ? '预览链接已复制到剪贴板' : `复制失败，请手工复制：\n${url}`);
	};

	const handleShare = async (id: string | number) => {
		if (sharingId !== null) return;
		setSharingId(id);
		try {
			const { uuid } = await analyticsApi.createScreenPublicLink(id);
			const url = `${window.location.origin}/analytics/public/screen/${uuid}`;
			const copied = await writeTextToClipboard(url);
			alert(copied ? '分享链接已复制到剪贴板' : `复制失败，请手工复制：\n${url}`);
		} catch (err) {
			console.error('Failed to create public link:', err);
			alert('创建分享链接失败');
		} finally {
			setSharingId(null);
		}
	};

	const handleSaveAsTemplate = async (id: string | number, screenName?: string) => {
		if (savingTemplateId !== null) return;

		const suggestedName = `${(screenName || '未命名大屏').trim() || '未命名大屏'} 模板`;
		const name = (window.prompt('请输入模板名称', suggestedName) || '').trim();
		if (!name) {
			return;
		}

		const categoryInput = (window.prompt('模板分类（business/tech/dashboard/monitor/custom）', 'custom') || 'custom').trim();
		const category = categoryInput || 'custom';

		setSavingTemplateId(id);
		try {
			await analyticsApi.createScreenTemplateFromScreen(id, {
				name,
				category,
				tags: ['saved-from-screen'],
			});
			alert('已保存到模板资产中心');
		} catch (err) {
			console.error('Failed to create template from screen:', err);
			alert('保存模板失败');
		} finally {
			setSavingTemplateId(null);
		}
	};

	const handleDelete = async (id: string | number) => {
		if (!confirm('确定要删除这个大屏吗？')) return;

		try {
			await analyticsApi.deleteScreen(id);
			loadScreens();
		} catch (err) {
			console.error('Failed to delete screen:', err);
			alert('删除失败');
		}
	};

	const formatDate = (dateStr?: string) => {
		if (!dateStr) return '-';
		const date = new Date(dateStr);
		return date.toLocaleDateString('zh-CN', {
			year: 'numeric',
			month: '2-digit',
			day: '2-digit',
			hour: '2-digit',
			minute: '2-digit',
		});
	};

	return (
		<PageContainer>
			<div className="space-y-4" data-testid="analytics-screens-page">
				<div className="flex items-center justify-between gap-3 pt-1 flex-wrap">
					<h1 className="m-0 text-xl font-semibold text-text-primary">大屏管理</h1>
					<div className="flex gap-2.5">
						<button className="inline-flex items-center justify-center h-8 px-4 text-sm font-normal leading-normal border border-brand rounded-md bg-brand text-white cursor-pointer transition-all duration-200 whitespace-nowrap hover:opacity-85 disabled:opacity-50 disabled:cursor-not-allowed" onClick={handleOpenAiGenerator}>
							自动生成
						</button>
						<button className="inline-flex items-center justify-center h-8 px-4 text-sm font-normal leading-normal border border-brand rounded-md bg-brand text-white cursor-pointer transition-all duration-200 whitespace-nowrap hover:opacity-85 disabled:opacity-50 disabled:cursor-not-allowed" data-testid="analytics-screen-create" onClick={handleCreate}>
							新建大屏
						</button>
					</div>
				</div>

				<div className="space-y-4">
					<div className="flex items-center justify-between gap-2.5 rounded-lg border border-border-default bg-surface-card px-4 py-3 flex-wrap">
						<div className="flex items-center gap-2 flex-wrap">
							<input
								ref={searchInputRef}
								className="min-w-[240px] max-w-[340px] w-[34vw] border border-border-default rounded-lg px-2.5 py-2 bg-surface-card text-text-primary text-[13px]"
								value={searchKeyword}
								onChange={(e) => setSearchKeyword(e.target.value)}
								placeholder="搜索大屏名称或描述（/）"
							/>
							<select
								className="border border-border-default rounded-lg px-2.5 py-2 bg-surface-card text-text-primary text-[13px]"
								value={publishFilter}
								onChange={(e) => {
									const next = e.target.value;
									if (next === 'published' || next === 'draft') {
										setPublishFilter(next);
										return;
									}
									setPublishFilter('all');
								}}
							>
								<option value="all">全部状态</option>
								<option value="published">仅已发布</option>
								<option value="draft">仅未发布</option>
							</select>
							<select
								className="border border-border-default rounded-lg px-2.5 py-2 bg-surface-card text-text-primary text-[13px]"
								value={sortMode}
								onChange={(e) => {
									const next = e.target.value;
									if (next === 'updated-asc' || next === 'name-asc' || next === 'name-desc') {
										setSortMode(next);
										return;
									}
									setSortMode('updated-desc');
								}}
							>
								<option value="updated-desc">按更新时间(新→旧)</option>
								<option value="updated-asc">按更新时间(旧→新)</option>
								<option value="name-asc">按名称(A→Z)</option>
								<option value="name-desc">按名称(Z→A)</option>
							</select>
							<button
								type="button"
								className="border border-border-default rounded-lg px-2.5 py-2 bg-surface-card text-text-primary text-[13px] cursor-pointer hover:border-brand hover:bg-brand/10"
								onClick={() => {
									setSearchKeyword('');
									setPublishFilter('all');
									setSortMode('updated-desc');
								}}
								title="恢复默认筛选与排序"
							>
								重置
							</button>
						</div>
						<div className="text-xs text-text-secondary">
							总计 {screens.length} · 已发布 {publishedCount} · 未发布 {draftCount} · 当前 {visibleScreens.length}
						</div>
					</div>
					{loading ? (
						<div className="flex flex-col items-center justify-center py-[60px] gap-4">
							<div className="w-8 h-8 border-[3px] border-border-default border-t-brand rounded-full animate-spin" />
							<span>加载中...</span>
						</div>
					) : error ? (
						<div className="flex flex-col items-center justify-center py-[60px] gap-4">
							<span>{error}</span>
							<button onClick={loadScreens}>重试</button>
						</div>
					) : screens.length === 0 ? (
						<div className="flex flex-col items-center justify-center px-5 py-10 text-center">
							<div className="text-5xl text-text-muted mb-4">屏</div>
							<div className="text-sm text-text-secondary">暂无大屏</div>
							<div className="text-xs text-text-muted mt-2">点击"新建大屏"创建您的第一个数据大屏</div>
							<button className="inline-flex items-center justify-center h-8 px-4 text-sm font-normal leading-normal border border-brand rounded-md bg-brand text-white cursor-pointer transition-all duration-200 whitespace-nowrap hover:opacity-85 mt-4" onClick={handleCreate}>
								新建大屏
							</button>
						</div>
					) : visibleScreens.length === 0 ? (
						<div className="flex flex-col items-center justify-center px-5 py-10 text-center">
							<div className="text-5xl text-text-muted mb-4">筛</div>
							<div className="text-sm text-text-secondary">没有匹配结果</div>
							<div className="text-xs text-text-muted mt-2">尝试清空搜索词或调整状态筛选</div>
							<button
								className="inline-flex items-center justify-center h-8 px-4 text-sm font-normal leading-normal border border-brand rounded-md bg-brand text-white cursor-pointer transition-all duration-200 whitespace-nowrap hover:opacity-85 mt-4"
								onClick={() => {
									setSearchKeyword('');
									setPublishFilter('all');
								}}
							>
								重置筛选
							</button>
						</div>
					) : (
						<div className="grid gap-5 p-5" style={{ gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))' }}>
						{visibleScreens.map((screen) => (
							<div key={screen.id} className="relative bg-surface-card border border-border-default rounded-lg overflow-visible transition-all duration-200 hover:border-brand hover:shadow-[0_4px_12px_rgba(0,0,0,0.1)]" data-testid={`analytics-screen-card-${screen.id}`}>
								<div
									className="relative h-40 flex items-center justify-center cursor-pointer rounded-t-lg overflow-hidden border-b border-border-default"
									style={{ background: 'linear-gradient(135deg, rgba(84,123,255,0.08) 0%, rgba(34,197,94,0.06) 50%, rgba(168,85,247,0.06) 100%)' }}
									data-testid={`analytics-screen-edit-${screen.id}`}
									onClick={() => handleEdit(screen.id)}
								>
									<div className="text-[32px] opacity-[0.18] font-bold tracking-wider text-text-primary">
										<svg width="48" height="36" viewBox="0 0 48 36" fill="none" xmlns="http://www.w3.org/2000/svg">
											<rect x="2" y="2" width="44" height="28" rx="3" stroke="currentColor" strokeWidth="2.5" />
											<line x1="18" y1="34" x2="30" y2="34" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" />
											<line x1="24" y1="30" x2="24" y2="34" stroke="currentColor" strokeWidth="2.5" />
										</svg>
									</div>
										<div className="absolute bottom-2 right-2 px-2 py-1 bg-surface-card text-text-secondary border border-border-default rounded text-[11px] font-semibold backdrop-blur-sm">
											{screen.width || 1920} × {screen.height || 1080}
										</div>
									</div>
								<div className="p-4">
									<h3 className="m-0 mb-2 text-base font-semibold text-text-primary">{screen.name || '未命名大屏'}</h3>
									<p className="m-0 mb-2 text-xs text-text-secondary overflow-hidden text-ellipsis whitespace-nowrap">
										{screen.description || '无描述'}
									</p>
									<div className="text-[11px] text-text-muted grid gap-1">
										<div className="flex items-center gap-2 flex-wrap">
											<span className={`inline-flex items-center rounded-full px-2 py-0.5 text-[11px] font-semibold border border-transparent ${screen.publishedVersionNo ? 'text-[#166534] bg-success/10 border-success/30' : 'text-[#9a3412] bg-warning/10 border-warning/30'}`}>
												{screen.publishedVersionNo ? `已发布 v${screen.publishedVersionNo}` : '未发布'}
											</span>
											{screen.publishedAt ? (
												<span>发布: {formatDate(screen.publishedAt)}</span>
											) : null}
										</div>
										<span>更新: {formatDate(screen.updatedAt)}</span>
										{screen.publishedVersionNo ? (
											<div className="flex items-center gap-1.5">
												<a
													href={getPreviewUrl(screen.id)}
													target="_blank"
													rel="noreferrer"
													className="text-brand no-underline max-w-[170px] overflow-hidden text-ellipsis whitespace-nowrap hover:underline"
													title="打开预览链接"
												>
													预览链接
												</a>
												<button
													type="button"
													className="border border-border-default rounded-md bg-surface-card text-text-primary text-[11px] px-1.5 py-0.5 cursor-pointer hover:border-brand hover:bg-brand/10"
													onClick={() => {
														void handleCopyPreviewUrl(screen.id);
													}}
													title="复制预览链接"
												>
													复制
												</button>
											</div>
										) : null}
									</div>
								</div>
								<div className="flex gap-2 px-4 py-3 border-t border-border-default items-center">
									<button
										className="flex-1 py-2 border border-border-default rounded-md bg-surface-card cursor-pointer text-xs font-medium transition-all duration-200 hover:border-brand hover:bg-brand/10"
										data-testid={`analytics-screen-edit-button-${screen.id}`}
										onClick={() => handleEdit(screen.id)}
										title="编辑大屏"
									>
										编辑
									</button>
									<button
										className="flex-1 py-2 border border-border-default rounded-md bg-surface-card cursor-pointer text-xs font-medium transition-all duration-200 hover:border-brand hover:bg-brand/10"
										data-testid={`analytics-screen-preview-${screen.id}`}
										onClick={() => handlePreview(screen.id)}
										title="预览大屏"
									>
										预览
									</button>
									<div className="screen-card-menu relative flex-1 z-[2]">
										<button
											className={`w-full py-2 border border-border-default rounded-md bg-surface-card cursor-pointer text-xs font-medium transition-all duration-200 hover:border-brand hover:bg-brand/10 ${activeCardMenuId === screen.id ? 'border-brand bg-brand/10' : ''}`}
											onClick={() => setActiveCardMenuId((prev) => (prev === screen.id ? null : screen.id))}
											title="更多操作"
										>
											更多
										</button>
										{activeCardMenuId === screen.id ? (
											<div className="absolute right-0 top-[calc(100%+6px)] min-w-[160px] z-[900] bg-surface-card text-text-primary opacity-100 border border-border-default rounded-lg shadow-[0_8px_24px_rgba(15,23,42,0.2)] p-1.5 grid gap-1">
												<button
													type="button"
													className="border border-transparent rounded-md px-2 py-[7px] bg-transparent text-text-primary text-xs text-left cursor-pointer hover:border-brand hover:bg-brand/10 disabled:opacity-55 disabled:cursor-not-allowed"
													onClick={() => {
														setActiveCardMenuId(null);
														void handleShare(screen.id);
													}}
													disabled={sharingId === screen.id}
												>
													{sharingId === screen.id ? '生成分享链接中...' : '生成分享链接'}
												</button>
												<button
													type="button"
													className="border border-transparent rounded-md px-2 py-[7px] bg-transparent text-text-primary text-xs text-left cursor-pointer hover:border-brand hover:bg-brand/10"
													onClick={() => {
														setActiveCardMenuId(null);
														void handleCopyPreviewUrl(screen.id);
													}}
												>
													复制预览链接
												</button>
												<button
													type="button"
													className="border border-transparent rounded-md px-2 py-[7px] bg-transparent text-text-primary text-xs text-left cursor-pointer hover:border-brand hover:bg-brand/10 disabled:opacity-55 disabled:cursor-not-allowed"
													onClick={() => {
														setActiveCardMenuId(null);
														void handleSaveAsTemplate(screen.id, screen.name);
													}}
													disabled={savingTemplateId === screen.id}
												>
													{savingTemplateId === screen.id ? '保存模板中...' : '保存为模板'}
												</button>
												<button
													type="button"
													className="border border-transparent rounded-md px-2 py-[7px] bg-transparent text-text-primary text-xs text-left cursor-pointer hover:border-error hover:bg-error/10"
													onClick={() => {
														setActiveCardMenuId(null);
														void handleDelete(screen.id);
													}}
												>
													删除大屏
												</button>
											</div>
										) : null}
									</div>
								</div>
							</div>
						))}
					</div>
				)}
				</div>
			</div>

			<style>{`
				@keyframes spin {
					to { transform: rotate(360deg); }
				}
			`}</style>

			{showTemplateGallery && (
				<TemplateGallery
					onSelect={handleTemplateSelect}
					onClose={() => setShowTemplateGallery(false)}
				/>
			)}

			{showAiGenerator && (
				<div className="fixed inset-0 bg-[rgba(10,18,32,0.6)] flex items-center justify-center z-[1400]" onClick={() => setShowAiGenerator(false)}>
					<div className="w-[min(920px,92vw)] max-h-[86vh] overflow-auto bg-[#0f172a] border border-white/20 rounded-xl shadow-[0_24px_80px_rgba(2,6,23,0.45)] text-[#e2e8f0]" onClick={(e) => e.stopPropagation()}>
						<div className="flex justify-between items-center px-5 py-4 border-b border-white/15">
							<h3 className="m-0">AI 生成大屏草稿</h3>
							<button className="flex-none py-2 px-3 border border-white/20 rounded-md bg-[rgba(30,41,59,0.8)] text-[#e2e8f0] text-xs cursor-pointer hover:bg-[rgba(51,65,85,0.9)] hover:border-white/40" style={{ maxWidth: 80 }} onClick={() => setShowAiGenerator(false)}>关闭</button>
						</div>
						<div className="px-5 py-[18px] grid gap-3">
							<div className="text-[13px] text-[#94a3b8]">
								描述业务场景、核心指标、时间粒度，系统将生成可编辑大屏草稿（可再绑定真实数据源）。
							</div>
							<textarea
								className="w-full min-h-[120px] border border-white/20 rounded-lg px-3 py-2.5 text-sm leading-relaxed resize-y bg-[#0b1222] text-[#e2e8f0]"
								value={aiPrompt}
								onChange={(e) => setAiPrompt(e.target.value)}
								placeholder="示例：生成一个制造车间运营大屏，包含产量趋势、良率、设备告警、班组排名和明细表"
							/>
							<textarea
								className="w-full min-h-[78px] border border-white/20 rounded-lg px-3 py-2.5 text-sm leading-relaxed resize-y bg-[#0b1222] text-[#e2e8f0]"
								value={aiRefinePrompt}
								onChange={(e) => setAiRefinePrompt(e.target.value)}
								placeholder="优化指令示例：改成三列布局，首图改成柱状图，切换为浅色主题，增加筛选器，加tab切换场景，移除tab切换，刷新30秒，放大字体"
							/>
							<div className="flex items-center gap-2">
								<label className="text-xs text-[#94a3b8] min-w-[88px]">优化模式</label>
								<select
									className="bg-[#0b1222] text-[#e2e8f0] border border-white/20 rounded-md px-2.5 py-1.5 text-[13px] max-w-[180px]"
									value={aiRefineMode}
									onChange={(e) => setAiRefineMode(e.target.value === 'suggest' ? 'suggest' : 'apply')}
								>
									<option value="apply">应用模式（默认）</option>
									<option value="suggest">建议模式（不自动发布）</option>
								</select>
							</div>
							{aiContextHistory.length > 0 && (
								<div className="border border-white/15 rounded-lg px-3 py-2.5 bg-[rgba(15,23,42,0.45)]">
									<div className="flex justify-between items-center">
										<div className="font-semibold text-xs">多轮上下文（最近 12 条）</div>
										<button
											className="flex-none py-2 px-3 border border-white/20 rounded-md bg-[rgba(30,41,59,0.8)] text-[#e2e8f0] text-xs cursor-pointer hover:bg-[rgba(51,65,85,0.9)] hover:border-white/40"
											style={{ maxWidth: 100 }}
											onClick={() => setAiContextHistory([])}
										>
											清空上下文
										</button>
									</div>
									<ol className="mt-2 pl-[18px] grid gap-1 max-h-[140px] overflow-auto text-xs text-[#cbd5e1]">
										{aiContextHistory.map((item, index) => (
											<li key={`${item}-${index}`}>{item}</li>
										))}
									</ol>
								</div>
							)}
							{aiResult?.screenSpec && (
								<div className="border border-white/15 rounded-lg p-3 bg-[rgba(15,23,42,0.7)]">
									<div className="font-semibold">生成预览</div>
									<div className="grid gap-2 mt-2" style={{ gridTemplateColumns: 'repeat(4, minmax(0, 1fr))' }}>
										<div className="border border-white/15 rounded-md p-2 text-xs text-[#cbd5e1]">名称: {aiResult.screenSpec.name || '-'}</div>
										<div className="border border-white/15 rounded-md p-2 text-xs text-[#cbd5e1]">主题: {aiResult.screenSpec.theme || '-'}</div>
										<div className="border border-white/15 rounded-md p-2 text-xs text-[#cbd5e1]">组件数: {(aiResult.screenSpec.components || []).length}</div>
										<div className="border border-white/15 rounded-md p-2 text-xs text-[#cbd5e1]">质量分: {aiResult.quality?.score ?? '-'}</div>
										<div className="border border-white/15 rounded-md p-2 text-xs text-[#cbd5e1]">上下文条数: {aiResult.contextCount ?? 0}</div>
										<div className="border border-white/15 rounded-md p-2 text-xs text-[#cbd5e1]">有效上下文: {aiResult.usedContextCount ?? aiResult.contextCount ?? 0}</div>
										<div className="border border-white/15 rounded-md p-2 text-xs text-[#cbd5e1]">优化模式: {aiResult.applyMode || 'apply'}</div>
										<div className="border border-white/15 rounded-md p-2 text-xs text-[#cbd5e1]">领域: {aiResult.intent?.domain || '-'}</div>
										<div className="border border-white/15 rounded-md p-2 text-xs text-[#cbd5e1]">时间范围: {aiResult.intent?.timeRange || '-'}</div>
										<div className="border border-white/15 rounded-md p-2 text-xs text-[#cbd5e1]">粒度: {aiResult.intent?.granularity || '-'}</div>
									</div>
									{aiResult.intent && (
										<div className="mt-2.5 text-xs text-[#cbd5e1]">
											识别指标：{(aiResult.intent.metrics || []).join('、') || '-'}；维度：{(aiResult.intent.dimensions || []).join('、') || '-'}；筛选：{(aiResult.intent.filters || []).join('、') || '-'}
										</div>
									)}
									{Array.isArray(aiResult.quality?.warnings) && aiResult.quality?.warnings.length > 0 && (
										<div className="mt-2.5 text-xs text-[#fbbf24]">
											{aiResult.quality?.warnings.join('；')}
										</div>
									)}
									{Array.isArray(aiResult.actions) && aiResult.actions.length > 0 && (
										<div className="mt-2.5 text-xs text-[#38bdf8]">
											{aiResult.applyMode === 'suggest' ? '建议动作：' : '已执行：'}{aiResult.actions.join('；')}
										</div>
									)}
									{Array.isArray(aiResult.queryRecommendations) && aiResult.queryRecommendations.length > 0 && (
										<div className="mt-2.5 text-xs text-[#93c5fd]">
											查询建议：{aiResult.queryRecommendations.map((q) => `${q.id || '-'}(${q.purpose || '-'})`).join('；')}
										</div>
									)}
									{aiResult.semanticModelHints && (
										<div className="mt-2.5 text-xs text-[#60a5fa]">
											语义映射：事实表 {aiResult.semanticModelHints.factTable || '-'}，时间字段 {aiResult.semanticModelHints.timeField || '-'}
										</div>
									)}
									{Array.isArray(aiResult.sqlBlueprints) && aiResult.sqlBlueprints.length > 0 && (
										<div className="mt-2.5 text-xs text-[#bfdbfe]">
											SQL蓝图：{aiResult.sqlBlueprints.map((row) => `${row.queryId || '-'}(${row.purpose || '-'})`).join('；')}
										</div>
									)}
									{aiResult.nl2sqlDiagnostics && (
										<div className="mt-2.5 text-xs text-[#fca5a5]">
											NL2SQL诊断：状态 {aiResult.nl2sqlDiagnostics.status || '-'}，就绪度 {aiResult.nl2sqlDiagnostics.executionReadiness || '-'}，可执行 {aiResult.nl2sqlDiagnostics.executableBlueprintCount ?? aiResult.nl2sqlDiagnostics.safeCount ?? 0}，需补参 {aiResult.nl2sqlDiagnostics.needsParamsCount ?? 0}，阻断 {aiResult.nl2sqlDiagnostics.blockedCount ?? 0}
										</div>
									)}
									{Array.isArray(aiResult.nl2sqlDiagnostics?.requiredVariables) && aiResult.nl2sqlDiagnostics.requiredVariables.length > 0 && (
										<div className="mt-2 text-xs text-[#fda4af]">
											识别参数：{aiResult.nl2sqlDiagnostics.requiredVariables.slice(0, 8).join('、')}
										</div>
									)}
									{Array.isArray(aiResult.nl2sqlDiagnostics?.pendingVariables) && aiResult.nl2sqlDiagnostics.pendingVariables.length > 0 && (
										<div className="mt-2 text-xs text-[#fecdd3]">
											待补参数：{aiResult.nl2sqlDiagnostics.pendingVariables.slice(0, 8).join('、')}
										</div>
									)}
									{Array.isArray(aiResult.nl2sqlDiagnostics?.autoInjectedVariables) && aiResult.nl2sqlDiagnostics.autoInjectedVariables.length > 0 && (
										<div className="mt-2 text-xs text-[#fdba74]">
											自动补齐变量：{aiResult.nl2sqlDiagnostics.autoInjectedVariables.slice(0, 8).join('、')}
										</div>
									)}
									{Array.isArray(aiResult.nl2sqlDiagnostics?.blueprintChecks) && aiResult.nl2sqlDiagnostics!.blueprintChecks!.length > 0 && (
										<div className="mt-2 text-xs text-[#fecaca]">
											蓝图检查：{aiResult.nl2sqlDiagnostics!.blueprintChecks!.slice(0, 6).map((row) => `${String(row.queryId || '-')}:${String(row.status || '-')}`).join('；')}
										</div>
									)}
									{aiResult.semanticRecall && (
										<div className="mt-2.5 text-xs text-[#86efac]">
											语义召回：候选表/字段 {aiResult.semanticRecall.schemaCandidates?.length ?? 0}，同义词命中 {aiResult.semanticRecall.synonymHits?.length ?? 0}，few-shot {aiResult.semanticRecall.fewShotExamples?.length ?? 0}
										</div>
									)}
									{Array.isArray(aiResult.vizRecommendations) && aiResult.vizRecommendations.length > 0 && (
										<div className="mt-2.5 text-xs text-[#a7f3d0]">
											图表建议：{aiResult.vizRecommendations.map((v) => `${v.componentType || '-'}←${v.queryId || '-'}`).join('；')}
										</div>
									)}
								</div>
							)}
						</div>
						<div className="flex justify-end gap-2.5 px-5 py-3.5 border-t border-white/15">
							<button className="flex-none py-2 px-3 border border-white/20 rounded-md bg-[rgba(30,41,59,0.8)] text-[#e2e8f0] text-xs cursor-pointer hover:bg-[rgba(51,65,85,0.9)] hover:border-white/40 disabled:opacity-40 disabled:cursor-not-allowed" style={{ maxWidth: 100 }} onClick={() => setShowAiGenerator(false)}>取消</button>
							<button className="flex-none py-2 px-3 border border-white/20 rounded-md bg-[rgba(30,41,59,0.8)] text-[#e2e8f0] text-xs cursor-pointer hover:bg-[rgba(51,65,85,0.9)] hover:border-white/40 disabled:opacity-40 disabled:cursor-not-allowed" style={{ maxWidth: 120 }} onClick={handleGenerateAi} disabled={aiLoading}>
								{aiLoading ? '生成中...' : '生成方案'}
							</button>
							<button className="flex-none py-2 px-3 border border-white/20 rounded-md bg-[rgba(30,41,59,0.8)] text-[#e2e8f0] text-xs cursor-pointer hover:bg-[rgba(51,65,85,0.9)] hover:border-white/40 disabled:opacity-40 disabled:cursor-not-allowed" style={{ maxWidth: 130 }} onClick={handleRefineAi} disabled={aiRefining || !aiResult?.screenSpec}>
								{aiRefining ? '优化中...' : '按指令优化'}
							</button>
							<button
								className="flex-none py-2 px-3 border border-white/20 rounded-md bg-[rgba(30,41,59,0.8)] text-[#e2e8f0] text-xs cursor-pointer hover:bg-[rgba(51,65,85,0.9)] hover:border-white/40 disabled:opacity-40 disabled:cursor-not-allowed"
								style={{ maxWidth: 130 }}
								onClick={handleCopyAiRecommendations}
								disabled={!aiResult}
							>
								复制建议
							</button>
							<button className="inline-flex items-center justify-center h-8 px-4 text-sm font-normal leading-normal border border-brand rounded-md bg-brand text-white cursor-pointer transition-all duration-200 whitespace-nowrap hover:opacity-85 disabled:opacity-40 disabled:cursor-not-allowed" onClick={handleCreateFromAi} disabled={aiCreating || !aiResult?.screenSpec}>
								{aiCreating ? '创建中...' : '创建草稿'}
							</button>
						</div>
					</div>
				</div>
			)}
		</PageContainer>
	);
}
