import { ChevronLeft, ChevronRight, Search, SlidersHorizontal } from 'lucide-react';

export function JsonViewer({ value }) {
  return <pre className="json-viewer">{JSON.stringify(value ?? {}, null, 2)}</pre>;
}

function Field({ label, icon: Icon, children }) {
  return <label className={`field ${Icon ? 'has-icon' : ''}`}><span className="field-label">{label}</span><span className="field-control">{Icon && <Icon className="field-icon" size={15}/>} {children}</span></label>;
}
export function TextField({ label, value, onChange, icon, placeholder = 'Search…' }) {
  return <Field label={label} icon={icon}><input value={value} placeholder={placeholder} onChange={(e) => onChange(e.target.value)}/></Field>;
}
export function SelectField({ label, value, onChange, options, placeholder = 'Any state' }) {
  return <Field label={label}><select value={value} onChange={(e) => onChange(e.target.value)}><option value="">{placeholder}</option>{options.map((option) => <option key={option} value={option}>{option}</option>)}</select></Field>;
}
export function DateField({ label, value, onChange }) {
  return <Field label={label}><input type="date" value={value} onChange={(e) => onChange(e.target.value)}/></Field>;
}

export function SearchBox({ value, onChange, label = 'Search', placeholder = 'Search…' }) {
  return <TextField label={label} value={value} onChange={onChange} icon={Search} placeholder={placeholder} />;
}

export function AutoRefresh({ enabled, onEnabledChange, interval, onIntervalChange, options = [3000, 5000, 8000, 10000, 30000, 60000] }) {
  const format = (ms) => ms < 60000 ? `${ms / 1000}s` : `${ms / 60000}m`;
  return (
    <div className="auto-refresh" title={enabled ? `Auto refresh every ${format(interval)}` : 'Auto refresh is off'}>
      <button
        type="button"
        className={`refresh-toggle ${enabled ? 'on' : ''}`}
        role="switch"
        aria-checked={enabled}
        onClick={() => onEnabledChange(!enabled)}
      >
        <span className="refresh-toggle-knob" />
        <span>Auto</span>
      </button>
      <select value={interval} onChange={(e) => onIntervalChange(Number(e.target.value))} disabled={!enabled} aria-label="Refresh frequency">
        {options.map((value) => <option key={value} value={value}>{format(value)}</option>)}
      </select>
    </div>
  );
}

export function StatCard({ label, value, icon: Icon, tone = 'default' }) {
  return (
    <div className={`stat stat-${tone}`}>
      <div className="stat-icon"><Icon size={16} /></div>
      <div className="stat-copy"><small>{label}</small><strong>{value}</strong></div>
    </div>
  );
}

export function FilterToggle({ active, onClick, count = 0 }) {
  return (
    <button className={`filter-toggle ${active ? 'active' : ''}`} onClick={onClick}>
      <SlidersHorizontal size={15} /> Filters {count > 0 && <span>{count}</span>}
    </button>
  );
}

export function Pagination({ page, totalPages, totalElements, size, onChange, onSizeChange }) {
  const lastPage = Math.max(totalPages - 1, 0);
  const start = totalElements ? page * size + 1 : 0;
  const end = Math.min((page + 1) * size, totalElements);
  return (
    <div className="pagination">
      <div className="pagination-summary"><strong>{start}–{end}</strong><span>of {totalElements}</span></div>
      <div className="pagination-controls">
        <label className="page-size">Rows
          <select value={size} onChange={(e) => onSizeChange?.(Number(e.target.value))}>
            {[...new Set([8, 10, 12, 16, 20, size])].sort((a, b) => a - b).map((value) => <option key={value} value={value}>{value}</option>)}
          </select>
        </label>
        <button className="icon-button compact-icon" disabled={page <= 0} onClick={() => onChange(page - 1)} aria-label="Previous page"><ChevronLeft size={16} /></button>
        <span className="pagination-page">{page + 1} / {Math.max(totalPages, 1)}</span>
        <button className="icon-button compact-icon" disabled={page >= lastPage} onClick={() => onChange(page + 1)} aria-label="Next page"><ChevronRight size={16} /></button>
      </div>
    </div>
  );
}
