import { useState } from 'react';
import { Search } from 'lucide-react';

/** Read-only, pretty-printed JSON block used to display context/metadata payloads. */
export function JsonViewer({ value }) {
  return <pre className="json-viewer">{JSON.stringify(value ?? {}, null, 2)}</pre>;
}

/**
 * Outlined form field with a floating label: the label sits inside the control at
 * rest and lifts to sit inline over the top border once the control is focused or
 * has a value, matching the standard "Material outlined" text field pattern.
 * Shared by `TextField`, `SelectField` and `DateField` below.
 */
function Field({ label, active, icon: Icon, children }) {
  return (
    <label className={`field ${active ? 'active' : ''} ${Icon ? 'has-icon' : ''}`}>
      {Icon && <Icon className="field-icon" size={15} />}
      {children}
      <span className="field-label">{label}</span>
    </label>
  );
}

/** Fully bordered text input with a floating label, controlled via `value`/`onChange`. */
export function TextField({ label, value, onChange, icon }) {
  const [focused, setFocused] = useState(false);
  return (
    <Field label={label} icon={icon} active={focused || !!value}>
      <input
        value={value}
        onChange={(e) => onChange(e.target.value)}
        onFocus={() => setFocused(true)}
        onBlur={() => setFocused(false)}
      />
    </Field>
  );
}

/** Fully bordered `<select>` with a floating label, matching `TextField`. */
export function SelectField({ label, value, onChange, options }) {
  const [focused, setFocused] = useState(false);
  return (
    <Field label={label} active={focused || !!value}>
      <select
        value={value}
        onChange={(e) => onChange(e.target.value)}
        onFocus={() => setFocused(true)}
        onBlur={() => setFocused(false)}
      >
        <option value="" />
        {options.map((option) => (
          <option key={option} value={option}>
            {option}
          </option>
        ))}
      </select>
    </Field>
  );
}

/** Native date input (opens the browser's calendar popup) with a floating label. */
export function DateField({ label, value, onChange }) {
  const [focused, setFocused] = useState(false);
  return (
    <Field label={label} active={focused || !!value}>
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

/** Text input with a search icon and floating label, controlled via `value`/`onChange`. */
export function SearchBox({ value, onChange, placeholder }) {
  return <TextField label={placeholder} value={value} onChange={onChange} icon={Search} />;
}

/** Compact "icon + label + value" tile used in stats rows. */
export function StatCard({ label, value, icon: Icon }) {
  return (
    <div className="stat">
      <div className="stat-icon">
        <Icon size={17} />
      </div>
      <div>
        <small>{label}</small>
        <strong>{value}</strong>
      </div>
    </div>
  );
}

/** Prev/next pager with a "page X of Y" indicator, for server-paged result sets. */
export function Pagination({ page, totalPages, totalElements, onChange }) {
  const lastPage = Math.max(totalPages - 1, 0);
  return (
    <div className="pagination">
      <span className="pagination-count">{totalElements} total</span>
      <div className="pagination-controls">
        <button className="button" disabled={page <= 0} onClick={() => onChange(page - 1)}>
          Previous
        </button>
        <span className="pagination-page">
          Page {page + 1} of {Math.max(totalPages, 1)}
        </span>
        <button className="button" disabled={page >= lastPage} onClick={() => onChange(page + 1)}>
          Next
        </button>
      </div>
    </div>
  );
}
