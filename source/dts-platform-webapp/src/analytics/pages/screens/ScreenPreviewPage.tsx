import { useEffect, useState, useCallback, useMemo, useRef } from 'react';
import { useParams } from 'react-router';
import { analyticsApi } from '../../api/analyticsApi';
import { ComponentRenderer } from './components/ComponentRenderer';
import { PreviewScaleControl } from './components/PreviewScaleControl';
import { RuntimeActionPanel } from './components/RuntimeActionPanel';
import { ScreenRuntimeProvider } from './ScreenRuntimeContext';
import { SharedStoreProvider } from './hooks/useSharedStore';
import type { ScreenConfig, ScreenTheme } from './types';
import { resolveScreenTheme } from './screenThemes';
import { applyThemeCssVariables } from './themes/screenCssVariables';
import { normalizeScreenConfig } from './specV2';
import { tryLoadV2 } from './v2/loader';
import type { ScreenConfigV2 } from './v2/types';
import { V2ScreenRuntime } from './v2/V2ScreenRuntime';
import { buildComponentMap, isComponentEffectivelyVisible } from './componentHierarchy';
import { safeCssBackgroundUrl } from './sanitize';
import { useScreenCarousel } from './hooks/useScreenCarousel';
import { resolveRuntimeScale } from './runtimeScale';
import { resolveRuntimeCanvasScaleStyle } from './runtimeCanvasStyle';
import { ScaleAdapter, type ScaleMode } from './renderers/ScaleAdapter';
import {
	isVisibleForDevice,
	resolveDeviceModeByViewport,
	type DeviceMode,
} from './deviceMode';
import { resolveComponentAppearanceStyle } from './componentAppearance';
import './screenRuntimeKeyframes.css';

const PREVIEW_BATCH_SIZE = 20;

const ENTRY_ANIMATION_KEYFRAMES: Record<string, string> = {
	fadeIn: 'screen-anim-fadeIn',
	slideUp: 'screen-anim-slideUp',
	slideDown: 'screen-anim-slideDown',
	slideLeft: 'screen-anim-slideLeft',
	slideRight: 'screen-anim-slideRight',
	zoomIn: 'screen-anim-zoomIn',
	bounceIn: 'screen-anim-bounceIn',
	rotateIn: 'screen-anim-rotateIn',
};

function resolveEntryAnimationStyle(config: Record<string, unknown>): React.CSSProperties | undefined {
	const type = String(config.animationType ?? 'none');
	const animName = ENTRY_ANIMATION_KEYFRAMES[type];
	if (!animName) return undefined;
	const duration = Number(config.animationDuration ?? 600);
	const delay = Number(config.animationDelay ?? 0);
	const easing = String(config.animationEasing ?? 'ease');
	return {
		animation: `${animName} ${duration}ms ${easing} ${delay}ms both`,
	};
}

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

/* Badge style helpers */

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

/* FAB style helpers */

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

export default function ScreenPreviewPage() {
	const { id } = useParams<{ id: string }>();
	const [screen, setScreen] = useState<ScreenConfig | null>(null);
	/** Sprint-12 F1/T03: v2 响应式大屏走 ResponsiveScreenLayout，与 v1 state 并存。 */
	const [v2Config, setV2Config] = useState<ScreenConfigV2 | null>(null);
	const [loading, setLoading] = useState(true);
	const [error, setError] = useState<string | null>(null);
	const [scale, setScale] = useState(1);
	const [autoScale, setAutoScale] = useState(1);
	const [manualScale, setManualScale] = useState<number | null>(null);
	// Stretch-mode axis scales: distinct sx/sy so the canvas fills the viewport
	// with zero letterbox regardless of screen aspect ratio. Only active when
	// manualScale === null (auto fit-to-screen). Manual zoom still uses uniform scale.
	const [autoScaleX, setAutoScaleX] = useState(1);
	const [autoScaleY, setAutoScaleY] = useState(1);
	const [viewportSize, setViewportSize] = useState({ w: 0, h: 0 });
	const [deviceMode, setDeviceMode] = useState<DeviceMode>('pc');
	const [visibleCount, setVisibleCount] = useState(PREVIEW_BATCH_SIZE);
	// ScaleAdapter mode: activated via ?scaleMode=fit|fill|stretch
	const scaleModeParam = useMemo(() => {
		const p = new URLSearchParams(window.location.search).get('scaleMode');
		return (p === 'fit' || p === 'fill' || p === 'stretch') ? p as ScaleMode : null;
	}, []);
	const [fabOpen, setFabOpen] = useState(false);
	const scrollContainerRef = useRef<HTMLDivElement | null>(null);
	const fabRef = useRef<HTMLDivElement | null>(null);

	useEffect(() => {
		if (!id) {
			setError('未找到大屏ID');
			setLoading(false);
			return;
		}

		analyticsApi.getScreen(id, { mode: 'draft' })
			.then((data) => {
				// Sprint-12 F1/T03: 优先尝试 v2 loader；不匹配才走 v1 normalizer。
				const v2 = tryLoadV2(data);
				if (v2) {
					setV2Config(v2);
					setLoading(false);
					return;
				}
				const normalized = normalizeScreenConfig(data, { id: data.id });
				if (normalized.warnings.length > 0) {
					console.warn('[screen-spec-v2] normalized with warnings:', normalized.warnings);
				}
				setScreen(normalized.config);
				setLoading(false);
			})
			.catch((err) => {
				console.error('Failed to load screen:', err);
				setError('加载大屏失败');
				setLoading(false);
			});
	}, [id]);

	// Multi-page carousel support
	const carousel = useScreenCarousel(screen?.pages, screen?.components || [], screen?.carouselConfig);
	const components = carousel.currentPageComponents;

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
	}, [screen, visibleSortedComponents]);

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
		// Stretch axes: each axis fills the viewport independently, eliminating
		// letterbox when the viewport aspect ratio differs from the design canvas.
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

	useEffect(() => {
		if (!visibleSortedComponents.length) {
			setVisibleCount(PREVIEW_BATCH_SIZE);
			return;
		}

		setVisibleCount(Math.min(PREVIEW_BATCH_SIZE, visibleSortedComponents.length));

		if (visibleSortedComponents.length <= PREVIEW_BATCH_SIZE) {
			return;
		}

		let cancelled = false;
		const loadNextBatch = () => {
			if (cancelled) return;
			setVisibleCount((prev) => {
				const next = Math.min(prev + PREVIEW_BATCH_SIZE, visibleSortedComponents.length);
				return next;
			});
		};

		const timer = window.setInterval(() => {
			if (cancelled) return;
			setVisibleCount((prev) => {
				if (prev >= visibleSortedComponents.length) {
					window.clearInterval(timer);
					return prev;
				}
				return Math.min(prev + PREVIEW_BATCH_SIZE, visibleSortedComponents.length);
			});
		}, 30);

		requestAnimationFrame(loadNextBatch);

		return () => {
			cancelled = true;
			window.clearInterval(timer);
		};
	}, [visibleSortedComponents]);

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

	// Auto mode = stretch (fill viewport, no letterbox); manual zoom = uniform.
	// Uniform path preserves legacy behaviour including zoom-fallback for interactive filters.
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

	const canvasRef = useRef<HTMLDivElement>(null);
	const rawTheme = (!loading && !error && screen) ? (screen as { theme?: string }).theme as ScreenTheme | undefined : undefined;
	const screenTheme = resolveScreenTheme(rawTheme, screen?.backgroundColor);

	// Inject CSS Variables for theme — ensures theme switching takes effect immediately
	useEffect(() => {
		if (canvasRef.current) {
			applyThemeCssVariables(canvasRef.current, screenTheme);
		}
	}, [screenTheme]);

	const isDark = screenTheme !== 'glacier';

	// ── Early returns MUST be after all hooks ──
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
						<h1 className="mb-2.5 text-[26px] font-bold tracking-tight" style={{ letterSpacing: '-0.04em' }}>正在加载预览</h1>
						<p className="m-0" style={{ color: 'rgba(255, 255, 255, 0.5)', lineHeight: 1.7 }}>正在准备已发布运行态画布和设备适配信息。</p>
					</div>
				</div>
			</div>
		);
	}

	if (error || (!screen && !v2Config)) {
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
						<h1 className="mb-2.5 text-[26px] font-bold tracking-tight" style={{ letterSpacing: '-0.04em' }}>预览不可用</h1>
						<p className="m-0" style={{ color: 'rgba(255, 255, 255, 0.5)', lineHeight: 1.7 }}>{error || '未找到大屏'}</p>
					</div>
				</div>
			</div>
		);
	}

	// Sprint-12 F1/T03: v2 响应式大屏走 ResponsiveScreenLayout，跳过 v1 scale 逻辑。
	if (v2Config) {
		return (
			<V2ScreenRuntime
				screen={v2Config}
				runtimeMeta={{ accessMode: 'private' }}
				dataTestId="analytics-screen-preview-v2"
			/>
		);
	}

	// 从这里开始是 v1 渲染路径（screen 非 null）
	if (!screen) {
		return null;
	}

	const screenWidth = contentBounds.width;
	const screenHeight = contentBounds.height;
	// In stretch-fill mode the stage matches the viewport exactly (no letterbox);
	// in manual-zoom mode the stage is sized from the uniform scale (legacy behaviour).
	const stageWidth = useStretchFill
		? Math.max(1, viewportSize.w || screenWidth * scale)
		: Math.max(1, screenWidth * scale);
	const stageHeight = useStretchFill
		? Math.max(1, viewportSize.h || screenHeight * scale)
		: Math.max(1, screenHeight * scale);
	const scalePercent = Math.round(scale * 100);

	return (
		<ScreenRuntimeProvider definitions={screen.globalVariables ?? []}>
		<SharedStoreProvider>
		<div
			data-testid="analytics-screen-preview"
			className="fixed inset-0 overflow-hidden p-0 box-border"
			style={{ ...themeVars(isDark), background: themeBg(isDark), color: themeColor(isDark) }}
		>
			<div ref={scrollContainerRef} className="w-full h-full overflow-auto">
				{scaleModeParam ? (
				<ScaleAdapter designWidth={screenWidth} designHeight={screenHeight} mode={scaleModeParam} className="min-w-full min-h-full flex items-center justify-center p-0 box-border">
					<div ref={canvasRef} className="relative overflow-hidden origin-top-left" style={{
						width: screenWidth, height: screenHeight,
						backgroundColor: carousel.currentPageBgColor || screen.backgroundColor || '#1e1f26',
						backgroundImage: safeCssBackgroundUrl(carousel.currentPageBgImage || screen.backgroundImage),
						backgroundSize: 'cover', backgroundPosition: 'center', backgroundRepeat: 'no-repeat',
					}}>
						{visibleSortedComponents.map((comp) => (
							<div key={comp.id} style={{ position: 'absolute', left: comp.x, top: comp.y, width: comp.width, height: comp.height, zIndex: comp.zIndex, ...resolveEntryAnimationStyle(comp.config), ...resolveComponentAppearanceStyle(comp.config) }}>
								<ComponentRenderer component={comp} mode="preview" theme={screenTheme} />
							</div>
						))}
					</div>
				</ScaleAdapter>
				) : (
				<div className="min-w-full min-h-full flex items-center justify-center p-0 box-border">
					<div className="relative flex-none" style={{ width: stageWidth, height: stageHeight }}>
						<div
							className="relative w-full h-full overflow-hidden"
						>
						<div
							ref={canvasRef}
							className="relative overflow-hidden origin-top-left"
							style={{
								width: screenWidth,
								height: screenHeight,
								backgroundColor: carousel.currentPageBgColor || screen.backgroundColor || '#1e1f26',
								backgroundImage: safeCssBackgroundUrl(carousel.currentPageBgImage || screen.backgroundImage),
								backgroundSize: 'cover',
								backgroundPosition: 'center',
								backgroundRepeat: 'no-repeat',
								...runtimeCanvasScaleStyle,
							}}
						>
							{visibleSortedComponents
								.slice(0, visibleCount)
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
											...resolveEntryAnimationStyle(component.config),
											...resolveComponentAppearanceStyle(component.config),
										}}
									>
										<ComponentRenderer component={component} mode="preview" theme={screenTheme} />
									</div>
								))}

							{visibleCount < visibleSortedComponents.length && (
								<div
									className="absolute right-3.5 bottom-3.5 z-[1000] inline-flex items-center rounded-full text-xs font-semibold"
									style={{
										minHeight: 34,
										padding: '0 12px',
										border: '1px solid rgba(148, 163, 184, 0.18)',
										background: isDark ? 'rgba(22, 32, 50, 0.88)' : 'rgba(255, 255, 255, 0.88)',
										color: isDark ? 'rgba(255, 255, 255, 0.7)' : 'var(--runtime-control-text-muted)',
										borderColor: isDark ? 'rgba(255, 255, 255, 0.1)' : 'rgba(148, 163, 184, 0.18)',
									}}
								>
									组件加载中 {visibleCount}/{visibleSortedComponents.length}
								</div>
							)}
						</div>
						</div>
					</div>
				</div>
				)}
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
			<div ref={fabRef} className="fixed bottom-6 right-6 z-[11000]">
				<button
					type="button"
					className="flex items-center justify-center w-12 h-12 border-none rounded-full text-white text-[22px] cursor-pointer transition-[background,transform] duration-200"
					style={{
						...fabTriggerStyle(isDark),
						boxShadow: '0 8px 24px rgba(15, 23, 42, 0.25)',
					}}
					onClick={() => setFabOpen((prev) => !prev)}
					title="预览控制面板"
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
									Published
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
						</div>
					</div>
				)}
			</div>
		</div>
		</SharedStoreProvider>
		</ScreenRuntimeProvider>
	);
}
