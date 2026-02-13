import { useScreen } from '../ScreenContext';
import type { ScreenComponent, DataSourceConfig, DrillLevel } from '../types';
import { DRILLABLE_TYPES } from '../types';
import { CardIdPicker } from './CardIdPicker';

export function PropertyPanel() {
    const { state, updateComponent } = useScreen();
    const { config, selectedIds } = state;

    const selectedComponent = selectedIds.length === 1
        ? config.components.find((c) => c.id === selectedIds[0])
        : null;

    if (!selectedComponent) {
        return (
            <div className="property-panel">
                <div className="property-panel-header">
                    <h3>属性</h3>
                </div>
                <div className="property-panel-content">
                    <div className="empty-state">
                        <div className="empty-state-icon">🎨</div>
                        <div className="empty-state-text">选择组件以编辑属性</div>
                        <div className="empty-state-hint">点击画布中的组件进行选择</div>
                    </div>
                </div>
            </div>
        );
    }

    const handleChange = (key: string, value: unknown) => {
        updateComponent(selectedComponent.id, { [key]: value });
    };

    const handleConfigChange = (key: string, value: unknown) => {
        updateComponent(selectedComponent.id, {
            config: { ...selectedComponent.config, [key]: value },
        });
    };

    return (
        <div className="property-panel">
            <div className="property-panel-header">
                <h3>属性 - {selectedComponent.name}</h3>
            </div>
            <div className="property-panel-content">
                {/* Position & Size */}
                <div className="property-section">
                    <div className="property-section-title">位置与尺寸</div>

                    <div className="property-row">
                        <label className="property-label">X</label>
                        <input
                            type="number"
                            className="property-input"
                            value={selectedComponent.x}
                            onChange={(e) => handleChange('x', Number(e.target.value))}
                        />
                    </div>

                    <div className="property-row">
                        <label className="property-label">Y</label>
                        <input
                            type="number"
                            className="property-input"
                            value={selectedComponent.y}
                            onChange={(e) => handleChange('y', Number(e.target.value))}
                        />
                    </div>

                    <div className="property-row">
                        <label className="property-label">宽度</label>
                        <input
                            type="number"
                            className="property-input"
                            value={selectedComponent.width}
                            onChange={(e) => handleChange('width', Number(e.target.value))}
                        />
                    </div>

                    <div className="property-row">
                        <label className="property-label">高度</label>
                        <input
                            type="number"
                            className="property-input"
                            value={selectedComponent.height}
                            onChange={(e) => handleChange('height', Number(e.target.value))}
                        />
                    </div>
                </div>

                {/* Component-specific config */}
                <div className="property-section">
                    <div className="property-section-title">组件配置</div>

                    {renderComponentConfig(selectedComponent, handleConfigChange)}
                </div>

                {/* Data Source */}
                <div className="property-section">
                    <div className="property-section-title">数据源</div>
                    {renderDataSourceConfig(selectedComponent, updateComponent)}
                </div>

                {/* Drill-down config */}
                {renderDrillDownConfig(selectedComponent, updateComponent)}

                {/* Visibility & Lock */}
                <div className="property-section">
                    <div className="property-section-title">其他</div>

                    <div className="property-row">
                        <label className="property-label">名称</label>
                        <input
                            type="text"
                            className="property-input"
                            value={selectedComponent.name}
                            onChange={(e) => handleChange('name', e.target.value)}
                        />
                    </div>

                    <div className="property-row">
                        <label className="property-label">锁定</label>
                        <input
                            type="checkbox"
                            checked={selectedComponent.locked}
                            onChange={(e) => handleChange('locked', e.target.checked)}
                        />
                    </div>

                    <div className="property-row">
                        <label className="property-label">可见</label>
                        <input
                            type="checkbox"
                            checked={selectedComponent.visible}
                            onChange={(e) => handleChange('visible', e.target.checked)}
                        />
                    </div>
                </div>
            </div>
        </div>
    );
}

function renderComponentConfig(
    component: ScreenComponent,
    onChange: (key: string, value: unknown) => void
) {
    const { type, config } = component;

    switch (type) {
        case 'line-chart':
        case 'bar-chart':
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">标题</label>
                        <input
                            type="text"
                            className="property-input"
                            value={config.title as string}
                            onChange={(e) => onChange('title', e.target.value)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">标题字号</label>
                        <input
                            type="number"
                            className="property-input"
                            min={10}
                            max={36}
                            value={(config.titleFontSize as number) || 14}
                            onChange={(e) => onChange('titleFontSize', Number(e.target.value))}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">图例位置</label>
                        <select
                            className="property-input"
                            value={(config.legendPosition as string) || 'top'}
                            onChange={(e) => onChange('legendPosition', e.target.value)}
                        >
                            <option value="top">顶部</option>
                            <option value="bottom">底部</option>
                            <option value="left">左侧</option>
                            <option value="right">右侧</option>
                        </select>
                    </div>
                </>
            );

        case 'pie-chart':
        case 'gauge-chart':
        case 'radar-chart':
        case 'funnel-chart':
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">标题</label>
                        <input
                            type="text"
                            className="property-input"
                            value={config.title as string}
                            onChange={(e) => onChange('title', e.target.value)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">标题字号</label>
                        <input
                            type="number"
                            className="property-input"
                            min={10}
                            max={36}
                            value={(config.titleFontSize as number) || 14}
                            onChange={(e) => onChange('titleFontSize', Number(e.target.value))}
                        />
                    </div>
                    {type !== 'gauge-chart' && (
                        <div className="property-row">
                            <label className="property-label">图例位置</label>
                            <select
                                className="property-input"
                                value={(config.legendPosition as string) || 'top'}
                                onChange={(e) => onChange('legendPosition', e.target.value)}
                            >
                                <option value="top">顶部</option>
                                <option value="bottom">底部</option>
                                <option value="left">左侧</option>
                                <option value="right">右侧</option>
                            </select>
                        </div>
                    )}
                    {type === 'gauge-chart' && (
                        <div className="property-row">
                            <label className="property-label">值</label>
                            <input
                                type="number"
                                className="property-input"
                                value={config.value as number}
                                onChange={(e) => onChange('value', Number(e.target.value))}
                            />
                        </div>
                    )}
                </>
            );

        case 'number-card':
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">标题</label>
                        <input
                            type="text"
                            className="property-input"
                            value={config.title as string}
                            onChange={(e) => onChange('title', e.target.value)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">数值</label>
                        <input
                            type="number"
                            className="property-input"
                            value={config.value as number}
                            onChange={(e) => onChange('value', Number(e.target.value))}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">前缀</label>
                        <input
                            type="text"
                            className="property-input"
                            value={config.prefix as string}
                            onChange={(e) => onChange('prefix', e.target.value)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">标题字号</label>
                        <input
                            type="number"
                            className="property-input"
                            min={10}
                            max={36}
                            value={(config.titleFontSize as number) || 12}
                            onChange={(e) => onChange('titleFontSize', Number(e.target.value))}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">数值字号</label>
                        <input
                            type="number"
                            className="property-input"
                            min={16}
                            max={72}
                            value={(config.valueFontSize as number) || 32}
                            onChange={(e) => onChange('valueFontSize', Number(e.target.value))}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">标题颜色</label>
                        <input
                            type="color"
                            className="property-color-input"
                            value={(config.titleColor as string) || '#ffffff'}
                            onChange={(e) => onChange('titleColor', e.target.value)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">数值颜色</label>
                        <input
                            type="color"
                            className="property-color-input"
                            value={(config.valueColor as string) || '#ffffff'}
                            onChange={(e) => onChange('valueColor', e.target.value)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">背景色</label>
                        <input
                            type="color"
                            className="property-color-input"
                            value={(config.backgroundColor as string) || '#1a1a2e'}
                            onChange={(e) => onChange('backgroundColor', e.target.value)}
                        />
                    </div>
                </>
            );

        case 'title':
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">文本</label>
                        <input
                            type="text"
                            className="property-input"
                            value={config.text as string}
                            onChange={(e) => onChange('text', e.target.value)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">字号</label>
                        <input
                            type="number"
                            className="property-input"
                            value={config.fontSize as number}
                            onChange={(e) => onChange('fontSize', Number(e.target.value))}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">颜色</label>
                        <input
                            type="color"
                            className="property-color-input"
                            value={config.color as string}
                            onChange={(e) => onChange('color', e.target.value)}
                        />
                    </div>
                </>
            );

        case 'datetime':
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">格式</label>
                        <input
                            type="text"
                            className="property-input"
                            value={config.format as string}
                            onChange={(e) => onChange('format', e.target.value)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">字号</label>
                        <input
                            type="number"
                            className="property-input"
                            value={config.fontSize as number}
                            onChange={(e) => onChange('fontSize', Number(e.target.value))}
                        />
                    </div>
                </>
            );

        case 'progress-bar':
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">值 (%)</label>
                        <input
                            type="number"
                            className="property-input"
                            min={0}
                            max={100}
                            value={config.value as number}
                            onChange={(e) => onChange('value', Number(e.target.value))}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">显示标签</label>
                        <input
                            type="checkbox"
                            checked={config.showLabel as boolean}
                            onChange={(e) => onChange('showLabel', e.target.checked)}
                        />
                    </div>
                </>
            );

        case 'image':
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">图片URL</label>
                        <input
                            type="text"
                            className="property-input"
                            value={config.src as string}
                            onChange={(e) => onChange('src', e.target.value)}
                            placeholder="输入图片地址"
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">填充方式</label>
                        <select
                            className="property-input"
                            value={config.fit as string}
                            onChange={(e) => onChange('fit', e.target.value)}
                        >
                            <option value="cover">覆盖</option>
                            <option value="contain">包含</option>
                            <option value="fill">拉伸</option>
                        </select>
                    </div>
                </>
            );

        case 'video':
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">视频URL</label>
                        <input
                            type="text"
                            className="property-input"
                            value={config.src as string}
                            onChange={(e) => onChange('src', e.target.value)}
                            placeholder="输入视频地址"
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">自动播放</label>
                        <input
                            type="checkbox"
                            checked={config.autoplay as boolean}
                            onChange={(e) => onChange('autoplay', e.target.checked)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">循环</label>
                        <input
                            type="checkbox"
                            checked={config.loop as boolean}
                            onChange={(e) => onChange('loop', e.target.checked)}
                        />
                    </div>
                </>
            );

        case 'iframe':
            return (
                <div className="property-row">
                    <label className="property-label">URL</label>
                    <input
                        type="text"
                        className="property-input"
                        value={config.src as string}
                        onChange={(e) => onChange('src', e.target.value)}
                        placeholder="输入网页地址"
                    />
                </div>
            );

        case 'border-box':
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">边框类型</label>
                        <select
                            className="property-input"
                            value={config.boxType as number}
                            onChange={(e) => onChange('boxType', Number(e.target.value))}
                        >
                            {[1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13].map((n) => (
                                <option key={n} value={n}>边框 {n}</option>
                            ))}
                        </select>
                    </div>
                </>
            );

        case 'decoration':
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">装饰类型</label>
                        <select
                            className="property-input"
                            value={config.decorationType as number}
                            onChange={(e) => onChange('decorationType', Number(e.target.value))}
                        >
                            {[1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12].map((n) => (
                                <option key={n} value={n}>装饰 {n}</option>
                            ))}
                        </select>
                    </div>
                </>
            );

        case 'water-level':
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">值 (%)</label>
                        <input
                            type="number"
                            className="property-input"
                            min={0}
                            max={100}
                            value={config.value as number}
                            onChange={(e) => onChange('value', Number(e.target.value))}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">形状</label>
                        <select
                            className="property-input"
                            value={config.shape as string}
                            onChange={(e) => onChange('shape', e.target.value)}
                        >
                            <option value="round">圆形</option>
                            <option value="rect">矩形</option>
                            <option value="roundRect">圆角矩形</option>
                        </select>
                    </div>
                </>
            );

        case 'digital-flop':
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">数值</label>
                        <input
                            type="number"
                            className="property-input"
                            value={(config.number as number[])?.[0] || 0}
                            onChange={(e) => onChange('number', [Number(e.target.value)])}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">字号</label>
                        <input
                            type="number"
                            className="property-input"
                            value={(config.style as { fontSize?: number })?.fontSize || 30}
                            onChange={(e) => onChange('style', {
                                ...(config.style as object),
                                fontSize: Number(e.target.value),
                            })}
                        />
                    </div>
                </>
            );

        case 'percent-pond':
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">值 (%)</label>
                        <input
                            type="number"
                            className="property-input"
                            min={0}
                            max={100}
                            value={config.value as number}
                            onChange={(e) => onChange('value', Number(e.target.value))}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">边框宽度</label>
                        <input
                            type="number"
                            className="property-input"
                            min={1}
                            max={10}
                            value={config.borderWidth as number}
                            onChange={(e) => onChange('borderWidth', Number(e.target.value))}
                        />
                    </div>
                </>
            );

        case 'scatter-chart':
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">标题</label>
                        <input
                            type="text"
                            className="property-input"
                            value={config.title as string}
                            onChange={(e) => onChange('title', e.target.value)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">标题字号</label>
                        <input
                            type="number"
                            className="property-input"
                            min={10}
                            max={36}
                            value={(config.titleFontSize as number) || 14}
                            onChange={(e) => onChange('titleFontSize', Number(e.target.value))}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">图例位置</label>
                        <select
                            className="property-input"
                            value={(config.legendPosition as string) || 'top'}
                            onChange={(e) => onChange('legendPosition', e.target.value)}
                        >
                            <option value="top">顶部</option>
                            <option value="bottom">底部</option>
                            <option value="left">左侧</option>
                            <option value="right">右侧</option>
                        </select>
                    </div>
                </>
            );

        case 'scroll-board':
            return <ScrollBoardConfig component={component} onChange={onChange} />;

        case 'table':
            return <TableConfig component={component} onChange={onChange} />;

        case 'scroll-ranking':
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">行数</label>
                        <input
                            type="number"
                            className="property-input"
                            min={1}
                            max={20}
                            value={config.rowNum as number}
                            onChange={(e) => onChange('rowNum', Number(e.target.value))}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">等待时间(ms)</label>
                        <input
                            type="number"
                            className="property-input"
                            min={500}
                            max={10000}
                            step={500}
                            value={config.waitTime as number || 2000}
                            onChange={(e) => onChange('waitTime', Number(e.target.value))}
                        />
                    </div>
                </>
            );

        default:
            return (
                <div className="empty-state-hint">
                    暂无可配置项
                </div>
            );
    }
}

function renderDataSourceConfig(
    component: ScreenComponent,
    updateComponent: (id: string, updates: Partial<ScreenComponent>) => void,
) {
    const ds = component.dataSource as DataSourceConfig | undefined;
    const dsType = ds?.type ?? 'static';

    const setDataSource = (newDs: DataSourceConfig | undefined) => {
        updateComponent(component.id, { dataSource: newDs });
    };

    return (
        <>
            <div className="property-row">
                <label className="property-label">类型</label>
                <select
                    className="property-input"
                    value={dsType}
                    onChange={(e) => {
                        const val = e.target.value;
                        if (val === 'static') {
                            setDataSource(undefined);
                        } else if (val === 'card') {
                            setDataSource({
                                type: 'card',
                                cardConfig: { cardId: 0 },
                            });
                        }
                    }}
                >
                    <option value="static">静态数据</option>
                    <option value="card">Card 查询</option>
                </select>
            </div>

            {dsType === 'card' && (
                <>
                    <div className="property-row">
                        <label className="property-label">Card</label>
                        <CardIdPicker
                            value={ds?.cardConfig?.cardId ?? 0}
                            onChange={(cardId) => {
                                setDataSource({
                                    ...ds!,
                                    type: 'card',
                                    cardConfig: {
                                        ...ds!.cardConfig!,
                                        cardId,
                                    },
                                });
                            }}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">刷新(秒)</label>
                        <input
                            type="number"
                            className="property-input"
                            min={0}
                            step={10}
                            value={ds?.cardConfig?.refreshInterval ?? 0}
                            onChange={(e) => {
                                const val = Number(e.target.value);
                                setDataSource({
                                    ...ds!,
                                    type: 'card',
                                    cardConfig: {
                                        ...ds!.cardConfig!,
                                        refreshInterval: val > 0 ? val : undefined,
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

function renderDrillDownConfig(
    component: ScreenComponent,
    updateComponent: (id: string, updates: Partial<ScreenComponent>) => void,
) {
    const { type, dataSource, drillDown } = component;
    const cardId = dataSource?.type === 'card' ? dataSource.cardConfig?.cardId : undefined;

    // Only show for drillable chart types with a valid card data source
    if (!DRILLABLE_TYPES.has(type) || dataSource?.type !== 'card' || !cardId || cardId <= 0) {
        return null;
    }

    const enabled = drillDown?.enabled ?? false;
    const levels = drillDown?.levels ?? [];

    const setDrillDown = (updates: Partial<typeof drillDown>) => {
        updateComponent(component.id, {
            drillDown: { enabled, levels, ...drillDown, ...updates },
        });
    };

    const updateLevel = (index: number, field: keyof DrillLevel, value: string | number) => {
        const newLevels = [...levels];
        newLevels[index] = { ...newLevels[index], [field]: value };
        setDrillDown({ levels: newLevels });
    };

    const removeLevel = (index: number) => {
        setDrillDown({ levels: levels.filter((_, i) => i !== index) });
    };

    const addLevel = () => {
        setDrillDown({ levels: [...levels, { cardId: 0, paramName: '', label: '' }] });
    };

    return (
        <div className="property-section">
            <div className="property-section-title">下钻配置</div>

            <div className="property-row">
                <label className="property-label">启用下钻</label>
                <input
                    type="checkbox"
                    checked={enabled}
                    onChange={(e) => setDrillDown({ enabled: e.target.checked })}
                />
            </div>

            {enabled && (
                <>
                    {levels.map((level, i) => (
                        <div key={i} style={{
                            border: '1px solid rgba(255,255,255,0.1)',
                            borderRadius: 4,
                            padding: 8,
                            marginBottom: 8,
                        }}>
                            <div style={{
                                display: 'flex',
                                justifyContent: 'space-between',
                                alignItems: 'center',
                                marginBottom: 4,
                                fontSize: 11,
                                color: '#888',
                            }}>
                                <span>层级 {i + 1}</span>
                                <button
                                    className="property-btn-small"
                                    onClick={() => removeLevel(i)}
                                    style={{
                                        background: 'none', border: 'none',
                                        color: '#ef4444', cursor: 'pointer', fontSize: 11,
                                    }}
                                >
                                    删除
                                </button>
                            </div>
                            <div className="property-row">
                                <label className="property-label">Card</label>
                                <CardIdPicker
                                    value={level.cardId || 0}
                                    onChange={(cardId) => updateLevel(i, 'cardId', cardId)}
                                    placeholder="-- 下钻目标 --"
                                />
                            </div>
                            <div className="property-row">
                                <label className="property-label">参数名</label>
                                <input
                                    type="text"
                                    className="property-input"
                                    value={level.paramName}
                                    onChange={(e) => updateLevel(i, 'paramName', e.target.value)}
                                    placeholder="如: region"
                                />
                            </div>
                            <div className="property-row">
                                <label className="property-label">标签</label>
                                <input
                                    type="text"
                                    className="property-input"
                                    value={level.label}
                                    onChange={(e) => updateLevel(i, 'label', e.target.value)}
                                    placeholder="如: 地区"
                                />
                            </div>
                        </div>
                    ))}

                    <button
                        className="property-input"
                        onClick={addLevel}
                        style={{
                            width: '100%', cursor: 'pointer',
                            textAlign: 'center', color: '#6366f1',
                        }}
                    >
                        + 添加下钻层级
                    </button>
                </>
            )}
        </div>
    );
}

/** Column config entry for scroll-board */
interface ColumnEntry { source: string; alias?: string }

/** scroll-board 专用属性面板，支持动态列选择 */
function ScrollBoardConfig({ component, onChange }: {
    component: ScreenComponent;
    onChange: (key: string, value: unknown) => void;
}) {
    const { config, dataSource } = component;

    // Read _sourceColumns persisted by ComponentRenderer (no separate API call)
    const sourceCols = config._sourceColumns as Array<{ name: string; displayName: string }> ?? [];
    const columns = config.columns as ColumnEntry[] | undefined;
    const hasCardSource = dataSource?.type === 'card' && !!dataSource.cardConfig?.cardId;

    // Static fallback: use config.header when no card data source
    const staticHeaders = config.header as string[] || [];
    const columnAlias = config.columnAlias as Record<string, string> || {};

    // Helper: initialize columns config from source columns (all selected)
    const initColumns = (): ColumnEntry[] =>
        sourceCols.map(c => ({ source: c.name }));

    const handleToggleColumn = (colName: string, selected: boolean) => {
        const current = columns ?? initColumns();
        if (selected) {
            // Add column back at its original source position
            const originalIdx = sourceCols.findIndex(c => c.name === colName);
            const newCols = [...current];
            let insertIdx = newCols.length;
            for (let i = 0; i < newCols.length; i++) {
                const idx = sourceCols.findIndex(c => c.name === newCols[i].source);
                if (idx > originalIdx) { insertIdx = i; break; }
            }
            newCols.splice(insertIdx, 0, { source: colName });
            onChange('columns', newCols);
        } else {
            onChange('columns', current.filter(c => c.source !== colName));
        }
    };

    const handleAliasChange = (colName: string, alias: string) => {
        const current = columns ?? initColumns();
        onChange('columns', current.map(c => {
            if (c.source !== colName) return c;
            if (alias) return { ...c, alias };
            const { alias: _a, ...rest } = c;
            return rest;
        }));
    };

    return (
        <>
            <div className="property-row">
                <label className="property-label">行数</label>
                <input
                    type="number"
                    className="property-input"
                    min={1}
                    max={20}
                    value={config.rowNum as number}
                    onChange={(e) => onChange('rowNum', Number(e.target.value))}
                />
            </div>
            <div className="property-row">
                <label className="property-label">等待时间(ms)</label>
                <input
                    type="number"
                    className="property-input"
                    min={500}
                    max={10000}
                    step={500}
                    value={config.waitTime as number || 2000}
                    onChange={(e) => onChange('waitTime', Number(e.target.value))}
                />
            </div>
            <div className="property-row">
                <label className="property-label">表头颜色</label>
                <input
                    type="color"
                    className="property-color-input"
                    value={(config.headerColor as string) || '#ffffff'}
                    onChange={(e) => onChange('headerColor', e.target.value)}
                />
            </div>
            <div className="property-row">
                <label className="property-label">表头背景</label>
                <input
                    type="color"
                    className="property-color-input"
                    value={(config.headerBGC as string) || '#003366'}
                    onChange={(e) => onChange('headerBGC', e.target.value)}
                />
            </div>

            {/* Card 数据源: 等待列加载 */}
            {hasCardSource && sourceCols.length === 0 && (
                <div style={{ fontSize: 11, color: '#888', marginTop: 8, padding: '4px 0' }}>
                    等待数据源加载列信息…
                </div>
            )}

            {/* Card 数据源: 动态列选择 */}
            {hasCardSource && sourceCols.length > 0 && (
                <>
                    <div style={{ fontSize: 11, color: '#888', marginTop: 8, marginBottom: 4 }}>
                        显示列 (来自数据源)
                    </div>
                    {sourceCols.map(col => {
                        const colConfig = columns?.find(c => c.source === col.name);
                        const isSelected = columns ? !!colConfig : true;
                        const displayName = col.displayName || col.name;
                        return (
                            <div key={col.name} style={{
                                border: '1px solid rgba(255,255,255,0.06)',
                                borderRadius: 4,
                                padding: '4px 6px',
                                marginBottom: 4,
                            }}>
                                <div className="property-row" style={{ marginBottom: isSelected ? 4 : 0 }}>
                                    <label className="property-label" style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                                        <input
                                            type="checkbox"
                                            checked={isSelected}
                                            onChange={(e) => handleToggleColumn(col.name, e.target.checked)}
                                        />
                                        <span title={col.name}>{displayName}</span>
                                    </label>
                                </div>
                                {isSelected && (
                                    <div className="property-row">
                                        <label className="property-label">别名</label>
                                        <input
                                            type="text"
                                            className="property-input"
                                            placeholder={displayName}
                                            value={colConfig?.alias || ''}
                                            onChange={(e) => handleAliasChange(col.name, e.target.value)}
                                        />
                                    </div>
                                )}
                            </div>
                        );
                    })}
                </>
            )}

            {/* 静态数据源: 按索引的表头别名 (保持向后兼容) */}
            {!hasCardSource && staticHeaders.length > 0 && (
                <>
                    <div style={{ fontSize: 11, color: '#888', marginTop: 8, marginBottom: 4 }}>
                        表头别名
                    </div>
                    {staticHeaders.map((h, i) => (
                        <div className="property-row" key={i}>
                            <label className="property-label" title={h}>列{i + 1}</label>
                            <input
                                type="text"
                                className="property-input"
                                placeholder={h}
                                value={columnAlias[String(i)] || ''}
                                onChange={(e) => {
                                    const newAlias = { ...columnAlias };
                                    if (e.target.value) {
                                        newAlias[String(i)] = e.target.value;
                                    } else {
                                        delete newAlias[String(i)];
                                    }
                                    onChange('columnAlias', newAlias);
                                }}
                            />
                        </div>
                    ))}
                </>
            )}
        </>
    );
}

/** table 专用属性面板，支持动态列选择和样式配置 */
function TableConfig({ component, onChange }: {
    component: ScreenComponent;
    onChange: (key: string, value: unknown) => void;
}) {
    const { config, dataSource } = component;

    const sourceCols = config._sourceColumns as Array<{ name: string; displayName: string }> ?? [];
    const columns = config.columns as ColumnEntry[] | undefined;
    const hasCardSource = dataSource?.type === 'card' && !!dataSource.cardConfig?.cardId;

    const staticHeaders = config.header as string[] || [];
    const columnAlias = config.columnAlias as Record<string, string> || {};

    const initColumns = (): ColumnEntry[] =>
        sourceCols.map(c => ({ source: c.name }));

    const handleToggleColumn = (colName: string, selected: boolean) => {
        const current = columns ?? initColumns();
        if (selected) {
            const originalIdx = sourceCols.findIndex(c => c.name === colName);
            const newCols = [...current];
            let insertIdx = newCols.length;
            for (let i = 0; i < newCols.length; i++) {
                const idx = sourceCols.findIndex(c => c.name === newCols[i].source);
                if (idx > originalIdx) {
                    insertIdx = i;
                    break;
                }
            }
            newCols.splice(insertIdx, 0, { source: colName });
            onChange('columns', newCols);
        } else {
            onChange('columns', current.filter(c => c.source !== colName));
        }
    };

    const handleAliasChange = (colName: string, alias: string) => {
        const current = columns ?? initColumns();
        onChange('columns', current.map(c => {
            if (c.source !== colName) return c;
            if (alias) return { ...c, alias };
            const { alias: _a, ...rest } = c;
            return rest;
        }));
    };

    return (
        <>
            <div className="property-row">
                <label className="property-label">字号</label>
                <input
                    type="number"
                    className="property-input"
                    min={10}
                    max={24}
                    value={(config.fontSize as number) || 13}
                    onChange={(e) => onChange('fontSize', Number(e.target.value))}
                />
            </div>
            <div className="property-row">
                <label className="property-label">表头颜色</label>
                <input
                    type="color"
                    className="property-color-input"
                    value={(config.headerColor as string) || '#e5e7eb'}
                    onChange={(e) => onChange('headerColor', e.target.value)}
                />
            </div>
            <div className="property-row">
                <label className="property-label">表头背景</label>
                <input
                    type="color"
                    className="property-color-input"
                    value={(config.headerBackground as string) || '#64748b'}
                    onChange={(e) => onChange('headerBackground', e.target.value)}
                />
            </div>
            <div className="property-row">
                <label className="property-label">正文颜色</label>
                <input
                    type="color"
                    className="property-color-input"
                    value={(config.bodyColor as string) || '#d1d5db'}
                    onChange={(e) => onChange('bodyColor', e.target.value)}
                />
            </div>
            <div className="property-row">
                <label className="property-label">边框颜色</label>
                <input
                    type="color"
                    className="property-color-input"
                    value={(config.borderColor as string) || '#94a3b8'}
                    onChange={(e) => onChange('borderColor', e.target.value)}
                />
            </div>

            {hasCardSource && sourceCols.length === 0 && (
                <div style={{ fontSize: 11, color: '#888', marginTop: 8, padding: '4px 0' }}>
                    等待数据源加载列信息…
                </div>
            )}

            {hasCardSource && sourceCols.length > 0 && (
                <>
                    <div style={{ fontSize: 11, color: '#888', marginTop: 8, marginBottom: 4 }}>
                        字段绑定 (来自数据源)
                    </div>
                    {sourceCols.map(col => {
                        const colConfig = columns?.find(c => c.source === col.name);
                        const isSelected = columns ? !!colConfig : true;
                        const displayName = col.displayName || col.name;
                        return (
                            <div key={col.name} style={{
                                border: '1px solid rgba(255,255,255,0.06)',
                                borderRadius: 4,
                                padding: '4px 6px',
                                marginBottom: 4,
                            }}>
                                <div className="property-row" style={{ marginBottom: isSelected ? 4 : 0 }}>
                                    <label className="property-label" style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                                        <input
                                            type="checkbox"
                                            checked={isSelected}
                                            onChange={(e) => handleToggleColumn(col.name, e.target.checked)}
                                        />
                                        <span title={col.name}>{displayName}</span>
                                    </label>
                                </div>
                                {isSelected && (
                                    <div className="property-row">
                                        <label className="property-label">表头标题</label>
                                        <input
                                            type="text"
                                            className="property-input"
                                            placeholder={displayName}
                                            value={colConfig?.alias || ''}
                                            onChange={(e) => handleAliasChange(col.name, e.target.value)}
                                        />
                                    </div>
                                )}
                            </div>
                        );
                    })}
                </>
            )}

            {!hasCardSource && staticHeaders.length > 0 && (
                <>
                    <div style={{ fontSize: 11, color: '#888', marginTop: 8, marginBottom: 4 }}>
                        表头别名
                    </div>
                    {staticHeaders.map((h, i) => (
                        <div className="property-row" key={i}>
                            <label className="property-label" title={h}>列{i + 1}</label>
                            <input
                                type="text"
                                className="property-input"
                                placeholder={h}
                                value={columnAlias[String(i)] || ''}
                                onChange={(e) => {
                                    const newAlias = { ...columnAlias };
                                    if (e.target.value) {
                                        newAlias[String(i)] = e.target.value;
                                    } else {
                                        delete newAlias[String(i)];
                                    }
                                    onChange('columnAlias', newAlias);
                                }}
                            />
                        </div>
                    ))}
                </>
            )}
        </>
    );
}
