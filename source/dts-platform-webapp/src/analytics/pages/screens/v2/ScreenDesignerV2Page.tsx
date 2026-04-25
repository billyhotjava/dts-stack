/**
 * Sprint-12 F3/T01 — v2 大屏编辑器页面。
 *
 * v2 与 v1 保持独立数据模型：
 * - 读取：GET /bi/api/screens/:id → tryLoadV2 → setScreenV2
 * - 编辑：grid units + 多页 + schema 化组件属性
 * - 保存：PUT /bi/api/screens/:id { components, pages, globalVariables, carouselConfig, v2Spec }
 */

import { useCallback, useEffect, useMemo, useState } from 'react';
import { DndProvider } from 'react-dnd';
import { HTML5Backend } from 'react-dnd-html5-backend';
import { useNavigate, useParams } from 'react-router';
import { ConfigProvider, theme as antdTheme } from 'antd';
import { toast } from 'sonner';

import { analyticsApi } from '../../../api/analyticsApi';
import type { ScreenWritePayload } from '../contracts';
import { ComponentLibraryPanel } from '../components';
import { PageManagerPanel, type PageManagerItem } from '../components/PageManagerPanel';
import { DesignerCanvasV2 } from './DesignerCanvasV2';
import { PropertyPanelV2 } from './PropertyPanelV2';
import { tryLoadV2 } from './loader';
import { validateScreenConfigV2 } from './schema';
import {
	createEmptyScreenV2,
	type ComponentV2,
	type ScreenConfigV2,
	type ScreenPageV2,
} from './types';
import '../ScreenDesigner.css';

interface V2DesignerShellProps {
	screen: ScreenConfigV2;
	onChange: (next: ScreenConfigV2) => void;
	selectedIds: string[];
	onSelect: (ids: string[]) => void;
	onSave: () => Promise<boolean>;
	onPreview: () => void;
	isSaving: boolean;
	dirty: boolean;
	pages: PageManagerItem[];
	activePageIndex: number;
	onPageChange: (next: number) => void;
	onAddPage: () => void;
	onDeletePage: (index: number) => void;
	onDuplicatePage: (index: number) => void;
	onRenamePage: (index: number, name: string) => void;
	onMovePage: (fromIndex: number, toIndex: number) => void;
}

function newEntityId(prefix: 'page' | 'c'): string {
	try {
		return `${prefix}_${crypto.randomUUID().replace(/-/g, '').slice(0, 12)}`;
	} catch {
		return `${prefix}_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 8)}`;
	}
}

function cloneConfig(config: Record<string, unknown>): Record<string, unknown> {
	if (typeof structuredClone === 'function') {
		return structuredClone(config);
	}
	return JSON.parse(JSON.stringify(config)) as Record<string, unknown>;
}

function cloneSerializable<T>(value: T): T {
	if (value == null) {
		return value;
	}
	if (typeof structuredClone === 'function') {
		return structuredClone(value);
	}
	return JSON.parse(JSON.stringify(value)) as T;
}

function cloneComponent(component: ComponentV2, nameSuffix?: string): ComponentV2 {
	return {
		...component,
		id: newEntityId('c'),
		name: nameSuffix ? `${component.name || component.type}${nameSuffix}` : component.name,
		layout: { ...component.layout },
		config: cloneConfig(component.config),
		dataSource: component.dataSource ? cloneSerializable(component.dataSource) : undefined,
		drillDown: component.drillDown ? cloneSerializable(component.drillDown) : undefined,
		actions: component.actions ? cloneSerializable(component.actions) : undefined,
		interaction: component.interaction ? cloneSerializable(component.interaction) : undefined,
		visibleByDevice: component.visibleByDevice ? { ...component.visibleByDevice } : undefined,
	};
}

function resolveScreenPages(screen: ScreenConfigV2): ScreenPageV2[] {
	if (screen.pages && screen.pages.length > 0) {
		return screen.pages;
	}
	return [{
		id: '__default__',
		name: '页面 1',
		components: screen.components,
		backgroundColor: screen.backgroundColor,
		backgroundImage: screen.backgroundImage,
	}];
}

function buildV2Payload(screen: ScreenConfigV2): ScreenWritePayload {
	const primaryPage = screen.pages?.[0];
	return {
		schemaVersion: 2,
		name: screen.name ?? '未命名大屏',
		description: screen.description,
		theme: screen.theme,
		backgroundColor: primaryPage?.backgroundColor ?? screen.backgroundColor,
		backgroundImage: primaryPage?.backgroundImage ?? screen.backgroundImage,
		width: 1920,
		height: 1080,
		components: primaryPage?.components ?? screen.components,
		globalVariables: screen.globalVariables ?? [],
		pages: screen.pages ?? [],
		carouselConfig: screen.carouselConfig,
		v2Spec: {
			schemaVersion: 2,
			layout: screen.layout,
			referenceViewport: screen.referenceViewport,
		},
	};
}

function getEditableScreen(screen: ScreenConfigV2, activePageIndex: number): ScreenConfigV2 {
	const pages = screen.pages ?? [];
	if (pages.length === 0) {
		return screen;
	}
	const safeIndex = Math.max(0, Math.min(activePageIndex, pages.length - 1));
	const page = pages[safeIndex] ?? pages[0];
	return {
		...screen,
		backgroundColor: page.backgroundColor ?? screen.backgroundColor,
		backgroundImage: page.backgroundImage ?? screen.backgroundImage,
		components: page.components,
	};
}

function mergeEditableScreen(
	baseScreen: ScreenConfigV2,
	editableScreen: ScreenConfigV2,
	activePageIndex: number,
): ScreenConfigV2 {
	const pages = baseScreen.pages ?? [];
	if (pages.length === 0) {
		return editableScreen;
	}
	const resolvedIndex = Math.max(0, Math.min(activePageIndex, pages.length - 1));
	return {
		...baseScreen,
		name: editableScreen.name,
		description: editableScreen.description,
		theme: editableScreen.theme,
		layout: editableScreen.layout,
		globalVariables: editableScreen.globalVariables,
		carouselConfig: editableScreen.carouselConfig,
		referenceViewport: editableScreen.referenceViewport,
		pages: pages.map((page, index) => (
			index === resolvedIndex
				? {
					...page,
					components: editableScreen.components,
					backgroundColor: editableScreen.backgroundColor,
					backgroundImage: editableScreen.backgroundImage,
				}
				: page
		)),
	};
}

function V2DesignerShell({
	screen,
	onChange,
	selectedIds,
	onSelect,
	onSave,
	onPreview,
	isSaving,
	dirty,
	pages,
	activePageIndex,
	onPageChange,
	onAddPage,
	onDeletePage,
	onDuplicatePage,
	onRenamePage,
	onMovePage,
}: V2DesignerShellProps) {
	const selectedComponent: ComponentV2 | null = useMemo(() => {
		if (selectedIds.length !== 1) return null;
		return screen.components.find((c) => c.id === selectedIds[0]) ?? null;
	}, [selectedIds, screen.components]);

	const handleDelete = useCallback(() => {
		if (selectedIds.length === 0) return;
		const keep = new Set(screen.components.map((c) => c.id));
		for (const id of selectedIds) keep.delete(id);
		onChange({
			...screen,
			components: screen.components.filter((c) => keep.has(c.id)),
		});
		onSelect([]);
	}, [screen, selectedIds, onChange, onSelect]);

	const handleDuplicateSelected = useCallback(() => {
		const selectedComponents = screen.components.filter((component) => selectedIds.includes(component.id));
		if (selectedComponents.length === 0) return;
		const cols = screen.layout?.cols ?? 12;
		const clones = selectedComponents.map((component, index) => {
			const nextX = Math.min(cols - 1, component.layout.x + 1);
			const nextW = Math.min(component.layout.w, Math.max(1, cols - nextX));
			return {
				...cloneComponent(component, ' (副本)'),
				layout: {
					...component.layout,
					x: nextX,
					y: component.layout.y + 1,
					w: nextW,
				},
				zIndex: (component.zIndex ?? 0) + selectedComponents.length + index + 1,
			};
		});
		onChange({
			...screen,
			components: [...screen.components, ...clones],
		});
		onSelect(clones.map((item) => item.id));
	}, [onChange, onSelect, screen, selectedIds]);

	const handleNameChange = (e: React.ChangeEvent<HTMLInputElement>) => {
		onChange({ ...screen, name: e.target.value });
	};

	useEffect(() => {
		const isTypingTarget = (target: EventTarget | null): boolean => {
			const node = target as HTMLElement | null;
			if (!node) return false;
			const tag = node.tagName;
			if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return true;
			return node.isContentEditable;
		};

		const handler = (e: KeyboardEvent) => {
			if (isTypingTarget(e.target)) {
				return;
			}
			const hotkey = e.ctrlKey || e.metaKey;
			if ((e.key === 'Delete' || e.key === 'Backspace') && selectedIds.length > 0) {
				e.preventDefault();
				handleDelete();
				return;
			}
			if (hotkey && e.key.toLowerCase() === 'd' && selectedIds.length > 0) {
				e.preventDefault();
				handleDuplicateSelected();
				return;
			}
			if (hotkey && e.key.toLowerCase() === 'a') {
				e.preventDefault();
				onSelect(screen.components.map((item) => item.id));
				return;
			}
			if (e.key === 'Escape' && selectedIds.length > 0) {
				e.preventDefault();
				onSelect([]);
			}
		};

		window.addEventListener('keydown', handler);
		return () => window.removeEventListener('keydown', handler);
	}, [handleDelete, handleDuplicateSelected, onSelect, screen.components, selectedIds]);

	const handleUpdateSelected = useCallback(
		(patch: Partial<ComponentV2>) => {
			if (selectedIds.length !== 1) return;
			const id = selectedIds[0];
			onChange({
				...screen,
				components: screen.components.map((c) => (c.id === id ? { ...c, ...patch } : c)),
			});
		},
		[selectedIds, screen, onChange],
	);

	return (
		<div className="screen-designer flex flex-col fixed inset-0 overflow-hidden isolate z-[9999]">
			<div
				style={{
					display: 'flex',
					alignItems: 'center',
					gap: 12,
					padding: '8px 16px',
					background: '#1a1f2e',
					borderBottom: '1px solid rgba(255,255,255,0.08)',
					color: '#e2e8f0',
				}}
			>
				<span style={{ fontSize: 12, color: '#94a3b8', letterSpacing: 0.4 }}>v2 自适应大屏</span>
				<input
					value={screen.name ?? ''}
					onChange={handleNameChange}
					placeholder="大屏名称"
					style={{
						flex: '0 1 320px',
						padding: '4px 10px',
						background: 'rgba(255,255,255,0.06)',
						border: '1px solid rgba(255,255,255,0.1)',
						borderRadius: 4,
						color: '#e2e8f0',
						fontSize: 14,
					}}
				/>
				<select
					value={String(activePageIndex)}
					onChange={(e) => onPageChange(Number(e.target.value) || 0)}
					style={{
						minWidth: 160,
						padding: '4px 10px',
						background: 'rgba(255,255,255,0.06)',
						border: '1px solid rgba(255,255,255,0.1)',
						borderRadius: 4,
						color: '#e2e8f0',
						fontSize: 13,
					}}
				>
					{pages.map((page, index) => (
						<option key={page.id} value={index}>
							{page.name}
						</option>
					))}
				</select>
				<button
					type="button"
					onClick={onAddPage}
					style={{
						padding: '6px 12px',
						background: 'rgba(255,255,255,0.08)',
						border: '1px solid rgba(255,255,255,0.15)',
						borderRadius: 4,
						color: '#e2e8f0',
						cursor: 'pointer',
					}}
				>
					添加页面
				</button>
				<div style={{ fontSize: 12, color: '#94a3b8' }}>
					{screen.components.length} 个组件 / {pages.length} 页
				</div>
				<div style={{ flex: 1 }} />
				{dirty ? <span style={{ fontSize: 12, color: '#fbbf24' }}>● 未保存</span> : null}
				<button
					type="button"
					onClick={onPreview}
					disabled={isSaving}
					style={{
						padding: '6px 14px',
						background: 'rgba(255,255,255,0.08)',
						border: '1px solid rgba(255,255,255,0.15)',
						borderRadius: 4,
						color: '#e2e8f0',
						cursor: 'pointer',
					}}
				>
					预览
				</button>
				<button
					type="button"
					onClick={() => void onSave()}
					disabled={isSaving}
					style={{
						padding: '6px 14px',
						background: '#6366f1',
						border: '1px solid #6366f1',
						borderRadius: 4,
						color: '#fff',
						cursor: isSaving ? 'not-allowed' : 'pointer',
						opacity: isSaving ? 0.6 : 1,
					}}
				>
					{isSaving ? '保存中…' : '保存'}
				</button>
			</div>

			<div className="flex flex-1 min-h-0 overflow-hidden">
				<div
					className="designer-side-rail designer-side-rail--library flex min-h-0 overflow-hidden shrink-0 border-r border-[var(--color-border)]"
					style={{ width: 'clamp(280px, 18vw, 320px)', flex: '0 0 clamp(280px, 18vw, 320px)' }}
				>
					<ComponentLibraryPanel />
				</div>

				<div className="flex-1 flex flex-col min-w-0 min-h-0 relative">
					<DesignerCanvasV2
						screen={screen}
						onChange={onChange}
						selectedIds={selectedIds}
						onSelect={onSelect}
					/>
					<div style={{ padding: '10px 12px 12px' }}>
						<PageManagerPanel
							pages={pages}
							currentPageIndex={activePageIndex}
							onSwitchPage={onPageChange}
							onAddPage={onAddPage}
							onDeletePage={onDeletePage}
							onDuplicatePage={onDuplicatePage}
							onRenamePage={onRenamePage}
							onMovePage={onMovePage}
						/>
					</div>
				</div>

				<div
					className="designer-side-rail designer-side-rail--inspector flex min-h-0 overflow-hidden shrink-0 border-l border-[var(--color-border)]"
					style={{ width: 'clamp(320px, 24vw, 380px)', flex: '0 0 clamp(320px, 24vw, 380px)' }}
				>
					<PropertyPanelV2
						screen={screen}
						selected={selectedComponent}
						onUpdateSelected={handleUpdateSelected}
						onScreenChange={onChange}
						onDeleteSelected={selectedIds.length > 0 ? handleDelete : undefined}
					/>
				</div>
			</div>
		</div>
	);
}

function ScreenDesignerV2Content() {
	const { id } = useParams<{ id: string }>();
	const navigate = useNavigate();

	const [screen, setScreen] = useState<ScreenConfigV2 | null>(null);
	const [selectedIds, setSelectedIds] = useState<string[]>([]);
	const [loading, setLoading] = useState(true);
	const [isSaving, setIsSaving] = useState(false);
	const [dirty, setDirty] = useState(false);
	const [error, setError] = useState<string | null>(null);
	const [activePageIndex, setActivePageIndex] = useState(0);

	useEffect(() => {
		if (!id) {
			setScreen(createEmptyScreenV2());
			setLoading(false);
			return;
		}
		let cancelled = false;
		analyticsApi
			.getScreen(id, { mode: 'draft' })
			.then((raw) => {
				if (cancelled) return;
				if (raw.canEdit === false) {
					toast.error('当前账号没有该大屏的编辑权限');
					navigate('/bi/screens', { replace: true });
					return;
				}
				const v2 = tryLoadV2(raw);
				if (v2) {
					setScreen(v2);
				} else {
					toast.error('此大屏是 v1 固定像素大屏，v2 编辑器不支持。请使用旧编辑器或新建 v2 大屏');
					navigate('/bi/screens', { replace: true });
				}
			})
			.catch((e) => {
				if (!cancelled) setError(String(e?.message ?? e));
			})
			.finally(() => {
				if (!cancelled) setLoading(false);
			});
		return () => {
			cancelled = true;
		};
	}, [id, navigate]);

	const resolvedPages = useMemo(() => (screen ? resolveScreenPages(screen) : []), [screen]);
	const pageItems = useMemo<PageManagerItem[]>(
		() => resolvedPages.map((page) => ({ id: page.id, name: page.name })),
		[resolvedPages],
	);

	useEffect(() => {
		if (resolvedPages.length === 0) {
			if (activePageIndex !== 0) setActivePageIndex(0);
			return;
		}
		if (activePageIndex >= resolvedPages.length) {
			setActivePageIndex(Math.max(0, resolvedPages.length - 1));
			setSelectedIds([]);
		}
	}, [activePageIndex, resolvedPages.length]);

	const editableScreen = useMemo(
		() => (screen ? getEditableScreen(screen, activePageIndex) : null),
		[screen, activePageIndex],
	);

	const commitScreen = useCallback((updater: (prev: ScreenConfigV2) => ScreenConfigV2) => {
		setScreen((prev) => {
			if (!prev) return prev;
			return updater(prev);
		});
		setDirty(true);
	}, []);

	const handleScreenChange = useCallback((next: ScreenConfigV2) => {
		commitScreen((prev) => mergeEditableScreen(prev, next, activePageIndex));
	}, [activePageIndex, commitScreen]);

	const handleAddPage = useCallback(() => {
		if (!screen) return;
		const currentPages = resolveScreenPages(screen);
		const nextPages = currentPages.map((page, index) => (
			index === activePageIndex
				? {
					...page,
					components: editableScreen?.components ?? page.components,
					backgroundColor: editableScreen?.backgroundColor ?? page.backgroundColor,
					backgroundImage: editableScreen?.backgroundImage ?? page.backgroundImage,
				}
				: page
		));
		const newPage: ScreenPageV2 = {
			id: newEntityId('page'),
			name: `页面 ${nextPages.length + 1}`,
			components: [],
			backgroundColor: editableScreen?.backgroundColor ?? screen.backgroundColor,
			backgroundImage: editableScreen?.backgroundImage ?? screen.backgroundImage,
		};
		commitScreen((prev) => ({
			...prev,
			pages: [...nextPages, newPage],
		}));
		setActivePageIndex(nextPages.length);
		setSelectedIds([]);
	}, [activePageIndex, commitScreen, editableScreen, screen]);

	const handleDeletePage = useCallback((index: number) => {
		if (!screen) return;
		const currentPages = resolveScreenPages(screen);
		if (currentPages.length <= 1) {
			return;
		}
		const nextPages = currentPages.filter((_, pageIndex) => pageIndex !== index);
		const nextIndex = activePageIndex === index
			? Math.max(0, Math.min(index, nextPages.length - 1))
			: (activePageIndex > index ? activePageIndex - 1 : activePageIndex);
		commitScreen((prev) => ({
			...prev,
			pages: nextPages,
		}));
		setActivePageIndex(nextIndex);
		setSelectedIds([]);
	}, [activePageIndex, commitScreen, screen]);

	const handleDuplicatePage = useCallback((index: number) => {
		if (!screen) return;
		const currentPages = resolveScreenPages(screen);
		const source = currentPages[index];
		if (!source) return;
		const duplicated: ScreenPageV2 = {
			...source,
			id: newEntityId('page'),
			name: `${source.name} (副本)`,
			components: source.components.map((component) => cloneComponent(component)),
		};
		const nextPages = [...currentPages];
		nextPages.splice(index + 1, 0, duplicated);
		commitScreen((prev) => ({
			...prev,
			pages: nextPages,
		}));
		setActivePageIndex(index + 1);
		setSelectedIds([]);
	}, [commitScreen, screen]);

	const handleRenamePage = useCallback((index: number, name: string) => {
		if (!screen) return;
		const currentPages = resolveScreenPages(screen);
		const nextPages = currentPages.map((page, pageIndex) => (
			pageIndex === index ? { ...page, name } : page
		));
		commitScreen((prev) => ({
			...prev,
			pages: nextPages,
		}));
	}, [commitScreen, screen]);

	const handleMovePage = useCallback((fromIndex: number, toIndex: number) => {
		if (!screen || fromIndex === toIndex) return;
		const currentPages = resolveScreenPages(screen);
		if (toIndex < 0 || toIndex >= currentPages.length) return;
		const nextPages = [...currentPages];
		const [moved] = nextPages.splice(fromIndex, 1);
		nextPages.splice(toIndex, 0, moved);
		commitScreen((prev) => ({
			...prev,
			pages: nextPages,
		}));
		if (activePageIndex === fromIndex) {
			setActivePageIndex(toIndex);
		} else if (activePageIndex > fromIndex && activePageIndex <= toIndex) {
			setActivePageIndex(activePageIndex - 1);
		} else if (activePageIndex < fromIndex && activePageIndex >= toIndex) {
			setActivePageIndex(activePageIndex + 1);
		}
		setSelectedIds([]);
	}, [activePageIndex, commitScreen, screen]);

	const handleSave = useCallback(async (): Promise<boolean> => {
		if (!screen || !id || isSaving) return false;
		const validationErrors = validateScreenConfigV2(screen);
		if (validationErrors.length > 0) {
			toast.error(`保存失败：当前大屏存在 ${validationErrors.length} 个布局问题`, {
				description: validationErrors.slice(0, 3).join('；'),
			});
			return false;
		}
		setIsSaving(true);
		try {
			await analyticsApi.updateScreen(id, buildV2Payload(screen));
			toast.success('保存成功');
			setDirty(false);
			return true;
		} catch (e) {
			toast.error(`保存失败：${String((e as Error)?.message ?? e)}`);
			return false;
		} finally {
			setIsSaving(false);
		}
	}, [screen, id, isSaving]);

	const handlePreview = useCallback(() => {
		if (!id) return;
		if (dirty) {
			const confirmLeave = window.confirm('还有未保存的改动，预览前要保存吗？');
			if (confirmLeave) {
				void handleSave().then((saved) => {
					if (saved) {
						navigate(`/bi/screens/${id}/preview`);
					}
				});
				return;
			}
		}
		navigate(`/bi/screens/${id}/preview`);
	}, [id, dirty, handleSave, navigate]);

	if (loading) {
		return (
			<div
				style={{
					position: 'fixed',
					inset: 0,
					display: 'flex',
					alignItems: 'center',
					justifyContent: 'center',
					background: '#0f172a',
					color: '#e2e8f0',
				}}
			>
				加载中…
			</div>
		);
	}

	if (error || !screen || !editableScreen) {
		return (
			<div
				style={{
					position: 'fixed',
					inset: 0,
					display: 'flex',
					alignItems: 'center',
					justifyContent: 'center',
					background: '#0f172a',
					color: '#f87171',
				}}
			>
				加载失败：{error ?? 'unknown'}
			</div>
		);
	}

	return (
		<V2DesignerShell
			screen={editableScreen}
			onChange={handleScreenChange}
			selectedIds={selectedIds}
			onSelect={setSelectedIds}
			onSave={handleSave}
			onPreview={handlePreview}
			isSaving={isSaving}
			dirty={dirty}
			pages={pageItems}
			activePageIndex={activePageIndex}
			onPageChange={(next) => {
				setActivePageIndex(next);
				setSelectedIds([]);
			}}
			onAddPage={handleAddPage}
			onDeletePage={handleDeletePage}
			onDuplicatePage={handleDuplicatePage}
			onRenamePage={handleRenamePage}
			onMovePage={handleMovePage}
		/>
	);
}

export default function ScreenDesignerV2Page() {
	return (
		<ConfigProvider
			theme={{
				algorithm: antdTheme.darkAlgorithm,
				token: {
					zIndexPopupBase: 10050,
					colorBgContainer: '#2a2b36',
					colorBgElevated: '#262730',
					colorBorder: 'rgba(255, 255, 255, 0.12)',
					colorBorderSecondary: 'rgba(255, 255, 255, 0.08)',
					colorText: 'rgba(255, 255, 255, 0.92)',
					colorTextSecondary: 'rgba(255, 255, 255, 0.60)',
					colorTextTertiary: 'rgba(255, 255, 255, 0.40)',
					colorTextPlaceholder: 'rgba(255, 255, 255, 0.35)',
					colorFillAlter: 'rgba(255, 255, 255, 0.04)',
					colorFillSecondary: 'rgba(255, 255, 255, 0.08)',
				},
			}}
		>
			<DndProvider backend={HTML5Backend}>
				<ScreenDesignerV2Content />
			</DndProvider>
		</ConfigProvider>
	);
}
