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

async function request(path, options = {}) {
  const url = `${API_BASE}${path}`;
  let response;
  try {
    response = await fetch(url, {
      headers: {
        Accept: 'application/json',
        ...(options.body != null ? { 'Content-Type': 'application/json' } : {}),
        ...(options.headers || {}),
      },
      ...options,
    });
  } catch (cause) {
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
  workflows: (filters = {}) => {
    const qs = query(filters);
    return request(`/workflows${qs ? `?${qs}` : ''}`);
  },
  workflow: (workflowId) => request(`/workflows/${encode(workflowId)}`),
  definition: (workflow) => request(`/workflows/definitions/${encode(workflow)}`),
  steps: (workflowId) => request(`/workflows/${encode(workflowId)}/steps`),
  step: (workflowId, stepName) => request(`/workflows/${encode(workflowId)}/steps/${encode(stepName)}`),
  stepContext: (workflowId, stepName) => request(`/workflows/${encode(workflowId)}/steps/${encode(stepName)}/context`),
  context: (workflowId) => request(`/workflows/${encode(workflowId)}/context`),
  metadata: (workflowId) => request(`/workflows/${encode(workflowId)}/metadata`),
  workflowLogs: (workflowId) => request(`/workflows/${encode(workflowId)}/logs`),
  stepLogs: (workflowId, stepName) => request(`/workflows/${encode(workflowId)}/steps/${encode(stepName)}/logs`),
  replayStep: (workflowId, stepName) => request(`/workflows/${encode(workflowId)}/steps/${encode(stepName)}/replay`, { method: 'POST' }),
  replaySuspendedSteps: (filters = {}) => request('/workflows/replay', {
    method: 'POST',
    body: JSON.stringify(filters),
  }),
  topics: () => request('/kafka/topics'),
  topic: (name) => request(`/kafka/topics/${encode(name)}`),
  consumerGroups: () => request('/kafka/consumer-groups'),
  consumerGroup: (groupId) => request(`/kafka/consumer-groups/${encode(groupId)}`),
};
