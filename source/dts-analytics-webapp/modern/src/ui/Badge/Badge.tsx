import { HTMLAttributes, forwardRef, ReactNode } from 'react';
import './Badge.css';

export type BadgeVariant = 'default' | 'primary' | 'success' | 'warning' | 'error' | 'info';
export type BadgeSize = 'sm' | 'md' | 'lg';

export interface BadgeProps extends HTMLAttributes<HTMLSpanElement> {
  variant?: BadgeVariant;
  size?: BadgeSize;
  icon?: ReactNode;
  removable?: boolean;
  onRemove?: () => void;
  children?: ReactNode;
}

export const Badge = forwardRef<HTMLSpanElement, BadgeProps>(
  (
    {
      variant = 'default',
      size = 'md',
      icon,
      removable = false,
      onRemove,
      className = '',
      children,
      ...props
    },
    ref
  ) => {
    const classNames = [
      'mb-badge',
      `mb-badge--${variant}`,
      `mb-badge--${size}`,
      removable && 'mb-badge--removable',
      className,
    ]
      .filter(Boolean)
      .join(' ');

    return (
      <span ref={ref} className={classNames} {...props}>
        {icon && <span className="mb-badge__icon">{icon}</span>}
        {children && <span className="mb-badge__label">{children}</span>}
        {removable && (
          <button
            type="button"
            className="mb-badge__remove"
            onClick={onRemove}
            aria-label="Remove"
          >
            <svg
              width="12"
              height="12"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
            >
              <path d="M18 6 6 18" />
              <path d="m6 6 12 12" />
            </svg>
          </button>
        )}
      </span>
    );
  }
);

Badge.displayName = 'Badge';

// Status Dot (for indicating status)
export type StatusDotVariant = 'success' | 'warning' | 'error' | 'info' | 'neutral';

export interface StatusDotProps {
  variant?: StatusDotVariant;
  label?: string;
  className?: string;
  pulse?: boolean;
}

export function StatusDot({
  variant = 'neutral',
  label,
  className = '',
  pulse = false,
}: StatusDotProps) {
  return (
    <span className={`mb-status-dot ${className}`}>
      <span
        className={`mb-status-dot__dot mb-status-dot__dot--${variant} ${pulse ? 'mb-status-dot__dot--pulse' : ''}`}
      />
      {label && <span className="mb-status-dot__label">{label}</span>}
    </span>
  );
}

// Count Badge (for notifications)
export interface CountBadgeProps {
  count: number;
  max?: number;
  variant?: 'default' | 'primary' | 'error';
  className?: string;
}

export function CountBadge({
  count,
  max = 99,
  variant = 'error',
  className = '',
}: CountBadgeProps) {
  if (count <= 0) return null;

  const displayCount = count > max ? `${max}+` : count;

  return (
    <span className={`mb-count-badge mb-count-badge--${variant} ${className}`}>
      {displayCount}
    </span>
  );
}
