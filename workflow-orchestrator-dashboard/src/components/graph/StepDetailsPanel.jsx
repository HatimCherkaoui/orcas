import { useState } from 'react';
import { X, RotateCcw } from 'lucide-react';
import { api } from '../../api';
import { useLoad } from '../../hooks/useLoad';
import { StatusBadge } from '../common/StatusBadge';
import { Loading, ErrorState, Empty } from '../common/States';
import { JsonViewer } from '../common/Inputs';
import { Tabs } from '../layout/Tabs';

const TABS = [
  { id: 'overview', label: 'Overview' },
  { id: 'config', label: 'Configuration' },
  { id: 'input', label: 'Input' },
  { id: 'output', label: 'Output' },
  { id: 'logs', label: 'Logs' },
];

const REPLAYABLE_STATES = ['FAILED', 'SUSPENDED'];

/** Formats a millisecond duration into a short, readable string (e.g. `5s`, `1m 30s`). */
function formatDuration(millis) {
  if (millis == null) return '—';
  if (millis < 1000) return `${millis}ms`;
  const totalSeconds = Math.round(millis / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  if (!minutes) return `${seconds}s`;
  return seconds ? `${minutes}m ${seconds}s` : `${minutes}m`;
}

function formatDateTime(value) {
  return value ? new Date(value).toLocaleString() : '—';
}

/**
 * Floating card shown alongside the graph when a step node is selected. Loads the
 * step's context/attributes and audit logs on demand and lets operators replay
 * failed or suspended steps without leaving the graph view.
 *
 * The `stepConfig` prop carries the static configuration for this step, resolved
 * from the workflow definition plus the effective retry properties
 * (`{ async, retryEnabled, maxAttempts, delayMillis, overridden }`), and is
 * surfaced in the "Configuration" tab.
 */
export function StepDetailsPanel({ workflowId, step, stepConfig, onClose, onReplayed }) {
  const [tab, setTab] = useState('overview');
  const [replaying, setReplaying] = useState(false);

  const details = useLoad(() => api.step(workflowId, step.stepName), [workflowId, step.stepName]);
  const context = useLoad(() => api.stepContext(workflowId, step.stepName), [workflowId, step.stepName]);
  const logs = useLoad(() => api.stepLogs(workflowId, step.stepName), [workflowId, step.stepName]);

  const retries = Number(step.retryCount) || 0;
  const effectiveStepConfig = details.data?.stepConfig || stepConfig;
  const scheduledRetry = details.data?.scheduledRetry;
  const circuitBreakerState = details.data?.circuitBreakerState;

  async function replay() {
    setReplaying(true);
    try {
      await api.replayStep(workflowId, step.stepName);
      details.reload();
      context.reload();
      logs.reload();
      onReplayed?.();
    } finally {
      setReplaying(false);
    }
  }

  return (
    <aside className="floating-panel">
      <header className="floating-panel-header">
        <div>
          <div className="eyebrow">Step</div>
          <h2>{step.stepName}</h2>
        </div>
        <StatusBadge value={step.state} />
        <button className="icon-button" onClick={onClose} aria-label="Close">
          <X size={16} />
        </button>
      </header>

      <Tabs items={TABS} active={tab} onChange={setTab} />

      <div className="floating-panel-body">
        {tab === 'overview' && (
          <dl className="details">
            <dt>Type</dt>
            <dd className="mono">{step.typeClassName || '—'}</dd>
            <dt>Started</dt>
            <dd>{formatDateTime(step.dateStarted)}</dd>
            <dt>Ended</dt>
            <dd>{formatDateTime(step.dateEnded)}</dd>
            <dt>Retries</dt>
            <dd>
              {retries > 0 ? (
                <span className="status status-suspended">
                  <RotateCcw size={12} />
                  {retries}
                </span>
              ) : (
                'None'
              )}
            </dd>
            <dt>Parent step</dt>
            <dd className="mono">{context.data?.parentStepName || '—'}</dd>
            <dt>Circuit state</dt>
            <dd>{circuitBreakerState || (effectiveStepConfig?.circuitBreakerEnabled ? 'Loading…' : 'Not configured')}</dd>
            <dt>Scheduled retry</dt>
            <dd>{scheduledRetry ? formatDateTime(scheduledRetry.scheduledAt) : 'Not scheduled'}</dd>
          </dl>
        )}

        {tab === 'config' &&
          (details.loading && !effectiveStepConfig ? (
            <Loading />
          ) : details.error && !effectiveStepConfig ? (
            <ErrorState error={details.error} retry={details.reload} />
          ) : effectiveStepConfig ? (
            <dl className="details">
              <dt>Execution</dt>
              <dd>{effectiveStepConfig.async ? 'Asynchronous' : 'Synchronous'}</dd>
              <dt>Auto retry</dt>
              <dd>{effectiveStepConfig.retryEnabled ? 'Enabled' : 'Disabled'}</dd>
              <dt>Max attempts</dt>
              <dd>{effectiveStepConfig.retryEnabled ? effectiveStepConfig.maxAttempts : '—'}</dd>
              <dt>Retry delay</dt>
              <dd>{effectiveStepConfig.retryEnabled ? formatDuration(effectiveStepConfig.delayMillis) : '—'}</dd>
              <dt>Policy</dt>
              <dd>{effectiveStepConfig.overridden ? 'Per-step override' : 'Workflow default'}</dd>
              <dt>Retries used</dt>
              <dd>
                {retries}
                {effectiveStepConfig.retryEnabled ? ` / ${effectiveStepConfig.maxAttempts}` : ''}
              </dd>
              <dt>Circuit breaker</dt>
              <dd>{effectiveStepConfig.circuitBreakerEnabled ? 'Enabled' : 'Disabled'}</dd>
              <dt>Breaker name</dt>
              <dd className="mono">{effectiveStepConfig.circuitBreakerName || '—'}</dd>
              <dt>Fallback</dt>
              <dd>{effectiveStepConfig.circuitBreakerFallback || '—'}</dd>
              <dt>Breaker state</dt>
              <dd>{circuitBreakerState || (effectiveStepConfig.circuitBreakerEnabled ? 'Loading…' : '—')}</dd>
              <dt>Open wait</dt>
              <dd>
                {effectiveStepConfig.circuitBreakerEnabled
                  ? formatDuration(effectiveStepConfig.circuitBreakerWaitOpenMillis)
                  : '—'}
              </dd>
              <dt>Half-open calls</dt>
              <dd>{effectiveStepConfig.circuitBreakerEnabled ? effectiveStepConfig.circuitBreakerPermittedHalfOpenCalls ?? '—' : '—'}</dd>
              <dt>Scheduled automatic retry</dt>
              <dd>
                {scheduledRetry
                  ? `${formatDateTime(scheduledRetry.scheduledAt)} (${formatDuration(scheduledRetry.remainingMillis)} remaining)`
                  : 'Not scheduled'}
              </dd>
              <dt>Scheduled retry type</dt>
              <dd>{scheduledRetry?.type || '—'}</dd>
              <dt>Scheduled retry reason</dt>
              <dd>{scheduledRetry?.reason || '—'}</dd>
              <dt>Scheduled batch size</dt>
              <dd>{scheduledRetry?.batchSize ?? '—'}</dd>
            </dl>
          ) : (
            <p className="muted-note">No configuration available for this step.</p>
          ))}

        {tab === 'input' &&
          (context.loading ? (
            <Loading />
          ) : context.error ? (
            <ErrorState error={context.error} retry={context.reload} />
          ) : context.data?.input == null ? (
            <p className="muted-note">No input recorded for this step.</p>
          ) : (
            <JsonViewer value={context.data.input} />
          ))}

        {tab === 'output' &&
          (context.loading ? (
            <Loading />
          ) : context.error ? (
            <ErrorState error={context.error} retry={context.reload} />
          ) : context.data?.output == null ? (
            <p className="muted-note">No output recorded for this step.</p>
          ) : (
            <JsonViewer value={context.data.output} />
          ))}

        {tab === 'logs' &&
          (logs.loading ? (
            <Loading />
          ) : logs.error ? (
            <ErrorState error={logs.error} />
          ) : logs.data?.length ? (
            <div className="logs">
              {logs.data.map((log) => (
                <div className={`log-row ${log.action === 'RETRY' ? 'log-row-retry' : ''}`} key={log.id}>
                  <time>{new Date(log.dateCreated).toLocaleString()}</time>
                  <strong>{log.action}</strong>
                  <code>{log.snapshotJson}</code>
                </div>
              ))}
            </div>
          ) : (
            <Empty title="No log entries" />
          ))}
      </div>

      {REPLAYABLE_STATES.includes(String(step.state).toUpperCase()) && (
        <footer className="floating-panel-footer">
          <button className="button primary" onClick={replay} disabled={replaying}>
            <RotateCcw size={15} />
            {replaying ? 'Replaying…' : 'Replay step'}
          </button>
        </footer>
      )}
    </aside>
  );
}
