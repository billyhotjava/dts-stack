import { useEffect, useMemo, useRef, useState } from 'react';
import { useParams } from 'react-router';
import { analyticsApi, HttpError } from '../../api/analyticsApi';
import { ComponentRenderer } from './components/ComponentRenderer';
import { RuntimeActionPanel } from './components/RuntimeActionPanel';
import { ScreenRuntimeProvider } from './ScreenRuntimeContext';
import type { DeviceMode } from './deviceMode';
import { isVisibleForDevice, resolveDeviceModeByViewport } from './deviceMode';
import { normalizeScreenConfig, buildScreenPayload } from './specV2';
import { resolveScreenTheme } from './screenThemes';
import { escapeHtml, safeCssBackgroundUrl } from './sanitize';
import type { ScreenConfig, ScreenTheme } from './types';
import './ScreenRuntimeShell.css';

function parseFormat(raw: string | null): 'png' | 'pdf' | 'json' {
    const text = String(raw || '').trim().toLowerCase();
    if (text === 'pdf' || text === 'json') return text;
    return 'png';
}

function parseMode(raw: string | null): 'draft' | 'published' | 'preview' {
    const text = String(raw || '').trim().toLowerCase();
    if (text === 'published' || text === 'preview') return text;
    return 'draft';
}

function parseDevice(raw: string | null): DeviceMode | null {
    const text = String(raw || '').trim().toLowerCase();
    if (text === 'pc' || text === 'tablet' || text === 'mobile') return text;
    return null;
}

function parseDelayMs(raw: string | null): number {
    const n = Number(raw ?? 0);
    if (!Number.isFinite(n) || n < 0) return 1200;
    return Math.min(12000, Math.max(200, Math.floor(n)));
}

function parsePixelRatio(raw: string | null): number | null {
    if (raw == null || String(raw).trim().length === 0) {
        return null;
    }
    const n = Number(raw);
    if (!Number.isFinite(n) || n <= 0) {
        return null;
    }
    return Math.max(1, Math.min(3, n));
}

function toErrorMessage(error: unknown, fallback: string): string {
    if (error instanceof HttpError) {
        try {
            const payload = JSON.parse(error.bodyText) as { message?: string };
            if (payload?.message) {
                return payload.message;
            }
        } catch {
            // ignore
        }
        return error.message || fallback;
    }
    if (error instanceof Error && error.message) {
        return error.message;
    }
    return fallback;
}

function isCrossOriginLikeError(error: unknown): boolean {
    if (!(error instanceof Error)) {
        return false;
    }
    const msg = String(error.message || '').toLowerCase();
    return msg.includes('tainted')
        || msg.includes('cross-origin')
        || msg.includes('cross origin')
        || msg.includes('securityerror')
        || msg.includes('toDataURL'.toLowerCase());
}

export default function ScreenExportPage() {
    const { id } = useParams<{ id: string }>();
    const [screen, setScreen] = useState<ScreenConfig | null>(null);
    const [loading, setLoading] = useState(true);
    const [statusText, setStatusText] = useState('正在准备导出...');
    const [error, setError] = useState<string | null>(null);
    const [requestId, setRequestId] = useState<string | null>(null);
    const [previewUrl, setPreviewUrl] = useState<string | null>(null);
    const [specDigest, setSpecDigest] = useState<string | null>(null);
    const [effectiveMode, setEffectiveMode] = useState<string>('draft');
    const [effectiveDevice, setEffectiveDevice] = useState<DeviceMode>('pc');
    const [watermark, setWatermark] = useState<{ enabled: boolean; text: string }>({
        enabled: false,
        text: '',
    });
    const canvasRef = useRef<HTMLDivElement | null>(null);
    const doneRef = useRef(false);
    const [retryNonce, setRetryNonce] = useState(0);

    const query = useMemo(() => new URLSearchParams(window.location.search), []);
    const format = useMemo(() => parseFormat(query.get('format')), [query]);
    const mode = useMemo(() => parseMode(query.get('mode')), [query]);
    const forcedDevice = useMemo(() => parseDevice(query.get('device')), [query]);
    const delayMs = useMemo(() => parseDelayMs(query.get('delayMs')), [query]);
    const requestedPixelRatio = useMemo(() => parsePixelRatio(query.get('pixelRatio')), [query]);
    const exportPixelRatio = useMemo(() => {
        if (requestedPixelRatio != null) {
            return requestedPixelRatio;
        }
        const browserRatio = Number.isFinite(window.devicePixelRatio)
            ? Math.max(1, Math.min(window.devicePixelRatio, 3))
            : 1;
        const baseRatio = format === 'pdf'
            ? Math.max(1.5, Math.min(browserRatio, 2))
            : Math.max(2, Math.min(browserRatio, 3));
        if (effectiveDevice === 'mobile') {
            return Number(Math.min(3, baseRatio + 0.5).toFixed(2));
        }
        if (effectiveDevice === 'tablet') {
            return Number(Math.min(3, baseRatio + 0.25).toFixed(2));
        }
        return Number(baseRatio.toFixed(2));
    }, [effectiveDevice, format, requestedPixelRatio]);

    useEffect(() => {
        setEffectiveMode(mode);
    }, [mode]);

    useEffect(() => {
        if (!id) {
            setError('未找到大屏 ID');
            setLoading(false);
            return;
        }

        let cancelled = false;
        const load = async () => {
            doneRef.current = false;
            setLoading(true);
            setError(null);
            setStatusText('正在校验导出策略...');
            try {
                const prepared = await analyticsApi.prepareScreenExport(id, {
                    format,
                    mode,
                    ...(forcedDevice ? { device: forcedDevice } : {}),
                    includeScreenSpec: true,
                });
                if (cancelled) return;
                setRequestId(prepared.requestId ?? null);
                setSpecDigest(String(prepared.specDigest || '').trim() || null);
                const resolvedMode = String(prepared.resolvedMode || prepared.mode || mode || 'draft').trim().toLowerCase();
                setEffectiveMode(resolvedMode || 'draft');
                const rawPreview = String(prepared.previewUrl || '').trim();
                if (rawPreview.length > 0) {
                    const normalizedPreview = rawPreview.startsWith('/analytics')
                        ? rawPreview
                        : (rawPreview.startsWith('/') ? `/analytics${rawPreview}` : `/analytics/${rawPreview}`);
                    setPreviewUrl(normalizedPreview);
                } else {
                    setPreviewUrl(null);
                }
                setWatermark({
                    enabled: prepared.policy?.watermarkEnabled === true,
                    text: String(prepared.policy?.watermarkText || '').trim(),
                });
                setStatusText('正在加载运行态画布...');
                if (prepared.screenSpec && typeof prepared.screenSpec === 'object') {
                    const normalized = normalizeScreenConfig(prepared.screenSpec, { id });
                    if (normalized.warnings.length > 0) {
                        console.warn('[screen-export] prepared snapshot normalized warnings:', normalized.warnings);
                    }
                    if (!cancelled) {
                        setScreen(normalized.config);
                        setEffectiveDevice(forcedDevice || resolveDeviceModeByViewport(window.innerWidth));
                        setStatusText('正在渲染导出内容...');
                    }
                    return;
                }

                const detail = await analyticsApi.getScreen(id, {
                    mode: resolvedMode === 'published' ? 'published' : mode,
                    fallbackDraft: true,
                });
                if (cancelled) return;
                const normalized = normalizeScreenConfig(detail, { id: detail.id });
                if (normalized.warnings.length > 0) {
                    console.warn('[screen-export] normalized warnings:', normalized.warnings);
                }
                setScreen(normalized.config);
                setEffectiveDevice(forcedDevice || resolveDeviceModeByViewport(window.innerWidth));
                setStatusText('正在渲染导出内容...');
            } catch (e) {
                if (cancelled) return;
                setError(toErrorMessage(e, '导出准备失败'));
            } finally {
                if (!cancelled) {
                    setLoading(false);
                }
            }
        };

        load();
        return () => {
            cancelled = true;
        };
    }, [forcedDevice, format, id, mode]);

    const buildPreviewFallbackUrl = (): string => {
        if (previewUrl && previewUrl.trim().length > 0) {
            return previewUrl;
        }
        const fallbackParams = new URLSearchParams();
        if (effectiveMode !== 'draft') {
            fallbackParams.set('mode', effectiveMode);
        }
        if (forcedDevice) {
            fallbackParams.set('device', forcedDevice);
        }
        const suffix = fallbackParams.toString();
        return `/analytics/screens/${id}/preview${suffix ? `?${suffix}` : ''}`;
    };

    const openPreviewFallback = () => {
        const target = buildPreviewFallbackUrl();
        const popup = window.open(target, '_blank');
        if (!popup) {
            throw new Error('请允许弹窗后打开预览页');
        }
    };

    const printCanvasDomFallback = () => {
        const canvasEl = canvasRef.current;
        if (!canvasEl || !screen) {
            throw new Error('导出画布未就绪');
        }
        const width = Math.max(1, Math.round(screen.width || 1920));
        const height = Math.max(1, Math.round(screen.height || 1080));
        const popup = window.open('', '_blank');
        if (!popup) {
            throw new Error('请允许弹窗后重试 PDF 导出');
        }
        const serialized = new XMLSerializer().serializeToString(canvasEl);
        popup.document.write(
            `<html><head><title>${escapeHtml(screen.name || 'screen')}</title>`
            + '<style>'
            + 'html,body{margin:0;padding:0;background:#fff;}'
            + `.print-root{width:${width}px;height:${height}px;position:relative;overflow:hidden;}`
            + '@page{size:auto;margin:0;}'
            + '</style></head>'
            + `<body><div class="print-root">${serialized}</div></body></html>`,
        );
        popup.document.close();
        window.setTimeout(() => {
            popup.focus();
            popup.print();
        }, 200);
    };

    const reportExport = async (
        status: 'success' | 'failed' | 'fallback',
        message?: string,
    ) => {
        if (!id) {
            return;
        }
        try {
            await analyticsApi.reportScreenExport(id, {
                status,
                format,
                mode,
                resolvedMode: effectiveMode,
                device: effectiveDevice,
                requestId: requestId || undefined,
                specDigest: specDigest || undefined,
                message,
            });
        } catch {
            // Export report must not block user workflow.
        }
    };

    const downloadBlob = (blob: Blob, fileName: string) => {
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = fileName;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        URL.revokeObjectURL(url);
    };

    const captureCanvasAsPngDataUrl = async (): Promise<string> => {
        const canvasEl = canvasRef.current;
        if (!canvasEl || !screen) {
            throw new Error('导出画布未就绪');
        }
        const width = Math.max(1, Math.round(screen.width || 1920));
        const height = Math.max(1, Math.round(screen.height || 1080));
        const serialized = new XMLSerializer().serializeToString(canvasEl);
        const svg = [
            `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}">`,
            '<foreignObject width="100%" height="100%">',
            serialized,
            '</foreignObject>',
            '</svg>',
        ].join('');
        const blob = new Blob([svg], { type: 'image/svg+xml;charset=utf-8' });
        const url = URL.createObjectURL(blob);
        try {
            const image = await new Promise<HTMLImageElement>((resolve, reject) => {
                const img = new Image();
                img.onload = () => resolve(img);
                img.onerror = reject;
                img.src = url;
            });
            const out = document.createElement('canvas');
            out.width = width;
            out.height = height;
            const ctx = out.getContext('2d');
            if (!ctx) {
                throw new Error('导出上下文初始化失败');
            }
            ctx.drawImage(image, 0, 0, width, height);
            return out.toDataURL('image/png');
        } finally {
            URL.revokeObjectURL(url);
        }
    };

    useEffect(() => {
        if (loading || !screen || error || doneRef.current) {
            return;
        }
        doneRef.current = true;
        let cancelled = false;

        const run = async () => {
            await new Promise((resolve) => window.setTimeout(resolve, delayMs));
            if (cancelled) return;

            try {
                if (format === 'json') {
                    const payload = {
                        schema: 'dts.screen.spec',
                        exportedAt: new Date().toISOString(),
                        screenSpec: buildScreenPayload(screen),
                    };
                    const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json;charset=utf-8' });
                    const url = URL.createObjectURL(blob);
                    const link = document.createElement('a');
                    link.href = url;
                    link.download = `${screen.name || 'screen'}-spec.json`;
                    document.body.appendChild(link);
                    link.click();
                    document.body.removeChild(link);
                    URL.revokeObjectURL(url);
                    void reportExport('success');
                    setStatusText('JSON 导出完成，可关闭窗口');
                    return;
                }

                if (format === 'png' || format === 'pdf') {
                    if (!id) {
                        throw new Error('未找到大屏 ID');
                    }
                    setStatusText('正在执行服务端一致性导出...');
                    try {
                        const rendered = await analyticsApi.renderScreenExport(id, {
                            format,
                            mode: effectiveMode || mode,
                            device: effectiveDevice,
                            pixelRatio: exportPixelRatio,
                            screenSpec: buildScreenPayload(screen),
                        });
                        if (cancelled) return;
                        const ext = format === 'pdf' ? 'pdf' : 'png';
                        const fallbackName = `${screen.name || 'screen'}.${ext}`;
                        downloadBlob(rendered.blob, rendered.fileName || fallbackName);
                        const resolvedRatio = rendered.pixelRatio ?? exportPixelRatio;
                        const hiddenByDevice = rendered.hiddenByDevice ?? 0;
                        void reportExport(
                            'success',
                            `server_render:${rendered.renderEngine || 'unknown'};ratio:${resolvedRatio};hiddenByDevice:${hiddenByDevice}`,
                        );
                        setStatusText(
                            `${format.toUpperCase()} 服务端导出完成，可关闭窗口`
                            + (hiddenByDevice > 0 ? `（按设备模式隐藏 ${hiddenByDevice} 个组件）` : ''),
                        );
                        return;
                    } catch (serverError) {
                        console.warn('[screen-export] server render failed, fallback to browser path:', serverError);
                        setStatusText('服务端导出失败，切换浏览器回退导出...');
                    }
                }

                setStatusText('正在生成导出文件...');
                const dataUrl = await captureCanvasAsPngDataUrl();
                if (cancelled) return;

                if (format === 'png') {
                    const link = document.createElement('a');
                    link.href = dataUrl;
                    link.download = `${screen.name || 'screen'}.png`;
                    document.body.appendChild(link);
                    link.click();
                    document.body.removeChild(link);
                    void reportExport('success');
                    setStatusText('PNG 导出完成，可关闭窗口');
                    return;
                }

                const popup = window.open('', '_blank');
                if (!popup) {
                    throw new Error('请允许弹窗后重试 PDF 导出');
                }
                popup.document.write(
                    `<html><head><title>${escapeHtml(screen.name || 'screen')}</title></head>`
                    + '<body style="margin:0"><img src="'
                    + dataUrl
                    + '" style="width:100%;height:auto;display:block"/></body></html>',
                );
                popup.document.close();
                popup.focus();
                popup.print();
                void reportExport('success');
                setStatusText('PDF 导出窗口已打开');
            } catch (e) {
                if (format === 'pdf') {
                    try {
                        printCanvasDomFallback();
                        void reportExport('fallback', 'capture_failed_print_fallback');
                        setStatusText('截图导出失败，已切换打印回退模式');
                        return;
                    } catch {
                        // continue to unified error handling
                    }
                }
                if (format === 'png' && isCrossOriginLikeError(e)) {
                    try {
                        openPreviewFallback();
                        void reportExport('fallback', 'capture_failed_preview_fallback');
                        setStatusText('PNG 自动导出失败，已打开预览页回退模式');
                    } catch {
                        // fallback open failed, keep original error
                    }
                    setError('PNG 自动导出失败（跨域资源限制）。已尝试打开预览页，请使用系统截图导出。');
                    return;
                }
                const msg = toErrorMessage(e, '导出失败');
                void reportExport('failed', msg);
                setError(msg);
            }
        };

        run();
        return () => {
            cancelled = true;
        };
    }, [delayMs, effectiveDevice, effectiveMode, error, exportPixelRatio, format, id, loading, mode, retryNonce, screen]);

    const rawTheme = screen?.theme as ScreenTheme | undefined;
    const screenTheme = resolveScreenTheme(rawTheme, screen?.backgroundColor);
    const components = (screen?.components ?? [])
        .filter((item) => item.visible && isVisibleForDevice(item, effectiveDevice))
        .sort((a, b) => a.zIndex - b.zIndex);

    return (
        <ScreenRuntimeProvider definitions={screen?.globalVariables ?? []}>
            <div className={`screen-runtime screen-export-shell ${screenTheme === 'glacier' ? 'screen-runtime--light' : 'screen-runtime--dark'}`}>
                <div className="screen-export-header">
                    <div className="screen-runtime__eyebrow">Screen Export</div>
                    <div className="screen-runtime__title">{screen?.name || '导出任务'}</div>
                    <div className="screen-runtime__meta-row">
                        <span className="screen-runtime__badge is-info">{format.toUpperCase()}</span>
                        <span className="screen-runtime__badge">模式 {effectiveMode}</span>
                        <span className="screen-runtime__badge">设备 {effectiveDevice}</span>
                        <span className="screen-runtime__badge">像素比 {exportPixelRatio}</span>
                        {requestId ? <span className="screen-runtime__badge">request {requestId}</span> : null}
                        {specDigest ? <span className="screen-runtime__badge">spec {specDigest.slice(0, 12)}</span> : null}
                    </div>
                </div>
                <div className={`screen-export-status ${error ? 'is-error' : ''}`}>
                    <span className={`screen-runtime__badge ${error ? 'is-warning' : 'is-info'}`}>
                        {error ? 'Export Failed' : (loading ? 'Rendering' : 'Runtime Ready')}
                    </span>
                    <div>{error ? `错误：${error}` : statusText}</div>
                </div>
                {error && !loading && (
                    <div className="screen-export-actions">
                        <button
                            type="button"
                            className="runtime-control-btn is-primary"
                            onClick={() => {
                                setError(null);
                                setStatusText('正在重试导出...');
                                doneRef.current = false;
                                setRetryNonce((prev) => prev + 1);
                            }}
                        >
                            重试导出
                        </button>
                        <button
                            type="button"
                            className="runtime-control-btn"
                            onClick={() => {
                                try {
                                    openPreviewFallback();
                                } catch (openError) {
                                    alert(toErrorMessage(openError, '打开预览页失败'));
                                }
                            }}
                        >
                            打开预览页
                        </button>
                        {format === 'pdf' && (
                            <button
                                type="button"
                                className="runtime-control-btn"
                                onClick={() => {
                                    try {
                                        printCanvasDomFallback();
                                    } catch (printError) {
                                        alert(toErrorMessage(printError, 'PDF 打印回退失败'));
                                    }
                                }}
                            >
                                PDF 打印回退
                            </button>
                        )}
                    </div>
                )}
                <div className="screen-export-canvas-shell">
                    {loading || !screen ? (
                        <div className="screen-runtime__feedback-card">
                            <h1>正在准备导出</h1>
                            <p>运行态画布、导出策略和渲染引擎正在同步。</p>
                        </div>
                    ) : (
                        <div
                            ref={canvasRef}
                            className="screen-export-canvas-frame"
                            style={{
                                width: screen.width || 1920,
                                height: screen.height || 1080,
                                backgroundColor: screen.backgroundColor || '#0d1b2a',
                                backgroundImage: safeCssBackgroundUrl(screen.backgroundImage),
                                backgroundSize: 'cover',
                                backgroundPosition: 'center',
                                position: 'relative',
                                overflow: 'hidden',
                            }}
                        >
                            {components.map((component) => (
                                <div
                                    key={component.id}
                                    data-component-id={component.id}
                                    data-component-name={component.name}
                                    data-component-type={component.type}
                                    style={{
                                        position: 'absolute',
                                        left: component.x,
                                        top: component.y,
                                        width: component.width,
                                        height: component.height,
                                        zIndex: component.zIndex,
                                    }}
                                >
                                    <ComponentRenderer component={component} mode="preview" theme={screenTheme} />
                                </div>
                            ))}
                            {watermark.enabled && watermark.text && (
                                <div className="screen-export-watermark">
                                    {Array.from({ length: 20 }).map((_, idx) => (
                                        <div
                                            key={`wm-${idx}`}
                                            className="screen-export-watermark__item"
                                            style={{
                                                left: `${(idx % 5) * 22}%`,
                                                top: `${Math.floor(idx / 5) * 24}%`,
                                                color: screenTheme === 'glacier' ? '#111827' : '#f8fafc',
                                            }}
                                        >
                                            {watermark.text}
                                        </div>
                                    ))}
                                </div>
                            )}
                        </div>
                    )}
                </div>
                <RuntimeActionPanel />
            </div>
        </ScreenRuntimeProvider>
    );
}
