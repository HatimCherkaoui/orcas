import { Activity, AlertCircle, CheckCircle2, Circle, Clock3, SkipForward } from 'lucide-react';

/** Icon for every status the engine actually emits (see `Status.java`); an
 * unmapped/future status still falls back to a neutral clock rather than
 * being silently dropped. */
const ICONS = {
  INIT: Circle,
  STARTED: Clock3,
  RUNNING: Activity,
  RUNNING_ASYNC: Activity,
  SUCCESS: CheckCircle2,
  FAILED: AlertCircle,
  SUSPENDED: AlertCircle,
  SKIPPED: SkipForward,
};

/** Small colored pill showing a workflow/step status with a matching icon. */
export function StatusBadge({ value }) {
  const status = String(value || 'UNKNOWN').toUpperCase();
  const Icon = ICONS[status] || Clock3;
  return (
    <span className={`status status-${status.toLowerCase()}`}>
      <Icon size={13} />
      {status.replace(/_/g, ' ')}
    </span>
  );
}
