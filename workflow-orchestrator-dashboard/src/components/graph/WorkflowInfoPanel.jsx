import { useState, useCallback } from 'react';
import { X, ShieldCheck, Layers, GitBranch, RotateCcw, Copy, Check } from 'lucide-react';
import { Loading, ErrorState, Empty } from '../common/States';
import { JsonViewer } from '../common/Inputs';

/** Copy button with momentary flash */
function CopyButton({ value }) {
  const [copied, setCopied] = useState(false);
  const handleCopy = useCallback((e) => {
    e.stopPropagation();
    const text = typeof value === 'string' ? value : JSON.stringify(value, null, 2);
    navigator.clipboard?.writeText(text);
    setCopied(true);
    setTimeout(() => setCopied(false), 1800);
  }, [value]);
  return (
    <button className="copy-mini-btn" onClick={handleCopy} title="Copy to clipboard" aria-label="Copy to clipboard">
      {copied ? <Check size={11} className="copied-icon" /> : <Copy size={11} />}
    </button>
  );
}

/** Section header with eyebrow label + copy action */
function SectionHeader({ label, value }) {
  return (
    <div className="panel-section-header">
      <span className="eyebrow">{label}</span>
      {value != null && <CopyButton value={value} />}
    </div>
  );
}

/**
 * Floating card showing workflow-level data (context, metadata, audit trail, or configuration).
 */
export function WorkflowInfoPanel({ tabId, title, subtitle, load, onClose }) {
  return (
    <aside className="floating-panel">
      <header className="floating-panel-header">
        <div>
          <div className="eyebrow">{subtitle}</div>
          <h2>{title}</h2>
        </div>
        <button className="icon-button" onClick={onClose} aria-label="Close panel">
          <X size={15} strokeWidth={1.6} />
        </button>
      </header>

      <div className="floating-panel-body">
        {load.loading ? (
          <Loading message={`Loading ${title.toLowerCase()}...`} />
        ) : load.error ? (
          <ErrorState error={load.error} retry={load.reload} />
        ) : tabId === 'logs' ? (
          <AuditLogList logs={load.data} />
        ) : tabId === 'config' ? (
          <WorkflowConfigView config={load.data} />
        ) : load.data == null ? (
          <p className="muted-note">No {title.toLowerCase()} recorded for this workflow.</p>
        ) : (
          <>
            <SectionHeader label={title} value={load.data} />
            <JsonViewer value={load.data} />
          </>
        )}
      </div>
    </aside>
  );
}

function WorkflowConfigView({ config }) {
  if (!config) return <Empty title="No configuration found" />;

  const stepConfigs = config.stepConfigs || {};
  const stepsList = Array.isArray(config.steps) ? config.steps : [];
  const routesList = Array.isArray(config.routes) ? config.routes : [];

  return (
    <div className="config-panel-container">
      {/* Definition summary */}
      <div className="config-card">
        <div className="config-card-header">
          <Layers size={14} strokeWidth={1.6} className="config-card-icon" />
          <div>
            <span className="config-card-title">Workflow Definition</span>
            <span className="config-card-sub">{config.workflow || 'Standard Workflow'}</span>
          </div>
          <span className="status status-init">v{config.version || '1.0.0'}</span>
        </div>
        {config.description && <p className="config-description">{config.description}</p>}
        <div className="config-card-grid">
          <div className="config-stat">
            <span className="config-label">Total Steps</span>
            <span className="config-value mono">{stepsList.length || Object.keys(stepConfigs).length || '—'}</span>
          </div>
          <div className="config-stat">
            <span className="config-label">Routes</span>
            <span className="config-value mono">{routesList.length || 'Sequential'}</span>
          </div>
          <div className="config-stat">
            <span className="config-label">Default Max Retries</span>
            <span className="config-value mono">3 attempts</span>
          </div>
          <div className="config-stat">
            <span className="config-label">Engine Resilience</span>
            <span className="status status-success">Active</span>
          </div>
        </div>
      </div>

      {/* Step resilience matrix */}
      <div className="config-card">
        <div className="config-card-header">
          <RotateCcw size={14} strokeWidth={1.6} className="config-card-icon" />
          <div>
            <span className="config-card-title">Step Retry Matrix</span>
            <span className="config-card-sub">Resilience policies across nodes</span>
          </div>
        </div>
        {Object.keys(stepConfigs).length > 0 ? (
          <div className="config-table-mini">
            <div className="config-table-head">
              <span>Step</span>
              <span>Retries</span>
              <span>Delay</span>
              <span>Breaker</span>
            </div>
            {Object.entries(stepConfigs).map(([name, cfg]) => (
              <div className="config-table-row" key={name}>
                <span className="mono config-truncate">{name}</span>
                <span>{cfg.retryEnabled ? `${cfg.maxAttempts || 3} max` : 'Off'}</span>
                <span>{cfg.delayMillis ? `${cfg.delayMillis}ms` : '0ms'}</span>
                <span className={`status status-${cfg.circuitBreakerEnabled ? 'success' : 'skipped'}`}>
                  {cfg.circuitBreakerEnabled ? 'CB On' : 'CB Off'}
                </span>
              </div>
            ))}
          </div>
        ) : (
          <div className="config-notice">
            <ShieldCheck size={14} strokeWidth={1.6} />
            <span>Engine defaults apply — 3 retries, 1000ms exponential backoff on all retryable steps.</span>
          </div>
        )}
      </div>

      {/* Raw definition with copy */}
      <div className="config-card">
        <div className="config-card-header">
          <GitBranch size={14} strokeWidth={1.6} className="config-card-icon" />
          <div>
            <span className="config-card-title">Raw Definition</span>
            <span className="config-card-sub">JSON specification</span>
          </div>
          <CopyButton value={config} />
        </div>
        <JsonViewer value={config} />
      </div>
    </div>
  );
}

function AuditLogList({ logs }) {
  const entries = Array.isArray(logs) ? logs : logs?.content || logs?.items || [];
  if (!entries.length) return <Empty title="No audit log entries" />;
  return (
    <div className="logs">
      {entries.map((log) => (
        <div className="log-row" key={log.id}>
          <time>{new Date(log.dateCreated || log.timestamp).toLocaleString()}</time>
          <strong>{log.action}</strong>
          <span>{log.stepName || 'workflow'}</span>
          {log.message && <p className="log-message">{log.message}</p>}
          {log.snapshotJson && <code>{log.snapshotJson}</code>}
        </div>
      ))}
    </div>
  );
}
