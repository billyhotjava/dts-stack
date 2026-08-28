import { useMemo, useCallback, useRef, useState, useEffect } from 'react';
import { analyticsApi, type ScreenListItem } from '../../../api/analyticsApi';
import type { ScreenComponent, ComponentInteractionMapping, ScreenComponentAction } from '../types';
import type { DrillState } from '../hooks/useDrillDown';
import {
    resolveInteractionValue, resolveInteractionMappedValue,
    resolveInteractionUrlTemplate, normalizeFilterDebounceMs,
} from './shared/chartUtils';
import {
    buildActionRuntimeParams,
    normalizeRuntimeJumpUrl,
    normalizeScreenActionType,
    normalizeDataPointClickPayload,
    resolvePreferredDrillValue,
    resolveActionMappingValues,
    resolveActionTemplateText,
    shouldRunDefaultDrill,
} from './shared/actionUtils';
import type { RuntimeEventKind } from '../ScreenRuntimeContext';
import { resolveRouteForOpen } from '../../../helpers/resolveAnalyticsUrl';

// ---------------------------------------------------------------------------
// Screen-reference URL resolution (moved from ComponentRenderer)
// ---------------------------------------------------------------------------

const SCREEN_REF_PREFIX = 'screen-ref:';
const SCREEN_REF_CACHE_TTL_MS = 30_000;
let screenRefCache: { expiresAt: number; items: ScreenListItem[] } | null = null;

interface ParsedScreenRef {
    /** Database id, when the legacy ref carries an `id=` segment. */
    screenId: string | null;
    /** Legacy display name (only used as a hint, never as the primary key). */
    screenName: string;
    fallbackUrl: string | null;
}

/**
 * Parse a legacy `screen-ref:{name}|{fallback}|id={id}` URL.
 *
 * Object linking should be by stable id, not by name. New data written by
 * the dropdown is a plain `/bi/screens/{id}/preview` URL and bypasses this
 * parser entirely. This function exists only as a read-only compatibility
 * layer for old user data and v2 instance template JSON.
 */
export function parseScreenReferenceUrl(targetUrl: string): ParsedScreenRef | null {
    if (!targetUrl.startsWith(SCREEN_REF_PREFIX)) {
        return null;
    }
    const raw = targetUrl.slice(SCREEN_REF_PREFIX.length);
    const segments = raw.split('|');
    const screenName = decodeURIComponent(segments[0] || '').trim();
    const fallbackRaw = decodeURIComponent(segments[1] || '').trim();
    const fallbackUrl = fallbackRaw || null;
    let screenId: string | null = null;
    for (let i = 2; i < segments.length; i += 1) {
        const seg = decodeURIComponent(segments[i] || '').trim();
        if (seg.startsWith('id=')) {
            const candidate = seg.slice(3).trim();
            screenId = candidate || null;
            break;
        }
    }
    return { screenId, screenName, fallbackUrl };
}

async function getCachedScreenList(): Promise<ScreenListItem[]> {
    const now = Date.now();
    if (screenRefCache && screenRefCache.expiresAt > now) {
        return screenRefCache.items;
    }
    const items = await analyticsApi.listScreens();
    screenRefCache = { expiresAt: now + SCREEN_REF_CACHE_TTL_MS, items };
    return items;
}

export async function resolveScreenReferenceUrl(targetUrl: string): Promise<string> {
    const parsed = parseScreenReferenceUrl(targetUrl);
    if (!parsed) {
        // Not a screen-ref: URL — pass through. Canonical id-based jump URLs
        // (`/bi/screens/{id}/preview`) take this branch and need no resolution.
        return targetUrl;
    }
    if (!parsed.screenId && !parsed.screenName) {
        return parsed.fallbackUrl ?? '';
    }
    try {
        const items = await getCachedScreenList();

        // Primary: lookup by id. This is the only stable identity for an
        // object — names can be edited freely by users.
        if (parsed.screenId) {
            const byId = items.find((item) => String(item.id) === parsed.screenId);
            if (byId?.id != null) {
                return resolveRouteForOpen(`/bi/screens/${encodeURIComponent(String(byId.id))}/preview`);
            }
            // id no longer exists in this environment (screen deleted, or ref
            // came from a different db) → fall through to fallback.
        }

        // Legacy: exact name lookup. Only used for old data without an id
        // segment. New writes never reach this branch. Kept narrow on purpose:
        // exact match only, no fuzzy/normalized matching, because matching
        // objects by user-editable names is fundamentally unreliable.
        if (!parsed.screenId && parsed.screenName) {
            const exact = items
                .filter((item) => String(item.name || '').trim() === parsed.screenName)
                .sort((a, b) => new Date(String(b.updatedAt || 0)).getTime() - new Date(String(a.updatedAt || 0)).getTime())[0];
            if (exact?.id != null) {
                return resolveRouteForOpen(`/bi/screens/${encodeURIComponent(String(exact.id))}/preview`);
            }
        }
    } catch (error) {
        console.error('Failed to resolve screen reference jump target:', error);
    }
    return parsed.fallbackUrl ?? '';
}

// ---------------------------------------------------------------------------
// ScreenRuntime – structural type matching ScreenRuntimeContextValue
// ---------------------------------------------------------------------------

interface ScreenRuntime {
    values: Record<string, string>;
    setVariable: (key: string, value: string, source?: string) => void;
    trackEvent: (event: { kind: RuntimeEventKind; key: string; value: string; source: string; meta?: string }) => void;
    openPanel: (title: string, body: string, source?: string) => void;
    drillView: {
        drillToView: (viewId: string, label: string, params?: Record<string, string>) => void;
    };
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
    drillLoading = false,
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
        const resolved = await resolveScreenReferenceUrl(targetUrl);
        if (!resolved) {
            runtime.trackEvent({
                kind: 'jump',
                key: 'jumpUrl',
                value: '',
                source,
                meta: `cancelled;raw=${targetUrl}`,
            });
            return;
        }
        const resolvedTargetUrl = normalizeRuntimeJumpUrl(
            resolved,
            {
                currentOrigin: window.location.origin,
                resolveAppRoute: resolveRouteForOpen,
            },
        );
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
            if (actionType !== 'drill-view') {
                for (const [key, value] of Object.entries(mappedValues)) {
                    runtime.setVariable(key, value, `action:${component.id}:${actionType}`);
                }
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
                if (!drillRuntimeEnabled || !drillState.canDrillDown || drillLoading) {
                    continue;
                }
                const clickedValue = Object.values(mappedValues)[0] ?? resolvePreferredDrillValue(actionParams) ?? "";
                const accepted = drillState.handleDrill(actionParams);
                runtime.trackEvent({
                    kind: 'drill-down',
                    key: accepted ? 'drillValue' : 'drillCancelled',
                    value: clickedValue,
                    source: `action:${component.id}`,
                    meta: accepted ? (action.label || undefined) : 'missing-mapping-or-duplicate',
                });
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
            if (actionType === 'drill-view') {
                const viewId = String(action.drillViewId ?? '').trim();
                if (!viewId) {
                    continue;
                }
                const label = String(action.drillViewLabel || action.label || viewId).trim() || viewId;
                runtime.drillView.drillToView(viewId, label, mappedValues);
                runtime.trackEvent({
                    kind: 'drill-down',
                    key: 'drillViewId',
                    value: viewId,
                    source: `action:${component.id}`,
                    meta: label,
                });
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
        drillLoading,
        mode,
        navigateToResolvedUrl,
        runtime,
    ]);

    // ECharts click handler for drill-down + variable interaction
    const echartsClickHandler = useMemo(() => {
        // Explicit actions own the click pipeline. This keeps charts aligned
        // with cards and tables and prevents one click from attempting both an
        // implicit drill and an explicit drill-down action.
        const canDrill = shouldRunDefaultDrill({
            drillActive,
            canDrillDown: drillState.canDrillDown,
            loading: drillLoading,
            actionCount: componentActions.length,
        });
        const canInteract = interactionMappings.length > 0;
        const canJump = !!interactionJump;
        const canAction = componentActions.length > 0;
        if (!canDrill && !canInteract && !canJump && !canAction) return undefined;

        return {
            click: (params: Record<string, unknown>) => {
                const clickPayload = normalizeDataPointClickPayload(params);
                if (!clickPayload) {
                    return;
                }
                if (canDrill) {
                    const clicked = resolvePreferredDrillValue(clickPayload) ?? "";
                    const accepted = drillState.handleDrill(clickPayload);
                    runtime.trackEvent({
                        kind: 'drill-down',
                        key: accepted ? 'drillValue' : 'drillCancelled',
                        value: clicked,
                        source: `drill:${component.id}`,
                        meta: accepted
                            ? `depth=${drillState.breadcrumbs.length}`
                            : 'missing-mapping-or-duplicate',
                    });
                }

                if (canInteract) {
                    for (const mapping of interactionMappings) {
                        const rawNextValue = resolveInteractionValue(clickPayload, mapping.sourcePath);
                        const nextValue = resolveInteractionMappedValue(rawNextValue, mapping);
                        if (nextValue != null) {
                            runtime.setVariable(mapping.variableKey, nextValue, `interaction:${component.id}`);
                        }
                    }
                }

                if (canJump && interactionJump) {
                    const targetUrl = resolveInteractionUrlTemplate(
                        interactionJump.template,
                        buildActionRuntimeParams(runtime.values, clickPayload),
                    );
                    if (targetUrl) {
                        navigateToResolvedUrl(targetUrl, interactionJump.openMode, `interaction:${component.id}`);
                    }
                }

                if (canAction) {
                    executeComponentActions(clickPayload);
                }
            },
        };
    }, [
        component.id,
        drillActive,
        drillState.breadcrumbs.length,
        drillState.canDrillDown,
        drillState.handleDrill,
        drillLoading,
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

// ---------------------------------------------------------------------------
// Resolvable jump status — pre-flight check for visual disable
// ---------------------------------------------------------------------------

export function useResolvableJumpStatus(component: ScreenComponent, mode: string): {
    hasResolvableJump: boolean;
    isResolving: boolean;
} {
    const [status, setStatus] = useState<{ hasResolvableJump: boolean; isResolving: boolean }>({
        hasResolvableJump: true,
        isResolving: true,
    });
    useEffect(() => {
        // Outside preview mode, don't pre-resolve — assume clickable
        if (mode !== 'preview') {
            setStatus({ hasResolvableJump: true, isResolving: false });
            return;
        }
        const candidates: string[] = [];
        // Templates containing {{var}} placeholders cannot be pre-resolved (they get
        // substituted at click time with runtime values). Treat their presence as
        // "potentially resolvable" so we don't false-positive into visual disable.
        let hasUnresolvableTemplate = false;
        const considerCandidate = (raw: string) => {
            if (raw.includes('{{')) {
                hasUnresolvableTemplate = true;
                return;
            }
            candidates.push(raw);
        };
        for (const a of component.actions ?? []) {
            if (a?.type === 'jump-url') {
                const tmpl = String(a.jumpUrlTemplate || '').trim();
                if (tmpl) considerCandidate(tmpl);
            }
        }
        if (component.interaction?.enabled === true
            && component.interaction?.jumpEnabled === true) {
            const tmpl = String(component.interaction?.jumpUrlTemplate || '').trim();
            if (tmpl) considerCandidate(tmpl);
        }
        // If any template has runtime placeholders, we can't reliably check it
        // beforehand — leave the component clickable and let click-time logic decide.
        if (hasUnresolvableTemplate) {
            setStatus({ hasResolvableJump: true, isResolving: false });
            return;
        }
        // No jump templates at all → cannot jump
        if (candidates.length === 0) {
            setStatus({ hasResolvableJump: false, isResolving: false });
            return;
        }
        let cancelled = false;
        (async () => {
            for (const tmpl of candidates) {
                const resolved = await resolveScreenReferenceUrl(tmpl).catch(() => '');
                if (cancelled) return;
                if (resolved) {
                    setStatus({ hasResolvableJump: true, isResolving: false });
                    return;
                }
            }
            setStatus({ hasResolvableJump: false, isResolving: false });
        })();
        return () => { cancelled = true; };
    }, [component.actions, component.interaction, mode]);
    return status;
}

export function hasNonJumpInteractivity(component: ScreenComponent): boolean {
    if (component.drillDown?.enabled === true) return true;
    const actions = component.actions ?? [];
    const hasOtherAction = actions.some((a) => {
        const t = a?.type;
        return t === 'drill-down'
            || t === 'drill-up'
            || t === 'drill-view'
            || t === 'open-panel'
            || t === 'set-variable'
            || t === 'emit-intent';
    });
    if (hasOtherAction) return true;
    const interaction = component.interaction;
    if (interaction?.enabled === true) {
        if (Array.isArray(interaction.mappings) && interaction.mappings.length > 0) return true;
    }
    return false;
}
