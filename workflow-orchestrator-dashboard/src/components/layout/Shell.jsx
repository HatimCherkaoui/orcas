import { useState } from 'react';
import { Activity, LayoutList, Radio } from 'lucide-react';
import { Footer } from './Footer';

const NAV_ITEMS = [
    { path: '/', label: 'Workflows', icon: LayoutList },
    { path: '/kafka', label: 'Kafka', icon: Radio },
];

/** Minimal toggle icon: outlined square with a right-pointing inner bar */
function CollapseIcon({ collapsed }) {
    return (
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
            <rect x="1.5" y="1.5" width="13" height="13" rx="2.5" stroke="currentColor" strokeWidth="1.4" />
            {collapsed
                ? <path d="M6 5l4 3-4 3" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
                : <path d="M10 5L6 8l4 3" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
            }
        </svg>
    );
}

export function Shell({ page, navigate, children, flush = false }) {
    const [collapsed, setCollapsed] = useState(false);

    return (
        <div className={`shell ${collapsed ? 'is-collapsed' : ''} ${flush ? 'is-flush' : ''}`}>
            <aside className="sidebar">
                <div className="sidebar-top">
                    <button className="brand" onClick={() => navigate?.('/')} title="Orcas Workflow Orchestrator">
                        {collapsed
                            ? <span className="brand-monogram">O</span>
                            : <span className="brand-wordmark">ORCAS</span>
                        }
                    </button>
                    <button
                        className="icon-button sidebar-toggle"
                        onClick={() => setCollapsed((c) => !c)}
                        aria-label="Toggle sidebar"
                    >
                        <CollapseIcon collapsed={collapsed} />
                    </button>
                </div>

                <div className="sidebar-section-label">Operations</div>
                <nav className="sidebar-nav" aria-label="Primary navigation">
                    {NAV_ITEMS.map(({ path: itemPath, label, icon: Icon }) => {
                        const active = itemPath === '/' ? page === '/' : page.startsWith(itemPath);
                        return (
                            <button
                                key={itemPath}
                                className={`sidebar-link ${active ? 'active' : ''}`}
                                onClick={() => navigate?.(itemPath)}
                                title={collapsed ? label : undefined}
                                aria-current={active ? 'page' : undefined}
                            >
                                <Icon size={16} strokeWidth={1.6} />
                                <span>{label}</span>
                                {active && !collapsed && <span className="nav-dot" />}
                            </button>
                        );
                    })}
                </nav>

                <div className="sidebar-status">
                    <Activity size={13} strokeWidth={1.6} />
                    {!collapsed && <><span>Service API</span><b>LIVE</b></>}
                </div>
                <Footer />
            </aside>
            <main className="main-content">{children}</main>
        </div>
    );
}
