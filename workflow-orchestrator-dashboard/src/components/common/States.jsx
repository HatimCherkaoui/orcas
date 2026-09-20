import { RefreshCw, AlertCircle, Inbox } from 'lucide-react';

export function Card({ children, className = '' }) {
  return <section className={`card ${className}`}>{children}</section>;
}

export function Loading() {
  return <div className="state-card"><RefreshCw className="spin" size={19} /><span>Loading…</span></div>;
}

export function ErrorState({ error, retry }) {
  return (
    <div className="state-card error">
      <AlertCircle size={21} />
      <div>
        <strong>Unable to load data</strong>
        <p>{error?.message || 'Unexpected error'}</p>
        {error?.endpoint && <p className="mono">{error.status ? `${error.status} · ` : ''}{error.endpoint}</p>}
      </div>
      {retry && <button className="button" onClick={retry}><RefreshCw size={14} /> Retry</button>}
    </div>
  );
}

export function Empty({ icon: Icon = Inbox, title = 'Nothing here', text = '' }) {
  return (
    <div className="empty">
      <Icon size={27} />
      <strong>{title}</strong>
      {text && <p>{text}</p>}
    </div>
  );
}
