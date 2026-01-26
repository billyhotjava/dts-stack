import { ReactNode, useState, useId, createContext, useContext } from 'react';
import './Tabs.css';

export type TabsVariant = 'underline' | 'pill';
export type TabsOrientation = 'horizontal' | 'vertical';

interface TabsContextValue {
  activeTab: string;
  setActiveTab: (id: string) => void;
  variant: TabsVariant;
  orientation: TabsOrientation;
}

const TabsContext = createContext<TabsContextValue | null>(null);

function useTabsContext() {
  const context = useContext(TabsContext);
  if (!context) {
    throw new Error('Tabs components must be used within a Tabs provider');
  }
  return context;
}

// Main Tabs Component
export interface TabsProps {
  defaultValue?: string;
  value?: string;
  onChange?: (value: string) => void;
  variant?: TabsVariant;
  orientation?: TabsOrientation;
  className?: string;
  children: ReactNode;
}

export function Tabs({
  defaultValue,
  value,
  onChange,
  variant = 'underline',
  orientation = 'horizontal',
  className = '',
  children,
}: TabsProps) {
  const [internalValue, setInternalValue] = useState(defaultValue || '');

  const activeTab = value !== undefined ? value : internalValue;
  const setActiveTab = (id: string) => {
    if (value === undefined) {
      setInternalValue(id);
    }
    onChange?.(id);
  };

  return (
    <TabsContext.Provider value={{ activeTab, setActiveTab, variant, orientation }}>
      <div
        className={`mb-tabs mb-tabs--${variant} mb-tabs--${orientation} ${className}`}
      >
        {children}
      </div>
    </TabsContext.Provider>
  );
}

// Tab List
export interface TabListProps {
  className?: string;
  children: ReactNode;
  'aria-label'?: string;
}

export function TabList({ className = '', children, 'aria-label': ariaLabel }: TabListProps) {
  const { orientation } = useTabsContext();

  return (
    <div
      role="tablist"
      aria-label={ariaLabel}
      aria-orientation={orientation}
      className={`mb-tabs__list ${className}`}
    >
      {children}
    </div>
  );
}

// Tab
export interface TabProps {
  value: string;
  className?: string;
  disabled?: boolean;
  icon?: ReactNode;
  children: ReactNode;
}

export function Tab({
  value,
  className = '',
  disabled = false,
  icon,
  children,
}: TabProps) {
  const { activeTab, setActiveTab, variant } = useTabsContext();
  const id = useId();
  const isActive = activeTab === value;

  const classNames = [
    'mb-tabs__tab',
    `mb-tabs__tab--${variant}`,
    isActive && 'mb-tabs__tab--active',
    disabled && 'mb-tabs__tab--disabled',
    className,
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <button
      id={`tab-${id}`}
      role="tab"
      type="button"
      aria-selected={isActive}
      aria-controls={`panel-${id}`}
      tabIndex={isActive ? 0 : -1}
      className={classNames}
      disabled={disabled}
      onClick={() => setActiveTab(value)}
    >
      {icon && <span className="mb-tabs__tab-icon">{icon}</span>}
      <span className="mb-tabs__tab-label">{children}</span>
    </button>
  );
}

// Tab Panels Container
export interface TabPanelsProps {
  className?: string;
  children: ReactNode;
}

export function TabPanels({ className = '', children }: TabPanelsProps) {
  return <div className={`mb-tabs__panels ${className}`}>{children}</div>;
}

// Tab Panel
export interface TabPanelProps {
  value: string;
  className?: string;
  children: ReactNode;
}

export function TabPanel({ value, className = '', children }: TabPanelProps) {
  const { activeTab } = useTabsContext();
  const id = useId();
  const isActive = activeTab === value;

  if (!isActive) return null;

  return (
    <div
      id={`panel-${id}`}
      role="tabpanel"
      aria-labelledby={`tab-${id}`}
      tabIndex={0}
      className={`mb-tabs__panel ${className}`}
    >
      {children}
    </div>
  );
}

// Simple Tabs (all-in-one component)
export interface SimpleTabItem {
  id: string;
  label: ReactNode;
  icon?: ReactNode;
  content: ReactNode;
  disabled?: boolean;
}

export interface SimpleTabsProps {
  items: SimpleTabItem[];
  defaultValue?: string;
  value?: string;
  onChange?: (value: string) => void;
  variant?: TabsVariant;
  orientation?: TabsOrientation;
  className?: string;
}

export function SimpleTabs({
  items,
  defaultValue,
  value,
  onChange,
  variant = 'underline',
  orientation = 'horizontal',
  className = '',
}: SimpleTabsProps) {
  const initialValue = defaultValue || (items.length > 0 ? items[0].id : '');

  return (
    <Tabs
      defaultValue={initialValue}
      value={value}
      onChange={onChange}
      variant={variant}
      orientation={orientation}
      className={className}
    >
      <TabList>
        {items.map((item) => (
          <Tab key={item.id} value={item.id} icon={item.icon} disabled={item.disabled}>
            {item.label}
          </Tab>
        ))}
      </TabList>
      <TabPanels>
        {items.map((item) => (
          <TabPanel key={item.id} value={item.id}>
            {item.content}
          </TabPanel>
        ))}
      </TabPanels>
    </Tabs>
  );
}
