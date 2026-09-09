import { Activity, AlertCircle, CheckCircle2, Clock3 } from 'lucide-react';

const ICONS = {
  SUCCESS: CheckCircle2,
  FAILED: AlertCircle,
  SUSPENDED: AlertCircle,
  RUNNING: Activity,
  RUNNING_ASYNC: Activity,
};

/** Small colored pill showing a workflow/step status with a matching icon. */
export function StatusBadge({ value }) {
  const status = String(value || 'UNKNOWN').toUpperCase();
  const Icon = ICONS[status] || Clock3;
  return (
    <span className={`status status-${status.toLowerCase()}`}>
      <Icon size={13} />
      {status.replace('_', ' ')}
    </span>
  );
}
