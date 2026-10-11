export function executionDuration(execution) {
  if (!execution) return null;
  const explicit = execution.duration ?? execution.durationMillis;
  if (explicit != null && Number.isFinite(Number(explicit))) return Number(explicit);
  const start = Date.parse(execution.dateStarted || execution.startTime);
  const end = Date.parse(execution.dateEnded || execution.endTime);
  return Number.isFinite(start) && Number.isFinite(end) ? Math.max(0, end - start) : null;
}
export function formatDuration(millis) {
  if (millis == null || !Number.isFinite(Number(millis))) return '—';
  if (millis < 1000) return `${Math.round(millis)} ms`;
  if (millis < 60000) return `${Number((millis / 1000).toFixed(2))} s`;
  const totalSeconds = Math.round(millis / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return seconds ? `${minutes} min ${seconds} s` : `${minutes} min`;
}
