import { ChevronDown } from 'lucide-react';

interface SectionToggleProps {
    collapsed: boolean;
    label: string;
    onToggle: () => void;
}

export function SectionToggle({ collapsed, label, onToggle }: SectionToggleProps) {
    return (
        <button
            type="button"
            className="property-section-toggle inline-flex items-center gap-1.5 text-[10px] text-text-muted transition-colors duration-200"
            onClick={onToggle}
            aria-expanded={!collapsed}
        >
            <ChevronDown
                size={13}
                strokeWidth={1.9}
                style={{
                    transform: collapsed ? 'rotate(-90deg)' : 'rotate(0deg)',
                    transition: 'transform 0.16s ease',
                }}
                aria-hidden="true"
            />
            <span>{label}</span>
        </button>
    );
}
