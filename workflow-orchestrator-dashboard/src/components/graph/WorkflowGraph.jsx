import { useEffect, useMemo } from 'react';
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

/**
 * The dashboard's primary, full-viewport interactive surface: it turns the workflow
 * definition's routing metadata plus the live step states into an auto-laid-out React
 * Flow graph. Users pan/zoom to explore steps that don't fit on screen and click any
 * node to inspect it (the caller renders the resulting detail as a floating panel).
 */
function GraphCanvas({ steps, definition, selectedStepName, onSelectStep }) {
  const { fitView } = useReactFlow();
  const graph = useMemo(() => buildGraph(steps, definition), [steps, definition]);
  const [nodes, setNodes, onNodesChange] = useNodesState([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState([]);

  useEffect(() => {
    const laidOut = layoutNodes(graph.nodes, graph.edges);
    setNodes(laidOut);
    setEdges(graph.edges);
    const timer = setTimeout(() => fitView({ padding: 0.2, duration: 250 }), 30);
    return () => clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [graph]);

  // Keep the "selected" flag on nodes in sync without rebuilding the whole graph/layout.
  useEffect(() => {
    setNodes((current) =>
      current.map((node) => ({ ...node, selected: node.id === selectedStepName }))
    );
  }, [selectedStepName, setNodes]);

  return (
    <ReactFlow
      nodes={nodes}
      edges={edges}
      onNodesChange={onNodesChange}
      onEdgesChange={onEdgesChange}
      onNodeClick={(_, node) => onSelectStep?.(node.data.step)}
      onPaneClick={() => onSelectStep?.(null)}
      nodeTypes={nodeTypes}
      fitView
      minZoom={0.2}
      maxZoom={1.6}
      proOptions={{ hideAttribution: true }}
    >
      <Background variant={BackgroundVariant.Dots} gap={18} size={1} />
      <Controls showInteractive={false} />
      <MiniMap pannable zoomable className="rf-minimap" nodeStrokeWidth={2} />
      <div className="graph-watermark">ORCAS</div>
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
