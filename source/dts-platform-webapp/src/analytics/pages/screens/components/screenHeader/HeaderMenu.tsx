import type { ReactNode } from 'react';

export function HeaderMenu({
    label,
    open,
    onToggle,
    children,
}: {
    label: string;
    open: boolean;
    onToggle: () => void;
    children: ReactNode;
}) {
    return (
        <div className="relative">
            <button
                type="button"
                className={`flex items-center gap-1.5 px-4 py-2 border border-[var(--color-border)] rounded-md bg-[var(--color-surface)] text-[var(--color-text-primary)] text-[13px] font-medium cursor-pointer whitespace-nowrap shrink-0 transition-all duration-200 min-w-16 justify-center hover:border-[var(--color-primary)] hover:bg-[var(--color-primary-light)] ${open ? 'border-[var(--color-primary)] bg-[var(--color-primary-light)]' : ''}`}
                aria-expanded={open}
                onClick={onToggle}
            >
                {label}
            </button>
            {open ? (
                <div className="absolute top-[calc(100%+6px)] right-0 z-[1600] min-w-[220px] w-[280px] max-w-[min(86vw,320px)] max-h-[min(72vh,560px)] overflow-y-auto bg-[var(--color-surface-secondary)] border border-[var(--color-border)] rounded-lg shadow-[0_10px_30px_rgba(2,6,23,0.28)] text-[var(--color-text-primary)] p-2 grid gap-1.5">
                    {children}
                </div>
            ) : null}
        </div>
    );
}
