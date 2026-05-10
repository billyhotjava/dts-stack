import { message } from 'antd';
import type {
    CardParameterBinding,
    DataSourceConfig,
    ScreenComponent,
    ScreenGlobalVariable,
} from '../../types';
import { CardIdPicker } from '../CardIdPicker';
import { CardParamBindingsEditor } from '../CardParamBindingsEditor';
import { DatabaseIdPicker } from '../DatabaseIdPicker';
import { MetricBindingEditor } from '../MetricBindingEditor';
import {
    extractSqlTemplateParameterNames,
    resolveDataSourceType,
    resolveSqlConfig,
    safeJsonParse,
    safeJsonStringify,
} from './helpers';
import { StaticDataEditor } from './StaticDataEditor';

export function renderDataSourceConfig(
    component: ScreenComponent,
    updateComponent: (id: string, updates: Partial<ScreenComponent>) => void,
    globalVariables: ScreenGlobalVariable[],
) {
    const ds = component.dataSource;
    const dsType = resolveDataSourceType(ds);
    const sqlConfig = resolveSqlConfig(ds);

    const cardBindings: CardParameterBinding[] = ds?.type === 'card' ? (ds.cardConfig?.parameterBindings ?? []) : [];
    const metricBindings: CardParameterBinding[] = dsType === 'metric' ? (ds?.metricConfig?.parameterBindings ?? []) : [];
    const sqlBindings: CardParameterBinding[] = dsType === 'sql' ? (sqlConfig?.parameterBindings ?? []) : [];
    const updateCardBindings = (bindings: CardParameterBinding[]) => {
        setDataSource({
            type: 'card',
            sourceType: 'card',
            cardConfig: {
                ...(ds?.type === 'card' ? ds.cardConfig : {}),
                cardId: ds?.type === 'card' ? (ds.cardConfig?.cardId ?? 0) : 0,
                parameterBindings: bindings,
            },
        });
    };

    const updateSqlBindings = (bindings: CardParameterBinding[]) => {
        const base = resolveSqlConfig(ds);
        setDataSource({
            type: 'sql',
            sourceType: 'sql',
            refreshInterval: dsType === 'sql' ? ds?.refreshInterval : undefined,
            sqlConfig: {
                ...(base ?? { query: '' }),
                query: base?.query ?? '',
                databaseId: base?.databaseId,
                connectionId: base?.connectionId,
                queryTimeoutSeconds: base?.queryTimeoutSeconds,
                maxRows: base?.maxRows,
                parameterBindings: bindings,
            },
        });
    };

    const updateMetricBindings = (bindings: CardParameterBinding[]) => {
        const currentMetricConfig = dsType === 'metric' ? ds?.metricConfig : undefined;
        setDataSource({
            type: 'metric',
            sourceType: 'metric',
            refreshInterval: dsType === 'metric' ? ds?.refreshInterval : undefined,
            metricConfig: {
                ...(currentMetricConfig ?? {}),
                cardId: currentMetricConfig?.cardId ?? 0,
                parameterBindings: bindings,
            },
        });
    };

    const setDataSource = (newDs: DataSourceConfig | undefined) => {
        updateComponent(component.id, { dataSource: newDs });
    };

    const setType = (nextType: string) => {
        if (nextType === 'static') {
            setDataSource(undefined);
            return;
        }
        if (nextType === 'card') {
            setDataSource({
                type: 'card',
                sourceType: 'card',
                cardConfig: {
                    cardId: ds?.type === 'card' ? (ds.cardConfig?.cardId ?? 0) : 0,
                    refreshInterval: ds?.type === 'card' ? ds.cardConfig?.refreshInterval : undefined,
                    metricId: ds?.type === 'card' ? ds.cardConfig?.metricId : undefined,
                    metricVersion: ds?.type === 'card' ? ds.cardConfig?.metricVersion : undefined,
                    parameterBindings: ds?.type === 'card' ? (ds.cardConfig?.parameterBindings ?? []) : [],
                },
            });
            return;
        }
        if (nextType === 'api') {
            setDataSource({
                type: 'api',
                sourceType: 'api',
                refreshInterval: ds?.type === 'api' ? ds.refreshInterval : undefined,
                apiConfig: {
                    url: ds?.type === 'api' ? (ds.apiConfig?.url ?? '') : '',
                    method: ds?.type === 'api' ? (ds.apiConfig?.method ?? 'GET') : 'GET',
                    body: ds?.type === 'api' ? ds.apiConfig?.body : undefined,
                },
            });
            return;
        }
        if (nextType === 'sql') {
            const base = resolveSqlConfig(ds);
            setDataSource({
                type: 'sql',
                sourceType: 'sql',
                refreshInterval: dsType === 'sql' ? ds?.refreshInterval : undefined,
                sqlConfig: {
                    databaseId: base?.databaseId,
                    connectionId: base?.connectionId,
                    query: base?.query ?? 'select 1',
                    queryTimeoutSeconds: base?.queryTimeoutSeconds,
                    maxRows: base?.maxRows,
                    parameterBindings: base?.parameterBindings ?? [],
                },
            });
            return;
        }
        if (nextType === 'dataset') {
            setDataSource({
                type: 'dataset',
                sourceType: 'dataset',
                refreshInterval: dsType === 'dataset' ? ds?.refreshInterval : undefined,
                datasetConfig: dsType === 'dataset'
                    ? ds?.datasetConfig
                    : { queryBody: { database: 0, type: 'query', query: {} } },
            });
            return;
        }
        if (nextType === 'metric') {
            setDataSource({
                type: 'metric',
                sourceType: 'metric',
                refreshInterval: dsType === 'metric' ? ds?.refreshInterval : undefined,
                metricConfig: {
                    cardId: dsType === 'metric' ? (ds?.metricConfig?.cardId ?? 0) : 0,
                    metricId: dsType === 'metric' ? ds?.metricConfig?.metricId : undefined,
                    metricVersion: dsType === 'metric' ? ds?.metricConfig?.metricVersion : undefined,
                    parameterBindings: dsType === 'metric' ? (ds?.metricConfig?.parameterBindings ?? []) : [],
                },
            });
        }
    };

    return (
        <>
            <div className="property-row flex items-center mb-3">
                <label className="property-label w-20 text-xs text-text-secondary">类型</label>
                <select
                    className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                    value={dsType}
                    onChange={(e) => setType(e.target.value)}
                >
                    <option value="static">静态数据</option>
                    <option value="card">Card 查询</option>
                    <option value="api">HTTP API</option>
                    <option value="sql">SQL 模式</option>
                    <option value="dataset">Dataset 模式</option>
                    <option value="metric">Metric 语义模式</option>
                </select>
            </div>

            {dsType === 'static' && (
                <StaticDataEditor key={component.id} component={component} updateComponent={updateComponent} />
            )}

            {dsType === 'card' && (
                <>
                    <div className="property-row flex items-center mb-3">
                        <label className="property-label w-20 text-xs text-text-secondary">Card</label>
                        <CardIdPicker
                            value={ds?.type === 'card' ? (ds.cardConfig?.cardId ?? 0) : 0}
                            onChange={(cardId) => {
                                setDataSource({
                                    type: 'card',
                                    sourceType: 'card',
                                    cardConfig: {
                                        ...(ds?.type === 'card' ? ds.cardConfig : {}),
                                        cardId,
                                    },
                                });
                            }}
                        />
                    </div>
                    <div className="property-row flex items-center mb-3">
                        <label className="property-label w-20 text-xs text-text-secondary">刷新(秒)</label>
                        <input
                            type="number"
                            className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                            min={0}
                            step={10}
                            value={ds?.type === 'card' ? (ds.cardConfig?.refreshInterval ?? 0) : 0}
                            onChange={(e) => {
                                const val = Number(e.target.value);
                                setDataSource({
                                    type: 'card',
                                    sourceType: 'card',
                                    cardConfig: {
                                        ...(ds?.type === 'card' ? ds.cardConfig : {}),
                                        cardId: ds?.type === 'card' ? (ds.cardConfig?.cardId ?? 0) : 0,
                                        refreshInterval: val > 0 ? val : undefined,
                                    },
                                });
                            }}
                            placeholder="0=不刷新"
                        />
                    </div>
                    <MetricBindingEditor
                        metricId={ds?.type === 'card' ? ds.cardConfig?.metricId : undefined}
                        metricVersion={ds?.type === 'card' ? ds.cardConfig?.metricVersion : undefined}
                        onMetricIdChange={(metricId) => {
                            setDataSource({
                                type: 'card',
                                sourceType: 'card',
                                cardConfig: {
                                    ...(ds?.type === 'card' ? ds.cardConfig : {}),
                                    cardId: ds?.type === 'card' ? (ds.cardConfig?.cardId ?? 0) : 0,
                                    refreshInterval: ds?.type === 'card' ? ds.cardConfig?.refreshInterval : undefined,
                                    metricId,
                                    metricVersion: metricId ? (ds?.type === 'card' ? ds.cardConfig?.metricVersion : undefined) : undefined,
                                },
                            });
                        }}
                        onMetricVersionChange={(metricVersion) => {
                            setDataSource({
                                type: 'card',
                                sourceType: 'card',
                                cardConfig: {
                                    ...(ds?.type === 'card' ? ds.cardConfig : {}),
                                    cardId: ds?.type === 'card' ? (ds.cardConfig?.cardId ?? 0) : 0,
                                    refreshInterval: ds?.type === 'card' ? ds.cardConfig?.refreshInterval : undefined,
                                    metricId: ds?.type === 'card' ? ds.cardConfig?.metricId : undefined,
                                    metricVersion,
                                },
                            });
                        }}
                    />
                    <CardParamBindingsEditor
                        bindings={cardBindings}
                        globalVariables={globalVariables}
                        onChange={updateCardBindings}
                    />
                </>
            )}

            {dsType === 'api' && (
                <>
                    <div className="property-row flex items-center mb-3">
                        <label className="property-label w-20 text-xs text-text-secondary">URL</label>
                        <input
                            type="text"
                            className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                            value={ds?.type === 'api' ? (ds.apiConfig?.url ?? '') : ''}
                            onChange={(e) => {
                                setDataSource({
                                    type: 'api',
                                    sourceType: 'api',
                                    refreshInterval: ds?.type === 'api' ? ds.refreshInterval : undefined,
                                    apiConfig: {
                                        ...(ds?.type === 'api' ? ds.apiConfig : { method: 'GET' as const }),
                                        url: e.target.value,
                                        method: ds?.type === 'api' ? (ds.apiConfig?.method ?? 'GET') : 'GET',
                                    },
                                });
                            }}
                            placeholder="/bi/api/card/1/query 或 https://..."
                        />
                    </div>
                    <div className="property-row flex items-center mb-3">
                        <label className="property-label w-20 text-xs text-text-secondary">方法</label>
                        <select
                            className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                            value={ds?.type === 'api' ? (ds.apiConfig?.method ?? 'GET') : 'GET'}
                            onChange={(e) => {
                                const method = (e.target.value as 'GET' | 'POST') || 'GET';
                                setDataSource({
                                    type: 'api',
                                    sourceType: 'api',
                                    refreshInterval: ds?.type === 'api' ? ds.refreshInterval : undefined,
                                    apiConfig: {
                                        ...(ds?.type === 'api' ? ds.apiConfig : {}),
                                        url: ds?.type === 'api' ? (ds.apiConfig?.url ?? '') : '',
                                        method,
                                    },
                                });
                            }}
                        >
                            <option value="GET">GET</option>
                            <option value="POST">POST</option>
                        </select>
                    </div>
                    <div className="property-row flex items-center mb-3">
                        <label className="property-label w-20 text-xs text-text-secondary">Body</label>
                        <textarea
                            className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                            rows={4}
                            value={ds?.type === 'api' ? (ds.apiConfig?.body ?? '') : ''}
                            onChange={(e) => {
                                setDataSource({
                                    type: 'api',
                                    sourceType: 'api',
                                    refreshInterval: ds?.type === 'api' ? ds.refreshInterval : undefined,
                                    apiConfig: {
                                        ...(ds?.type === 'api' ? ds.apiConfig : {}),
                                        url: ds?.type === 'api' ? (ds.apiConfig?.url ?? '') : '',
                                        method: ds?.type === 'api' ? (ds.apiConfig?.method ?? 'GET') : 'GET',
                                        body: e.target.value,
                                    },
                                });
                            }}
                            placeholder='{"parameters":[]}'
                        />
                    </div>
                    <div className="property-row flex items-center mb-3">
                        <label className="property-label w-20 text-xs text-text-secondary">刷新(秒)</label>
                        <input
                            type="number"
                            className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                            min={0}
                            step={10}
                            value={ds?.type === 'api' ? (ds.refreshInterval ?? 0) : 0}
                            onChange={(e) => {
                                const val = Number(e.target.value);
                                setDataSource({
                                    type: 'api',
                                    sourceType: 'api',
                                    refreshInterval: val > 0 ? val : undefined,
                                    apiConfig: ds?.type === 'api'
                                        ? {
                                            ...(ds.apiConfig ?? { method: 'GET' as const, url: '' }),
                                            method: ds.apiConfig?.method ?? 'GET',
                                            url: ds.apiConfig?.url ?? '',
                                        }
                                        : { method: 'GET', url: '' },
                                });
                            }}
                            placeholder="0=不刷新"
                        />
                    </div>
                </>
            )}

            {dsType === 'sql' && (
                <>
                    <div className="property-row flex items-center mb-3">
                        <label className="property-label w-20 text-xs text-text-secondary">数据库</label>
                        <DatabaseIdPicker
                            value={sqlConfig?.databaseId ?? 0}
                            onChange={(databaseId) => {
                                const base = resolveSqlConfig(ds);
                                setDataSource({
                                    type: 'sql',
                                    sourceType: 'sql',
                                    refreshInterval: dsType === 'sql' ? ds?.refreshInterval : undefined,
                                    sqlConfig: {
                                        ...(base ?? { query: '' }),
                                        databaseId: databaseId > 0 ? databaseId : undefined,
                                        query: base?.query ?? '',
                                    },
                                });
                            }}
                        />
                    </div>
                    <div className="property-row flex items-center mb-3">
                        <label className="property-label w-20 text-xs text-text-secondary">数据库ID(手工)</label>
                        <input
                            type="number"
                            className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                            min={1}
                            value={sqlConfig?.databaseId ?? 0}
                            onChange={(e) => {
                                const n = Number(e.target.value);
                                const base = resolveSqlConfig(ds);
                                setDataSource({
                                    type: 'sql',
                                    sourceType: 'sql',
                                    refreshInterval: dsType === 'sql' ? ds?.refreshInterval : undefined,
                                    sqlConfig: {
                                        ...(base ?? { query: '' }),
                                        databaseId: Number.isFinite(n) && n > 0 ? n : undefined,
                                        query: base?.query ?? '',
                                    },
                                });
                            }}
                            placeholder="用于离线环境或未同步数据库列表"
                        />
                    </div>
                    <div className="property-row flex items-center mb-3">
                        <label className="property-label w-20 text-xs text-text-secondary">SQL</label>
                        <textarea
                            className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                            rows={6}
                            value={sqlConfig?.query ?? ''}
                            onChange={(e) => {
                                const base = resolveSqlConfig(ds);
                                setDataSource({
                                    type: 'sql',
                                    sourceType: 'sql',
                                    refreshInterval: dsType === 'sql' ? ds?.refreshInterval : undefined,
                                    sqlConfig: {
                                        ...(base ?? { query: '' }),
                                        databaseId: base?.databaseId,
                                        query: e.target.value,
                                    },
                                });
                            }}
                            placeholder="select * from public.table where day = {{day}} limit 200"
                        />
                    </div>
                    <div className="property-row flex items-center mb-3">
                        <label className="property-label w-20 text-xs text-text-secondary">参数提取</label>
                        <button
                            type="button"
                            className="property-btn-small inline-flex items-center justify-center px-3 py-1.5 min-h-8 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed"
                            onClick={() => {
                                const names = extractSqlTemplateParameterNames(sqlConfig?.query ?? '');
                                if (names.length === 0) {
                                    message.info('未识别到 SQL 参数，占位符示例：{{day}} 或 ${day}');
                                    return;
                                }
                                const previous = new Map(
                                    (sqlBindings ?? []).map((item) => [String(item.name ?? '').trim(), item]),
                                );
                                const nextBindings: CardParameterBinding[] = names.map((name) => {
                                    const exists = previous.get(name);
                                    if (!exists) {
                                        return {
                                            name,
                                            variableKey: '',
                                            value: '',
                                        };
                                    }
                                    return {
                                        name,
                                        variableKey: exists.variableKey ?? '',
                                        value: exists.value ?? '',
                                    };
                                });
                                updateSqlBindings(nextBindings);
                            }}
                        >
                            从 SQL 提取参数
                        </button>
                    </div>
                    <div style={{ fontSize: 11, color: 'var(--color-text-secondary)', marginTop: -2 }}>
                        自动识别 &#123;&#123;param&#125;&#125; / $&#123;param&#125; 占位符并生成参数绑定。
                    </div>
                    <div className="property-row flex items-center mb-3">
                        <label className="property-label w-20 text-xs text-text-secondary">最大行数</label>
                        <input
                            type="number"
                            className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                            min={1}
                            step={100}
                            value={sqlConfig?.maxRows ?? 2000}
                            onChange={(e) => {
                                const n = Number(e.target.value);
                                const base = resolveSqlConfig(ds);
                                setDataSource({
                                    type: 'sql',
                                    sourceType: 'sql',
                                    refreshInterval: dsType === 'sql' ? ds?.refreshInterval : undefined,
                                    sqlConfig: {
                                        ...(base ?? { query: '' }),
                                        query: base?.query ?? '',
                                        maxRows: Number.isFinite(n) && n > 0 ? n : undefined,
                                    },
                                });
                            }}
                            placeholder="默认2000"
                        />
                    </div>
                    <div className="property-row flex items-center mb-3">
                        <label className="property-label w-20 text-xs text-text-secondary">超时(秒)</label>
                        <input
                            type="number"
                            className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                            min={1}
                            step={5}
                            value={sqlConfig?.queryTimeoutSeconds ?? 60}
                            onChange={(e) => {
                                const n = Number(e.target.value);
                                const base = resolveSqlConfig(ds);
                                setDataSource({
                                    type: 'sql',
                                    sourceType: 'sql',
                                    refreshInterval: dsType === 'sql' ? ds?.refreshInterval : undefined,
                                    sqlConfig: {
                                        ...(base ?? { query: '' }),
                                        query: base?.query ?? '',
                                        queryTimeoutSeconds: Number.isFinite(n) && n > 0 ? n : undefined,
                                    },
                                });
                            }}
                            placeholder="默认60"
                        />
                    </div>
                    <CardParamBindingsEditor
                        bindings={sqlBindings}
                        globalVariables={globalVariables}
                        onChange={updateSqlBindings}
                    />
                    <div className="property-row flex items-center mb-3">
                        <label className="property-label w-20 text-xs text-text-secondary">刷新(秒)</label>
                        <input
                            type="number"
                            className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                            min={0}
                            step={10}
                            value={dsType === 'sql' ? (ds?.refreshInterval ?? 0) : 0}
                            onChange={(e) => {
                                const val = Number(e.target.value);
                                const base = resolveSqlConfig(ds);
                                setDataSource({
                                    type: 'sql',
                                    sourceType: 'sql',
                                    refreshInterval: val > 0 ? val : undefined,
                                    sqlConfig: base
                                        ? {
                                            ...base,
                                            query: base.query ?? '',
                                        }
                                        : { query: '' },
                                });
                            }}
                            placeholder="0=不刷新"
                        />
                    </div>
                </>
            )}

            {dsType === 'dataset' && (
                <>
                    <div className="property-row flex items-center mb-3">
                        <label className="property-label w-20 text-xs text-text-secondary">QueryBody(JSON)</label>
                        <textarea
                            className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                            rows={8}
                            value={safeJsonStringify(ds?.datasetConfig?.queryBody)}
                            onChange={(e) => {
                                const parsed = safeJsonParse(e.target.value);
                                setDataSource({
                                    type: 'dataset',
                                    sourceType: 'dataset',
                                    refreshInterval: dsType === 'dataset' ? ds?.refreshInterval : undefined,
                                    datasetConfig: { queryBody: parsed ?? {} },
                                });
                            }}
                            placeholder='{"database":1,"type":"native","native":{"query":"select 1"}}'
                        />
                    </div>
                    <div className="property-row flex items-center mb-3">
                        <label className="property-label w-20 text-xs text-text-secondary">刷新(秒)</label>
                        <input
                            type="number"
                            className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                            min={0}
                            step={10}
                            value={dsType === 'dataset' ? (ds?.refreshInterval ?? 0) : 0}
                            onChange={(e) => {
                                const val = Number(e.target.value);
                                setDataSource({
                                    type: 'dataset',
                                    sourceType: 'dataset',
                                    refreshInterval: val > 0 ? val : undefined,
                                    datasetConfig: ds?.datasetConfig ?? { queryBody: {} },
                                });
                            }}
                            placeholder="0=不刷新"
                        />
                    </div>
                </>
            )}

            {dsType === 'metric' && (
                <>
                    <div className="property-row flex items-center mb-3">
                        <label className="property-label w-20 text-xs text-text-secondary">Card</label>
                        <CardIdPicker
                            value={dsType === 'metric' ? (ds?.metricConfig?.cardId ?? 0) : 0}
                            onChange={(cardId) => {
                                const currentMetricConfig = dsType === 'metric' ? ds?.metricConfig : undefined;
                                setDataSource({
                                    type: 'metric',
                                    sourceType: 'metric',
                                    refreshInterval: dsType === 'metric' ? ds?.refreshInterval : undefined,
                                    metricConfig: {
                                        ...(currentMetricConfig ?? {}),
                                        cardId,
                                    },
                                });
                            }}
                        />
                    </div>
                    <MetricBindingEditor
                        metricId={dsType === 'metric' ? ds?.metricConfig?.metricId : undefined}
                        metricVersion={dsType === 'metric' ? ds?.metricConfig?.metricVersion : undefined}
                        onMetricIdChange={(metricId) => {
                            const currentMetricConfig = dsType === 'metric' ? ds?.metricConfig : undefined;
                            setDataSource({
                                type: 'metric',
                                sourceType: 'metric',
                                refreshInterval: dsType === 'metric' ? ds?.refreshInterval : undefined,
                                metricConfig: {
                                    ...(currentMetricConfig ?? {}),
                                    cardId: currentMetricConfig?.cardId ?? 0,
                                    metricId,
                                    metricVersion: metricId ? currentMetricConfig?.metricVersion : undefined,
                                },
                            });
                        }}
                        onMetricVersionChange={(metricVersion) => {
                            const currentMetricConfig = dsType === 'metric' ? ds?.metricConfig : undefined;
                            setDataSource({
                                type: 'metric',
                                sourceType: 'metric',
                                refreshInterval: dsType === 'metric' ? ds?.refreshInterval : undefined,
                                metricConfig: {
                                    ...(currentMetricConfig ?? {}),
                                    cardId: currentMetricConfig?.cardId ?? 0,
                                    metricId: currentMetricConfig?.metricId,
                                    metricVersion,
                                },
                            });
                        }}
                    />
                    <CardParamBindingsEditor
                        bindings={metricBindings}
                        globalVariables={globalVariables}
                        onChange={updateMetricBindings}
                    />
                    <div className="property-row flex items-center mb-3">
                        <label className="property-label w-20 text-xs text-text-secondary">刷新(秒)</label>
                        <input
                            type="number"
                            className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                            min={0}
                            step={10}
                            value={dsType === 'metric' ? (ds?.refreshInterval ?? 0) : 0}
                            onChange={(e) => {
                                const val = Number(e.target.value);
                                const currentMetricConfig = dsType === 'metric' ? ds?.metricConfig : undefined;
                                setDataSource({
                                    type: 'metric',
                                    sourceType: 'metric',
                                    refreshInterval: val > 0 ? val : undefined,
                                    metricConfig: {
                                        ...(currentMetricConfig ?? {}),
                                        cardId: currentMetricConfig?.cardId ?? 0,
                                    },
                                });
                            }}
                            placeholder="0=不刷新"
                        />
                    </div>
                </>
            )}

        </>
    );
}
