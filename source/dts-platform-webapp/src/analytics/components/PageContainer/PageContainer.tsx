import { ReactNode } from 'react';

export interface PageContainerProps {
	children: ReactNode;
	className?: string;
	maxWidth?: 'sm' | 'md' | 'lg' | 'xl' | 'full';
	padding?: 'none' | 'sm' | 'md' | 'lg';
}

const maxWidthMap = {
	sm: 'max-w-screen-sm',
	md: 'max-w-screen-md',
	lg: 'max-w-[var(--page-max-width)]',
	xl: 'max-w-[1400px]',
	full: 'max-w-full',
} as const;

const paddingMap = {
	none: 'p-0',
	sm: 'p-[var(--spacing-md)] md:p-[var(--spacing-md)]',
	md: 'p-[var(--spacing-md)] md:p-[var(--spacing-lg)]',
	lg: 'p-[var(--spacing-md)] md:p-[var(--spacing-xl)]',
} as const;

export function PageContainer({
	children,
	className = '',
	maxWidth = 'lg',
	padding = 'lg',
}: PageContainerProps) {
	return (
		<div className={`w-full mx-auto ${maxWidthMap[maxWidth]} ${paddingMap[padding]} ${className}`.trim()}>
			{children}
		</div>
	);
}

// Page Header
export interface PageHeaderProps {
	title: ReactNode;
	breadcrumbs?: ReactNode;
	actions?: ReactNode;
	className?: string;
}

export function PageHeader({
	title,
	breadcrumbs,
	actions,
	className = '',
}: PageHeaderProps) {
	return (
		<div className={`mb-[var(--spacing-lg)] ${className}`.trim()}>
			{breadcrumbs && <div className="mb-[var(--spacing-sm)]">{breadcrumbs}</div>}
			<div className="flex items-center justify-between gap-[var(--spacing-lg)] flex-wrap">
				<div className="flex-1 min-w-0">
					<h1 className="text-[length:var(--font-size-xl)] font-semibold text-text-primary m-0 leading-tight">
						{title}
					</h1>
				</div>
				{actions && (
					<div className="flex items-center gap-[var(--spacing-sm)] shrink-0">
						{actions}
					</div>
				)}
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
		<section className={`mb-[var(--spacing-xl)] last:mb-0 ${className}`.trim()}>
			{(title || actions) && (
				<div className="flex items-start justify-between gap-[var(--spacing-md)] mb-[var(--spacing-md)]">
					<div className="flex-1 min-w-0">
						{title && (
							<h2 className="text-[length:var(--font-size-lg)] font-semibold text-text-primary m-0">
								{title}
							</h2>
						)}
						{description && (
							<p className="text-[length:var(--font-size-sm)] text-text-secondary mt-[var(--spacing-xs)] mb-0">
								{description}
							</p>
						)}
					</div>
					{actions && (
						<div className="flex items-center gap-[var(--spacing-sm)] shrink-0">
							{actions}
						</div>
					)}
				</div>
			)}
			<div>{children}</div>
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
		<nav className={`text-[length:var(--font-size-sm)] ${className}`.trim()} aria-label="Breadcrumb">
			<ol className="flex items-center flex-wrap gap-[var(--spacing-xs)] list-none m-0 p-0">
				{items.map((item, index) => {
					const isLast = index === items.length - 1;
					return (
						<li key={index} className="flex items-center gap-[var(--spacing-xs)]">
							{!isLast && item.href ? (
								<a
									href={item.href}
									className="text-text-secondary no-underline transition-colors duration-100 hover:text-brand"
									onClick={item.onClick}
								>
									{item.label}
								</a>
							) : !isLast && item.onClick ? (
								<button
									type="button"
									className="p-0 border-none bg-transparent font-[inherit] cursor-pointer text-text-secondary transition-colors duration-100 hover:text-brand"
									onClick={item.onClick}
								>
									{item.label}
								</button>
							) : (
								<span className={isLast ? 'text-text-primary font-medium' : 'text-text-secondary'}>
									{item.label}
								</span>
							)}
							{!isLast && (
								<span className="flex items-center text-text-muted" aria-hidden="true">
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
		<div className={`flex flex-col items-center justify-center p-[var(--spacing-2xl)] text-center ${className}`.trim()}>
			{icon && (
				<div className="flex items-center justify-center w-16 h-16 mb-[var(--spacing-md)] rounded-full bg-surface-muted text-text-muted [&_svg]:w-8 [&_svg]:h-8">
					{icon}
				</div>
			)}
			<h3 className="text-[length:var(--font-size-lg)] font-semibold text-text-primary m-0 mb-[var(--spacing-xs)]">
				{title}
			</h3>
			{description && (
				<p className="text-[length:var(--font-size-md)] text-text-secondary m-0 mb-[var(--spacing-lg)] max-w-[400px]">
					{description}
				</p>
			)}
			{action && (
				<div className="flex items-center gap-[var(--spacing-sm)]">
					{action}
				</div>
			)}
		</div>
	);
}
