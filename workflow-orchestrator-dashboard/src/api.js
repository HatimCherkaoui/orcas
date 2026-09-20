// Thin REST client for the orchestrator service API. Every function returns a plain
// promise resolving to parsed JSON (or null for empty responses) and rejects with a
// plain Error carrying the response body/text as its message on non-2xx responses.

const BASE_URL = import.meta.env.VITE_API_URL || '/api/orchestrator';

async function request(path, options = {}) {
  const { allowMissing, ...fetchOptions } = options;
  const response = await fetch(`${BASE_URL}${path}`, {
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      ...(fetchOptions.headers || {}),
    },
    ...fetchOptions,
  });

  if (response.status === 404 && allowMissing) return null;
  if (!response.ok) {
    const message = (await response.text()) || `HTTP ${response.status}`;
    throw new Error(message);
  }
  if (response.status === 204) return null;
  return response.json();
}

function query(params) {
  const entries = Object.entries(params).filter(([, value]) => value !== '' && value != null);
  return new URLSearchParams(entries).toString();
}

export const api = {
  // Pipelines
  workflows: (filters) => request(`/workflows?${query(filters)}`),
  workflow: (id) => request(`/workflows/${encodeURIComponent(id)}`),
  definition: (workflow) => request(`/workflows/definitions/${encodeURIComponent(workflow)}`),
  context: (id) => request(`/workflows/${encodeURIComponent(id)}/context`, { allowMissing: true }),
  metadata: (id) => request(`/workflows/${encodeURIComponent(id)}/metadata`, { allowMissing: true }),
  workflowLogs: (id) => request(`/workflows/${encodeURIComponent(id)}/logs`),

  // Steps
  steps: (id) => request(`/workflows/${encodeURIComponent(id)}/steps`),
  step: (id, step) =>
    request(`/workflows/${encodeURIComponent(id)}/steps/${encodeURIComponent(step)}`),
  stepContext: (id, step) =>
    request(`/workflows/${encodeURIComponent(id)}/steps/${encodeURIComponent(step)}/context`, {
      allowMissing: true,
    }),
  stepLogs: (id, step) =>
    request(`/workflows/${encodeURIComponent(id)}/steps/${encodeURIComponent(step)}/logs`),
  replayStep: (id, step) =>
    request(`/workflows/${encodeURIComponent(id)}/steps/${encodeURIComponent(step)}/replay`, {
      method: 'POST',
    }),

  // Kafka administration
  topics: () => request('/kafka/topics'),
  topic: (name) => request(`/kafka/topics/${encodeURIComponent(name)}`),
  consumerGroups: () => request('/kafka/consumer-groups'),
  consumerGroup: (id) => request(`/kafka/consumer-groups/${encodeURIComponent(id)}`),
};
