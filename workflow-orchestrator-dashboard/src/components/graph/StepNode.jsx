import { Handle, Position } from '@xyflow/react';
import { AlertCircle, CheckCircle2, Circle, Clock3, Play, RotateCcw, SkipForward } from 'lucide-react';

/**
 * Visual metadata for every status the engine actually emits (`Status.java`:
 * INIT, STARTED, RUNNING, RUNNING_ASYNC, SUCCESS, FAILED, SUSPENDED, SKIPPED),
 * plus the dashboard-only `PENDING` sentinel used for steps the graph knows
 * about (via route metadata) but that haven't recorded any state yet. Steps
 * must always resolve to their *own* status here - never silently fall back
 * to the "not reached" PENDING look, or a resolved step (e.g. INIT once it
 * moves on) would incorrectly still read as pending.
 */
const STATE_META = {
  INIT: { icon: Circle, className: 'init' },
  STARTED: { icon: Clock3, className: 'started' },
  RUNNING: { icon: Play, className: 'running' },
  RUNNING_ASYNC: { icon: Play, className: 'running' },
  SUCCESS: { icon: CheckCircle2, className: 'success' },
  FAILED: { icon: AlertCircle, className: 'failed' },
  SUSPENDED: { icon: AlertCircle, className: 'suspended' },
  SKIPPED: { icon: SkipForward, className: 'skipped' },
  PENDING: { icon: Clock3, className: 'pending' },
};

/** Custom React Flow node rendering a single workflow step as a compact status card. */
export function StepNode({ data, selected }) {
  const state = String(data.step.state || 'PENDING').toUpperCase();
  // Fall back to a status-derived class (not PENDING) for any future/unknown
  // status name, so the node still reflects its real state dynamically
  // instead of looking indistinguishable from a step that hasn't started.
  const meta = STATE_META[state] || { icon: Clock3, className: state.toLowerCase() };
  const Icon = meta.icon;
  // Number of automatic retries recorded for this step by the engine. Only shown
  // when non-zero so healthy steps stay visually clean.
  const retries = Number(data.step.retryCount) || 0;

  return (
    <div className={`rf-step-node state-${meta.className} ${selected ? 'selected' : ''}`}>
      <Handle type="target" position={Position.Left} />
      <div className="rf-step-icon">
        <Icon size={15} />
      </div>
      <div className="rf-step-body">
        <strong>{data.step.stepName}</strong>
        <span className="rf-step-state">{state.replace(/_/g, ' ')}</span>
      </div>
      {retries > 0 && (
        <span
          className="rf-step-retries"
          title={`${retries} automatic ${retries === 1 ? 'retry' : 'retries'} recorded`}
        >
          <RotateCcw size={11} />
          {retries}
        </span>
      )}
      <Handle type="source" position={Position.Right} />
    </div>
  );
}

export const nodeTypes = { stepNode: StepNode };

