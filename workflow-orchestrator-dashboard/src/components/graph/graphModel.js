import dagre from '@dagrejs/dagre';

const NODE_WIDTH = 220;
const NODE_HEIGHT = 76;

/** Lays out nodes left-to-right using dagre, since the backend supplies no coordinates. */
export function layoutNodes(nodes, edges) {
  const graph = new dagre.graphlib.Graph();
  graph.setGraph({ rankdir: 'LR', nodesep: 48, ranksep: 110, marginx: 24, marginy: 24 });
  graph.setDefaultEdgeLabel(() => ({}));
  nodes.forEach((node) => graph.setNode(node.id, { width: NODE_WIDTH, height: NODE_HEIGHT }));
  edges.forEach((edge) => graph.setEdge(edge.source, edge.target));
  dagre.layout(graph);

  return nodes.map((node) => {
    const { x, y } = graph.node(node.id);
    return { ...node, position: { x: x - NODE_WIDTH / 2, y: y - NODE_HEIGHT / 2 } };
  });
}

const EDGE_STYLES = {
  PARALLEL: { className: 'parallel', label: 'parallel' },
  ASYNC: { className: 'async', label: 'async', animated: true },
  join: { className: 'join', label: 'join' },
  sequential: { className: 'sequential', label: '' },
};

function makeEdge(source, target, mode, key, triggerStatus) {
  const style = EDGE_STYLES[mode] || EDGE_STYLES.sequential;
  return {
    id: `${source}->${target}-${key || mode}`,
    source,
    target,
    type: 'smoothstep',
    animated: Boolean(style.animated || triggerStatus === 'RUNNING'),
    className: `rf-edge-${style.className}`,
    label: triggerStatus && triggerStatus !== 'SUCCESS' ? triggerStatus : style.label || undefined,
  };
}

/**
 * Converts a workflow definition and step states into React Flow nodes and edges.
 * Handles both routes-based definitions, dependencies-based definitions,
 * and falls back cleanly to sequential order.
 */
export function buildGraph(steps, definition) {
  const stepList = Array.isArray(steps) ? steps : steps?.content || steps?.items || [];
  const stepByName = new Map(
    stepList.map((step) => {
      const name = step.stepName || step.name;
      const state = (step.state || step.status || 'PENDING').toUpperCase();
      return [name, { ...step, stepName: name, state }];
    })
  );

  const stepConfigs = definition?.stepConfigs || {};
  const routes = definition?.routes || [];
  const defSteps = definition?.steps || [];

  const stepOrFallback = (name) => {
    const found = stepByName.get(name);
    if (found) return found;
    return {
      stepName: name,
      name,
      state: 'PENDING',
      status: 'PENDING',
      retryCount: 0,
      retries: 0
    };
  };

  const getEffectiveConfig = (name) => {
    if (stepConfigs[name]) return stepConfigs[name];
    const s = stepOrFallback(name);
    return {
      async: Boolean(s.typeClassName?.includes('Async') || s.state === 'RUNNING_ASYNC'),
      retryEnabled: true,
      maxAttempts: 3,
      delayMillis: 1000,
      overridden: false,
      circuitBreakerEnabled: Boolean(s.circuitBreaker || s.circuitBreakerState),
      circuitBreakerName: `${name}-cb`,
      circuitBreakerState: s.circuitBreaker || s.circuitBreakerState || 'CLOSED',
      circuitBreakerWaitOpenMillis: 5000,
      circuitBreakerPermittedHalfOpenCalls: 2
    };
  };

  const seen = new Set();
  const nodes = [];
  const edges = [];

  const addNode = (name) => {
    if (!name || seen.has(name)) return;
    seen.add(name);
    nodes.push({
      id: name,
      type: 'stepNode',
      data: {
        step: stepOrFallback(name),
        stepConfig: getEffectiveConfig(name),
      },
      position: { x: 0, y: 0 },
    });
  };

  // 1. If explicit routes are defined
  if (routes.length > 0) {
    addNode(routes[0]?.triggerStep || 'INIT');
    routes.forEach((route, index) => {
      addNode(route.triggerStep);
      const branches = route.branches || [];
      branches.forEach((branch) => {
        addNode(branch);
        edges.push(makeEdge(route.triggerStep, branch, route.mode, `${index}`, route.triggerStatus));
      });
      if (route.joinStep) {
        addNode(route.joinStep);
        branches.forEach((branch) => edges.push(makeEdge(branch, route.joinStep, 'join', `${index}-join`)));
      }
    });
    return { nodes, edges };
  }

  // 2. If steps define dependencies (DAG structure)
  const stepsWithDeps = defSteps.filter((s) => s.dependencies && s.dependencies.length > 0);
  if (stepsWithDeps.length > 0) {
    defSteps.forEach((s) => addNode(s.name || s.stepName));
    defSteps.forEach((s, idx) => {
      const stepName = s.name || s.stepName;
      if (s.dependencies && s.dependencies.length > 0) {
        s.dependencies.forEach((dep, depIdx) => {
          addNode(dep);
          edges.push(makeEdge(dep, stepName, 'sequential', `dep-${idx}-${depIdx}`));
        });
      }
    });
    return { nodes, edges };
  }

  // 3. Sequential fallback from recorded steps or definition steps
  const sourceSteps = stepList.length > 0 ? stepList : defSteps;
  const ordered = sourceSteps
    .map((s) => s.stepName || s.name)
    .filter((name) => name && name !== 'INIT');

  ordered.forEach((name) => addNode(name));
  ordered.forEach((name, index) => {
    if (index > 0) {
      edges.push(makeEdge(ordered[index - 1], name, 'sequential', `seq-${index}`));
    }
  });

  return { nodes, edges };
}
