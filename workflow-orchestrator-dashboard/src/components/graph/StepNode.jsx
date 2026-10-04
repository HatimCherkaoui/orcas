import { Handle, Position } from '@xyflow/react';
import { AlertCircle, CheckCircle2, Circle, Clock3, Play, RotateCcw, SkipForward, Zap } from 'lucide-react';

const STATE_META = {
  INIT: { icon: Circle, className: 'init', label: 'Init' },
  STARTED: { icon: Clock3, className: 'started', label: 'Started' },
  RUNNING: { icon: Play, className: 'running', label: 'Running', pulse: true },
  RUNNING_ASYNC: { icon: Play, className: 'running', label: 'Running Async', pulse: true },
  SUCCESS: { icon: CheckCircle2, className: 'success', label: 'Success' },
  FAILED: { icon: AlertCircle, className: 'failed', label: 'Failed' },
  SUSPENDED: { icon: AlertCircle, className: 'suspended', label: 'Suspended' },
  SKIPPED: { icon: SkipForward, className: 'skipped', label: 'Skipped' },
  PENDING: { icon: Clock3, className: 'pending', label: 'Pending' },
};

/** Custom React Flow node rendering a single workflow step as a modern monitoring card. */
export function StepNode({ data, selected }) {
  const step = data?.step || {};
  const stepConfig = data?.stepConfig || {};

  const stepName = step.stepName || step.name || 'Unnamed Step';
  const rawState = (step.state || step.status || 'PENDING').toUpperCase();
  const meta = STATE_META[rawState] || { icon: Clock3, className: rawState.toLowerCase(), label: rawState };
  const Icon = meta.icon;

  const retries = Number(step.retryCount ?? step.retries ?? 0);
  const maxAttempts = Number(stepConfig.maxAttempts || 3);
  const hasCircuitBreaker = Boolean(stepConfig.circuitBreakerEnabled || step.circuitBreaker);
  const cbState = step.circuitBreakerState || step.circuitBreaker || stepConfig.circuitBreakerState || (hasCircuitBreaker ? 'CLOSED' : null);

  return (
    <div className={`rf-step-node state-${meta.className} ${selected ? 'selected' : ''} ${meta.pulse ? 'node-pulse' : ''}`}>
      <Handle type="target" position={Position.Left} />
      
      <div className="rf-step-icon">
        <Icon size={15} />
      </div>

      <div className="rf-step-body">
        <div className="rf-step-title-row">
          <strong title={stepName}>{stepName}</strong>
          {hasCircuitBreaker && (
            <span
              className={`rf-step-circuit-badge cb-${String(cbState).toLowerCase()}`}
              title={`Circuit Breaker: ${cbState} (${stepConfig.circuitBreakerName || 'default'})`}
              aria-label="Circuit breaker status"
            >
              <Zap size={10} />
            </span>
          )}
        </div>
        <div className="rf-step-meta-row">
          <span className="rf-step-state">{meta.label}</span>
          {step.duration != null && <span className="rf-step-duration mono">{step.duration}ms</span>}
        </div>
      </div>

      {retries > 0 && (
        <span
          className="rf-step-retries"
          title={`${retries} retry attempt(s) recorded (Max: ${maxAttempts})`}
        >
          <RotateCcw size={10} />
          <span>{retries}/{maxAttempts}</span>
        </span>
      )}

      <Handle type="source" position={Position.Right} />
    </div>
  );
}

export const nodeTypes = { stepNode: StepNode };
