import { RefreshCw, AlertCircle, Inbox } from 'lucide-react';

/** Simple bordered content container used throughout the dashboard. */
export function Card({ children, className = '' }) {
  return <section className={`card ${className}`}>{children}</section>;
}

/** Centered spinner shown while a `useLoad` request is in flight. */
export function Loading() {
  return (
    <div className="state-card">
      <RefreshCw className="spin" size={20} />
      <span>Loading…</span>
    </div>
  );
}

/** Error placeholder with an optional retry action, for failed `useLoad` requests. */
export function ErrorState({ error, retry }) {
  return (
    <div className="state-card error">
      <AlertCircle size={20} />
      <div>
        <strong>Unable to load data</strong>
        <p>{error?.message || 'Unexpected error'}</p>
      </div>
      {retry && (
        <button className="button" onClick={retry}>
          Retry
        </button>
      )}
    </div>
  );
}

/** Placeholder for empty lists/collections. */
export function Empty({ icon: Icon = Inbox, title = 'Nothing here', text = '' }) {
  return (
    <div className="empty">
      <Icon size={28} />
      <strong>{title}</strong>
      {text && <p>{text}</p>}
    </div>
  );
}
