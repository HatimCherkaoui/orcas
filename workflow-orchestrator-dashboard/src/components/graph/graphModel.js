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
    animated: Boolean(style.animated),
    className: `rf-edge-${style.className}`,
    label: triggerStatus && triggerStatus !== 'SUCCESS' ? triggerStatus : style.label || undefined,
  };
}

/**
 * Converts a workflow definition (`{ routes: [{ triggerStep, mode, branches, joinStep }] }`)
 * plus the live step states recorded so far into React Flow nodes/edges. Falls back to a
 * simple sequential chain of recorded steps when no route metadata is available.
 */
export function buildGraph(steps, definition) {
  const stepByName = new Map((steps || []).map((step) => [step.stepName, step]));
  const stepOrFallback = (name) => stepByName.get(name) || { stepName: name, state: 'PENDING' };
  const routes = definition?.routes || [];

  const seen = new Set();
  const nodes = [];
  const edges = [];

  const addNode = (name) => {
    if (seen.has(name)) return;
    seen.add(name);
    nodes.push({ id: name, type: 'stepNode', data: { step: stepOrFallback(name) }, position: { x: 0, y: 0 } });
  };

  if (routes.length === 0) {
    const ordered = (steps || []).filter((step) => step.stepName !== 'INIT');
    ordered.forEach((step) => addNode(step.stepName));
    ordered.forEach((step, index) => {
      if (index > 0) edges.push(makeEdge(ordered[index - 1].stepName, step.stepName, 'sequential'));
    });
    return { nodes, edges };
  }

  addNode('INIT');
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
