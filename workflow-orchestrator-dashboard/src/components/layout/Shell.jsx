import { useState } from 'react';
import { Boxes, ChevronLeft, ChevronRight, Waves } from 'lucide-react';
import { Footer } from './Footer';

const NAV_ITEMS = [
  { path: '/', label: 'Pipelines', icon: Boxes },
  { path: '/kafka', label: 'Kafka', icon: Waves },
];

/**
 * Application chrome for the "list-style" pages (pipeline list, Kafka admin): a
 * full navbar sidebar (brand, nav links, footer trademark) plus a scrollable main
 * content area. The sidebar can be collapsed to an icon rail via the hover-reveal
 * chevron toggle. The interactive graph page (`PipelineDetailPage`) intentionally
 * does not use this shell - it owns the full viewport instead.
 */
export function Shell({ page, navigate, children }) {
  const [collapsed, setCollapsed] = useState(false);

  return (
    <div className="shell">
      <aside className={`sidebar ${collapsed ? 'collapsed' : ''}`}>
        <div className="sidebar-head">
          <button className="brand-mark" onClick={() => navigate('/')} aria-label="Orcas home">
            {collapsed ? 'O' : 'ORCAS'}
          </button>
          <button
            className="sidebar-collapse"
            onClick={() => setCollapsed((value) => !value)}
            aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
          >
            {collapsed ? <ChevronRight size={14} /> : <ChevronLeft size={14} />}
          </button>
        </div>
        <nav>
          {NAV_ITEMS.map(({ path, label, icon: Icon }) => (
            <button
              key={path}
              className={`nav-item ${page === path ? 'active' : ''}`}
              onClick={() => navigate(path)}
            >
              <Icon size={17} />
              <span>{label}</span>
            </button>
          ))}
        </nav>
        <Footer />
      </aside>
      <main className="main">{children}</main>
    </div>
  );
}
