import { TextareaHTMLAttributes, forwardRef, useId } from 'react';
import './Input.css';

export type TextAreaSize = 'sm' | 'md' | 'lg';

export interface TextAreaProps extends Omit<TextareaHTMLAttributes<HTMLTextAreaElement>, 'size'> {
  label?: string;
  helperText?: string;
  error?: string;
  size?: TextAreaSize;
  fullWidth?: boolean;
  resize?: 'none' | 'vertical' | 'horizontal' | 'both';
}

export const TextArea = forwardRef<HTMLTextAreaElement, TextAreaProps>(
  (
    {
      label,
      helperText,
      error,
      size = 'md',
      fullWidth = true,
      resize = 'vertical',
      className = '',
      id,
      rows = 4,
      ...props
    },
    ref
  ) => {
    const generatedId = useId();
    const textareaId = id || generatedId;
    const hasError = Boolean(error);

    const wrapperClassNames = [
      'mb-input-wrapper',
      fullWidth && 'mb-input-wrapper--full-width',
      className,
    ]
      .filter(Boolean)
      .join(' ');

    const textareaClassNames = [
      'mb-textarea',
      `mb-textarea--${size}`,
      hasError && 'mb-textarea--error',
      `mb-textarea--resize-${resize}`,
    ]
      .filter(Boolean)
      .join(' ');

    return (
      <div className={wrapperClassNames}>
        {label && (
          <label htmlFor={textareaId} className="mb-input__label">
            {label}
          </label>
        )}
        <textarea
          ref={ref}
          id={textareaId}
          className={textareaClassNames}
          rows={rows}
          aria-invalid={hasError}
          aria-describedby={error ? `${textareaId}-error` : helperText ? `${textareaId}-helper` : undefined}
          {...props}
        />
        {error && (
          <span id={`${textareaId}-error`} className="mb-input__error" role="alert">
            {error}
          </span>
        )}
        {helperText && !error && (
          <span id={`${textareaId}-helper`} className="mb-input__helper">
            {helperText}
          </span>
        )}
      </div>
    );
  }
);

TextArea.displayName = 'TextArea';
