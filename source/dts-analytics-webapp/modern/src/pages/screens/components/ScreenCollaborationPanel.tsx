import { useEffect, useMemo, useRef, useState, type CSSProperties } from 'react';
import {
    analyticsApi,
    type ScreenCollaborationPresenceRow,
    type ScreenComment,
} from '../../../api/analyticsApi';
import type { ScreenComponent } from '../types';
import { Modal } from '../../../ui/Modal/Modal';

interface ScreenCollaborationPanelProps {
    open: boolean;
    screenId?: string | number;
    components: ScreenComponent[];
    selectedIds?: string[];
    onLocateComponent?: (componentId: string) => void;
    onClose: () => void;
}

export function ScreenCollaborationPanel({
    open,
    screenId,
    components,
    selectedIds,
    onLocateComponent,
    onClose,
}: ScreenCollaborationPanelProps) {
    const eventSourceSupported = typeof window !== 'undefined' && typeof window.EventSource !== 'undefined';
    const webSocketSupported = typeof window !== 'undefined' && typeof window.WebSocket !== 'undefined';
    const [loading, setLoading] = useState(false);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [limit, setLimit] = useState(200);
    const [rows, setRows] = useState<ScreenComment[]>([]);
    const [message, setMessage] = useState('');
    const [componentId, setComponentId] = useState<string>('');
    const [baselineUpdatedAt, setBaselineUpdatedAt] = useState<string>('');
    const [latestUpdatedAt, setLatestUpdatedAt] = useState<string>('');
    const [driftWarning, setDriftWarning] = useState(false);
    const [autoRefresh, setAutoRefresh] = useState(true);
    const [refreshSeconds, setRefreshSeconds] = useState(15);
    const [wsRefresh, setWsRefresh] = useState(webSocketSupported);
    const [wsConnected, setWsConnected] = useState(false);
    const [streamRefresh, setStreamRefresh] = useState(eventSourceSupported);
    const [presenceStreamRefresh, setPresenceStreamRefresh] = useState(eventSourceSupported);
    const [liveRefresh, setLiveRefresh] = useState(true);
    const [commentCursor, setCommentCursor] = useState(0);
    const [presenceRows, setPresenceRows] = useState<ScreenCollaborationPresenceRow[]>([]);
    const [presenceError, setPresenceError] = useState<string | null>(null);
    const baselineUpdatedAtRef = useRef<string>('');
    const commentCursorRef = useRef(0);
    const presenceSessionIdRef = useRef<string>(getOrCreatePresenceSessionId());
    const typingRef = useRef(false);
    const componentIdRef = useRef('');
    const selectedIdsRef = useRef<string[]>([]);
    const wsRef = useRef<WebSocket | null>(null);
    const wsPendingCommentRef = useRef<Map<string, {
        resolve: (row: ScreenComment) => void;
        reject: (error: Error) => void;
        timer: number;
    }>>(new Map());

    const presenceTtlSeconds = useMemo(
        () => Math.max(30, Math.min(Math.floor(refreshSeconds * 3), 180)),
        [refreshSeconds],
    );

    const componentOptions = useMemo(() => {
        return components.map((item) => ({
            id: item.id,
            label: `${item.name || item.type || item.id} (${item.id})`,
        }));
    }, [components]);

    const componentLabelMap = useMemo(() => {
        const out = new Map<string, string>();
        for (const item of components) {
            out.set(item.id, item.name || item.type || item.id);
        }
        return out;
    }, [components]);

    const openCount = useMemo(
        () => rows.filter((item) => (item.status || 'open') !== 'resolved').length,
        [rows],
    );

    const activeCollaborators = useMemo(
        () => presenceRows.filter((item) => !item.mine),
        [presenceRows],
    );

    const typingCollaborators = useMemo(
        () => activeCollaborators.filter((item) => item.typing),
        [activeCollaborators],
    );

    const conflictHotspots = useMemo(() => {
        const map = new Map<string, { componentId: string; users: string[]; typingCount: number; mineSelected: boolean }>();
        const mineSelected = new Set(
            (Array.isArray(selectedIds) ? selectedIds : [])
                .map((item) => String(item || '').trim())
                .filter((item) => item.length > 0),
        );
        for (const row of activeCollaborators) {
            const componentKey = String(row.componentId || '').trim();
            if (!componentKey) continue;
            const name = String(row.displayName || row.userId || '匿名');
            const current = map.get(componentKey);
            if (!current) {
                map.set(componentKey, {
                    componentId: componentKey,
                    users: [name],
                    typingCount: row.typing ? 1 : 0,
                    mineSelected: mineSelected.has(componentKey),
                });
                continue;
            }
            if (!current.users.includes(name)) {
                current.users.push(name);
            }
            if (row.typing) {
                current.typingCount += 1;
            }
            current.mineSelected = current.mineSelected || mineSelected.has(componentKey);
        }
        return Array.from(map.values())
            .filter((item) => item.users.length >= 2 || item.mineSelected)
            .sort((a, b) => {
                if (b.users.length !== a.users.length) return b.users.length - a.users.length;
                return a.componentId.localeCompare(b.componentId);
            });
    }, [activeCollaborators, selectedIds]);

    const refreshDriftHint = async (resetBaseline = false) => {
        if (!screenId) return;
        try {
            const detail = await analyticsApi.getScreen(screenId, { mode: 'draft', fallbackDraft: true });
            const nextUpdatedAt = String(detail.updatedAt || '');
            if (resetBaseline || baselineUpdatedAtRef.current.length === 0) {
                baselineUpdatedAtRef.current = nextUpdatedAt;
                setBaselineUpdatedAt(nextUpdatedAt);
                setLatestUpdatedAt(nextUpdatedAt);
                setDriftWarning(false);
                return;
            }
            setLatestUpdatedAt(nextUpdatedAt);
            setDriftWarning(
                nextUpdatedAt.length > 0
                && baselineUpdatedAtRef.current.length > 0
                && nextUpdatedAt !== baselineUpdatedAtRef.current,
            );
        } catch {
            // Keep comments workflow available even if drift hint API fails.
        }
    };

    const loadRows = async (targetLimit: number) => {
        if (!screenId) return;
        setLoading(true);
        setError(null);
        try {
            const data = await analyticsApi.listScreenComments(screenId, targetLimit);
            const safeRows = Array.isArray(data) ? data : [];
            setRows(safeRows);
            setCommentCursor(computeNextCursor(0, safeRows, 0));
        } catch (e) {
            setRows([]);
            setCommentCursor(0);
            setError(e instanceof Error ? e.message : '加载评论失败');
        } finally {
            setLoading(false);
        }
        await refreshDriftHint(false);
    };

    const loadRowsSilently = async (targetLimit: number) => {
        if (!screenId) return;
        try {
            const delta = await analyticsApi.listScreenCommentChanges(screenId, commentCursor, targetLimit);
            const nextRows = Array.isArray(delta?.rows) ? delta.rows : [];
            if (delta?.fullReload) {
                setRows(nextRows);
            } else if (nextRows.length > 0) {
                setRows((prev) => mergeCommentRows(prev, nextRows));
            }
            setCommentCursor((prev) => computeNextCursor(prev, nextRows, delta?.cursor));
            await refreshDriftHint(false);
        } catch {
            try {
                const data = await analyticsApi.listScreenComments(screenId, targetLimit);
                const safeRows = Array.isArray(data) ? data : [];
                setRows(safeRows);
                setCommentCursor((prev) => computeNextCursor(prev, safeRows, 0));
                await refreshDriftHint(false);
            } catch {
                // Silent polling should not interrupt current interaction.
            }
        }
    };

    const loadPresence = async (silent = true) => {
        if (!screenId) return;
        try {
            const data = await analyticsApi.getScreenCollaborationPresence(
                screenId,
                presenceTtlSeconds,
                presenceSessionIdRef.current,
            );
            const safeRows = Array.isArray(data?.rows) ? data.rows : [];
            setPresenceRows(safeRows);
            if (!silent) {
                setPresenceError(null);
            }
        } catch (e) {
            if (!silent) {
                setPresenceError(e instanceof Error ? e.message : '加载在线协作状态失败');
            }
        }
    };

    const heartbeatPresence = async (silent = true, typing?: boolean) => {
        if (!screenId) return;
        const typingFlag = typing !== undefined ? typing : typingRef.current;
        try {
            const data = await analyticsApi.heartbeatScreenCollaborationPresence(screenId, {
                sessionId: presenceSessionIdRef.current,
                componentId: componentIdRef.current || null,
                typing: typingFlag,
                clientType: 'web',
                selectedIds: selectedIdsRef.current,
            }, presenceTtlSeconds);
            const safeRows = Array.isArray(data?.rows) ? data.rows : [];
            setPresenceRows(safeRows);
            if (!silent) {
                setPresenceError(null);
            }
        } catch (e) {
            if (!silent) {
                setPresenceError(e instanceof Error ? e.message : '更新在线协作状态失败');
            }
        }
    };

    const sendWsPresence = (typing?: boolean) => {
        const socket = wsRef.current;
        if (!socket || socket.readyState !== WebSocket.OPEN) {
            return;
        }
        const typingFlag = typing !== undefined ? typing : typingRef.current;
        try {
            socket.send(JSON.stringify({
                type: 'presence.heartbeat',
                componentId: componentIdRef.current || null,
                cursorId: componentIdRef.current || null,
                typing: typingFlag,
                clientType: 'web',
                selectedIds: selectedIdsRef.current,
            }));
        } catch {
            // keep collaboration path non-blocking
        }
    };

    const locateComponent = (componentId: string) => {
        const target = String(componentId || '').trim();
        if (!target) return;
        setComponentId(target);
        if (typeof onLocateComponent === 'function') {
            onLocateComponent(target);
        }
    };

    const submitCommentViaWebSocket = async (
        text: string,
        targetComponentId: string | null,
    ): Promise<ScreenComment> => {
        const socket = wsRef.current;
        if (!socket || socket.readyState !== WebSocket.OPEN || !screenId) {
            throw new Error('WebSocket unavailable');
        }
        const requestId = `ws-comment-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
        return await new Promise<ScreenComment>((resolve, reject) => {
            const timer = window.setTimeout(() => {
                const pending = wsPendingCommentRef.current.get(requestId);
                if (!pending) return;
                wsPendingCommentRef.current.delete(requestId);
                reject(new Error('WS提交超时'));
            }, 3500);
            wsPendingCommentRef.current.set(requestId, { resolve, reject, timer });
            try {
                socket.send(JSON.stringify({
                    type: 'comment.create',
                    payload: {
                        requestId,
                        message: text,
                        componentId: targetComponentId,
                    },
                }));
            } catch (error) {
                window.clearTimeout(timer);
                wsPendingCommentRef.current.delete(requestId);
                reject(error instanceof Error ? error : new Error('WS发送失败'));
            }
        });
    };

    const submitCommentStatusViaWebSocket = async (
        commentId: string | number,
        action: 'resolve' | 'reopen',
        note?: string,
    ): Promise<ScreenComment> => {
        const socket = wsRef.current;
        if (!socket || socket.readyState !== WebSocket.OPEN || !screenId) {
            throw new Error('WebSocket unavailable');
        }
        const requestId = `ws-comment-${action}-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
        const payload: Record<string, unknown> = {
            requestId,
            commentId,
        };
        if (action === 'resolve' && typeof note === 'string' && note.trim().length > 0) {
            payload.note = note.trim();
        }
        return await new Promise<ScreenComment>((resolve, reject) => {
            const timer = window.setTimeout(() => {
                const pending = wsPendingCommentRef.current.get(requestId);
                if (!pending) return;
                wsPendingCommentRef.current.delete(requestId);
                reject(new Error('WS提交超时'));
            }, 3500);
            wsPendingCommentRef.current.set(requestId, { resolve, reject, timer });
            try {
                socket.send(JSON.stringify({
                    type: action === 'resolve' ? 'comment.resolve' : 'comment.reopen',
                    payload,
                }));
            } catch (error) {
                window.clearTimeout(timer);
                wsPendingCommentRef.current.delete(requestId);
                reject(error instanceof Error ? error : new Error('WS发送失败'));
            }
        });
    };

    useEffect(() => {
        commentCursorRef.current = commentCursor;
    }, [commentCursor]);

    useEffect(() => {
        typingRef.current = message.trim().length > 0;
    }, [message]);

    useEffect(() => {
        componentIdRef.current = componentId;
    }, [componentId]);

    useEffect(() => {
        if (!Array.isArray(selectedIds)) {
            selectedIdsRef.current = [];
            return;
        }
        selectedIdsRef.current = selectedIds
            .map((item) => String(item || '').trim())
            .filter((item) => item.length > 0)
            .slice(0, 20);
    }, [selectedIds]);

    useEffect(() => {
        if (!open || !screenId) return;
        baselineUpdatedAtRef.current = '';
        setBaselineUpdatedAt('');
        setLatestUpdatedAt('');
        setDriftWarning(false);
        setCommentCursor(0);
        setPresenceRows([]);
        setPresenceError(null);
        setWsConnected(false);
        refreshDriftHint(true);
        loadRows(limit);
        loadPresence(true);
        if (!wsRefresh) {
            heartbeatPresence(true, false);
        }
        const selectedId = Array.isArray(selectedIds) && selectedIds.length > 0 ? selectedIds[0] : '';
        setComponentId(selectedId || '');
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [open, screenId, wsRefresh]);

    useEffect(() => {
        if (!open || !screenId || !autoRefresh || !wsRefresh || !webSocketSupported) {
            setWsConnected(false);
            if (wsRef.current) {
                try {
                    wsRef.current.close();
                } catch {
                    // no-op
                }
                wsRef.current = null;
            }
            return;
        }
        let cancelled = false;
        let reconnectTimer = 0;
        let heartbeatTimer = 0;
        const reconnectDelayMs = 1200;

        const clearTimers = () => {
            if (reconnectTimer) {
                window.clearTimeout(reconnectTimer);
                reconnectTimer = 0;
            }
            if (heartbeatTimer) {
                window.clearInterval(heartbeatTimer);
                heartbeatTimer = 0;
            }
        };

        const rejectPendingCommentRequests = (reason: string) => {
            const pending = Array.from(wsPendingCommentRef.current.entries());
            wsPendingCommentRef.current.clear();
            for (const [, item] of pending) {
                window.clearTimeout(item.timer);
                item.reject(new Error(reason));
            }
        };

        const clearSocket = () => {
            const existing = wsRef.current;
            if (existing) {
                try {
                    existing.close();
                } catch {
                    // ignore close failure
                }
                wsRef.current = null;
            }
            rejectPendingCommentRequests('WebSocket disconnected');
        };

        const scheduleReconnect = () => {
            if (cancelled || reconnectTimer) return;
            reconnectTimer = window.setTimeout(() => {
                reconnectTimer = 0;
                connectWebSocket();
            }, reconnectDelayMs);
        };

        const connectWebSocket = () => {
            if (cancelled) return;
            clearSocket();
            const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws';
            const params = new URLSearchParams();
            params.set('sessionId', presenceSessionIdRef.current);
            params.set('clientType', 'web');
            const url = `${protocol}://${window.location.host}/analytics/api/screens/${encodeURIComponent(String(screenId))}/collaboration/ws?${params.toString()}`;
            const socket = new WebSocket(url);
            wsRef.current = socket;
            socket.onopen = () => {
                if (cancelled) return;
                setWsConnected(true);
                sendWsPresence(false);
                const intervalMs = Math.max(3000, Math.min(refreshSeconds * 1000, 12000));
                if (heartbeatTimer) {
                    window.clearInterval(heartbeatTimer);
                }
                heartbeatTimer = window.setInterval(() => {
                    sendWsPresence();
                }, intervalMs);
            };
            socket.onmessage = (event) => {
                const parsed = parseWsEvent(event);
                if (!parsed) return;
                if (parsed.event === 'presence-change') {
                    const nextRows = Array.isArray(parsed.payload?.rows)
                        ? (parsed.payload.rows as ScreenCollaborationPresenceRow[])
                        : [];
                    setPresenceRows(nextRows);
                    setPresenceError(null);
                    return;
                }
                if (parsed.event === 'comment-change') {
                    const next = parsed.payload as ScreenComment | undefined;
                    if (next && next.id !== undefined && next.id !== null) {
                        setRows((prev) => mergeCommentRows(prev, [next]));
                        setCommentCursor((prev) => computeNextCursor(prev, [next], 0));
                    }
                    return;
                }
                if (parsed.event === 'comment-created') {
                    const requestId = String(parsed.payload?.requestId || '').trim();
                    const pending = requestId ? wsPendingCommentRef.current.get(requestId) : undefined;
                    const row = parsed.payload as ScreenComment | undefined;
                    if (pending && row) {
                        window.clearTimeout(pending.timer);
                        wsPendingCommentRef.current.delete(requestId);
                        pending.resolve(row);
                    }
                    return;
                }
                if (parsed.event === 'comment-updated') {
                    const requestId = String(parsed.payload?.requestId || '').trim();
                    const pending = requestId ? wsPendingCommentRef.current.get(requestId) : undefined;
                    const row = parsed.payload as ScreenComment | undefined;
                    if (pending && row) {
                        window.clearTimeout(pending.timer);
                        wsPendingCommentRef.current.delete(requestId);
                        pending.resolve(row);
                    }
                    if (row && row.id !== undefined && row.id !== null) {
                        setRows((prev) => mergeCommentRows(prev, [row]));
                        setCommentCursor((prev) => computeNextCursor(prev, [row], 0));
                    }
                    return;
                }
                if (parsed.event === 'error') {
                    const code = String(parsed.payload?.code || 'ws_error');
                    const requestId = String(parsed.payload?.requestId || '').trim();
                    const pending = requestId ? wsPendingCommentRef.current.get(requestId) : undefined;
                    if (pending) {
                        window.clearTimeout(pending.timer);
                        wsPendingCommentRef.current.delete(requestId);
                        pending.reject(new Error(`WS提交失败: ${code}`));
                    } else {
                        setPresenceError(`在线协作态 WS 异常：${code}`);
                    }
                }
            };
            socket.onerror = () => {
                if (cancelled) return;
                setWsConnected(false);
                scheduleReconnect();
            };
            socket.onclose = () => {
                if (cancelled) return;
                setWsConnected(false);
                scheduleReconnect();
            };
        };

        connectWebSocket();
        return () => {
            cancelled = true;
            setWsConnected(false);
            clearTimers();
            clearSocket();
        };
    }, [autoRefresh, open, refreshSeconds, screenId, webSocketSupported, wsRefresh]);

    useEffect(() => {
        if (!open || !screenId || !autoRefresh || !streamRefresh || saving || loading || !eventSourceSupported || (wsRefresh && wsConnected)) {
            return;
        }
        let cancelled = false;
        let source: EventSource | null = null;
        let reconnectTimer = 0;
        const reconnectDelayMs = 1200;

        const clearSource = () => {
            if (source) {
                source.close();
                source = null;
            }
        };

        const scheduleReconnect = (delayMs: number) => {
            if (cancelled) return;
            if (reconnectTimer) {
                window.clearTimeout(reconnectTimer);
            }
            reconnectTimer = window.setTimeout(() => {
                reconnectTimer = 0;
                connectStream();
            }, delayMs);
        };

        const onDelta = async (delta: { rows?: ScreenComment[]; fullReload?: boolean; cursor?: number } | null) => {
            if (!delta || cancelled) return;
            const nextRows = Array.isArray(delta.rows) ? delta.rows : [];
            if (delta.fullReload) {
                setRows(nextRows);
            } else if (nextRows.length > 0) {
                setRows((prev) => mergeCommentRows(prev, nextRows));
            }
            setCommentCursor((prev) => computeNextCursor(prev, nextRows, delta.cursor));
            await refreshDriftHint(false);
        };

        const connectStream = () => {
            if (cancelled) return;
            clearSource();
            const sinceId = commentCursorRef.current;
            const durationSec = Math.max(20, Math.min(Math.floor(refreshSeconds * 4), 120));
            const waitMs = Math.max(500, Math.min(Math.floor(refreshSeconds * 1000), 5000));
            const url = `/analytics/api/screens/${encodeURIComponent(String(screenId))}/comments/stream`
                + `?sinceId=${encodeURIComponent(String(sinceId))}`
                + `&limit=${encodeURIComponent(String(limit))}`
                + `&durationSec=${encodeURIComponent(String(durationSec))}`
                + `&waitMs=${encodeURIComponent(String(waitMs))}`;
            source = new EventSource(url);
            source.addEventListener('comment-change', (event) => {
                const delta = parseStreamDelta(event);
                void onDelta(delta);
            });
            source.addEventListener('stream-end', () => {
                clearSource();
                scheduleReconnect(100);
            });
            source.onerror = () => {
                clearSource();
                void loadRowsSilently(limit);
                scheduleReconnect(reconnectDelayMs);
            };
        };

        connectStream();
        return () => {
            cancelled = true;
            if (reconnectTimer) {
                window.clearTimeout(reconnectTimer);
            }
            clearSource();
        };
    }, [autoRefresh, eventSourceSupported, limit, loading, open, refreshSeconds, saving, screenId, streamRefresh, wsConnected, wsRefresh]);

    useEffect(() => {
        if (!eventSourceSupported && streamRefresh) {
            setStreamRefresh(false);
        }
        if (!eventSourceSupported && presenceStreamRefresh) {
            setPresenceStreamRefresh(false);
        }
        if (!webSocketSupported && wsRefresh) {
            setWsRefresh(false);
        }
    }, [eventSourceSupported, presenceStreamRefresh, streamRefresh, webSocketSupported, wsRefresh]);

    useEffect(() => {
        if (!open || !screenId || !autoRefresh || streamRefresh || !liveRefresh || saving || loading || (wsRefresh && wsConnected)) {
            return;
        }
        const seconds = Number.isFinite(refreshSeconds) ? Math.max(5, Math.min(120, Math.floor(refreshSeconds))) : 15;
        const timer = window.setInterval(() => {
            void loadRowsSilently(limit);
        }, seconds * 1000);
        return () => window.clearInterval(timer);
    }, [autoRefresh, limit, liveRefresh, loading, open, refreshSeconds, saving, screenId, streamRefresh, wsConnected, wsRefresh]);

    useEffect(() => {
        if (!open || !screenId || !autoRefresh || streamRefresh || !liveRefresh || saving || loading || (wsRefresh && wsConnected)) {
            return;
        }
        let cancelled = false;
        const loop = async () => {
            while (!cancelled) {
                try {
                    const waitMs = Math.max(1000, Math.min(Math.floor(refreshSeconds * 1000), 30000));
                    const sinceId = commentCursorRef.current;
                    const delta = await analyticsApi.listScreenCommentChangesLive(screenId, sinceId, limit, waitMs);
                    if (cancelled) return;
                    const nextRows = Array.isArray(delta?.rows) ? delta.rows : [];
                    if (delta?.fullReload) {
                        setRows(nextRows);
                    } else if (nextRows.length > 0) {
                        setRows((prev) => mergeCommentRows(prev, nextRows));
                    }
                    setCommentCursor((prev) => computeNextCursor(prev, nextRows, delta?.cursor));
                    await refreshDriftHint(false);
                } catch {
                    if (cancelled) return;
                    await loadRowsSilently(limit);
                }
            }
        };
        void loop();
        return () => {
            cancelled = true;
        };
    }, [autoRefresh, limit, liveRefresh, loading, open, refreshSeconds, saving, screenId, streamRefresh, wsConnected, wsRefresh]);

    useEffect(() => {
        if (!open || !screenId || wsRefresh) {
            return;
        }
        const intervalMs = Math.max(5000, Math.min(refreshSeconds * 1000, 15000));
        void heartbeatPresence(true);
        const timer = window.setInterval(() => {
            void heartbeatPresence(true);
        }, intervalMs);
        return () => window.clearInterval(timer);
    }, [open, refreshSeconds, screenId, presenceTtlSeconds, wsRefresh]);

    useEffect(() => {
        if (!open || !screenId || !autoRefresh || !presenceStreamRefresh || !eventSourceSupported || wsRefresh) {
            return;
        }
        let cancelled = false;
        let source: EventSource | null = null;
        let reconnectTimer = 0;
        const reconnectDelayMs = 1200;

        const clearSource = () => {
            if (source) {
                source.close();
                source = null;
            }
        };

        const scheduleReconnect = (delayMs: number) => {
            if (cancelled) return;
            if (reconnectTimer) {
                window.clearTimeout(reconnectTimer);
            }
            reconnectTimer = window.setTimeout(() => {
                reconnectTimer = 0;
                connectStream();
            }, delayMs);
        };

        const connectStream = () => {
            if (cancelled) return;
            clearSource();
            const durationSec = Math.max(20, Math.min(Math.floor(refreshSeconds * 4), 120));
            const waitMs = Math.max(500, Math.min(Math.floor(refreshSeconds * 1000), 5000));
            const url = `/analytics/api/screens/${encodeURIComponent(String(screenId))}/collaboration/presence/stream`
                + `?sessionId=${encodeURIComponent(presenceSessionIdRef.current)}`
                + `&ttlSeconds=${encodeURIComponent(String(presenceTtlSeconds))}`
                + `&durationSec=${encodeURIComponent(String(durationSec))}`
                + `&waitMs=${encodeURIComponent(String(waitMs))}`;
            source = new EventSource(url);
            source.addEventListener('presence-change', (event) => {
                const payload = parsePresenceStream(event);
                if (!payload || cancelled) return;
                const nextRows = Array.isArray(payload.rows) ? payload.rows : [];
                setPresenceRows(nextRows);
                setPresenceError(null);
            });
            source.addEventListener('stream-end', () => {
                clearSource();
                scheduleReconnect(100);
            });
            source.onerror = () => {
                clearSource();
                void analyticsApi.getScreenCollaborationPresence(
                    screenId,
                    presenceTtlSeconds,
                    presenceSessionIdRef.current,
                ).then((snapshot) => {
                    if (cancelled) return;
                    const safeRows = Array.isArray(snapshot?.rows) ? snapshot.rows : [];
                    setPresenceRows(safeRows);
                }).catch(() => {
                    // Ignore fallback errors and keep reconnecting.
                });
                scheduleReconnect(reconnectDelayMs);
            };
        };

        connectStream();
        return () => {
            cancelled = true;
            if (reconnectTimer) {
                window.clearTimeout(reconnectTimer);
            }
            clearSource();
        };
    }, [
        autoRefresh,
        eventSourceSupported,
        open,
        presenceStreamRefresh,
        presenceTtlSeconds,
        refreshSeconds,
        screenId,
        wsRefresh,
    ]);

    useEffect(() => {
        if (!open || !screenId) {
            return;
        }
        const timer = window.setTimeout(() => {
            if (wsRefresh) {
                sendWsPresence();
                return;
            }
            void heartbeatPresence(true);
        }, 450);
        return () => window.clearTimeout(timer);
    }, [componentId, message, open, screenId, presenceTtlSeconds, wsRefresh]);

    useEffect(() => {
        if (open || !screenId) {
            return;
        }
        void analyticsApi.leaveScreenCollaborationPresence(screenId, {
            sessionId: presenceSessionIdRef.current,
        }, presenceTtlSeconds);
    }, [open, screenId, presenceTtlSeconds]);

    useEffect(() => {
        if (!open || !screenId) {
            return;
        }
        return () => {
            void analyticsApi.leaveScreenCollaborationPresence(screenId, {
                sessionId: presenceSessionIdRef.current,
            }, presenceTtlSeconds);
        };
    }, [open, screenId, presenceTtlSeconds]);

    return (
        <Modal isOpen={open} onClose={onClose} title="协作批注中心" size="xl">
            <div style={{ fontSize: 12, opacity: 0.8, marginBottom: 10 }}>
                轻协作模式：支持对大屏或指定组件添加评论，按状态跟踪“待处理/已解决”。
            </div>
            {driftWarning && (
                <div style={{
                    border: '1px solid #f59e0b',
                    background: 'rgba(245,158,11,0.12)',
                    color: '#f59e0b',
                    borderRadius: 8,
                    padding: 10,
                    marginBottom: 10,
                    fontSize: 12,
                    lineHeight: 1.5,
                }}>
                    检测到草稿版本已变化，可能存在多人并行修改。<br />
                    基线时间: {baselineUpdatedAt || '-'}<br />
                    当前时间: {latestUpdatedAt || '-'}
                </div>
            )}

            <div style={{ border: '1px solid var(--color-border)', borderRadius: 8, padding: 10, marginBottom: 12 }}>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 8, marginBottom: 8 }}>
                    <div style={{ fontWeight: 600 }}>在线协作态</div>
                    <button
                        type="button"
                        className="header-btn"
                        disabled={!screenId}
                        onClick={() => {
                            void loadPresence(false);
                        }}
                    >
                        刷新在线状态
                    </button>
                </div>
                <div style={{ fontSize: 12, opacity: 0.85, lineHeight: 1.6 }}>
                    在线人数: {presenceRows.length}，其他协作者: {activeCollaborators.length}
                    {typingCollaborators.length > 0 && (
                        <span>
                            {' '}| 输入中: {typingCollaborators.map((item) => {
                                const name = String(item.displayName || item.userId || '匿名');
                                if (!item.componentId) return name;
                                const target = componentLabelMap.get(String(item.componentId)) || String(item.componentId);
                                return `${name} @ ${target}`;
                            }).join('、')}
                        </span>
                    )}
                </div>
                {presenceError && (
                    <div style={{ marginTop: 8, fontSize: 12, color: '#ef4444' }}>
                        {presenceError}
                    </div>
                )}
                <div style={{ marginTop: 8, display: 'flex', flexWrap: 'wrap', gap: 6 }}>
                    {presenceRows.map((item) => {
                        const mine = !!item.mine;
                        const name = String(item.displayName || item.userId || '匿名');
                        const idle = Number.isFinite(item.idleSeconds as number) ? Number(item.idleSeconds) : 0;
                        const selectedCount = Number.isFinite(item.selectedCount as number)
                            ? Math.max(0, Number(item.selectedCount))
                            : 0;
                        const target = item.componentId
                            ? (componentLabelMap.get(String(item.componentId)) || String(item.componentId))
                            : '全屏';
                        const selectionText = selectedCount > 0
                            ? `选中${selectedCount}`
                            : (item.selectionPreview ? `选中:${String(item.selectionPreview)}` : '无选中');
                        const clickable = !mine && !!item.componentId;
                        return (
                            <span
                                key={String(item.sessionId || `${name}-${target}`)}
                                style={{
                                    display: 'inline-flex',
                                    alignItems: 'center',
                                    gap: 4,
                                    border: '1px solid var(--color-border)',
                                    borderRadius: 999,
                                    padding: '2px 8px',
                                    fontSize: 11,
                                    background: mine ? 'rgba(59,130,246,0.14)' : 'rgba(148,163,184,0.1)',
                                    color: mine ? '#60a5fa' : 'inherit',
                                }}
                            >
                                {name}{mine ? '(我)' : ''}{item.typing ? ' 输入中' : ''} · {target} · {selectionText} · {idle}s
                                {clickable && (
                                    <button
                                        type="button"
                                        className="header-btn"
                                        style={{ padding: '2px 6px', fontSize: 10 }}
                                        onClick={() => locateComponent(String(item.componentId))}
                                        title="定位到该协作者当前组件"
                                    >
                                        定位
                                    </button>
                                )}
                            </span>
                        );
                    })}
                    {presenceRows.length === 0 && (
                        <span style={{ fontSize: 12, opacity: 0.7 }}>暂无在线协作者</span>
                    )}
                </div>
                {conflictHotspots.length > 0 && (
                    <div style={{ marginTop: 10, borderTop: '1px dashed var(--color-border)', paddingTop: 8 }}>
                        <div style={{ fontSize: 12, fontWeight: 600, marginBottom: 6 }}>冲突热点</div>
                        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
                            {conflictHotspots.map((item) => {
                                const label = componentLabelMap.get(item.componentId) || item.componentId;
                                const typingTag = item.typingCount > 0 ? `，${item.typingCount}人输入中` : '';
                                const mineTag = item.mineSelected ? '（含我当前选中）' : '';
                                return (
                                    <button
                                        key={item.componentId}
                                        type="button"
                                        className="header-btn"
                                        style={{ padding: '4px 8px', fontSize: 11 }}
                                        title={`${label}: ${item.users.join('、')}${typingTag}${mineTag}`}
                                        onClick={() => locateComponent(item.componentId)}
                                    >
                                        {label} · {item.users.length}人{mineTag ? ' · 含我' : ''}
                                    </button>
                                );
                            })}
                        </div>
                    </div>
                )}
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, minmax(120px, 1fr))', gap: 8, marginBottom: 12 }}>
                <MetricCell title="评论总数" value={String(rows.length)} />
                <MetricCell title="待处理" value={String(openCount)} />
                <MetricCell title="已解决" value={String(rows.length - openCount)} />
            </div>

            <div style={{ border: '1px solid var(--color-border)', borderRadius: 8, padding: 10, marginBottom: 12 }}>
                <div style={{ fontWeight: 600, marginBottom: 8 }}>新增评论</div>
                <div style={{ display: 'grid', gridTemplateColumns: '240px 1fr auto', gap: 8, alignItems: 'start' }}>
                    <select
                        className="property-input"
                        value={componentId}
                        onChange={(e) => setComponentId(e.target.value)}
                    >
                        <option value="">-- 整个大屏 --</option>
                        {componentOptions.map((item) => (
                            <option key={item.id} value={item.id}>{item.label}</option>
                        ))}
                    </select>

                    <textarea
                        className="property-input"
                        value={message}
                        onChange={(e) => setMessage(e.target.value)}
                        onBlur={() => {
                            if (wsRefresh) {
                                sendWsPresence(false);
                            } else {
                                void heartbeatPresence(true, false);
                            }
                        }}
                        placeholder="输入批注内容，例如：该组件口径需与生产日报一致，建议补充单位和更新时间。"
                        rows={3}
                        style={{ resize: 'vertical', minHeight: 72 }}
                    />

                    <button
                        type="button"
                        className="header-btn"
                        disabled={saving || !screenId || message.trim().length === 0}
                        onClick={async () => {
                            if (!screenId) return;
                            const text = message.trim();
                            if (!text) return;
                            const targetComponentId = componentId || null;
                            setSaving(true);
                            setError(null);
                            try {
                                let created: ScreenComment;
                                if (wsRefresh && wsConnected) {
                                    try {
                                        created = await submitCommentViaWebSocket(text, targetComponentId);
                                    } catch {
                                        created = await analyticsApi.createScreenComment(screenId, {
                                            message: text,
                                            componentId: targetComponentId,
                                        });
                                    }
                                } else {
                                    created = await analyticsApi.createScreenComment(screenId, {
                                        message: text,
                                        componentId: targetComponentId,
                                    });
                                }
                                setRows((prev) => [created, ...prev]);
                                setCommentCursor((prev) => computeNextCursor(prev, [created], 0));
                                setMessage('');
                                await refreshDriftHint(false);
                            } catch (e) {
                                setError(e instanceof Error ? e.message : '创建评论失败');
                            } finally {
                                setSaving(false);
                            }
                        }}
                    >
                        {saving ? '提交中...' : '添加评论'}
                    </button>
                </div>
            </div>

            <div style={{ display: 'flex', gap: 8, marginBottom: 10, alignItems: 'center' }}>
                <span style={{ fontSize: 12, opacity: 0.8 }}>最大行数</span>
                <input
                    type="number"
                    min={1}
                    max={500}
                    className="property-input"
                    value={limit}
                    onChange={(e) => {
                        const n = Number(e.target.value);
                        setLimit(Number.isFinite(n) ? Math.max(1, Math.min(500, n)) : 200);
                    }}
                    style={{ width: 140 }}
                />
                <button type="button" className="header-btn" disabled={!screenId || loading} onClick={() => loadRows(limit)}>
                    {loading ? '刷新中...' : '刷新'}
                </button>
                <label style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 12, opacity: 0.9 }}>
                    <input
                        type="checkbox"
                        checked={autoRefresh}
                        onChange={(e) => setAutoRefresh(e.target.checked)}
                    />
                    自动刷新
                </label>
                <label style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 12, opacity: 0.9 }}>
                    <input
                        type="checkbox"
                        checked={wsRefresh}
                        onChange={(e) => setWsRefresh(e.target.checked)}
                        disabled={!autoRefresh || !webSocketSupported}
                    />
                    WebSocket协作态
                    {wsRefresh ? (wsConnected ? '(已连接)' : '(重连中)') : ''}
                </label>
                <label style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 12, opacity: 0.9 }}>
                    <input
                        type="checkbox"
                        checked={streamRefresh}
                        onChange={(e) => setStreamRefresh(e.target.checked)}
                        disabled={!autoRefresh || !eventSourceSupported || wsRefresh}
                    />
                    SSE实时流
                </label>
                <label style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 12, opacity: 0.9 }}>
                    <input
                        type="checkbox"
                        checked={presenceStreamRefresh}
                        onChange={(e) => setPresenceStreamRefresh(e.target.checked)}
                        disabled={!autoRefresh || !eventSourceSupported || wsRefresh}
                    />
                    在线态SSE
                </label>
                <label style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 12, opacity: 0.9 }}>
                    <input
                        type="checkbox"
                        checked={liveRefresh}
                        onChange={(e) => setLiveRefresh(e.target.checked)}
                        disabled={!autoRefresh || streamRefresh || wsRefresh}
                    />
                    实时长轮询
                </label>
                <input
                    type="number"
                    min={5}
                    max={120}
                    className="property-input"
                    value={refreshSeconds}
                    onChange={(e) => {
                        const n = Number(e.target.value);
                        setRefreshSeconds(Number.isFinite(n) ? Math.max(5, Math.min(120, n)) : 15);
                    }}
                    style={{ width: 100 }}
                    title={streamRefresh ? 'SSE心跳窗口(秒)' : (liveRefresh ? '长轮询窗口(秒)' : '自动刷新间隔(秒)')}
                    disabled={!autoRefresh}
                />
                {!eventSourceSupported && (
                    <span style={{ fontSize: 12, opacity: 0.7 }}>当前浏览器不支持 SSE，已降级为轮询。</span>
                )}
                {!webSocketSupported && (
                    <span style={{ fontSize: 12, opacity: 0.7 }}>当前浏览器不支持 WebSocket 协作态。</span>
                )}
            </div>

            {error && (
                <div style={{
                    border: '1px solid #ef4444',
                    background: 'rgba(239,68,68,0.08)',
                    color: '#ef4444',
                    borderRadius: 8,
                    padding: 10,
                    marginBottom: 10,
                    fontSize: 12,
                    whiteSpace: 'pre-wrap',
                }}>
                    {error}
                </div>
            )}

            <div style={{
                border: '1px solid var(--color-border)',
                borderRadius: 8,
                maxHeight: 360,
                overflow: 'auto',
            }}>
                <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 12 }}>
                    <thead>
                        <tr style={{ position: 'sticky', top: 0, background: 'var(--color-surface)' }}>
                            <th style={thStyle}>状态</th>
                            <th style={thStyle}>评论</th>
                            <th style={thStyle}>组件</th>
                            <th style={thStyle}>创建时间</th>
                            <th style={thStyle}>创建人</th>
                            <th style={thStyle}>操作</th>
                        </tr>
                    </thead>
                    <tbody>
                        {rows.map((row) => {
                            const resolved = (row.status || 'open') === 'resolved';
                            const targetId = row.componentId || '';
                            const targetName = targetId ? (componentLabelMap.get(targetId) || targetId) : '全屏';
                            return (
                                <tr key={String(row.id)}>
                                    <td style={tdStyle}>
                                        <StatusBadge resolved={resolved} />
                                    </td>
                                    <td style={tdStyle}>{String(row.message || '-')}</td>
                                    <td style={tdStyle}>{targetName}</td>
                                    <td style={tdStyle}>{String(row.createdAt || '-')}</td>
                                    <td style={tdStyle}>{String(row.createdBy ?? '-')}</td>
                                    <td style={tdStyle}>
                                        {resolved ? (
                                            <button
                                                type="button"
                                                className="header-btn"
                                                onClick={async () => {
                                                    if (!screenId) return;
                                                    setError(null);
                                                    try {
                                                        let updated: ScreenComment;
                                                        if (wsRefresh && wsConnected) {
                                                            try {
                                                                updated = await submitCommentStatusViaWebSocket(row.id, 'reopen');
                                                            } catch {
                                                                updated = await analyticsApi.reopenScreenComment(screenId, row.id);
                                                            }
                                                        } else {
                                                            updated = await analyticsApi.reopenScreenComment(screenId, row.id);
                                                        }
                                                        setRows((prev) => prev.map((item) => item.id === row.id ? updated : item));
                                                        setCommentCursor((prev) => computeNextCursor(prev, [updated], 0));
                                                        await refreshDriftHint(false);
                                                    } catch (e) {
                                                        setError(e instanceof Error ? e.message : '重新打开失败');
                                                    }
                                                }}
                                            >
                                                重新打开
                                            </button>
                                        ) : (
                                            <button
                                                type="button"
                                                className="header-btn"
                                                onClick={async () => {
                                                    if (!screenId) return;
                                                    const note = (window.prompt('处理备注（可选）', '') || '').trim();
                                                    setError(null);
                                                    try {
                                                        let updated: ScreenComment;
                                                        if (wsRefresh && wsConnected) {
                                                            try {
                                                                updated = await submitCommentStatusViaWebSocket(row.id, 'resolve', note);
                                                            } catch {
                                                                updated = await analyticsApi.resolveScreenComment(screenId, row.id, { note });
                                                            }
                                                        } else {
                                                            updated = await analyticsApi.resolveScreenComment(screenId, row.id, { note });
                                                        }
                                                        setRows((prev) => prev.map((item) => item.id === row.id ? updated : item));
                                                        setCommentCursor((prev) => computeNextCursor(prev, [updated], 0));
                                                        await refreshDriftHint(false);
                                                    } catch (e) {
                                                        setError(e instanceof Error ? e.message : '标记已解决失败');
                                                    }
                                                }}
                                            >
                                                标记已解决
                                            </button>
                                        )}
                                    </td>
                                </tr>
                            );
                        })}
                        {rows.length === 0 && !loading && (
                            <tr>
                                <td style={tdStyle} colSpan={6}>暂无评论</td>
                            </tr>
                        )}
                    </tbody>
                </table>
            </div>
        </Modal>
    );
}

function parseCommentId(value: unknown): number {
    const text = String(value ?? '').trim();
    if (!text) return 0;
    const num = Number(text);
    return Number.isFinite(num) && num > 0 ? num : 0;
}

const PRESENCE_SESSION_STORAGE_KEY = 'dts.analytics.collaboration.presence.sessionId';

function getOrCreatePresenceSessionId(): string {
    if (typeof window === 'undefined') {
        return createPresenceSessionId();
    }
    try {
        const cached = window.sessionStorage.getItem(PRESENCE_SESSION_STORAGE_KEY);
        if (cached && cached.trim()) {
            return cached.trim();
        }
    } catch {
        // ignore read failures and fall back to fresh session id
    }
    const next = createPresenceSessionId();
    try {
        window.sessionStorage.setItem(PRESENCE_SESSION_STORAGE_KEY, next);
    } catch {
        // ignore write failures and keep in-memory session id only
    }
    return next;
}

function createPresenceSessionId(): string {
    const prefix = `collab-${Date.now().toString(36)}`;
    if (typeof window === 'undefined') {
        return `${prefix}-srv`;
    }
    try {
        if (window.crypto && typeof window.crypto.getRandomValues === 'function') {
            const bytes = new Uint8Array(6);
            window.crypto.getRandomValues(bytes);
            const suffix = Array.from(bytes)
                .map((item) => item.toString(16).padStart(2, '0'))
                .join('');
            return `${prefix}-${suffix}`;
        }
    } catch {
        // Fall back to Math.random if crypto API is unavailable.
    }
    const fallback = Math.floor(Math.random() * 1e12).toString(36);
    return `${prefix}-${fallback}`;
}

function parseStreamDelta(event: Event): { rows?: ScreenComment[]; fullReload?: boolean; cursor?: number } | null {
    const message = event as MessageEvent<string>;
    const raw = typeof message.data === 'string' ? message.data.trim() : '';
    if (!raw) return null;
    try {
        const parsed = JSON.parse(raw) as { rows?: ScreenComment[]; fullReload?: boolean; cursor?: number };
        return parsed;
    } catch {
        return null;
    }
}

function parsePresenceStream(event: Event): { rows?: ScreenCollaborationPresenceRow[] } | null {
    const message = event as MessageEvent<string>;
    const raw = typeof message.data === 'string' ? message.data.trim() : '';
    if (!raw) return null;
    try {
        const parsed = JSON.parse(raw) as { rows?: ScreenCollaborationPresenceRow[] };
        return parsed;
    } catch {
        return null;
    }
}

function parseWsEvent(event: MessageEvent<string>): { event?: string; payload?: Record<string, unknown> } | null {
    const raw = typeof event.data === 'string' ? event.data.trim() : '';
    if (!raw) return null;
    try {
        const parsed = JSON.parse(raw) as { event?: string; payload?: Record<string, unknown> };
        return parsed;
    } catch {
        return null;
    }
}

function computeNextCursor(prev: number, rows: ScreenComment[], cursor?: number): number {
    let next = Number.isFinite(prev) ? prev : 0;
    if (Number.isFinite(cursor as number)) {
        next = Math.max(next, Number(cursor));
    }
    for (const row of rows || []) {
        next = Math.max(next, parseCommentId(row?.id));
    }
    return next;
}

function mergeCommentRows(current: ScreenComment[], delta: ScreenComment[]): ScreenComment[] {
    if (!Array.isArray(current) || current.length === 0) {
        return Array.isArray(delta) ? [...delta] : [];
    }
    if (!Array.isArray(delta) || delta.length === 0) {
        return [...current];
    }
    const map = new Map<string, ScreenComment>();
    for (const item of current) {
        map.set(String(item.id), item);
    }
    for (const item of delta) {
        map.set(String(item.id), item);
    }
    return Array.from(map.values()).sort((a, b) => {
        const ta = Date.parse(String(a.createdAt || ''));
        const tb = Date.parse(String(b.createdAt || ''));
        if (Number.isFinite(ta) && Number.isFinite(tb) && ta !== tb) {
            return tb - ta;
        }
        return parseCommentId(b.id) - parseCommentId(a.id);
    });
}

function StatusBadge({ resolved }: { resolved: boolean }) {
    return (
        <span style={{
            display: 'inline-flex',
            alignItems: 'center',
            justifyContent: 'center',
            minWidth: 56,
            padding: '2px 8px',
            borderRadius: 999,
            border: `1px solid ${resolved ? '#16a34a' : '#f59e0b'}`,
            background: resolved ? 'rgba(22,163,74,0.1)' : 'rgba(245,158,11,0.12)',
            color: resolved ? '#16a34a' : '#f59e0b',
            fontSize: 11,
        }}>
            {resolved ? '已解决' : '待处理'}
        </span>
    );
}

const thStyle: CSSProperties = {
    textAlign: 'left',
    padding: '8px 10px',
    borderBottom: '1px solid var(--color-border)',
    fontWeight: 600,
};

const tdStyle: CSSProperties = {
    textAlign: 'left',
    padding: '8px 10px',
    borderBottom: '1px solid var(--color-border)',
    verticalAlign: 'top',
};

function MetricCell({ title, value }: { title: string; value: string }) {
    return (
        <div style={{
            border: '1px solid var(--color-border)',
            borderRadius: 8,
            padding: 10,
            background: 'rgba(255,255,255,0.02)',
        }}>
            <div style={{ fontSize: 12, opacity: 0.75, marginBottom: 6 }}>{title}</div>
            <div style={{ fontSize: 16, fontWeight: 600 }}>{value}</div>
        </div>
    );
}
