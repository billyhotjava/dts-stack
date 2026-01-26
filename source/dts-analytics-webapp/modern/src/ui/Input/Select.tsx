import { SelectHTMLAttributes, forwardRef, ReactNode, useId, useState, useRef, useEffect, KeyboardEvent } from 'react';
import './Input.css';

export type SelectSize = 'sm' | 'md' | 'lg';

export interface SelectOption {
  value: string;
  label: string;
  disabled?: boolean;
}

// Native Select
export interface NativeSelectProps extends Omit<SelectHTMLAttributes<HTMLSelectElement>, 'size'> {
  label?: string;
  helperText?: string;
  error?: string;
  size?: SelectSize;
  options: SelectOption[];
  placeholder?: string;
  fullWidth?: boolean;
}

export const NativeSelect = forwardRef<HTMLSelectElement, NativeSelectProps>(
  (
    {
      label,
      helperText,
      error,
      size = 'md',
      options,
      placeholder,
      fullWidth = true,
      className = '',
      id,
      ...props
    },
    ref
  ) => {
    const generatedId = useId();
    const selectId = id || generatedId;
    const hasError = Boolean(error);

    const wrapperClassNames = [
      'mb-input-wrapper',
      fullWidth && 'mb-input-wrapper--full-width',
      className,
    ]
      .filter(Boolean)
      .join(' ');

    const selectClassNames = [
      'mb-select',
      `mb-select--${size}`,
      hasError && 'mb-select--error',
    ]
      .filter(Boolean)
      .join(' ');

    return (
      <div className={wrapperClassNames}>
        {label && (
          <label htmlFor={selectId} className="mb-input__label">
            {label}
          </label>
        )}
        <div className="mb-select__container">
          <select
            ref={ref}
            id={selectId}
            className={selectClassNames}
            aria-invalid={hasError}
            {...props}
          >
            {placeholder && (
              <option value="" disabled>
                {placeholder}
              </option>
            )}
            {options.map((option) => (
              <option key={option.value} value={option.value} disabled={option.disabled}>
                {option.label}
              </option>
            ))}
          </select>
          <span className="mb-select__chevron">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="m6 9 6 6 6-6" />
            </svg>
          </span>
        </div>
        {error && (
          <span className="mb-input__error" role="alert">
            {error}
          </span>
        )}
        {helperText && !error && (
          <span className="mb-input__helper">
            {helperText}
          </span>
        )}
      </div>
    );
  }
);

NativeSelect.displayName = 'NativeSelect';

// Custom Searchable Select
export interface SelectProps {
  label?: string;
  helperText?: string;
  error?: string;
  size?: SelectSize;
  options: SelectOption[];
  value?: string;
  onChange?: (value: string) => void;
  placeholder?: string;
  searchable?: boolean;
  searchPlaceholder?: string;
  disabled?: boolean;
  fullWidth?: boolean;
  className?: string;
}

export function Select({
  label,
  helperText,
  error,
  size = 'md',
  options,
  value,
  onChange,
  placeholder = 'Select...',
  searchable = false,
  searchPlaceholder = 'Search...',
  disabled = false,
  fullWidth = true,
  className = '',
}: SelectProps) {
  const [isOpen, setIsOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [highlightedIndex, setHighlightedIndex] = useState(-1);
  const containerRef = useRef<HTMLDivElement>(null);
  const searchInputRef = useRef<HTMLInputElement>(null);
  const listRef = useRef<HTMLUListElement>(null);
  const buttonId = useId();
  const listId = useId();

  const selectedOption = options.find((opt) => opt.value === value);

  const filteredOptions = searchable && searchQuery
    ? options.filter((opt) =>
        opt.label.toLowerCase().includes(searchQuery.toLowerCase())
      )
    : options;

  const hasError = Boolean(error);

  useEffect(() => {
    if (isOpen && searchable && searchInputRef.current) {
      searchInputRef.current.focus();
    }
  }, [isOpen, searchable]);

  useEffect(() => {
    setHighlightedIndex(-1);
  }, [searchQuery]);

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setIsOpen(false);
        setSearchQuery('');
      }
    };

    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const handleKeyDown = (event: KeyboardEvent) => {
    if (disabled) return;

    switch (event.key) {
      case 'Enter':
      case ' ':
        if (!isOpen) {
          event.preventDefault();
          setIsOpen(true);
        } else if (highlightedIndex >= 0 && filteredOptions[highlightedIndex]) {
          event.preventDefault();
          handleSelect(filteredOptions[highlightedIndex].value);
        }
        break;
      case 'ArrowDown':
        event.preventDefault();
        if (!isOpen) {
          setIsOpen(true);
        } else {
          setHighlightedIndex((prev) =>
            prev < filteredOptions.length - 1 ? prev + 1 : 0
          );
        }
        break;
      case 'ArrowUp':
        event.preventDefault();
        if (isOpen) {
          setHighlightedIndex((prev) =>
            prev > 0 ? prev - 1 : filteredOptions.length - 1
          );
        }
        break;
      case 'Escape':
        setIsOpen(false);
        setSearchQuery('');
        break;
      case 'Tab':
        setIsOpen(false);
        setSearchQuery('');
        break;
    }
  };

  const handleSelect = (optionValue: string) => {
    onChange?.(optionValue);
    setIsOpen(false);
    setSearchQuery('');
  };

  const wrapperClassNames = [
    'mb-input-wrapper',
    fullWidth && 'mb-input-wrapper--full-width',
    className,
  ]
    .filter(Boolean)
    .join(' ');

  const triggerClassNames = [
    'mb-select-trigger',
    `mb-select-trigger--${size}`,
    isOpen && 'mb-select-trigger--open',
    hasError && 'mb-select-trigger--error',
    disabled && 'mb-select-trigger--disabled',
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <div className={wrapperClassNames} ref={containerRef}>
      {label && (
        <label className="mb-input__label" id={`${buttonId}-label`}>
          {label}
        </label>
      )}
      <div className="mb-select-custom">
        <button
          type="button"
          id={buttonId}
          className={triggerClassNames}
          onClick={() => !disabled && setIsOpen(!isOpen)}
          onKeyDown={handleKeyDown}
          aria-haspopup="listbox"
          aria-expanded={isOpen}
          aria-labelledby={label ? `${buttonId}-label` : undefined}
          aria-controls={listId}
          disabled={disabled}
        >
          <span className={`mb-select-trigger__value ${!selectedOption ? 'mb-select-trigger__placeholder' : ''}`}>
            {selectedOption?.label || placeholder}
          </span>
          <span className={`mb-select-trigger__chevron ${isOpen ? 'mb-select-trigger__chevron--open' : ''}`}>
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="m6 9 6 6 6-6" />
            </svg>
          </span>
        </button>

        {isOpen && (
          <div className="mb-select-dropdown">
            {searchable && (
              <div className="mb-select-dropdown__search">
                <input
                  ref={searchInputRef}
                  type="text"
                  className="mb-select-dropdown__search-input"
                  placeholder={searchPlaceholder}
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  onKeyDown={handleKeyDown}
                />
              </div>
            )}
            <ul
              ref={listRef}
              id={listId}
              className="mb-select-dropdown__list"
              role="listbox"
              aria-labelledby={buttonId}
            >
              {filteredOptions.length === 0 ? (
                <li className="mb-select-dropdown__empty">No options found</li>
              ) : (
                filteredOptions.map((option, index) => (
                  <li
                    key={option.value}
                    role="option"
                    aria-selected={option.value === value}
                    className={[
                      'mb-select-dropdown__option',
                      option.value === value && 'mb-select-dropdown__option--selected',
                      index === highlightedIndex && 'mb-select-dropdown__option--highlighted',
                      option.disabled && 'mb-select-dropdown__option--disabled',
                    ]
                      .filter(Boolean)
                      .join(' ')}
                    onClick={() => !option.disabled && handleSelect(option.value)}
                    onMouseEnter={() => setHighlightedIndex(index)}
                  >
                    {option.label}
                    {option.value === value && (
                      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                        <polyline points="20 6 9 17 4 12" />
                      </svg>
                    )}
                  </li>
                ))
              )}
            </ul>
          </div>
        )}
      </div>
      {error && (
        <span className="mb-input__error" role="alert">
          {error}
        </span>
      )}
      {helperText && !error && (
        <span className="mb-input__helper">
          {helperText}
        </span>
      )}
    </div>
  );
}
