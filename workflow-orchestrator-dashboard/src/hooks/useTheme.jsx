import { createContext, useContext, useEffect, useState } from 'react';
const ThemeContext = createContext(null);
const STORAGE_KEY = 'orcas.console.theme';
function initialTheme() {
  try { const saved = localStorage.getItem(STORAGE_KEY); if (saved === 'dark' || saved === 'light') return saved; } catch {}
  return window.matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark';
}
// Apply before React mounts so a saved preference never flashes the other theme.
const firstTheme = initialTheme();
document.documentElement.dataset.theme = firstTheme;
export function ThemeProvider({ children }) {
  const [theme, setTheme] = useState(firstTheme);
  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    document.querySelector('meta[name="theme-color"]')?.setAttribute('content', theme === 'light' ? '#f5f4ee' : '#0b0d0f');
    try { localStorage.setItem(STORAGE_KEY, theme); } catch {}
  }, [theme]);
  useEffect(() => {
    const sync = ({ key, newValue }) => { if (key === STORAGE_KEY && ['light', 'dark'].includes(newValue)) setTheme(newValue); };
    window.addEventListener('storage', sync);
    return () => window.removeEventListener('storage', sync);
  }, []);
  return <ThemeContext.Provider value={{ theme, setTheme }}>{children}</ThemeContext.Provider>;
}
export function useTheme() { return useContext(ThemeContext); }
