import { useState } from 'react';
import { LayoutList, Radio, PanelLeftClose, PanelLeftOpen, Moon, Sun } from 'lucide-react';
import { Footer } from './Footer';
import { useTheme } from '../../hooks/useTheme';
const NAV_ITEMS = [{ path: '/', label: 'Workflows', icon: LayoutList }, { path: '/kafka', label: 'Kafka', icon: Radio }];
function OrcaMark() {
  return <svg className="orca-mark" width="28" height="28" viewBox="0 0 32 32" fill="none" aria-hidden="true"><path d="M3 21C6 13 13 10 20 12l-2-8c4 2 6 6 6 10l5 4-5 2c-4 5-12 7-21 1Z" fill="currentColor"/><path d="M8 21c5 2 11 0 14-4-2 0-4 1-5 3-3 1-6 1-9 1Z" fill="var(--surface-0)"/><ellipse cx="21" cy="15" rx="2" ry="1" fill="var(--surface-0)"/></svg>;
}
export function Shell({ page, navigate, children, flush = false }) {
  const { theme, setTheme } = useTheme();
  const [collapsed, setCollapsed] = useState(() => { try { return localStorage.getItem('orcas.console.sidebar') === 'collapsed'; } catch { return false; } });
  function toggleSidebar() {
    setCollapsed(!collapsed);
    try { localStorage.setItem('orcas.console.sidebar', collapsed ? 'expanded' : 'collapsed'); } catch {}
  }
  return <div className={`shell ${collapsed ? 'is-collapsed' : ''} ${flush ? 'is-flush' : ''}`}>
    <a className="skip-link" href="#console-main">Skip to content</a>
    <aside className="sidebar">
      <div className="sidebar-top">
        <button className="brand" onClick={() => navigate('/')} aria-label="Orcas home"><OrcaMark /><span className="brand-wordmark">orcas</span></button>
        <button className="icon-button sidebar-toggle" onClick={toggleSidebar} aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'} aria-expanded={!collapsed}>{collapsed ? <PanelLeftOpen size={16}/> : <PanelLeftClose size={16}/>}</button>
      </div>
      <div className="sidebar-section-label">Workspace</div>
      <nav className="sidebar-nav" aria-label="Primary navigation">
        {NAV_ITEMS.map(({ path, label, icon: Icon }) => {
          const active = path === '/' ? !page.startsWith('/kafka') : page.startsWith(path);
          return <button key={path} className={`sidebar-link ${active ? 'active' : ''}`} onClick={() => navigate(path)} title={label} aria-label={label} aria-current={active ? 'page' : undefined}><Icon size={17} strokeWidth={1.6}/><span>{label}</span></button>;
        })}
      </nav>
      <div className="sidebar-preferences">
        <span className="preference-label">Appearance</span>
        <div className="theme-controls" role="group" aria-label="Color theme">
          <button aria-label="Use light theme" aria-pressed={theme === 'light'} onClick={() => setTheme('light')}><Sun size={15}/><span>Light</span></button>
          <button aria-label="Use dark theme" aria-pressed={theme === 'dark'} onClick={() => setTheme('dark')}><Moon size={15}/><span>Dark</span></button>
        </div>
      </div>
      <Footer />
    </aside>
    <main id="console-main" className="main-content" tabIndex={-1}>{children}</main>
  </div>;
}
