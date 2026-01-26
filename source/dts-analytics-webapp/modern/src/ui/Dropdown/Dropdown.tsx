import { HTMLAttributes, ReactNode, useState, useRef, useEffect, useId } from 'react';
import './Dropdown.css';

export type DropdownPlacement = 'bottom-start' | 'bottom-end' | 'top-start' | 'top-end';

export interface DropdownProps {
  trigger: ReactNode;
  children: ReactNode;
  placement?: DropdownPlacement;
  className?: string;
  closeOnSelect?: boolean;
  disabled?: boolean;
}

export function Dropdown({
  trigger,
  children,
  placement = 'bottom-start',
  className = '',
  closeOnSelect = true,
  disabled = false,
}: DropdownProps) {
  const [isOpen, setIsOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);
  const triggerId = useId();
  const menuId = useId();

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    };

    const handleEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setIsOpen(false);
      }
    };

    document.addEventListener('mousedown', handleClickOutside);
    document.addEventListener('keydown', handleEscape);
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
      document.removeEventListener('keydown', handleEscape);
    };
  }, []);

  const handleTriggerClick = () => {
    if (!disabled) {
      setIsOpen(!isOpen);
    }
  };

  const handleMenuClick = () => {
    if (closeOnSelect) {
      setIsOpen(false);
    }
  };

  return (
    <div ref={containerRef} className={`mb-dropdown ${className}`}>
      <div
        id={triggerId}
        className="mb-dropdown__trigger"
        onClick={handleTriggerClick}
        aria-haspopup="true"
        aria-expanded={isOpen}
        aria-controls={menuId}
      >
        {trigger}
      </div>
      {isOpen && (
        <div
          id={menuId}
          className={`mb-dropdown__menu mb-dropdown__menu--${placement}`}
          role="menu"
          aria-labelledby={triggerId}
          onClick={handleMenuClick}
        >
          {children}
        </div>
      )}
    </div>
  );
}

// Dropdown Item
export interface DropdownItemProps extends HTMLAttributes<HTMLButtonElement> {
  icon?: ReactNode;
  disabled?: boolean;
  danger?: boolean;
  children: ReactNode;
}

export function DropdownItem({
  icon,
  disabled = false,
  danger = false,
  className = '',
  children,
  ...props
}: DropdownItemProps) {
  const classNames = [
    'mb-dropdown__item',
    disabled && 'mb-dropdown__item--disabled',
    danger && 'mb-dropdown__item--danger',
    className,
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <button
      type="button"
      role="menuitem"
      className={classNames}
      disabled={disabled}
      {...props}
    >
      {icon && <span className="mb-dropdown__item-icon">{icon}</span>}
      <span className="mb-dropdown__item-label">{children}</span>
    </button>
  );
}

// Dropdown Separator
export function DropdownSeparator({ className = '' }: { className?: string }) {
  return <div className={`mb-dropdown__separator ${className}`} role="separator" />;
}

// Dropdown Label
export function DropdownLabel({
  children,
  className = '',
}: {
  children: ReactNode;
  className?: string;
}) {
  return <div className={`mb-dropdown__label ${className}`}>{children}</div>;
}

// Dropdown Section (for grouping items)
export function DropdownSection({
  label,
  children,
  className = '',
}: {
  label?: string;
  children: ReactNode;
  className?: string;
}) {
  return (
    <div className={`mb-dropdown__section ${className}`}>
      {label && <DropdownLabel>{label}</DropdownLabel>}
      {children}
    </div>
  );
}
