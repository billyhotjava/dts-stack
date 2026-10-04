import { useCallback } from 'react';
import { toast } from 'sonner';
import {
    analyticsApi,
    HttpError,
    type ScreenExportPrepareResult,
    type ScreenExportReportRequest,
    type ScreenExportRenderResult,
} from '../../../../api/analyticsApi';
import { resolveRouteForOpen } from '../../../../helpers/resolveAnalyticsUrl';
import { buildScreenPayload } from '../../screenSpec';
import type { ScreenConfig } from '../../types';

type ScreenExportFormat = 'png' | 'pdf';
type PreviewDeviceMode = 'auto' | 'pc' | 'tablet' | 'mobile';

interface UseScreenExportActionsParams {
    id?: string;
    persistedConfig: ScreenConfig;
    previewDeviceMode: PreviewDeviceMode;
    screenName?: string;
}

interface ExportAttemptContext {
    requestId?: string;
    specDigest?: string;
}

function getDeviceReportPayload(previewDeviceMode: PreviewDeviceMode): Pick<ScreenExportReportRequest, 'device'> | Record<string, never> {
    return previewDeviceMode === 'auto' ? {} : { device: previewDeviceMode };
}

function resolveExportPixelRatio(format: ScreenExportFormat, previewDeviceMode: PreviewDeviceMode): number {
    const browserRatio = typeof window !== 'undefined' && Number.isFinite(window.devicePixelRatio)
        ? Math.max(1, Math.min(window.devicePixelRatio, 3))
        : 1;
    const baseRatio = format === 'pdf'
        ? Math.max(1.5, Math.min(browserRatio, 2))
        : Math.max(2, Math.min(browserRatio, 3));

    if (previewDeviceMode === 'mobile') {
        return Number(Math.min(3, baseRatio + 0.5).toFixed(2));
    }
    if (previewDeviceMode === 'tablet') {
        return Number(Math.min(3, baseRatio + 0.25).toFixed(2));
    }
    return Number(baseRatio.toFixed(2));
}

function downloadBlob(blob: Blob, fileName: string) {
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = fileName;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
}

function resolveExportErrorMessage(error: unknown): string {
    if (error instanceof HttpError) {
        try {
            const payload = JSON.parse(error.bodyText) as { message?: string };
            if (payload?.message) {
                return payload.message;
            }
        } catch {
            // keep the original HTTP error message
        }
        return error.message || '导出失败';
    }
    return error instanceof Error ? error.message : '导出失败';
}

export function useScreenExportActions({
    id,
    persistedConfig,
    previewDeviceMode,
    screenName,
}: UseScreenExportActionsParams) {
    const ensureExportAllowed = useCallback(async (format: ScreenExportFormat): Promise<ScreenExportPrepareResult | null> => {
        if (!id) {
            return null;
        }
        try {
            return await analyticsApi.prepareScreenExport(id, {
                format,
                mode: 'draft',
                ...(previewDeviceMode === 'auto' ? {} : { device: previewDeviceMode }),
            });
        } catch (error) {
            throw new Error(resolveExportErrorMessage(error));
        }
    }, [id, previewDeviceMode]);

    const reportExport = useCallback((
        format: ScreenExportFormat,
        status: ScreenExportReportRequest['status'],
        context: ExportAttemptContext,
        patch: Partial<ScreenExportReportRequest> = {},
    ) => {
        if (!id) {
            return;
        }
        void analyticsApi.reportScreenExport(id, {
            status,
            format,
            mode: 'draft',
            resolvedMode: 'draft',
            ...getDeviceReportPayload(previewDeviceMode),
            requestId: context.requestId,
            specDigest: context.specDigest,
            ...patch,
        });
    }, [id, previewDeviceMode]);

    const openExportWindow = useCallback((format: ScreenExportFormat) => {
        if (!id) {
            throw new Error('请先保存大屏后再导出');
        }
        const params = new URLSearchParams();
        params.set('format', format);
        params.set('mode', 'draft');
        params.set('pixelRatio', String(resolveExportPixelRatio(format, previewDeviceMode)));
        if (previewDeviceMode !== 'auto') {
            params.set('device', previewDeviceMode);
        }
        const url = resolveRouteForOpen(`/bi/screens/${id}/export?${params.toString()}`);
        const popup = window.open(url, '_blank', 'noopener,noreferrer');
        if (!popup) {
            throw new Error('请允许弹窗后重试导出');
        }
    }, [id, previewDeviceMode]);

    const renderExportByServer = useCallback(async (format: ScreenExportFormat): Promise<ScreenExportRenderResult> => {
        if (!id) {
            throw new Error('请先保存大屏后再导出');
        }
        const rendered = await analyticsApi.renderScreenExport(id, {
            format,
            mode: 'draft',
            ...(previewDeviceMode === 'auto' ? {} : { device: previewDeviceMode }),
            pixelRatio: resolveExportPixelRatio(format, previewDeviceMode),
            screenSpec: buildScreenPayload(persistedConfig) as unknown as Record<string, unknown>,
        });
        const fallbackName = `${screenName || 'screen'}.${format}`;
        downloadBlob(rendered.blob, rendered.fileName || fallbackName);
        return rendered;
    }, [id, persistedConfig, previewDeviceMode, screenName]);

    const executeExport = useCallback(async (format: ScreenExportFormat) => {
        const label = format.toUpperCase();
        const context: ExportAttemptContext = {};

        try {
            const prepared = await ensureExportAllowed(format);
            context.requestId = prepared?.requestId || undefined;
            context.specDigest = prepared?.specDigest || undefined;
        } catch (error) {
            reportExport(format, 'failed', context, {
                message: error instanceof Error ? error.message : 'prepare_failed',
            });
            toast.error(error instanceof Error ? error.message : `${label} 导出失败`);
            return;
        }

        try {
            const rendered = await renderExportByServer(format);
            reportExport(format, 'success', {
                requestId: rendered.requestId || context.requestId,
                specDigest: rendered.specDigest || context.specDigest,
            }, {
                resolvedMode: rendered.resolvedMode || 'draft',
            });
        } catch (error) {
            console.warn(`Failed to export ${format} by server render, fallback to export page:`, error);
            try {
                openExportWindow(format);
                reportExport(format, 'fallback', context, {
                    message: error instanceof Error ? error.message : 'server_render_failed',
                });
            } catch (fallbackError) {
                console.error(`Failed to export ${format}:`, fallbackError);
                reportExport(format, 'failed', context, {
                    message: fallbackError instanceof Error ? fallbackError.message : 'export_failed',
                });
                toast.error(fallbackError instanceof Error ? fallbackError.message : `${label} 导出失败`);
            }
        }
    }, [ensureExportAllowed, openExportWindow, renderExportByServer, reportExport]);

    return {
        handleExportPng: useCallback(() => executeExport('png'), [executeExport]),
        handleExportPdf: useCallback(() => executeExport('pdf'), [executeExport]),
    };
}
