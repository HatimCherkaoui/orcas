import { useEffect, useMemo, useRef } from 'react';
import {
  ReactFlow,
  ReactFlowProvider,
  Background,
  BackgroundVariant,
  Controls,
  MiniMap,
  useNodesState,
  useEdgesState,
  useReactFlow,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import { buildGraph, layoutNodes } from './graphModel';
import { nodeTypes } from './StepNode';
import { useTheme } from '../../hooks/useTheme';

/**
 * The dashboard's primary, full-viewport interactive surface: it turns the workflow
 * definition's routing metadata plus the live step states into an auto-laid-out React
 * Flow graph. Users pan/zoom to explore steps that don't fit on screen and click any
 * node to inspect it (the caller renders the resulting detail as a floating panel).
 */
function GraphCanvas({ steps, definition, selectedStepName, onSelectStep }) {
  const { fitView } = useReactFlow();
  const { theme } = useTheme();
  const layoutCache = useRef({ topology: null, positions: new Map() });
  const graph = useMemo(() => buildGraph(steps, definition), [steps, definition]);
  const [nodes, setNodes, onNodesChange] = useNodesState([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState([]);

  const topology = JSON.stringify([graph.nodes.map((node) => node.id), graph.edges.map(({ id, source, target }) => [id, source, target])]);
  useEffect(() => {
    const changed = layoutCache.current.topology !== topology;
    if (changed) {
      layoutCache.current = { topology, positions: new Map(layoutNodes(graph.nodes, graph.edges).map((node) => [node.id, node.position])) };
    }
    setNodes((current) => {
      const previous = new Map(current.map((node) => [node.id, node]));
      return graph.nodes.map((node) => {
        const old = previous.get(node.id);
        const selected = node.id === selectedStepName;
        const sameData = old && JSON.stringify(old.data) === JSON.stringify(node.data);
        if (!changed && sameData && old.selected === selected) return old;
        return { ...node, data: sameData ? old.data : node.data, selected,
          position: !changed && old ? old.position : layoutCache.current.positions.get(node.id) };
      });
    });
    setEdges(graph.edges.map((edge) => ({ ...edge, animated: edge.animated && graph.nodes.some((node) => node.id === edge.source && ['RUNNING', 'RUNNING_ASYNC'].includes(node.data.step.state)) })));
  }, [graph, topology, selectedStepName, setNodes, setEdges]);

  useEffect(() => {
    if (!graph.nodes.length) return;
    const frame = requestAnimationFrame(() => fitView({ padding: 0.2, duration: 0 }));
    return () => cancelAnimationFrame(frame);
  }, [topology, fitView]);

  return (
    <ReactFlow
      colorMode={theme}
      nodes={nodes}
      edges={edges}
      onNodesChange={onNodesChange}
      onEdgesChange={onEdgesChange}
      onNodeClick={(_, node) => onSelectStep?.(node.data.step)}
      onPaneClick={() => onSelectStep?.(null)}
      nodeTypes={nodeTypes}
      minZoom={0.2}
      maxZoom={1.6}
    >
      <Background variant={BackgroundVariant.Dots} gap={18} size={1} />
      <Controls showInteractive={false} />
      <MiniMap pannable zoomable className="rf-minimap" nodeStrokeWidth={2} />

    </ReactFlow>
  );
}

/** Public entry point: wraps `GraphCanvas` with the provider `useReactFlow` needs. */
export function WorkflowGraph(props) {
  return (
    <div className="workflow-graph">
      <ReactFlowProvider>
        <GraphCanvas {...props} />
      </ReactFlowProvider>
    </div>
  );
}
