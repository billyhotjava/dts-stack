import type { DeviceMode } from '../deviceMode';

interface DeviceModeSwitcherProps {
    deviceMode: DeviceMode;
    forcedDeviceMode: DeviceMode | null;
    position?: 'absolute' | 'fixed' | 'inline';
    onSetForcedMode: (mode: DeviceMode | null) => void;
}

export function DeviceModeSwitcher({
    deviceMode,
    forcedDeviceMode,
    position = 'absolute',
    onSetForcedMode,
}: DeviceModeSwitcherProps) {
    return (
        <div className={`runtime-control-card device-mode-switcher device-mode-switcher--${position}`}>
            <div className="device-mode-switcher__status">
                <div className="runtime-control-card__title">设备模式</div>
                <span className={`screen-runtime__badge ${forcedDeviceMode ? 'is-info' : ''}`}>
                    {deviceMode}{forcedDeviceMode ? ' 强制' : ' 自动'}
                </span>
            </div>
            <div className="runtime-control-row">
                {[
                    { key: 'auto', label: '自动' },
                    { key: 'pc', label: 'PC' },
                    { key: 'tablet', label: '平板' },
                    { key: 'mobile', label: '手机' },
                ].map((item) => {
                    const active = (item.key === 'auto' && !forcedDeviceMode) || forcedDeviceMode === item.key;
                    return (
                        <button
                            key={item.key}
                            type="button"
                            onClick={() => {
                                if (item.key === 'auto') {
                                    onSetForcedMode(null);
                                    return;
                                }
                                onSetForcedMode(item.key as DeviceMode);
                            }}
                            className={`runtime-control-btn ${active ? 'is-active' : ''}`}
                        >
                            {item.label}
                        </button>
                    );
                })}
            </div>
        </div>
    );
}
