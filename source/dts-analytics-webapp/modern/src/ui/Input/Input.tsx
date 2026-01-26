import { InputHTMLAttributes, forwardRef, ReactNode, useId } from 'react';
import './Input.css';

export type InputSize = 'sm' | 'md' | 'lg';

export interface InputProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'size'> {
  label?: string;
  helperText?: string;
  error?: string;
  size?: InputSize;
  icon?: ReactNode;
  iconPosition?: 'left' | 'right';
  fullWidth?: boolean;
}

export const Input = forwardRef<HTMLInputElement, InputProps>(
  (
    {
      label,
      helperText,
      error,
      size = 'md',
      icon,
      iconPosition = 'left',
      fullWidth = true,
      className = '',
      id,
      ...props
    },
    ref
  ) => {
    const generatedId = useId();
    const inputId = id || generatedId;
    const hasError = Boolean(error);

    const wrapperClassNames = [
      'mb-input-wrapper',
      fullWidth && 'mb-input-wrapper--full-width',
      className,
    ]
      .filter(Boolean)
      .join(' ');

    const inputClassNames = [
      'mb-input',
      `mb-input--${size}`,
      hasError && 'mb-input--error',
      icon && `mb-input--with-icon-${iconPosition}`,
    ]
      .filter(Boolean)
      .join(' ');

    return (
      <div className={wrapperClassNames}>
        {label && (
          <label htmlFor={inputId} className="mb-input__label">
            {label}
          </label>
        )}
        <div className="mb-input__container">
          {icon && iconPosition === 'left' && (
            <span className="mb-input__icon mb-input__icon--left">{icon}</span>
          )}
          <input
            ref={ref}
            id={inputId}
            className={inputClassNames}
            aria-invalid={hasError}
            aria-describedby={error ? `${inputId}-error` : helperText ? `${inputId}-helper` : undefined}
            {...props}
          />
          {icon && iconPosition === 'right' && (
            <span className="mb-input__icon mb-input__icon--right">{icon}</span>
          )}
        </div>
        {error && (
          <span id={`${inputId}-error`} className="mb-input__error" role="alert">
            {error}
          </span>
        )}
        {helperText && !error && (
          <span id={`${inputId}-helper`} className="mb-input__helper">
            {helperText}
          </span>
        )}
      </div>
    );
  }
);

Input.displayName = 'Input';

// Search Input variant
export interface SearchInputProps extends Omit<InputProps, 'icon' | 'iconPosition' | 'type'> {
  onClear?: () => void;
}

export const SearchInput = forwardRef<HTMLInputElement, SearchInputProps>(
  ({ onClear, value, className = '', ...props }, ref) => {
    const hasValue = value !== undefined && value !== '';

    return (
      <div className={`mb-search-input ${className}`}>
        <Input
          ref={ref}
          type="search"
          value={value}
          icon={
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <circle cx="11" cy="11" r="8" />
              <path d="m21 21-4.35-4.35" />
            </svg>
          }
          iconPosition="left"
          {...props}
        />
        {hasValue && onClear && (
          <button
            type="button"
            className="mb-search-input__clear"
            onClick={onClear}
            aria-label="Clear search"
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M18 6 6 18" />
              <path d="m6 6 12 12" />
            </svg>
          </button>
        )}
      </div>
    );
  }
);

SearchInput.displayName = 'SearchInput';
