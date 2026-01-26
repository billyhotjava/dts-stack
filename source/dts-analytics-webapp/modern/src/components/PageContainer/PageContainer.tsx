import { ReactNode } from 'react';
import './PageContainer.css';

export interface PageContainerProps {
  children: ReactNode;
  className?: string;
  maxWidth?: 'sm' | 'md' | 'lg' | 'xl' | 'full';
  padding?: 'none' | 'sm' | 'md' | 'lg';
}

export function PageContainer({
  children,
  className = '',
  maxWidth = 'lg',
  padding = 'lg',
}: PageContainerProps) {
  const classNames = [
    'mb-page-container',
    `mb-page-container--max-${maxWidth}`,
    `mb-page-container--padding-${padding}`,
    className,
  ]
    .filter(Boolean)
    .join(' ');

  return <div className={classNames}>{children}</div>;
}

// Page Header
export interface PageHeaderProps {
  title: ReactNode;
  subtitle?: ReactNode;
  breadcrumbs?: ReactNode;
  actions?: ReactNode;
  className?: string;
}

export function PageHeader({
  title,
  subtitle,
  breadcrumbs,
  actions,
  className = '',
}: PageHeaderProps) {
  return (
    <div className={`mb-page-header ${className}`}>
      {breadcrumbs && <div className="mb-page-header__breadcrumbs">{breadcrumbs}</div>}
      <div className="mb-page-header__content">
        <div className="mb-page-header__titles">
          <h1 className="mb-page-header__title">{title}</h1>
          {subtitle && <p className="mb-page-header__subtitle">{subtitle}</p>}
        </div>
        {actions && <div className="mb-page-header__actions">{actions}</div>}
      </div>
    </div>
  );
}

// Page Section
export interface PageSectionProps {
  title?: ReactNode;
  description?: ReactNode;
  actions?: ReactNode;
  children: ReactNode;
  className?: string;
}

export function PageSection({
  title,
  description,
  actions,
  children,
  className = '',
}: PageSectionProps) {
  return (
    <section className={`mb-page-section ${className}`}>
      {(title || actions) && (
        <div className="mb-page-section__header">
          <div className="mb-page-section__titles">
            {title && <h2 className="mb-page-section__title">{title}</h2>}
            {description && <p className="mb-page-section__description">{description}</p>}
          </div>
          {actions && <div className="mb-page-section__actions">{actions}</div>}
        </div>
      )}
      <div className="mb-page-section__content">{children}</div>
    </section>
  );
}

// Breadcrumb
export interface BreadcrumbItem {
  label: string;
  href?: string;
  onClick?: () => void;
}

export interface BreadcrumbProps {
  items: BreadcrumbItem[];
  className?: string;
}

export function Breadcrumb({ items, className = '' }: BreadcrumbProps) {
  return (
    <nav className={`mb-breadcrumb ${className}`} aria-label="Breadcrumb">
      <ol className="mb-breadcrumb__list">
        {items.map((item, index) => {
          const isLast = index === items.length - 1;
          return (
            <li key={index} className="mb-breadcrumb__item">
              {!isLast && item.href ? (
                <a href={item.href} className="mb-breadcrumb__link" onClick={item.onClick}>
                  {item.label}
                </a>
              ) : !isLast && item.onClick ? (
                <button
                  type="button"
                  className="mb-breadcrumb__link mb-breadcrumb__link--button"
                  onClick={item.onClick}
                >
                  {item.label}
                </button>
              ) : (
                <span className={`mb-breadcrumb__text ${isLast ? 'mb-breadcrumb__text--current' : ''}`}>
                  {item.label}
                </span>
              )}
              {!isLast && (
                <span className="mb-breadcrumb__separator" aria-hidden="true">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <path d="m9 18 6-6-6-6" />
                  </svg>
                </span>
              )}
            </li>
          );
        })}
      </ol>
    </nav>
  );
}

// Empty State
export interface EmptyStateProps {
  icon?: ReactNode;
  title: string;
  description?: string;
  action?: ReactNode;
  className?: string;
}

export function EmptyState({
  icon,
  title,
  description,
  action,
  className = '',
}: EmptyStateProps) {
  return (
    <div className={`mb-empty-state ${className}`}>
      {icon && <div className="mb-empty-state__icon">{icon}</div>}
      <h3 className="mb-empty-state__title">{title}</h3>
      {description && <p className="mb-empty-state__description">{description}</p>}
      {action && <div className="mb-empty-state__action">{action}</div>}
    </div>
  );
}
