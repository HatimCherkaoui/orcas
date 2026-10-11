import { newIdentifier } from './utils/observability';
const API_BASE = (import.meta.env.VITE_API_URL || '/api/orchestrator').replace(/\/$/, '');

function query(params = {}) {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value == null || value === '') return;
    if (Array.isArray(value)) {
      value.forEach((item) => search.append(key, String(item)));
      return;
    }
    search.set(key, String(value));
  });
  return search.toString();
}

function normalizeError(status, statusText, body, path) {
  let message = `${status} ${statusText}`.trim();
  if (body) {
    try {
      const parsed = JSON.parse(body);
      message = parsed.message || parsed.error || parsed.detail || message;
    } catch {
      message = body.length > 300 ? `${body.slice(0, 300)}…` : body;
    }
  }
  const error = new Error(message);
  error.status = status;
  error.path = path;
  error.endpoint = `${API_BASE}${path}`;
  return error;
}

const dashboardCorrelationId = newIdentifier();

async function request(path, options = {}) {
  const url = `${API_BASE}${path}`;
  let response;
  try {
    response = await fetch(url, {
      ...options,
      headers: {
        Accept: 'application/json',
        'X-Request-ID': newIdentifier(),
        'X-Transaction-ID': newIdentifier(),
        'X-Correlation-ID': dashboardCorrelationId,
        ...(options.body != null ? { 'Content-Type': 'application/json' } : {}),
        ...(options.headers || {}),
      },
    });
  } catch (cause) {
    if (cause.name === 'AbortError') throw cause;
    const error = new Error(`Cannot reach the workflow service at ${API_BASE}`);
    error.cause = cause;
    error.endpoint = url;
    error.status = 0;
    throw error;
  }

  const body = await response.text();
  if (!response.ok) throw normalizeError(response.status, response.statusText, body, path);
  if (!body) return null;

  try {
    return JSON.parse(body);
  } catch {
    const error = new Error(`Invalid JSON returned by ${path}`);
    error.endpoint = url;
    error.status = response.status;
    throw error;
  }
}

function encode(value) {
  return encodeURIComponent(value);
}

export const api = {
  baseUrl: API_BASE,
  workflows: (filters = {}, options = {}) => {
    const qs = query(filters);
    return request(`/workflows${qs ? `?${qs}` : ''}`, options);
  },
  workflow: (workflowId, options = {}) => request(`/workflows/${encode(workflowId)}`, options),
  definition: (workflow, options = {}) => request(`/workflows/definitions/${encode(workflow)}`, options),
  steps: (workflowId, options = {}) => request(`/workflows/${encode(workflowId)}/steps`, options),
  step: (workflowId, stepName, options = {}) => request(`/workflows/${encode(workflowId)}/steps/${encode(stepName)}`, options),
  stepContext: (workflowId, stepName, options = {}) => request(`/workflows/${encode(workflowId)}/steps/${encode(stepName)}/context`, options),
  context: (workflowId, options = {}) => request(`/workflows/${encode(workflowId)}/context`, options),
  metadata: (workflowId, options = {}) => request(`/workflows/${encode(workflowId)}/metadata`, options),
  workflowLogs: (workflowId, options = {}) => request(`/workflows/${encode(workflowId)}/logs`, options),
  stepLogs: (workflowId, stepName, options = {}) => request(`/workflows/${encode(workflowId)}/steps/${encode(stepName)}/logs`, options),
  replayStep: (workflowId, stepName) => request(`/workflows/${encode(workflowId)}/steps/${encode(stepName)}/replay`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
  }),
  replaySuspendedSteps: (filters = {}) => request('/workflows/replay', {
    method: 'POST',
    body: JSON.stringify(filters),
  }),
  topics: (options = {}) => request('/kafka/topics', options),
  topic: (name, options = {}) => request(`/kafka/topics/${encode(name)}`, options),
  consumerGroups: (options = {}) => request('/kafka/consumer-groups', options),
  consumerGroup: (groupId, options = {}) => request(`/kafka/consumer-groups/${encode(groupId)}`, options),
};
