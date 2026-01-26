import { InputHTMLAttributes, forwardRef, ReactNode, useId } from 'react';
import './Input.css';

export type CheckboxSize = 'sm' | 'md' | 'lg';

export interface CheckboxProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'type' | 'size'> {
  label?: ReactNode;
  description?: string;
  size?: CheckboxSize;
  indeterminate?: boolean;
}

export const Checkbox = forwardRef<HTMLInputElement, CheckboxProps>(
  (
    {
      label,
      description,
      size = 'md',
      indeterminate = false,
      className = '',
      id,
      disabled,
      ...props
    },
    ref
  ) => {
    const generatedId = useId();
    const checkboxId = id || generatedId;

    const wrapperClassNames = [
      'mb-checkbox-wrapper',
      `mb-checkbox-wrapper--${size}`,
      disabled && 'mb-checkbox-wrapper--disabled',
      className,
    ]
      .filter(Boolean)
      .join(' ');

    return (
      <div className={wrapperClassNames}>
        <div className="mb-checkbox">
          <input
            ref={(element) => {
              if (element) {
                element.indeterminate = indeterminate;
              }
              if (typeof ref === 'function') {
                ref(element);
              } else if (ref) {
                ref.current = element;
              }
            }}
            type="checkbox"
            id={checkboxId}
            className="mb-checkbox__input"
            disabled={disabled}
            {...props}
          />
          <span className="mb-checkbox__box">
            {indeterminate ? (
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round">
                <line x1="5" y1="12" x2="19" y2="12" />
              </svg>
            ) : (
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round">
                <polyline points="20 6 9 17 4 12" />
              </svg>
            )}
          </span>
        </div>
        {(label || description) && (
          <div className="mb-checkbox__content">
            {label && (
              <label htmlFor={checkboxId} className="mb-checkbox__label">
                {label}
              </label>
            )}
            {description && (
              <span className="mb-checkbox__description">{description}</span>
            )}
          </div>
        )}
      </div>
    );
  }
);

Checkbox.displayName = 'Checkbox';

// Radio Button
export interface RadioProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'type' | 'size'> {
  label?: ReactNode;
  description?: string;
  size?: CheckboxSize;
}

export const Radio = forwardRef<HTMLInputElement, RadioProps>(
  (
    {
      label,
      description,
      size = 'md',
      className = '',
      id,
      disabled,
      ...props
    },
    ref
  ) => {
    const generatedId = useId();
    const radioId = id || generatedId;

    const wrapperClassNames = [
      'mb-radio-wrapper',
      `mb-radio-wrapper--${size}`,
      disabled && 'mb-radio-wrapper--disabled',
      className,
    ]
      .filter(Boolean)
      .join(' ');

    return (
      <div className={wrapperClassNames}>
        <div className="mb-radio">
          <input
            ref={ref}
            type="radio"
            id={radioId}
            className="mb-radio__input"
            disabled={disabled}
            {...props}
          />
          <span className="mb-radio__circle">
            <span className="mb-radio__dot" />
          </span>
        </div>
        {(label || description) && (
          <div className="mb-radio__content">
            {label && (
              <label htmlFor={radioId} className="mb-radio__label">
                {label}
              </label>
            )}
            {description && (
              <span className="mb-radio__description">{description}</span>
            )}
          </div>
        )}
      </div>
    );
  }
);

Radio.displayName = 'Radio';

// Toggle Switch
export interface ToggleProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'type' | 'size'> {
  label?: ReactNode;
  size?: CheckboxSize;
}

export const Toggle = forwardRef<HTMLInputElement, ToggleProps>(
  (
    {
      label,
      size = 'md',
      className = '',
      id,
      disabled,
      ...props
    },
    ref
  ) => {
    const generatedId = useId();
    const toggleId = id || generatedId;

    const wrapperClassNames = [
      'mb-toggle-wrapper',
      `mb-toggle-wrapper--${size}`,
      disabled && 'mb-toggle-wrapper--disabled',
      className,
    ]
      .filter(Boolean)
      .join(' ');

    return (
      <label htmlFor={toggleId} className={wrapperClassNames}>
        <input
          ref={ref}
          type="checkbox"
          id={toggleId}
          className="mb-toggle__input"
          disabled={disabled}
          {...props}
        />
        <span className="mb-toggle__track">
          <span className="mb-toggle__thumb" />
        </span>
        {label && <span className="mb-toggle__label">{label}</span>}
      </label>
    );
  }
);

Toggle.displayName = 'Toggle';
