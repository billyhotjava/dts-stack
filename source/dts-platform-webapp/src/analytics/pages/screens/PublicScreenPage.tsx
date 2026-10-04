import { useEffect, useState, useCallback, useMemo, useRef } from 'react';
import { useParams } from 'react-router';
import { analyticsApi, HttpError } from '../../api/analyticsApi';
import { ComponentRenderer } from './components/ComponentRenderer';
import { GlobalVariablePanel } from './components/GlobalVariablePanel';
import { PreviewScaleControl } from './components/PreviewScaleControl';
import { RuntimeActionPanel } from './components/RuntimeActionPanel';
import { ScreenRuntimeProvider } from './ScreenRuntimeContext';
import type { ScreenConfig, ScreenTheme } from './types';
import { resolveScreenTheme } from './screenThemes';
import { applyThemeCssVariables } from './themes/screenCssVariables';
import { normalizeScreenConfig } from './screenSpec';
import { buildComponentMap, isComponentEffectivelyVisible } from './componentHierarchy';
import { safeCssBackgroundUrl } from './sanitize';
import { useScreenCarousel } from './hooks/useScreenCarousel';
import { useScreenFontFaces } from './hooks/useScreenFontFaces';
import { resolveRuntimeScale } from './runtimeScale';
import { resolveRuntimeCanvasScaleStyle } from './runtimeCanvasStyle';
import {
	isVisibleForDevice,
	resolveDeviceModeByViewport,
	type DeviceMode,
} from './deviceMode';
import { resolveComponentAppearanceStyle } from './componentAppearance';
import { resolveScreenFontFamily } from './screenTypography';
import './screenRuntimeKeyframes.css';

/* ── Theme-dependent inline style helpers ── */

const darkBg = 'radial-gradient(circle at top left, rgba(84, 123, 255, 0.18), transparent 26%), radial-gradient(circle at top right, rgba(34, 197, 94, 0.12), transparent 22%), linear-gradient(180deg, #0d1524 0%, #08101d 100%)';
const lightBg = 'radial-gradient(circle at top left, rgba(84, 123, 255, 0.1), transparent 26%), radial-gradient(circle at top right, rgba(34, 197, 94, 0.08), transparent 24%), linear-gradient(180deg, #f4f7fb 0%, #edf3f7 100%)';

const runtimeVarsLight = {
	'--runtime-border': 'rgba(148, 163, 184, 0.22)',
	'--runtime-control-bg': 'rgba(255, 255, 255, 0.9)',
	'--runtime-control-muted': 'rgba(244, 247, 251, 0.9)',
	'--runtime-control-shadow': '0 20px 40px rgba(15, 23, 42, 0.16)',
	'--runtime-control-text': 'rgba(15, 23, 42, 0.92)',
	'--runtime-control-text-muted': 'rgba(71, 85, 105, 0.72)',
} as React.CSSProperties;

const runtimeVarsDark = {
	'--runtime-border': 'rgba(255, 255, 255, 0.12)',
	'--runtime-control-bg': 'rgba(22, 32, 50, 0.92)',
	'--runtime-control-muted': 'rgba(255, 255, 255, 0.08)',
	'--runtime-control-shadow': '0 20px 40px rgba(0, 0, 0, 0.4)',
	'--runtime-control-text': 'rgba(255, 255, 255, 0.92)',
	'--runtime-control-text-muted': 'rgba(255, 255, 255, 0.5)',
} as React.CSSProperties;

function themeVars(isDark: boolean): React.CSSProperties {
	return isDark ? runtimeVarsDark : runtimeVarsLight;
}

function themeBg(isDark: boolean): string {
	return isDark ? darkBg : lightBg;
}

function themeColor(isDark: boolean): string {
	return isDark ? 'rgba(255, 255, 255, 0.92)' : 'rgba(15, 23, 42, 0.92)';
}

function badgeStyle(isDark: boolean, variant?: 'info' | 'warning'): React.CSSProperties {
	if (variant === 'info') {
		return isDark
			? { background: 'rgba(111, 179, 242, 0.15)', color: '#7db8f0', borderColor: 'rgba(111, 179, 242, 0.25)' }
			: { borderColor: 'rgba(80, 158, 227, 0.24)', background: 'rgba(80, 158, 227, 0.12)', color: '#275e95' };
	}
	if (variant === 'warning') {
		return isDark
			? { background: 'rgba(245, 158, 11, 0.15)', color: '#f0c06e', borderColor: 'rgba(245, 158, 11, 0.25)' }
			: { borderColor: 'rgba(245, 158, 11, 0.22)', background: 'rgba(255, 244, 216, 0.92)', color: '#9a6700' };
	}
	return isDark
		? { background: 'rgba(255, 255, 255, 0.08)', color: 'rgba(255, 255, 255, 0.7)', borderColor: 'rgba(255, 255, 255, 0.12)' }
		: { background: 'rgba(255, 255, 255, 0.88)', color: 'var(--runtime-control-text-muted)', borderColor: 'var(--runtime-border)' };
}

function fabPanelStyle(isDark: boolean): React.CSSProperties {
	return isDark
		? { background: 'rgba(15, 22, 36, 0.96)', borderColor: 'rgba(255, 255, 255, 0.1)', boxShadow: '0 20px 48px rgba(0, 0, 0, 0.5)' }
		: { borderColor: 'var(--runtime-border)', background: 'var(--runtime-control-bg, rgba(255, 255, 255, 0.95))', boxShadow: '0 20px 48px rgba(15, 23, 42, 0.2)' };
}

function fabTriggerStyle(isDark: boolean): React.CSSProperties {
	return isDark
		? { background: 'rgba(111, 179, 242, 0.85)' }
		: { background: 'rgba(80, 158, 227, 0.92)' };
}

function fabSectionTitleColor(isDark: boolean): string {
	return isDark ? 'rgba(255, 255, 255, 0.45)' : 'var(--runtime-control-text-muted)';
}

function fabInfoNameColor(isDark: boolean): string {
	return isDark ? 'rgba(255, 255, 255, 0.92)' : 'inherit';
}

function fabDividerBg(isDark: boolean): string {
	return isDark ? 'rgba(255, 255, 255, 0.08)' : 'var(--runtime-border)';
}

export default function PublicScreenPage() {
	const { uuid } = useParams<{ uuid: string }>();
	const [screen, setScreen] = useState<ScreenConfig | null>(null);
	const [loading, setLoading] = useState(true);
	const [error, setError] = useState<string | null>(null);
	const [scale, setScale] = useState(1);
	const [autoScale, setAutoScale] = useState(1);
	const [manualScale, setManualScale] = useState<number | null>(null);
	// Stretch-mode axis scales: fill viewport without letterbox when manualScale===null.
	const [autoScaleX, setAutoScaleX] = useState(1);
	const [autoScaleY, setAutoScaleY] = useState(1);
	const [viewportSize, setViewportSize] = useState({ w: 0, h: 0 });
	const [deviceMode, setDeviceMode] = useState<DeviceMode>('pc');
	const [fabOpen, setFabOpen] = useState(false);
	const [authError, setAuthError] = useState<'not-authenticated' | 'forbidden' | null>(null);
	const scrollContainerRef = useRef<HTMLDivElement | null>(null);
	const fabRef = useRef<HTMLDivElement | null>(null);

	// Embed mode: ?embed=1 hides all controls; ?hideControls=1 hides only zoom controls
	const searchParams = useMemo(() => new URLSearchParams(window.location.search), []);
	const isEmbedMode = searchParams.get('embed') === '1';
	const hideControls = isEmbedMode || searchParams.get('hideControls') === '1';

	// URL parameter passthrough: ?var_{key}=value -> globalVariable
	const urlVariableOverrides = useMemo(() => {
		const overrides: Record<string, string> = {};
		for (const [k, v] of searchParams) {
			if (k.startsWith('var_')) {
				overrides[k.slice(4)] = v;
			}
		}
		return overrides;
	}, [searchParams]);


	useEffect(() => {
		if (!uuid) {
			setError('未找到大屏链接');
			setLoading(false);
			return;
		}

		analyticsApi.getPublicScreen(uuid)
			.then((data) => {
				const normalized = normalizeScreenConfig(data, { id: data.id });
				if (normalized.warnings.length > 0) {
					console.warn('[screen-spec] normalized with warnings:', normalized.warnings);
				}
				setScreen(normalized.config);
				setLoading(false);
			})
			.catch((err) => {
				if (err instanceof HttpError && err.status === 401) {
					setAuthError('not-authenticated');
					setLoading(false);
				} else if (err instanceof HttpError && err.status === 403) {
					setAuthError('forbidden');
					setLoading(false);
				} else {
					console.error('Failed to load public screen:', err);
					setError('加载大屏失败');
					setLoading(false);
				}
			});
	}, [uuid]);

	// Multi-page carousel support -- must be called before early returns
	const carousel = useScreenCarousel(screen?.pages, screen?.components || [], screen?.carouselConfig);
	const components = carousel.currentPageComponents;
	const handleDrillViewChange = useCallback((viewId: string | null) => {
		const pages = screen?.pages ?? [];
		const targetIndex = viewId === null ? 0 : pages.findIndex((page) => page.id === viewId);
		if (targetIndex >= 0) carousel.goToPage(targetIndex);
	}, [carousel.goToPage, screen?.pages]);

	const visibleSortedComponents = useMemo(
		() => {
			const componentMap = buildComponentMap(components);
			return components
				.filter((c) => c.visible && isVisibleForDevice(c, deviceMode) && isComponentEffectivelyVisible(c, componentMap))
				.sort((a, b) => a.zIndex - b.zIndex);
		},
		[components, deviceMode],
	);

	const contentBounds = useMemo(() => {
		const baseWidth = Math.max(1, screen?.width || 1920);
		const baseHeight = Math.max(1, screen?.height || 1080);
		let minLeft = 0;
		let minTop = 0;
		let maxRight = baseWidth;
		let maxBottom = baseHeight;
		for (const component of visibleSortedComponents) {
			const left = Number(component.x) || 0;
			const top = Number(component.y) || 0;
			const right = left + Math.max(0, Number(component.width) || 0);
			const bottom = top + Math.max(0, Number(component.height) || 0);
			if (left < minLeft) minLeft = left;
			if (top < minTop) minTop = top;
			if (right > maxRight) maxRight = right;
			if (bottom > maxBottom) maxBottom = bottom;
		}
		return {
			minLeft,
			minTop,
			width: Math.max(1, maxRight - minLeft),
			height: Math.max(1, maxBottom - minTop),
		};
	}, [screen?.height, screen?.width, visibleSortedComponents]);

	const computeScale = useCallback(() => {
		if (!screen) return;
		const viewport = window.visualViewport;
		const vw = viewport?.width ?? window.innerWidth;
		const vh = viewport?.height ?? window.innerHeight;
		setViewportSize({ w: vw, h: vh });
		const nextMode: DeviceMode = resolveDeviceModeByViewport(vw);
		setDeviceMode(nextMode);
		const nextAutoScale = resolveRuntimeScale({
			viewportWidth: vw,
			viewportHeight: vh,
			screenWidth: contentBounds.width,
			screenHeight: contentBounds.height,
			fullscreen: true,
			allowUpscale: true,
		}).scale;
		setAutoScale(nextAutoScale);
		// Stretch axes: each axis fills the viewport independently (no letterbox).
		const sx = vw / Math.max(1, contentBounds.width);
		const sy = vh / Math.max(1, contentBounds.height);
		setAutoScaleX(sx);
		setAutoScaleY(sy);
		if (manualScale === null) {
			setScale(nextAutoScale);
		}
	}, [manualScale, screen, contentBounds]);

	useEffect(() => {
		computeScale();
		window.addEventListener('resize', computeScale);
		return () => window.removeEventListener('resize', computeScale);
	}, [computeScale]);

	const clampScale = (value: number) => Math.max(0.2, Math.min(2, value));
	const setFitScale = useCallback(() => {
		setManualScale(null);
		setScale(autoScale);
	}, [autoScale]);
	const setAbsoluteScale = useCallback((value: number) => {
		const next = clampScale(value);
		setManualScale(next);
		setScale(next);
	}, []);
	const adjustScale = useCallback((delta: number) => {
		const base = manualScale === null ? autoScale : manualScale;
		setAbsoluteScale(base + delta);
	}, [autoScale, manualScale, setAbsoluteScale]);

	useEffect(() => {
		const node = scrollContainerRef.current;
		if (!node) return;
		const handleWheel = (event: WheelEvent) => {
			if (!event.ctrlKey && !event.metaKey) {
				return;
			}
			event.preventDefault();
			const direction = event.deltaY > 0 ? -1 : 1;
			adjustScale(direction * 0.05);
		};
		node.addEventListener('wheel', handleWheel, { passive: false });
		return () => {
			node.removeEventListener('wheel', handleWheel);
		};
	}, [adjustScale]);

	useEffect(() => {
		const isTypingTarget = (target: EventTarget | null): boolean => {
			const node = target as HTMLElement | null;
			if (!node) return false;
			const tag = node.tagName;
			if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return true;
			return node.isContentEditable;
		};
		const handleKeyDown = (event: KeyboardEvent) => {
			if (event.ctrlKey || event.metaKey || event.altKey) return;
			if (isTypingTarget(event.target)) return;
			const key = event.key;
			if (key === '0') {
				event.preventDefault();
				setAbsoluteScale(1);
				return;
			}
			if (key.toLowerCase() === 'f') {
				event.preventDefault();
				setFitScale();
				return;
			}
			if (key === '+' || key === '=' || key === 'NumpadAdd') {
				event.preventDefault();
				adjustScale(0.1);
				return;
			}
			if (key === '-' || key === '_' || key === 'NumpadSubtract') {
				event.preventDefault();
				adjustScale(-0.1);
			}
		};
		window.addEventListener('keydown', handleKeyDown);
		return () => window.removeEventListener('keydown', handleKeyDown);
	}, [adjustScale, setAbsoluteScale, setFitScale]);

	// Close FAB panel on click outside
	useEffect(() => {
		if (!fabOpen) return;
		const handleClick = (e: MouseEvent) => {
			if (fabRef.current && !fabRef.current.contains(e.target as Node)) {
				setFabOpen(false);
			}
		};
		document.addEventListener('mousedown', handleClick);
		return () => document.removeEventListener('mousedown', handleClick);
	}, [fabOpen]);

	const globalVariables = screen?.globalVariables ?? [];
	// Apply URL variable overrides to global variable definitions
	const effectiveGlobalVars = useMemo(() => {
		if (Object.keys(urlVariableOverrides).length === 0) return globalVariables;
		return globalVariables.map(gv => {
			const override = urlVariableOverrides[gv.key];
			return override !== undefined ? { ...gv, defaultValue: override } : gv;
		});
	}, [globalVariables, urlVariableOverrides]);
	// Auto mode = stretch (fill viewport, no letterbox); manual zoom = uniform.
	const useStretchFill = manualScale === null;
	const runtimeCanvasScaleStyle = useMemo(() => {
		if (useStretchFill) {
			return {
				transform: `scale(${autoScaleX}, ${autoScaleY})`,
				transformOrigin: 'top left',
			} as const;
		}
		return resolveRuntimeCanvasScaleStyle(scale, components);
	}, [useStretchFill, autoScaleX, autoScaleY, scale, components]);

	const rawTheme = screen?.theme as ScreenTheme | undefined;
	const screenTheme = resolveScreenTheme(rawTheme, screen?.backgroundColor);
	const screenFontFamily = resolveScreenFontFamily(screen?.fontFamily ?? screen?.customTheme?.fontFamily);
	useScreenFontFaces();
	const publicCanvasRef = useRef<HTMLDivElement>(null);
	useEffect(() => {
		if (!publicCanvasRef.current) return;
		applyThemeCssVariables(publicCanvasRef.current, screenTheme, screen?.customTheme);
		publicCanvasRef.current.style.setProperty('--screen-font-family', screenFontFamily);
	}, [screenTheme, screen?.customTheme, screenFontFamily]);

	const isDark = useMemo(() => {
		if (!screen) return true; // default dark for loading/error states
		return resolveScreenTheme(rawTheme, screen.backgroundColor) !== 'glacier';
	}, [screen]);

	// ── Early returns MUST be after all hooks ──
	if (authError === 'not-authenticated') {
		return (
			<div className="flex flex-col items-center justify-center min-h-screen gap-4 p-8 text-center">
				<div className="text-4xl opacity-30">&#x1f512;</div>
				<h2 className="text-xl font-semibold">需要登录</h2>
				<p className="text-text-secondary">请先登录后再查看此大屏</p>
				<a href="/bi" className="px-4 py-2 rounded-md bg-brand text-white">返回登录</a>
			</div>
		);
	}
	if (authError === 'forbidden') {
		return (
			<div className="flex flex-col items-center justify-center min-h-screen gap-4 p-8 text-center">
				<div className="text-4xl opacity-30">&#x1f6ab;</div>
				<h2 className="text-xl font-semibold">无访问权限</h2>
				<p className="text-text-secondary">您没有权限查看此大屏，请联系大屏拥有者授权</p>
				<a href="/bi" className="px-4 py-2 rounded-md bg-brand text-white">返回首页</a>
			</div>
		);
	}
	if (loading) {
		return (
			<div
				className="fixed inset-0 overflow-hidden p-0 box-border"
				style={{ ...themeVars(true), background: themeBg(true), color: themeColor(true) }}
			>
				<div className="fixed inset-0 flex items-center justify-center p-6">
					<div
						className="text-center"
						style={{
							width: 'min(480px, 100%)',
							padding: 28,
							border: '1px solid rgba(255, 255, 255, 0.12)',
							borderRadius: 30,
							background: 'rgba(255, 255, 255, 0.9)',
							boxShadow: '0 20px 40px rgba(0, 0, 0, 0.4)',
						}}
					>
						<h1 className="mb-2.5 text-[26px] font-bold tracking-tight" style={{ letterSpacing: '-0.04em' }}>正在加载公开大屏</h1>
						<p className="m-0" style={{ color: 'rgba(255, 255, 255, 0.5)', lineHeight: 1.7 }}>正在准备公开访问所需的画布和运行态参数。</p>
					</div>
				</div>
			</div>
		);
	}

	if (error || !screen) {
		return (
			<div
				className="fixed inset-0 overflow-hidden p-0 box-border"
				style={{ ...themeVars(true), background: themeBg(true), color: themeColor(true) }}
			>
				<div className="fixed inset-0 flex items-center justify-center p-6">
					<div
						className="text-center"
						style={{
							width: 'min(480px, 100%)',
							padding: 28,
							border: '1px solid rgba(255, 255, 255, 0.12)',
							borderRadius: 30,
							background: 'rgba(255, 255, 255, 0.9)',
							boxShadow: '0 20px 40px rgba(0, 0, 0, 0.4)',
						}}
					>
						<h1 className="mb-2.5 text-[26px] font-bold tracking-tight" style={{ letterSpacing: '-0.04em' }}>公开链接不可用</h1>
						<p className="m-0" style={{ color: 'rgba(255, 255, 255, 0.5)', lineHeight: 1.7 }}>{error || '未找到大屏'}</p>
					</div>
				</div>
			</div>
		);
	}

	const carouselTransition = screen.carouselConfig?.transition ?? 'fade';
	const carouselDuration = screen.carouselConfig?.transitionDuration ?? 800;
	const screenWidth = contentBounds.width;
	const screenHeight = contentBounds.height;
	// In stretch-fill mode the stage matches the viewport exactly; in manual-zoom
	// mode the stage is sized from the uniform scale (legacy behaviour).
	const stageWidth = useStretchFill
		? Math.max(1, viewportSize.w || screenWidth * scale)
		: Math.max(1, screenWidth * scale);
	const stageHeight = useStretchFill
		? Math.max(1, viewportSize.h || screenHeight * scale)
		: Math.max(1, screenHeight * scale);
	const scalePercent = Math.round(scale * 100);

	return (
		<ScreenRuntimeProvider
			definitions={effectiveGlobalVars}
			runtimeMeta={uuid ? { accessMode: 'public', publicScreenUuid: uuid } : { accessMode: 'public' }}
			onDrillViewChange={handleDrillViewChange}
		>
			<div
				className={`fixed inset-0 overflow-hidden box-border ${isEmbedMode ? 'p-0' : 'p-0'}`}
				style={{ ...themeVars(isDark), background: themeBg(isDark), color: themeColor(isDark) }}
			>
				<div ref={scrollContainerRef} className="w-full h-full overflow-auto">
					<div className="min-w-full min-h-full flex items-center justify-center p-0 box-border">
						<div className="relative flex-none" style={{ width: stageWidth, height: stageHeight }}>
							<div
								className="relative w-full h-full overflow-hidden"
							>
							<div
								ref={publicCanvasRef}
								className="relative overflow-hidden origin-top-left"
								style={{
									width: screenWidth,
									height: screenHeight,
									backgroundColor: carousel.currentPageBgColor || screen.backgroundColor || '#1e1f26',
									backgroundImage: safeCssBackgroundUrl(carousel.currentPageBgImage || screen.backgroundImage),
									backgroundSize: 'cover',
									backgroundPosition: 'center',
									fontFamily: screenFontFamily,
									...runtimeCanvasScaleStyle,
									transition: carousel.transitioning
										? `opacity ${carouselDuration}ms ease, transform ${carouselDuration}ms ease`
										: 'none',
									...(carousel.transitioning && carouselTransition === 'fade'
										? { animation: `carousel-fade-in ${carouselDuration}ms ease` }
										: {}),
									...(carousel.transitioning ? { willChange: 'opacity, transform' } : {}),
								}}
							>
								{visibleSortedComponents
									.map((component) => (
										<div
											key={component.id}
											data-component-id={component.id}
											data-component-name={component.name}
											data-component-type={component.type}
											style={{
												position: 'absolute',
												left: component.x - contentBounds.minLeft,
												top: component.y - contentBounds.minTop,
												width: component.width,
												height: component.height,
												zIndex: component.zIndex,
												fontFamily: screenFontFamily,
												...resolveComponentAppearanceStyle(component.config),
											}}
										>
											<ComponentRenderer
												component={component}
												mode="preview"
												theme={screenTheme}
												customTheme={screen.customTheme}
												fontFamily={screenFontFamily}
											/>
										</div>
									))}
							</div>
							</div>
						</div>
					</div>
					{/* Carousel page indicator */}
					{carousel.pageCount > 1 && (
						<div
							className="fixed bottom-[18px] left-1/2 z-[10990] flex items-center gap-2.5 rounded-full backdrop-blur-[16px]"
							style={{
								minHeight: 50,
								padding: '8px 12px',
								border: '1px solid var(--runtime-border)',
								background: 'rgba(255, 255, 255, 0.9)',
								boxShadow: 'var(--runtime-control-shadow)',
								transform: 'translateX(-50%)',
							}}
						>
							<button type="button" onClick={carousel.prevPage} className="runtime-control-btn screen-runtime__pager-nav">&#8249;</button>
							{Array.from({ length: carousel.pageCount }, (_, i) => (
								<button
									key={i}
									type="button"
									onClick={() => carousel.goToPage(i)}
									className={`runtime-control-btn screen-runtime__pager-dot ${i === carousel.pageIndex ? 'is-active' : ''}`}
									title={`第 ${i + 1} 页`}
								/>
							))}
							<button type="button" onClick={carousel.nextPage} className="runtime-control-btn screen-runtime__pager-nav">&#8250;</button>
							<button
								type="button"
								onClick={carousel.togglePlay}
								className="runtime-control-btn screen-runtime__pager-play"
								title={carousel.isPlaying ? '暂停自动轮播' : '开始自动轮播'}
							>
								<svg width="14" height="14" viewBox="0 0 16 16" fill="currentColor">
									{carousel.isPlaying
										? <><rect x="3" y="2" width="4" height="12" rx="1" /><rect x="9" y="2" width="4" height="12" rx="1" /></>
										: <path d="M4 2l10 6-10 6V2z" />
									}
								</svg>
							</button>
						</div>
					)}
					<RuntimeActionPanel />
				</div>

				{/* Floating controls FAB */}
				{!isEmbedMode && (
					<div ref={fabRef} className="fixed bottom-6 right-6 z-[11000]">
						<button
							type="button"
							className="flex items-center justify-center w-12 h-12 border-none rounded-full text-white text-[22px] cursor-pointer transition-[background,transform] duration-200"
							style={{
								...fabTriggerStyle(isDark),
								boxShadow: '0 8px 24px rgba(15, 23, 42, 0.25)',
							}}
							onClick={() => setFabOpen((prev) => !prev)}
							title="控制面板"
						>
							&#9881;
						</button>
						{fabOpen && (
							<div
								className="absolute bottom-[calc(100%+12px)] right-0 w-[300px] overflow-y-auto p-4 rounded-2xl backdrop-blur-[16px] grid gap-3"
								style={{
									maxHeight: 'min(80vh, 520px)',
									border: '1px solid',
									...fabPanelStyle(isDark),
								}}
							>
								<div>
									<div
										className="text-[11px] font-bold uppercase tracking-wide mb-1.5"
										style={{ letterSpacing: '0.06em', color: fabSectionTitleColor(isDark) }}
									>
										屏幕信息
									</div>
									<div
										className="text-[15px] font-semibold leading-snug mb-1.5"
										style={{ color: fabInfoNameColor(isDark) }}
									>
										{screen.name || '未命名大屏'}
									</div>
									<div className="flex flex-wrap gap-1.5">
										<span
											className="inline-flex items-center rounded-full text-xs font-semibold"
											style={{ minHeight: 34, padding: '0 12px', border: '1px solid', ...badgeStyle(isDark, 'info') }}
										>
											公开访问
										</span>
										<span
											className="inline-flex items-center rounded-full text-xs font-semibold"
											style={{ minHeight: 34, padding: '0 12px', border: '1px solid', ...badgeStyle(isDark) }}
										>
											{screenWidth} &times; {screenHeight}
										</span>
										<span
											className="inline-flex items-center rounded-full text-xs font-semibold"
											style={{ minHeight: 34, padding: '0 12px', border: '1px solid', ...badgeStyle(isDark) }}
										>
											{visibleSortedComponents.length} 组件
										</span>
										{carousel.pageCount > 1 ? (
											<span
												className="inline-flex items-center rounded-full text-xs font-semibold"
												style={{ minHeight: 34, padding: '0 12px', border: '1px solid', ...badgeStyle(isDark) }}
											>
												{carousel.pageIndex + 1}/{carousel.pageCount} 页
											</span>
										) : null}
										{Object.keys(urlVariableOverrides).length > 0 ? (
											<span
												className="inline-flex items-center rounded-full text-xs font-semibold"
												style={{ minHeight: 34, padding: '0 12px', border: '1px solid', ...badgeStyle(isDark, 'warning') }}
											>
												URL 参数覆盖 {Object.keys(urlVariableOverrides).length}
											</span>
										) : null}
									</div>
								</div>
								<div className="h-px" style={{ background: fabDividerBg(isDark) }} />
								<div>
									<div
										className="text-[11px] font-bold uppercase tracking-wide mb-1.5"
										style={{ letterSpacing: '0.06em', color: fabSectionTitleColor(isDark) }}
									>
										缩放
									</div>
									{!hideControls && (
										<PreviewScaleControl
											scalePercent={scalePercent}
											onFit={setFitScale}
											onReset100={() => setAbsoluteScale(1)}
											onZoomOut={() => adjustScale(-0.1)}
											onZoomIn={() => adjustScale(0.1)}
											onSetScalePercent={(percent) => {
												const safePercent = Number.isFinite(percent) ? Math.max(20, Math.min(200, Math.round(percent))) : 100;
												setAbsoluteScale(safePercent / 100);
											}}
										/>
									)}
								</div>
								{globalVariables.length > 0 && (
									<>
										<div className="h-px" style={{ background: fabDividerBg(isDark) }} />
										<div>
											<div
												className="text-[11px] font-bold uppercase tracking-wide mb-1.5"
												style={{ letterSpacing: '0.06em', color: fabSectionTitleColor(isDark) }}
											>
												运行时变量
											</div>
											<GlobalVariablePanel />
										</div>
									</>
								)}
							</div>
						)}
					</div>
				)}
			</div>
		</ScreenRuntimeProvider>
	);
}
