import { ReactNode, useState, createContext, useContext } from 'react';
import { NavLink } from 'react-router';
import './SidebarNav.css';

// Sidebar Context
interface SidebarContextValue {
  isCollapsed: boolean;
  setIsCollapsed: (collapsed: boolean) => void;
  toggleCollapsed: () => void;
}

const SidebarContext = createContext<SidebarContextValue | null>(null);

export function useSidebar() {
  const context = useContext(SidebarContext);
  if (!context) {
    throw new Error('useSidebar must be used within a SidebarProvider');
  }
  return context;
}

// Sidebar Provider
export interface SidebarProviderProps {
  children: ReactNode;
  defaultCollapsed?: boolean;
}

export function SidebarProvider({ children, defaultCollapsed = false }: SidebarProviderProps) {
  const [isCollapsed, setIsCollapsed] = useState(defaultCollapsed);
  const toggleCollapsed = () => setIsCollapsed(!isCollapsed);

  return (
    <SidebarContext.Provider value={{ isCollapsed, setIsCollapsed, toggleCollapsed }}>
      {children}
    </SidebarContext.Provider>
  );
}

// Main Sidebar Component
export interface SidebarNavProps {
  logo?: ReactNode;
  logoCollapsed?: ReactNode;
  header?: ReactNode;
  footer?: ReactNode;
  children: ReactNode;
  className?: string;
}

export function SidebarNav({
  logo,
  logoCollapsed,
  header,
  footer,
  children,
  className = '',
}: SidebarNavProps) {
  const { isCollapsed, toggleCollapsed } = useSidebar();

  return (
    <aside className={`mb-sidebar ${isCollapsed ? 'mb-sidebar--collapsed' : ''} ${className}`}>
      <div className="mb-sidebar__header">
        <div className="mb-sidebar__logo">
          {isCollapsed ? (logoCollapsed || logo) : logo}
        </div>
        <button
          type="button"
          className="mb-sidebar__toggle"
          onClick={toggleCollapsed}
          aria-label={isCollapsed ? 'Expand sidebar' : 'Collapse sidebar'}
        >
          <svg
            width="20"
            height="20"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
            className={isCollapsed ? 'mb-sidebar__toggle-icon--collapsed' : ''}
          >
            <path d="m15 18-6-6 6-6" />
          </svg>
        </button>
      </div>

      {header && <div className="mb-sidebar__custom-header">{header}</div>}

      <nav className="mb-sidebar__nav">{children}</nav>

      {footer && <div className="mb-sidebar__footer">{footer}</div>}
    </aside>
  );
}

// Sidebar Section
export interface SidebarSectionProps {
  title?: string;
  children: ReactNode;
  className?: string;
}

export function SidebarSection({ title, children, className = '' }: SidebarSectionProps) {
  const { isCollapsed } = useSidebar();

  return (
    <div className={`mb-sidebar__section ${className}`}>
      {title && !isCollapsed && (
        <div className="mb-sidebar__section-title">{title}</div>
      )}
      <div className="mb-sidebar__section-items">{children}</div>
    </div>
  );
}

// Sidebar Item
export interface SidebarItemProps {
  to: string;
  icon?: ReactNode;
  label: string;
  badge?: ReactNode;
  end?: boolean;
  className?: string;
}

export function SidebarItem({
  to,
  icon,
  label,
  badge,
  end = false,
  className = '',
}: SidebarItemProps) {
  const { isCollapsed } = useSidebar();

  return (
    <NavLink
      to={to}
      end={end}
      className={({ isActive }) =>
        `mb-sidebar__item ${isActive ? 'mb-sidebar__item--active' : ''} ${className}`
      }
      title={isCollapsed ? label : undefined}
    >
      {icon && <span className="mb-sidebar__item-icon">{icon}</span>}
      {!isCollapsed && (
        <>
          <span className="mb-sidebar__item-label">{label}</span>
          {badge && <span className="mb-sidebar__item-badge">{badge}</span>}
        </>
      )}
    </NavLink>
  );
}

// Sidebar Button (for non-navigation actions)
export interface SidebarButtonProps {
  icon?: ReactNode;
  label: string;
  onClick?: () => void;
  className?: string;
}

export function SidebarButton({
  icon,
  label,
  onClick,
  className = '',
}: SidebarButtonProps) {
  const { isCollapsed } = useSidebar();

  return (
    <button
      type="button"
      className={`mb-sidebar__item mb-sidebar__item--button ${className}`}
      onClick={onClick}
      title={isCollapsed ? label : undefined}
    >
      {icon && <span className="mb-sidebar__item-icon">{icon}</span>}
      {!isCollapsed && <span className="mb-sidebar__item-label">{label}</span>}
    </button>
  );
}

// Sidebar Divider
export function SidebarDivider({ className = '' }: { className?: string }) {
  return <div className={`mb-sidebar__divider ${className}`} />;
}

// Sidebar Search
export interface SidebarSearchProps {
  placeholder?: string;
  onSearch?: (query: string) => void;
  className?: string;
}

export function SidebarSearch({
  placeholder = 'Search...',
  onSearch,
  className = '',
}: SidebarSearchProps) {
  const { isCollapsed } = useSidebar();

  if (isCollapsed) {
    return (
      <button
        type="button"
        className={`mb-sidebar__search-collapsed ${className}`}
        title={placeholder}
      >
        <svg
          width="18"
          height="18"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
        >
          <circle cx="11" cy="11" r="8" />
          <path d="m21 21-4.35-4.35" />
        </svg>
      </button>
    );
  }

  return (
    <div className={`mb-sidebar__search ${className}`}>
      <svg
        className="mb-sidebar__search-icon"
        width="16"
        height="16"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
      >
        <circle cx="11" cy="11" r="8" />
        <path d="m21 21-4.35-4.35" />
      </svg>
      <input
        type="search"
        className="mb-sidebar__search-input"
        placeholder={placeholder}
        onChange={(e) => onSearch?.(e.target.value)}
      />
    </div>
  );
}
