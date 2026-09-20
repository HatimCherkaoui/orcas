import { useState } from 'react';
import { ChevronLeft, ChevronRight, Search, SlidersHorizontal } from 'lucide-react';

export function JsonViewer({ value }) {
  return <pre className="json-viewer">{JSON.stringify(value ?? {}, null, 2)}</pre>;
}

function Field({ label, placeholder, active, icon: Icon, children }) {
  return (
    <label className={`field ${active ? 'active' : ''} ${Icon ? 'has-icon' : ''}`}>
      {Icon && <Icon className="field-icon" size={15} />}
      {children}
      <span className="field-label">{label}</span>
      {placeholder && <span className="field-placeholder">{placeholder}</span>}
    </label>
  );
}

export function TextField({ label, value, onChange, icon, placeholder = 'Search…' }) {
  const [focused, setFocused] = useState(false);
  return (
    <Field label={label} placeholder={placeholder} icon={icon} active={focused || !!value}>
      <input
        value={value}
        placeholder={focused || value ? placeholder : ''}
        onChange={(e) => onChange(e.target.value)}
        onFocus={() => setFocused(true)}
        onBlur={() => setFocused(false)}
      />
    </Field>
  );
}

export function SelectField({ label, value, onChange, options, placeholder = 'Any state' }) {
  const [focused, setFocused] = useState(false);
  return (
    <Field label={label} placeholder={placeholder} active={focused || !!value}>
      <select
        value={value}
        onChange={(e) => onChange(e.target.value)}
        onFocus={() => setFocused(true)}
        onBlur={() => setFocused(false)}
      >
        <option value="">{placeholder}</option>
        {options.map((option) => <option key={option} value={option}>{option}</option>)}
      </select>
    </Field>
  );
}

export function DateField({ label, value, onChange, placeholder = 'Pick a date' }) {
  const [focused, setFocused] = useState(false);
  return (
    <Field label={label} placeholder={placeholder} active={focused || !!value}>
      <input
        type="date"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        onFocus={() => setFocused(true)}
        onBlur={() => setFocused(false)}
      />
    </Field>
  );
}

export function SearchBox({ value, onChange, label = 'Search', placeholder = 'Search…' }) {
  return <TextField label={label} value={value} onChange={onChange} icon={Search} placeholder={placeholder} />;
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
            {[8, 10, 12, 16, 20].map((value) => <option key={value} value={value}>{value}</option>)}
          </select>
        </label>
        <button className="icon-button compact-icon" disabled={page <= 0} onClick={() => onChange(page - 1)} aria-label="Previous page"><ChevronLeft size={16} /></button>
        <span className="pagination-page">{page + 1} / {Math.max(totalPages, 1)}</span>
        <button className="icon-button compact-icon" disabled={page >= lastPage} onClick={() => onChange(page + 1)} aria-label="Next page"><ChevronRight size={16} /></button>
      </div>
    </div>
  );
}
