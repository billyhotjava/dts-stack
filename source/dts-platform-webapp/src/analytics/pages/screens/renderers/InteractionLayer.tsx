import { useMemo, useCallback, useRef } from 'react';
import { analyticsApi, type ScreenListItem } from '../../../api/analyticsApi';
import type { ScreenComponent, ComponentInteractionMapping, ScreenComponentAction } from '../types';
import type { DrillState } from '../hooks/useDrillDown';
import {
    resolveInteractionValue, resolveInteractionMappedValue,
    resolveInteractionUrlTemplate, normalizeFilterDebounceMs,
} from './shared/chartUtils';
import {
    buildActionRuntimeParams,
    normalizeScreenActionType,
    resolvePreferredDrillValue,
    resolveActionMappingValues,
    resolveActionTemplateText,
} from './shared/actionUtils';
import type { RuntimeEventKind } from '../ScreenRuntimeContext';
import { resolveRouteForOpen } from '../../../helpers/resolveAnalyticsUrl';

// ---------------------------------------------------------------------------
// Screen-reference URL resolution (moved from ComponentRenderer)
// ---------------------------------------------------------------------------

const SCREEN_REF_PREFIX = 'screen-ref:';
const SCREEN_REF_CACHE_TTL_MS = 30_000;
let screenRefCache: { expiresAt: number; items: ScreenListItem[] } | null = null;

function parseScreenReferenceUrl(targetUrl: string): { screenName: string; fallbackUrl: string } | null {
    if (!targetUrl.startsWith(SCREEN_REF_PREFIX)) {
        return null;
    }
    const raw = targetUrl.slice(SCREEN_REF_PREFIX.length);
    const [screenNamePart, fallbackPart = ''] = raw.split('|', 2);
    const screenName = decodeURIComponent(screenNamePart || '').trim();
    const fallbackUrl = decodeURIComponent(fallbackPart || '').trim() || '/bi/screens';
    if (!screenName) {
        return { screenName: '', fallbackUrl };
    }
    return { screenName, fallbackUrl };
}

export async function resolveScreenReferenceUrl(targetUrl: string): Promise<string> {
    const parsed = parseScreenReferenceUrl(targetUrl);
    if (!parsed) {
        return targetUrl;
    }
    if (!parsed.screenName) {
        return parsed.fallbackUrl;
    }
    try {
        const now = Date.now();
        let items = screenRefCache && screenRefCache.expiresAt > now ? screenRefCache.items : null;
        if (!items) {
            items = await analyticsApi.listScreens();
            screenRefCache = { expiresAt: now + SCREEN_REF_CACHE_TTL_MS, items };
        }
        const exact = items
            .filter((item) => String(item.name || '').trim() === parsed.screenName)
            .sort((a, b) => new Date(String(b.updatedAt || 0)).getTime() - new Date(String(a.updatedAt || 0)).getTime())[0];
        if (exact?.id != null) {
            return resolveRouteForOpen(`/bi/screens/${encodeURIComponent(String(exact.id))}/preview`);
        }
    } catch (error) {
        console.error('Failed to resolve screen reference jump target:', error);
    }
    return parsed.fallbackUrl;
}

// ---------------------------------------------------------------------------
// ScreenRuntime – structural type matching ScreenRuntimeContextValue
// ---------------------------------------------------------------------------

interface ScreenRuntime {
    values: Record<string, string>;
    setVariable: (key: string, value: string, source?: string) => void;
    trackEvent: (event: { kind: RuntimeEventKind; key: string; value: string; source: string; meta?: string }) => void;
    openPanel: (title: string, body: string, source?: string) => void;
}

// ---------------------------------------------------------------------------
// Hook
// ---------------------------------------------------------------------------

export function useComponentInteractions(
    component: ScreenComponent,
    mode: string,
    runtime: ScreenRuntime,
    drillState: DrillState,
    drillRuntimeEnabled: boolean,
    drillActive: boolean,
) {
    const filterVariableTimersRef = useRef<Map<string, ReturnType<typeof setTimeout>>>(new Map());

    const scheduleFilterVariableUpdate = (
        key: string,
        value: string,
        source: string,
        debounceMsRaw: unknown,
        immediate = false,
    ) => {
        const safeKey = String(key || '').trim();
        if (!safeKey) return;
        const debounceMs = normalizeFilterDebounceMs(debounceMsRaw);
        const currentTimer = filterVariableTimersRef.current.get(safeKey);
        if (currentTimer) {
            clearTimeout(currentTimer);
            filterVariableTimersRef.current.delete(safeKey);
        }
        if (immediate || debounceMs <= 0) {
            runtime.setVariable(safeKey, value, source);
            return;
        }
        const timer = setTimeout(() => {
            filterVariableTimersRef.current.delete(safeKey);
            runtime.setVariable(safeKey, value, `${source}:debounced`);
        }, debounceMs);
        filterVariableTimersRef.current.set(safeKey, timer);
    };

    const interactionMappings = useMemo(() => (
        mode === "preview" && component.interaction?.enabled
            ? (component.interaction.mappings ?? []).filter((m): m is ComponentInteractionMapping => !!m && !!m.variableKey && !!m.sourcePath)
            : []
    ), [component.interaction, mode]);

    const interactionJump = useMemo<{ template: string; openMode: 'self' | 'new-tab' } | null>(() => {
        if (mode !== 'preview' || component.interaction?.enabled !== true || component.interaction?.jumpEnabled !== true) {
            return null;
        }
        const template = String(component.interaction.jumpUrlTemplate || '').trim();
        if (!template) return null;
        return {
            template,
            openMode: component.interaction.jumpOpenMode === 'self' ? 'self' : 'new-tab',
        };
    }, [component.interaction, mode]);

    const componentActions = useMemo(() => (
        mode === 'preview'
            ? (component.actions ?? []).filter((action): action is ScreenComponentAction => normalizeScreenActionType(action?.type) !== null)
            : []
    ), [component.actions, mode]);

    const navigateToResolvedUrl = useCallback(async (targetUrl: string, openMode: 'self' | 'new-tab', source: string) => {
        const resolvedTargetUrl = await resolveScreenReferenceUrl(targetUrl);
        runtime.trackEvent({
            kind: 'jump',
            key: 'jumpUrl',
            value: resolvedTargetUrl,
            source,
            meta: `openMode=${openMode};raw=${targetUrl}`,
        });
        if (openMode === 'self') {
            if (resolvedTargetUrl.startsWith('/') || (() => { try { return new URL(resolvedTargetUrl).origin === window.location.origin; } catch { return false; } })()) {
                window.location.assign(resolvedTargetUrl);
            }
            return;
        }
        window.open(resolvedTargetUrl, '_blank', 'noopener,noreferrer');
    }, [runtime]);

    const executeComponentActions = useCallback((params: Record<string, unknown>) => {
        if (mode !== 'preview' || componentActions.length === 0) {
            return;
        }
        const actionParams = buildActionRuntimeParams(runtime.values, params);
        for (const action of componentActions) {
            const actionType = normalizeScreenActionType(action.type);
            if (!actionType) {
                continue;
            }
            const mappedValues = resolveActionMappingValues(actionParams, action.mappings);
            for (const [key, value] of Object.entries(mappedValues)) {
                runtime.setVariable(key, value, `action:${component.id}:${actionType}`);
            }
            if (actionType === 'set-variable') {
                runtime.trackEvent({
                    kind: 'action',
                    key: actionType,
                    value: Object.keys(mappedValues).join(','),
                    source: `action:${component.id}`,
                    meta: action.label || undefined,
                });
                continue;
            }
            if (actionType === 'drill-down') {
                if (!drillRuntimeEnabled || !drillState.canDrillDown) {
                    continue;
                }
                const clickedValue = resolvePreferredDrillValue(actionParams);
                if (!clickedValue) {
                    continue;
                }
                runtime.trackEvent({
                    kind: 'drill-down',
                    key: 'drillValue',
                    value: clickedValue,
                    source: `action:${component.id}`,
                    meta: action.label || undefined,
                });
                drillState.handleDrill(clickedValue);
                continue;
            }
            if (actionType === 'drill-up') {
                if (!drillRuntimeEnabled || drillState.breadcrumbs.length <= 0) {
                    continue;
                }
                const nextDepth = Math.max(0, drillState.breadcrumbs.length - 2);
                runtime.trackEvent({
                    kind: 'drill-up',
                    key: 'drillDepth',
                    value: String(nextDepth),
                    source: `action:${component.id}`,
                    meta: action.label || undefined,
                });
                drillState.handleRollUp(nextDepth);
                continue;
            }
            if (actionType === 'jump-url') {
                const template = String(action.jumpUrlTemplate || '').trim();
                const targetUrl = resolveInteractionUrlTemplate(template, actionParams);
                if (!targetUrl) {
                    continue;
                }
                navigateToResolvedUrl(targetUrl, action.jumpOpenMode === 'self' ? 'self' : 'new-tab', `action:${component.id}`);
                continue;
            }
            if (actionType === 'open-panel') {
                const title = resolveActionTemplateText(action.panelTitle || action.label || '详情', actionParams);
                const body = resolveActionTemplateText(action.panelBodyTemplate || '', actionParams);
                runtime.trackEvent({
                    kind: 'panel',
                    key: 'open-panel',
                    value: title,
                    source: `panel:${component.id}`,
                    meta: action.label || undefined,
                });
                runtime.openPanel(title || '详情', body, component.name || component.id);
                continue;
            }
            if (actionType === 'emit-intent') {
                const intentName = String(action.intentName || action.label || 'intent').trim() || 'intent';
                const payload = resolveActionTemplateText(action.intentPayloadTemplate || '', actionParams) || JSON.stringify(mappedValues);
                runtime.trackEvent({
                    kind: 'intent',
                    key: intentName,
                    value: payload,
                    source: `intent:${component.id}`,
                    meta: action.label || undefined,
                });
            }
        }
    }, [
        component.id,
        component.name,
        componentActions,
        drillRuntimeEnabled,
        drillState.breadcrumbs.length,
        drillState.canDrillDown,
        drillState.handleDrill,
        drillState.handleRollUp,
        mode,
        navigateToResolvedUrl,
        runtime,
    ]);

    // ECharts click handler for drill-down + variable interaction
    const echartsClickHandler = useMemo(() => {
        const canDrill = drillActive && drillState.canDrillDown;
        const canInteract = interactionMappings.length > 0;
        const canJump = !!interactionJump;
        const canAction = componentActions.length > 0;
        if (!canDrill && !canInteract && !canJump && !canAction) return undefined;

        return {
            click: (params: Record<string, unknown>) => {
                if (canDrill) {
                    const value = (params.name as string | undefined)
                        ?? ((params.data as Record<string, unknown> | undefined)?.name as string | undefined);
                    if (value) {
                        const clicked = String(value);
                        runtime.trackEvent({
                            kind: 'drill-down',
                            key: 'drillValue',
                            value: clicked,
                            source: `drill:${component.id}`,
                            meta: `depth=${drillState.breadcrumbs.length}`,
                        });
                        drillState.handleDrill(clicked);
                    }
                }

                if (canInteract) {
                    for (const mapping of interactionMappings) {
                        const rawNextValue = resolveInteractionValue(params, mapping.sourcePath);
                        const nextValue = resolveInteractionMappedValue(rawNextValue, mapping);
                        if (nextValue != null) {
                            runtime.setVariable(mapping.variableKey, nextValue, `interaction:${component.id}`);
                        }
                    }
                }

                if (canJump && interactionJump) {
                    const targetUrl = resolveInteractionUrlTemplate(
                        interactionJump.template,
                        buildActionRuntimeParams(runtime.values, params),
                    );
                    if (targetUrl) {
                        navigateToResolvedUrl(targetUrl, interactionJump.openMode, `interaction:${component.id}`);
                    }
                }

                if (canAction) {
                    executeComponentActions(params);
                }
            },
        };
    }, [
        component.id,
        drillActive,
        drillState.breadcrumbs.length,
        drillState.canDrillDown,
        drillState.handleDrill,
        executeComponentActions,
        interactionJump,
        interactionMappings,
        componentActions.length,
        navigateToResolvedUrl,
        runtime,
    ]);

    return {
        scheduleFilterVariableUpdate,
        interactionMappings,
        interactionJump,
        componentActions,
        navigateToResolvedUrl,
        executeComponentActions,
        echartsClickHandler,
        filterVariableTimersRef,
    };
}
