import './Loading.css';

export type SpinnerSize = 'sm' | 'md' | 'lg' | 'xl';

export interface SpinnerProps {
  size?: SpinnerSize;
  className?: string;
  label?: string;
}

export function Spinner({ size = 'md', className = '', label }: SpinnerProps) {
  return (
    <div className={`mb-spinner mb-spinner--${size} ${className}`} role="status">
      <svg
        className="mb-spinner__icon"
        viewBox="0 0 24 24"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
      >
        <circle
          cx="12"
          cy="12"
          r="10"
          stroke="currentColor"
          strokeWidth="2.5"
          strokeLinecap="round"
          className="mb-spinner__track"
        />
        <circle
          cx="12"
          cy="12"
          r="10"
          stroke="currentColor"
          strokeWidth="2.5"
          strokeLinecap="round"
          strokeDasharray="32"
          strokeDashoffset="8"
          className="mb-spinner__circle"
        />
      </svg>
      {label && <span className="mb-spinner__label">{label}</span>}
    </div>
  );
}

// Loading Overlay (full page or container)
export interface LoadingOverlayProps {
  visible: boolean;
  label?: string;
  blur?: boolean;
  className?: string;
}

export function LoadingOverlay({
  visible,
  label = 'Loading...',
  blur = false,
  className = '',
}: LoadingOverlayProps) {
  if (!visible) return null;

  return (
    <div
      className={`mb-loading-overlay ${blur ? 'mb-loading-overlay--blur' : ''} ${className}`}
    >
      <div className="mb-loading-overlay__content">
        <Spinner size="lg" label={label} />
      </div>
    </div>
  );
}

// Inline Loading (for buttons, etc.)
export interface InlineLoadingProps {
  label?: string;
  className?: string;
}

export function InlineLoading({ label, className = '' }: InlineLoadingProps) {
  return (
    <span className={`mb-inline-loading ${className}`}>
      <Spinner size="sm" />
      {label && <span className="mb-inline-loading__label">{label}</span>}
    </span>
  );
}
