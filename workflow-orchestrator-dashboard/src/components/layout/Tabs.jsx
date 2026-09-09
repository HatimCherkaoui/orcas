/** Horizontal tab strip; `items` is a list of `{ id, label, icon? }`. */
export function Tabs({ items, active, onChange }) {
  return (
    <div className="tabs">
      {items.map(({ id, label, icon: Icon }) => (
        <button key={id} className={active === id ? 'active' : ''} onClick={() => onChange(id)}>
          {Icon && <Icon size={14} />}
          {label}
        </button>
      ))}
    </div>
  );
}
