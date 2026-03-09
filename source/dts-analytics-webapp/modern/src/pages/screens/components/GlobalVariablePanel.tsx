import { useScreenRuntime } from '../ScreenRuntimeContext';

export function GlobalVariablePanel() {
    const { definitions, values, setVariable } = useScreenRuntime();

    if (!definitions.length) {
        return null;
    }

    return (
        <div className="runtime-control-card global-variable-panel">
            <div className="runtime-control-card__title">运行时变量</div>
            <div className="runtime-control-card__subtitle">页面参数会实时驱动筛选、联动和展示。</div>
            <div className="global-variable-panel__list">
                {definitions.map((item) => {
                    const key = item.key;
                    const value = values[key] ?? '';
                    return (
                        <label key={key} className="global-variable-panel__field">
                            <span className="global-variable-panel__label">{item.label || key}</span>
                            <input
                                type={item.type === 'number' ? 'number' : item.type === 'date' ? 'date' : 'text'}
                                value={value}
                                onChange={(e) => setVariable(key, e.target.value, 'global-variable-panel')}
                                className="runtime-control-input"
                            />
                        </label>
                    );
                })}
            </div>
        </div>
    );
}
