import { useState, useCallback } from 'react';
import { X, RotateCcw, ShieldCheck, Clock, Zap, CheckCircle2, Copy, Check } from 'lucide-react';
import { api } from '../../api';
import { useLoad } from '../../hooks/useLoad';
import { StatusBadge } from '../common/StatusBadge';
import { Loading, ErrorState, Empty } from '../common/States';
import { JsonViewer } from '../common/Inputs';
import { Tabs } from '../layout/Tabs';

const TABS = [
  { id: 'overview', label: 'Overview' },
  { id: 'config', label: 'Config & Retry' },
  { id: 'input', label: 'Input' },
  { id: 'output', label: 'Output' },
  { id: 'logs', label: 'Logs' },
];

const REPLAYABLE_STATES = ['FAILED', 'SUSPENDED'];

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

/** Small copy-to-clipboard button with a momentary checkmark flash */
function CopyButton({ value, className = '' }) {
  const [copied, setCopied] = useState(false);
  const handleCopy = useCallback((e) => {
    e.stopPropagation();
    const text = typeof value === 'string' ? value : JSON.stringify(value, null, 2);
    navigator.clipboard?.writeText(text);
    setCopied(true);
    setTimeout(() => setCopied(false), 1800);
  }, [value]);
  return (
    <button
      className={`copy-mini-btn ${className}`}
      onClick={handleCopy}
      title="Copy to clipboard"
      aria-label="Copy to clipboard"
    >
      {copied ? <Check size={11} className="copied-icon" /> : <Copy size={11} />}
    </button>
  );
}

/** Section header row with label + optional copy button */
function SectionHeader({ label, value }) {
  return (
    <div className="panel-section-header">
      <span className="eyebrow">{label}</span>
      {value != null && <CopyButton value={value} />}
    </div>
  );
}

export function StepDetailsPanel({ workflowId, step, stepConfig, onClose, onReplayed }) {
  const [tab, setTab] = useState('overview');
  const [replaying, setReplaying] = useState(false);
  const [replayError, setReplayError] = useState(null);
  const [replaySuccess, setReplaySuccess] = useState(null);

  const stepName = step?.stepName || step?.name || '';
  const details = useLoad(() => (workflowId && stepName ? api.step(workflowId, stepName) : null), [workflowId, stepName]);
  const context = useLoad(() => (workflowId && stepName ? api.stepContext(workflowId, stepName) : null), [workflowId, stepName]);
  const logs = useLoad(() => (workflowId && stepName ? api.stepLogs(workflowId, stepName) : null), [workflowId, stepName]);

  const retries = Number(step?.retryCount ?? step?.retries ?? details.data?.retryCount ?? 0);
  const state = (step?.state || step?.status || details.data?.state || details.data?.status || 'PENDING').toUpperCase();

  const rawConfig = details.data?.stepConfig || stepConfig || {};
  const effectiveConfig = {
    async: rawConfig.async ?? Boolean(step?.typeClassName?.includes('Async') || state === 'RUNNING_ASYNC'),
    retryEnabled: rawConfig.retryEnabled ?? (retries > 0 || true),
    maxAttempts: rawConfig.maxAttempts || 3,
    delayMillis: rawConfig.delayMillis != null ? rawConfig.delayMillis : 1000,
    overridden: Boolean(rawConfig.overridden),
    circuitBreakerEnabled: rawConfig.circuitBreakerEnabled ?? Boolean(step?.circuitBreaker || details.data?.circuitBreakerState),
    circuitBreakerName: rawConfig.circuitBreakerName || `${stepName}-cb`,
    circuitBreakerState: details.data?.circuitBreakerState || step?.circuitBreaker || rawConfig.circuitBreakerState || 'CLOSED',
    circuitBreakerFallback: rawConfig.circuitBreakerFallback || null,
    circuitBreakerWaitOpenMillis: rawConfig.circuitBreakerWaitOpenMillis || 5000,
    circuitBreakerPermittedHalfOpenCalls: rawConfig.circuitBreakerPermittedHalfOpenCalls || 2,
  };

  const scheduledRetry = details.data?.scheduledRetry || step?.scheduledRetry || null;
  const circuitBreakerState = effectiveConfig.circuitBreakerState;

  async function replay() {
    setReplaying(true);
    setReplayError(null);
    setReplaySuccess(null);
    try {
      await api.replayStep(workflowId, stepName);
      setReplaySuccess('Replay queued successfully.');
      details.reload();
      context.reload();
      logs.reload();
      onReplayed?.();
    } catch (error) {
      setReplayError(error);
    } finally {
      setReplaying(false);
    }
  }

  return (
    <aside className="floating-panel">
      <header className="floating-panel-header">
        <div>
          <div className="eyebrow">Step Inspection</div>
          <h2>{stepName}</h2>
        </div>
        <StatusBadge value={state} />
        <button className="icon-button" onClick={onClose} aria-label="Close inspector">
          <X size={15} strokeWidth={1.6} />
        </button>
      </header>

      <Tabs items={TABS} active={tab} onChange={setTab} />

      <div className="floating-panel-body">
        {/* ─── Overview ─── */}
        {tab === 'overview' && (
          <div className="panel-overview">
            <dl className="details">
              <dt>Type</dt>
              <dd className="mono detail-truncate">{step?.typeClassName || 'DefaultStep'}</dd>
              <dt>State</dt>
              <dd><StatusBadge value={state} /></dd>
              <dt>Started</dt>
              <dd>{formatDateTime(step?.dateStarted || step?.startTime)}</dd>
              <dt>Ended</dt>
              <dd>{formatDateTime(step?.dateEnded || step?.endTime)}</dd>
              <dt>Duration</dt>
              <dd className="mono">{formatDuration(step?.duration)}</dd>
              <dt>Parent Step</dt>
              <dd className="mono">{context.data?.parentStepName || '—'}</dd>
              <dt>Retry Status</dt>
              <dd>
                <span className={`status ${retries > 0 ? 'status-suspended' : 'status-init'}`}>
                  <RotateCcw size={10} strokeWidth={1.8} /> {retries} of {effectiveConfig.maxAttempts} used
                </span>
              </dd>
              <dt>Circuit Breaker</dt>
              <dd>
                <span className={`status status-${circuitBreakerState === 'CLOSED' ? 'success' : circuitBreakerState === 'HALF_OPEN' ? 'suspended' : 'failed'}`}>
                  <Zap size={10} strokeWidth={1.8} /> {circuitBreakerState}
                </span>
              </dd>
              <dt>Scheduled Retry</dt>
              <dd>
                {scheduledRetry ? (
                  <span className="scheduled-pill">
                    <Clock size={11} strokeWidth={1.6} />
                    {formatDateTime(scheduledRetry.scheduledAt)} ({formatDuration(scheduledRetry.remainingMillis)} left)
                  </span>
                ) : (
                  <span className="muted-note">None pending</span>
                )}
              </dd>
            </dl>
          </div>
        )}

        {/* ─── Config & Retry ─── */}
        {tab === 'config' && (
          <div className="config-panel-container">
            {/* Retry Policy */}
            <div className="config-card">
              <div className="config-card-header">
                <RotateCcw size={14} strokeWidth={1.6} className="config-card-icon" />
                <div>
                  <span className="config-card-title">Retry Policy</span>
                  <span className="config-card-sub">{effectiveConfig.overridden ? 'Per-step override' : 'Engine default'}</span>
                </div>
                <span className={`status ${effectiveConfig.retryEnabled ? 'status-success' : 'status-skipped'}`}>
                  {effectiveConfig.retryEnabled ? 'Enabled' : 'Disabled'}
                </span>
              </div>
              <div className="config-card-grid">
                <div className="config-stat">
                  <span className="config-label">Max Attempts</span>
                  <span className="config-value mono">{effectiveConfig.maxAttempts}</span>
                </div>
                <div className="config-stat">
                  <span className="config-label">Retry Delay</span>
                  <span className="config-value mono">{formatDuration(effectiveConfig.delayMillis)}</span>
                </div>
                <div className="config-stat">
                  <span className="config-label">Backoff</span>
                  <span className="config-value">Exponential 2×</span>
                </div>
                <div className="config-stat">
                  <span className="config-label">Used / Max</span>
                  <span className="config-value mono">{retries} / {effectiveConfig.maxAttempts}</span>
                </div>
              </div>
              <div className="retry-progress-bar">
                <div
                  className="retry-progress-fill"
                  style={{ width: `${Math.min(100, (retries / effectiveConfig.maxAttempts) * 100)}%` }}
                />
              </div>
            </div>

            {/* Circuit Breaker */}
            <div className="config-card">
              <div className="config-card-header">
                <Zap size={14} strokeWidth={1.6} className="config-card-icon" />
                <div>
                  <span className="config-card-title">Circuit Breaker</span>
                  <span className="config-card-sub mono">{effectiveConfig.circuitBreakerName}</span>
                </div>
                <span className={`status status-${circuitBreakerState === 'CLOSED' ? 'success' : circuitBreakerState === 'HALF_OPEN' ? 'suspended' : 'failed'}`}>
                  {circuitBreakerState}
                </span>
              </div>
              <div className="config-card-grid">
                <div className="config-stat">
                  <span className="config-label">Wait (Open)</span>
                  <span className="config-value mono">{formatDuration(effectiveConfig.circuitBreakerWaitOpenMillis)}</span>
                </div>
                <div className="config-stat">
                  <span className="config-label">Half-Open Calls</span>
                  <span className="config-value mono">{effectiveConfig.circuitBreakerPermittedHalfOpenCalls}</span>
                </div>
                <div className="config-stat">
                  <span className="config-label">Fallback</span>
                  <span className="config-value">{effectiveConfig.circuitBreakerFallback || 'Default handler'}</span>
                </div>
                <div className="config-stat">
                  <span className="config-label">Health</span>
                  <span className="config-value">
                    {circuitBreakerState === 'CLOSED' ? 'Healthy' : circuitBreakerState === 'HALF_OPEN' ? 'Recovering' : 'Tripped'}
                  </span>
                </div>
              </div>
            </div>

            {/* Scheduled Retry */}
            {scheduledRetry ? (
              <div className="config-card alert-card">
                <div className="config-card-header">
                  <Clock size={14} strokeWidth={1.6} className="config-card-icon warning-icon" />
                  <div>
                    <span className="config-card-title">Scheduled Retry</span>
                    <span className="config-card-sub">{scheduledRetry.reason || 'Engine backoff policy'}</span>
                  </div>
                </div>
                <dl className="details details-padded">
                  <dt>Scheduled At</dt>
                  <dd>{formatDateTime(scheduledRetry.scheduledAt)}</dd>
                  <dt>Time Remaining</dt>
                  <dd className="mono">{formatDuration(scheduledRetry.remainingMillis)}</dd>
                  <dt>Schedule Type</dt>
                  <dd className="mono">{scheduledRetry.type || 'EXPONENTIAL_BACKOFF'}</dd>
                </dl>
              </div>
            ) : (
              <div className="config-notice">
                <ShieldCheck size={14} strokeWidth={1.6} />
                <span>No retry scheduled — step is stable or awaits operator replay.</span>
              </div>
            )}
          </div>
        )}

        {/* ─── Input ─── */}
        {tab === 'input' && (
          context.loading ? <Loading message="Loading step input..." /> :
          context.error ? <ErrorState error={context.error} retry={context.reload} /> :
          context.data?.input == null ? (
            <p className="muted-note">No input context recorded for this step.</p>
          ) : (
            <>
              <SectionHeader label="Step Input" value={context.data.input} />
              <JsonViewer value={context.data.input} />
            </>
          )
        )}

        {/* ─── Output ─── */}
        {tab === 'output' && (
          context.loading ? <Loading message="Loading step output..." /> :
          context.error ? <ErrorState error={context.error} retry={context.reload} /> :
          context.data?.output == null ? (
            <p className="muted-note">No output context recorded for this step.</p>
          ) : (
            <>
              <SectionHeader label="Step Output" value={context.data.output} />
              <JsonViewer value={context.data.output} />
            </>
          )
        )}

        {/* ─── Logs ─── */}
        {tab === 'logs' && (
          logs.loading ? <Loading message="Loading execution logs..." /> :
          logs.error ? <ErrorState error={logs.error} retry={logs.reload} /> :
          (Array.isArray(logs.data) ? logs.data : logs.data?.content || logs.data?.items || []).length ? (
            <div className="logs">
              {(Array.isArray(logs.data) ? logs.data : logs.data?.content || logs.data?.items || []).map((log) => (
                <div className={`log-row ${log.action === 'RETRY' ? 'log-row-retry' : ''}`} key={log.id}>
                  <time>{new Date(log.dateCreated || log.timestamp).toLocaleString()}</time>
                  <strong>{log.action}</strong>
                  {log.message && <p className="log-message">{log.message}</p>}
                  {log.snapshotJson && <code>{log.snapshotJson}</code>}
                </div>
              ))}
            </div>
          ) : (
            <Empty title="No log entries for this step" />
          )
        )}
      </div>

      {REPLAYABLE_STATES.includes(state) && (
        <footer className="floating-panel-footer">
          {replayError && <div className="inline-notice danger panel-notice">Replay failed: {replayError.message}</div>}
          {replaySuccess && <div className="inline-notice success panel-notice"><CheckCircle2 size={13} /> {replaySuccess}</div>}
          <button className="button primary" onClick={replay} disabled={replaying}>
            <RotateCcw size={14} strokeWidth={1.8} className={replaying ? 'spin' : ''} />
            {replaying ? 'Replaying…' : 'Replay step now'}
          </button>
        </footer>
      )}
    </aside>
  );
}
