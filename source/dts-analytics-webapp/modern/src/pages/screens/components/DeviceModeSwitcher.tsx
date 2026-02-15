import type { DeviceMode } from '../deviceMode';

interface DeviceModeSwitcherProps {
    deviceMode: DeviceMode;
    forcedDeviceMode: DeviceMode | null;
    position?: 'absolute' | 'fixed';
    onSetForcedMode: (mode: DeviceMode | null) => void;
}

export function DeviceModeSwitcher({
    deviceMode,
    forcedDeviceMode,
    position = 'absolute',
    onSetForcedMode,
}: DeviceModeSwitcherProps) {
    return (
        <>
            <div
                style={{
                    position,
                    left: 12,
                    bottom: 12,
                    background: 'rgba(0,0,0,0.55)',
                    color: '#fff',
                    fontSize: 12,
                    padding: '4px 8px',
                    borderRadius: 6,
                    zIndex: 9999,
                }}
            >
                设备模式: {deviceMode}{forcedDeviceMode ? ' (forced)' : ' (auto)'}
            </div>
            <div
                style={{
                    position,
                    left: 12,
                    top: 12,
                    display: 'flex',
                    gap: 6,
                    zIndex: 9999,
                    background: 'rgba(0,0,0,0.45)',
                    borderRadius: 8,
                    padding: 6,
                }}
            >
                {[
                    { key: 'auto', label: 'Auto' },
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
                            style={{
                                border: active ? '1px solid #38bdf8' : '1px solid rgba(255,255,255,0.35)',
                                background: active ? 'rgba(56,189,248,0.2)' : 'rgba(0,0,0,0.35)',
                                color: '#fff',
                                borderRadius: 6,
                                padding: '2px 8px',
                                fontSize: 12,
                                cursor: 'pointer',
                            }}
                        >
                            {item.label}
                        </button>
                    );
                })}
            </div>
        </>
    );
}

