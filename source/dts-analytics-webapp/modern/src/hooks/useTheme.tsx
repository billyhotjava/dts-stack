import { useState, useEffect, useCallback, createContext, useContext, ReactNode } from 'react';

export type Theme = 'light' | 'dark';

const THEME_STORAGE_KEY = 'dts-analytics.theme';

function getPlatformTheme(): Theme | null {
  try {
    const raw = localStorage.getItem('settings');
    if (!raw) return null;
    const store = JSON.parse(raw);
    // Zustand persist stores under { state: { settings: { themeMode } } } or { settings: { themeMode } }
    const settings = store?.state?.settings ?? store?.settings;
    if (settings?.themeMode === 'dark') return 'dark';
    if (settings?.themeMode === 'light') return 'light';
  } catch { /* ignore */ }
  return null;
}

function getInitialTheme(): Theme {
  // Check analytics-specific preference first
  const stored = localStorage.getItem(THEME_STORAGE_KEY);
  if (stored === 'light' || stored === 'dark') {
    return stored;
  }
  // Fall back to platform webapp's theme setting
  const platformTheme = getPlatformTheme();
  if (platformTheme) return platformTheme;
  return 'light';
}

export function useTheme() {
  const [theme, setThemeState] = useState<Theme>(getInitialTheme);

  useEffect(() => {
    // Apply theme to document
    document.documentElement.setAttribute('data-theme', theme);
    localStorage.setItem(THEME_STORAGE_KEY, theme);
  }, [theme]);

  useEffect(() => {
    // Listen for system theme changes
    const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
    const handleChange = (e: MediaQueryListEvent) => {
      // Only auto-switch if user hasn't set a preference
      const stored = localStorage.getItem(THEME_STORAGE_KEY);
      if (!stored) {
        setThemeState(e.matches ? 'dark' : 'light');
      }
    };

    mediaQuery.addEventListener('change', handleChange);
    return () => mediaQuery.removeEventListener('change', handleChange);
  }, []);

  const setTheme = useCallback((newTheme: Theme) => {
    setThemeState(newTheme);
  }, []);

  const toggleTheme = useCallback(() => {
    setThemeState(current => current === 'light' ? 'dark' : 'light');
  }, []);

  const isDark = theme === 'dark';
  const isLight = theme === 'light';

  return {
    theme,
    setTheme,
    toggleTheme,
    isDark,
    isLight,
  };
}

// Theme Context for sharing theme across components
interface ThemeContextValue {
  theme: Theme;
  setTheme: (theme: Theme) => void;
  toggleTheme: () => void;
  isDark: boolean;
  isLight: boolean;
}

const ThemeContext = createContext<ThemeContextValue | null>(null);

export function ThemeProvider({ children }: { children: ReactNode }) {
  const themeValue = useTheme();

  return (
    <ThemeContext.Provider value={themeValue}>
      {children}
    </ThemeContext.Provider>
  );
}

export function useThemeContext() {
  const context = useContext(ThemeContext);
  if (!context) {
    throw new Error('useThemeContext must be used within a ThemeProvider');
  }
  return context;
}
