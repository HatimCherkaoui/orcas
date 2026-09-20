import { X } from 'lucide-react';
import { Loading, ErrorState, Empty } from '../common/States';
import { JsonViewer } from '../common/Inputs';

/**
 * Floating card showing workflow-level data (context, metadata or the audit trail).
 * Rendered instead of `StepDetailsPanel` when no step node is selected but the
 * operator picked one of the top-bar info tabs.
 */
export function WorkflowInfoPanel({ tabId, title, subtitle, load, onClose }) {
  return (
    <aside className="floating-panel">
      <header className="floating-panel-header">
        <div>
          <div className="eyebrow">{subtitle}</div>
          <h2>{title}</h2>
        </div>
        <button className="icon-button" onClick={onClose} aria-label="Close">
          <X size={16} />
        </button>
      </header>

      <div className="floating-panel-body">
        {load.loading ? (
          <Loading />
        ) : load.error ? (
          <ErrorState error={load.error} retry={load.reload} />
        ) : tabId === 'logs' ? (
          <AuditLogList logs={load.data} />
        ) : load.data == null ? (
          <p className="muted-note">No {title.toLowerCase()} recorded for this workflow.</p>
        ) : (
          <JsonViewer value={load.data} />
        )}
      </div>
    </aside>
  );
}

function AuditLogList({ logs }) {
  const entries = Array.isArray(logs) ? logs : logs?.content || logs?.items || [];
  if (!entries.length) return <Empty title="No audit log entries" />;
  return (
    <div className="logs">
      {entries.map((log) => (
        <div className="log-row" key={log.id}>
          <time>{new Date(log.dateCreated).toLocaleString()}</time>
          <strong>{log.action}</strong>
          <span>{log.stepName || 'workflow'}</span>
          <code>{log.snapshotJson}</code>
        </div>
      ))}
    </div>
  );
}
