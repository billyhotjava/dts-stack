import { HTMLAttributes, forwardRef, ReactNode, useState } from 'react';
import './Card.css';

export interface CardProps extends HTMLAttributes<HTMLDivElement> {
  variant?: 'default' | 'hoverable' | 'collapsible';
  padding?: 'none' | 'sm' | 'md' | 'lg';
  shadow?: 'none' | 'sm' | 'md' | 'lg';
  children?: ReactNode;
}

export const Card = forwardRef<HTMLDivElement, CardProps>(
  (
    {
      variant = 'default',
      padding = 'md',
      shadow = 'sm',
      className = '',
      children,
      ...props
    },
    ref
  ) => {
    const classNames = [
      'mb-card',
      `mb-card--${variant}`,
      `mb-card--padding-${padding}`,
      `mb-card--shadow-${shadow}`,
      className,
    ]
      .filter(Boolean)
      .join(' ');

    return (
      <div ref={ref} className={classNames} {...props}>
        {children}
      </div>
    );
  }
);

Card.displayName = 'Card';

// Card Header
export interface CardHeaderProps extends Omit<HTMLAttributes<HTMLDivElement>, 'title'> {
  title?: ReactNode;
  subtitle?: ReactNode;
  action?: ReactNode;
  icon?: ReactNode;
}

export function CardHeader({
  title,
  subtitle,
  action,
  icon,
  className = '',
  children,
  ...props
}: CardHeaderProps) {
  return (
    <div className={`mb-card__header ${className}`} {...props}>
      {icon && <div className="mb-card__header-icon">{icon}</div>}
      <div className="mb-card__header-content">
        {title && <h3 className="mb-card__title">{title}</h3>}
        {subtitle && <p className="mb-card__subtitle">{subtitle}</p>}
        {children}
      </div>
      {action && <div className="mb-card__header-action">{action}</div>}
    </div>
  );
}

// Card Body
export interface CardBodyProps extends HTMLAttributes<HTMLDivElement> {
  children?: ReactNode;
}

export function CardBody({ className = '', children, ...props }: CardBodyProps) {
  return (
    <div className={`mb-card__body ${className}`} {...props}>
      {children}
    </div>
  );
}

// Card Footer
export interface CardFooterProps extends HTMLAttributes<HTMLDivElement> {
  children?: ReactNode;
  align?: 'left' | 'center' | 'right' | 'between';
}

export function CardFooter({
  align = 'right',
  className = '',
  children,
  ...props
}: CardFooterProps) {
  return (
    <div
      className={`mb-card__footer mb-card__footer--${align} ${className}`}
      {...props}
    >
      {children}
    </div>
  );
}

// Collapsible Card
export interface CollapsibleCardProps extends Omit<CardProps, 'variant' | 'title' | 'onToggle'> {
  title: ReactNode;
  subtitle?: ReactNode;
  icon?: ReactNode;
  action?: ReactNode;
  defaultOpen?: boolean;
  onToggle?: (isOpen: boolean) => void;
}

export function CollapsibleCard({
  title,
  subtitle,
  icon,
  action,
  defaultOpen = true,
  onToggle,
  children,
  className = '',
  ...props
}: CollapsibleCardProps) {
  const [isOpen, setIsOpen] = useState(defaultOpen);

  const handleToggle = () => {
    const newState = !isOpen;
    setIsOpen(newState);
    onToggle?.(newState);
  };

  return (
    <Card variant="collapsible" className={className} {...props}>
      <button
        type="button"
        className={`mb-card__collapse-trigger ${isOpen ? 'mb-card__collapse-trigger--open' : ''}`}
        onClick={handleToggle}
        aria-expanded={isOpen}
      >
        {icon && <div className="mb-card__header-icon">{icon}</div>}
        <div className="mb-card__header-content">
          <h3 className="mb-card__title">{title}</h3>
          {subtitle && <p className="mb-card__subtitle">{subtitle}</p>}
        </div>
        <span className="mb-card__collapse-chevron">
          <svg
            width="16"
            height="16"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
          >
            <path d="m6 9 6 6 6-6" />
          </svg>
        </span>
      </button>
      {action && <div className="mb-card__collapse-action">{action}</div>}
      <div className={`mb-card__collapse-content ${isOpen ? 'mb-card__collapse-content--open' : ''}`}>
        {children}
      </div>
    </Card>
  );
}

// Stat Card
export interface StatCardProps extends Omit<CardProps, 'children'> {
  label: string;
  value: string | number;
  change?: string;
  changeType?: 'positive' | 'negative' | 'neutral';
  icon?: ReactNode;
}

export function StatCard({
  label,
  value,
  change,
  changeType = 'neutral',
  icon,
  className = '',
  ...props
}: StatCardProps) {
  return (
    <Card className={`mb-stat-card ${className}`} {...props}>
      <div className="mb-stat-card__content">
        <span className="mb-stat-card__label">{label}</span>
        <span className="mb-stat-card__value">{value}</span>
        {change && (
          <span className={`mb-stat-card__change mb-stat-card__change--${changeType}`}>
            {changeType === 'positive' && (
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="m18 15-6-6-6 6" />
              </svg>
            )}
            {changeType === 'negative' && (
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="m6 9 6 6 6-6" />
              </svg>
            )}
            {change}
          </span>
        )}
      </div>
      {icon && <div className="mb-stat-card__icon">{icon}</div>}
    </Card>
  );
}
