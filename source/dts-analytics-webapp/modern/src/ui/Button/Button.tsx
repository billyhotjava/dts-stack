import { ButtonHTMLAttributes, forwardRef, ReactNode } from 'react';
import './Button.css';

export type ButtonVariant = 'primary' | 'secondary' | 'tertiary' | 'danger' | 'success';
export type ButtonSize = 'sm' | 'md' | 'lg';

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  loading?: boolean;
  icon?: ReactNode;
  iconPosition?: 'left' | 'right';
  fullWidth?: boolean;
  children?: ReactNode;
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  (
    {
      variant = 'secondary',
      size = 'md',
      loading = false,
      icon,
      iconPosition = 'left',
      fullWidth = false,
      disabled,
      className = '',
      children,
      ...props
    },
    ref
  ) => {
    const isDisabled = disabled || loading;

    const classNames = [
      'mb-button',
      `mb-button--${variant}`,
      `mb-button--${size}`,
      fullWidth && 'mb-button--full-width',
      loading && 'mb-button--loading',
      !children && icon && 'mb-button--icon-only',
      className,
    ]
      .filter(Boolean)
      .join(' ');

    return (
      <button
        ref={ref}
        className={classNames}
        disabled={isDisabled}
        {...props}
      >
        {loading && (
          <span className="mb-button__spinner">
            <svg
              width="16"
              height="16"
              viewBox="0 0 16 16"
              fill="none"
              className="mb-button__spinner-icon"
            >
              <circle
                cx="8"
                cy="8"
                r="6"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
                strokeDasharray="32"
                strokeDashoffset="8"
              />
            </svg>
          </span>
        )}
        {!loading && icon && iconPosition === 'left' && (
          <span className="mb-button__icon">{icon}</span>
        )}
        {children && <span className="mb-button__label">{children}</span>}
        {!loading && icon && iconPosition === 'right' && (
          <span className="mb-button__icon">{icon}</span>
        )}
      </button>
    );
  }
);

Button.displayName = 'Button';

// Icon Button variant
export interface IconButtonProps extends Omit<ButtonProps, 'children' | 'icon' | 'iconPosition'> {
  icon: ReactNode;
  'aria-label': string;
}

export const IconButton = forwardRef<HTMLButtonElement, IconButtonProps>(
  ({ icon, className = '', ...props }, ref) => {
    return (
      <Button
        ref={ref}
        icon={icon}
        className={`mb-icon-button ${className}`}
        {...props}
      />
    );
  }
);

IconButton.displayName = 'IconButton';

// Button Group
export interface ButtonGroupProps {
  children: ReactNode;
  className?: string;
}

export function ButtonGroup({ children, className = '' }: ButtonGroupProps) {
  return <div className={`mb-button-group ${className}`}>{children}</div>;
}
