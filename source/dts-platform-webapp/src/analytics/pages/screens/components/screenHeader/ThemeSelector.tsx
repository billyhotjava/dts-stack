import type { ChangeEvent } from 'react';
import type { ScreenTheme } from '../../types';
import { THEME_OPTIONS } from './helpers';

interface ThemeSelectorProps {
    value: ScreenTheme;
    onChange: (e: ChangeEvent<HTMLSelectElement>) => void;
}

export function ThemeSelector({ value, onChange }: ThemeSelectorProps) {
    return (
        <select
            className="px-2.5 py-[7px] border border-[var(--color-border)] rounded-md bg-[var(--color-surface)] text-[var(--color-text-primary)] text-[13px] font-medium min-w-[110px] w-full focus:outline-none focus:border-[var(--color-primary)]"
            value={value}
            onChange={onChange}
            title="切换主题"
        >
            {THEME_OPTIONS.map((opt) => (
                <option key={opt.value} value={opt.value}>{opt.label}</option>
            ))}
        </select>
    );
}
