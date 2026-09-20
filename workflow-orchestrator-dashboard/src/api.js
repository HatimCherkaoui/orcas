const API_BASE = (import.meta.env.VITE_API_URL || 'http://localhost:8080/api/orchestrator').replace(/\/$/, '');

function query(params = {}) {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value == null || value === '') return;
    search.set(key, String(value));
  });
  return search.toString();
}

async function request(path, options = {}) {
  const response = await fetch(`${API_BASE}${path}`, {
    headers: {
      Accept: 'application/json',
      ...(options.headers || {}),
    },
    ...options,
  });

  if (!response.ok) {
    const body = await response.text();
    throw new Error(body || `${response.status} ${response.statusText}`);
  }

  if (response.status === 204) {
    return null;
  }

  const body = await response.text();
  return body ? JSON.parse(body) : null;
}

export const api = {
  workflows: (filters = {}) => {
    const qs = query(filters);
    return request(`/workflows${qs ? `?${qs}` : ''}`);
  },
  workflow: (workflowId) => request(`/workflows/${encodeURIComponent(workflowId)}`),
  definition: (workflow) => request(`/workflows/definitions/${encodeURIComponent(workflow)}`),
  steps: (workflowId) => request(`/workflows/${encodeURIComponent(workflowId)}/steps`),
  step: (workflowId, stepName) =>
    request(`/workflows/${encodeURIComponent(workflowId)}/steps/${encodeURIComponent(stepName)}`),
  stepContext: (workflowId, stepName) =>
    request(`/workflows/${encodeURIComponent(workflowId)}/steps/${encodeURIComponent(stepName)}/context`),
  context: (workflowId) => request(`/workflows/${encodeURIComponent(workflowId)}/context`),
  metadata: (workflowId) => request(`/workflows/${encodeURIComponent(workflowId)}/metadata`),
  workflowLogs: (workflowId) => request(`/workflows/${encodeURIComponent(workflowId)}/logs`),
  stepLogs: (workflowId, stepName) =>
    request(`/workflows/${encodeURIComponent(workflowId)}/steps/${encodeURIComponent(stepName)}/logs`),
  replayStep: (workflowId, stepName) =>
    request(`/workflows/${encodeURIComponent(workflowId)}/steps/${encodeURIComponent(stepName)}/replay`, {
      method: 'POST',
    }),
  replaySuspendedSteps: (filters = {}) => {
    const qs = query(filters);
    return request(`/workflows/replays/suspended${qs ? `?${qs}` : ''}`, { method: 'POST' });
  },
  topics: () => request('/kafka/topics'),
  topic: (name) => request(`/kafka/topics/${encodeURIComponent(name)}`),
  consumerGroups: () => request('/kafka/consumer-groups'),
  consumerGroup: (groupId) => request(`/kafka/consumer-groups/${encodeURIComponent(groupId)}`),
};
