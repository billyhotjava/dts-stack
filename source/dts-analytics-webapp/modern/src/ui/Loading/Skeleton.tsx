import './Loading.css';

export interface SkeletonProps {
  width?: string | number;
  height?: string | number;
  variant?: 'text' | 'circular' | 'rectangular';
  animation?: 'pulse' | 'wave' | 'none';
  className?: string;
}

export function Skeleton({
  width,
  height,
  variant = 'text',
  animation = 'pulse',
  className = '',
}: SkeletonProps) {
  const style: React.CSSProperties = {
    width: typeof width === 'number' ? `${width}px` : width,
    height: typeof height === 'number' ? `${height}px` : height,
  };

  const classNames = [
    'mb-skeleton',
    `mb-skeleton--${variant}`,
    `mb-skeleton--${animation}`,
    className,
  ]
    .filter(Boolean)
    .join(' ');

  return <div className={classNames} style={style} />;
}

// Card Skeleton
export interface CardSkeletonProps {
  hasImage?: boolean;
  lines?: number;
  className?: string;
}

export function CardSkeleton({
  hasImage = false,
  lines = 3,
  className = '',
}: CardSkeletonProps) {
  return (
    <div className={`mb-skeleton-card ${className}`}>
      {hasImage && <Skeleton variant="rectangular" height={160} className="mb-skeleton-card__image" />}
      <div className="mb-skeleton-card__content">
        <Skeleton variant="text" width="60%" height={24} />
        <div className="mb-skeleton-card__lines">
          {Array.from({ length: lines }).map((_, i) => (
            <Skeleton
              key={i}
              variant="text"
              width={i === lines - 1 ? '80%' : '100%'}
              height={16}
            />
          ))}
        </div>
      </div>
    </div>
  );
}

// Table Skeleton
export interface TableSkeletonProps {
  rows?: number;
  columns?: number;
  hasHeader?: boolean;
  className?: string;
}

export function TableSkeleton({
  rows = 5,
  columns = 4,
  hasHeader = true,
  className = '',
}: TableSkeletonProps) {
  return (
    <div className={`mb-skeleton-table ${className}`}>
      {hasHeader && (
        <div className="mb-skeleton-table__header">
          {Array.from({ length: columns }).map((_, i) => (
            <Skeleton key={i} variant="text" height={16} />
          ))}
        </div>
      )}
      <div className="mb-skeleton-table__body">
        {Array.from({ length: rows }).map((_, rowIndex) => (
          <div key={rowIndex} className="mb-skeleton-table__row">
            {Array.from({ length: columns }).map((_, colIndex) => (
              <Skeleton key={colIndex} variant="text" height={16} />
            ))}
          </div>
        ))}
      </div>
    </div>
  );
}

// List Skeleton
export interface ListSkeletonProps {
  items?: number;
  hasAvatar?: boolean;
  hasAction?: boolean;
  className?: string;
}

export function ListSkeleton({
  items = 5,
  hasAvatar = false,
  hasAction = false,
  className = '',
}: ListSkeletonProps) {
  return (
    <div className={`mb-skeleton-list ${className}`}>
      {Array.from({ length: items }).map((_, i) => (
        <div key={i} className="mb-skeleton-list__item">
          {hasAvatar && <Skeleton variant="circular" width={40} height={40} />}
          <div className="mb-skeleton-list__content">
            <Skeleton variant="text" width="40%" height={16} />
            <Skeleton variant="text" width="70%" height={14} />
          </div>
          {hasAction && <Skeleton variant="rectangular" width={80} height={32} />}
        </div>
      ))}
    </div>
  );
}

// Chart Skeleton
export interface ChartSkeletonProps {
  type?: 'bar' | 'line' | 'pie';
  className?: string;
}

export function ChartSkeleton({ type = 'bar', className = '' }: ChartSkeletonProps) {
  return (
    <div className={`mb-skeleton-chart mb-skeleton-chart--${type} ${className}`}>
      {type === 'bar' && (
        <div className="mb-skeleton-chart__bars">
          {Array.from({ length: 6 }).map((_, i) => (
            <Skeleton
              key={i}
              variant="rectangular"
              width="12%"
              height={`${Math.random() * 60 + 20}%`}
            />
          ))}
        </div>
      )}
      {type === 'line' && (
        <div className="mb-skeleton-chart__line">
          <Skeleton variant="rectangular" height="100%" animation="wave" />
        </div>
      )}
      {type === 'pie' && (
        <div className="mb-skeleton-chart__pie">
          <Skeleton variant="circular" width={200} height={200} />
        </div>
      )}
    </div>
  );
}
