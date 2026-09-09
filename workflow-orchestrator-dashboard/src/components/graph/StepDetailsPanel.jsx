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
  { id: 'input', label: 'Input' },
  { id: 'output', label: 'Output' },
  { id: 'logs', label: 'Logs' },
];

const REPLAYABLE_STATES = ['FAILED', 'SUSPENDED'];

/**
 * Floating card shown alongside the graph when a step node is selected. Loads the
 * step's context/attributes and audit logs on demand and lets operators replay
 * failed or suspended steps without leaving the graph view.
 */
export function StepDetailsPanel({ pipelineId, step, onClose, onReplayed }) {
  const [tab, setTab] = useState('overview');
  const [replaying, setReplaying] = useState(false);

  const context = useLoad(() => api.stepContext(pipelineId, step.stepName), [pipelineId, step.stepName]);
  const logs = useLoad(() => api.stepLogs(pipelineId, step.stepName), [pipelineId, step.stepName]);

  async function replay() {
    setReplaying(true);
    try {
      await api.replayStep(pipelineId, step.stepName);
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
            <dd>{step.dateStarted ? new Date(step.dateStarted).toLocaleString() : '—'}</dd>
            <dt>Ended</dt>
            <dd>{step.dateEnded ? new Date(step.dateEnded).toLocaleString() : '—'}</dd>
            <dt>Parent step</dt>
            <dd className="mono">{context.data?.parentStepName || '—'}</dd>
          </dl>
        )}

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
                <div className="log-row" key={log.id}>
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
