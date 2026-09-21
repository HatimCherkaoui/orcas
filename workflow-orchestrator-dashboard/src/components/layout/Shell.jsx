import {useState} from 'react';
import {Activity, Boxes, ChevronLeft, ChevronRight, Waves} from 'lucide-react';
import logo from '../../assets/logo.svg';
import {Footer} from './Footer';

const NAV_ITEMS = [
    {path: '/', label: 'Workflows', icon: Boxes},
    {path: '/kafka', label: 'Kafka', icon: Waves},
];

export function Shell({page, navigate, children, flush = false}) {
    const [collapsed, setCollapsed] = useState(false);

    return (
        <div className={`shell ${collapsed ? 'is-collapsed' : ''} ${flush ? 'is-flush' : ''}`}>
            <aside className="sidebar">
                <div className="sidebar-top">
                    <button className="brand" onClick={() => navigate?.('/')} title="Orcas Workflow Orchestrator">
                        <img className="brand-logo" src={logo} alt="Orcas"/>
                    </button>
                    <button className="icon-button sidebar-toggle" onClick={() => setCollapsed((current) => !current)}
                            aria-label="Toggle sidebar">
                        {collapsed ? <ChevronRight size={16}/> : <ChevronLeft size={16}/>}
                    </button>
                </div>

                <div className="sidebar-section-label">Operations</div>
                <nav className="sidebar-nav" aria-label="Primary navigation">
                    {NAV_ITEMS.map(({path: itemPath, label, icon: Icon}) => {
                        const active = itemPath === '/' ? page === '/' : page.startsWith(itemPath);
                        return (
                            <button key={itemPath} className={`sidebar-link ${active ? 'active' : ''}`}
                                    onClick={() => navigate?.(itemPath)} title={collapsed ? label : undefined}
                                    aria-current={active ? 'page' : undefined}>
                                <Icon size={17}/><span>{label}</span>
                                {active && !collapsed && <span className="nav-dot"/>}
                            </button>
                        );
                    })}
                </nav>

                <div className="sidebar-status">
                    <Activity size={14}/>
                    {!collapsed && <><span>Service API</span><b>LIVE</b></>}
                </div>
                <Footer/>
            </aside>
            <main className="main-content">{children}</main>
        </div>
    );
}
