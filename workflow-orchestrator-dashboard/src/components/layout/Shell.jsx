import { useState } from 'react';
import { Boxes, ChevronLeft, ChevronRight, Waves } from 'lucide-react';
import logo from '../../assets/logo.svg';
import { Footer } from './Footer';

const NAV_ITEMS = [
  { path: '/', label: 'Workflows', icon: Boxes },
  { path: '/kafka', label: 'Kafka', icon: Waves },
];

/**
 * Shared application chrome for list-style pages (workflow list and Kafka admin).
 * The sidebar can collapse down to icons only, while the main area renders the page
 * content provided by the caller.
 */
export function Shell({ page, navigate, children }) {
  const [collapsed, setCollapsed] = useState(false);

  return (
    <div className={`shell ${collapsed ? 'is-collapsed' : ''}`}>
      <aside className="sidebar">
        <div className="sidebar-top">
          <div className="brand">
            <img className="brand-logo" src={logo} alt="Orcas" />
            {!collapsed && (
              <div className="brand-copy">
                <strong>Workflow Orchestrator</strong>
                <p>Operations dashboard</p>
              </div>
            )}
          </div>

          <button
            className="icon-button sidebar-toggle"
            onClick={() => setCollapsed((current) => !current)}
            aria-label="Toggle sidebar"
          >
            {collapsed ? <ChevronRight size={16} /> : <ChevronLeft size={16} />}
          </button>
        </div>

        <nav className="sidebar-nav" aria-label="Primary navigation">
          {NAV_ITEMS.map(({ path: itemPath, label, icon: Icon }) => {
            const active = page === itemPath;
            return (
              <button
                key={itemPath}
                className={`sidebar-link ${active ? 'active' : ''}`}
                onClick={() => navigate?.(itemPath)}
                title={collapsed ? label : undefined}
                aria-current={active ? 'page' : undefined}
              >
                <Icon size={16} />
                {!collapsed && <span>{label}</span>}
              </button>
            );
          })}
        </nav>

        <Footer />
      </aside>

      <main className="main-content">{children}</main>
    </div>
  );
}
