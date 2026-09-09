import { Handle, Position } from '@xyflow/react';
import { AlertCircle, CheckCircle2, Clock3, Play } from 'lucide-react';

const STATE_META = {
  SUCCESS: { icon: CheckCircle2, className: 'success' },
  FAILED: { icon: AlertCircle, className: 'failed' },
  SUSPENDED: { icon: AlertCircle, className: 'suspended' },
  RUNNING: { icon: Play, className: 'running' },
  RUNNING_ASYNC: { icon: Play, className: 'running' },
  PENDING: { icon: Clock3, className: 'pending' },
};

/** Custom React Flow node rendering a single workflow step as a compact status card. */
export function StepNode({ data, selected }) {
  const state = String(data.step.state || 'UNKNOWN').toUpperCase();
  const meta = STATE_META[state] || STATE_META.PENDING;
  const Icon = meta.icon;

  return (
    <div className={`rf-step-node state-${meta.className} ${selected ? 'selected' : ''}`}>
      <Handle type="target" position={Position.Left} />
      <div className="rf-step-icon">
        <Icon size={15} />
      </div>
      <div className="rf-step-body">
        <strong>{data.step.stepName}</strong>
        <span className="rf-step-state">{state.replace('_', ' ')}</span>
      </div>
      <Handle type="source" position={Position.Right} />
    </div>
  );
}

export const nodeTypes = { stepNode: StepNode };
