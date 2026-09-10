import { useState } from 'react';
import { ArrowLeft, ListTree, RefreshCw, ScrollText, Tags } from 'lucide-react';
import { api } from '../api';
import { useLoad } from '../hooks/useLoad';
import { StatusBadge } from '../components/common/StatusBadge';
import { Loading, ErrorState } from '../components/common/States';
import { WorkflowGraph } from '../components/graph/WorkflowGraph';
import { StepDetailsPanel } from '../components/graph/StepDetailsPanel';
import { WorkflowInfoPanel } from '../components/graph/WorkflowInfoPanel';

const INFO_TABS = [
  { id: 'context', label: 'Context', icon: ListTree, subtitle: 'Original business context' },
  { id: 'metadata', label: 'Metadata', icon: Tags, subtitle: 'Technical key/value data' },
  { id: 'logs', label: 'Audit log', icon: ScrollText, subtitle: 'Full audit trail' },
];

/**
 * The dashboard's main screen: a full-viewport, non-scrolling interactive workflow
 * graph. Node clicks and the top-bar info tabs both open a floating panel over the
 * graph (never a page navigation or a full-page scroll) so the graph stays the one
 * constant, dominant point of reference - only the graph canvas itself pans/zooms
 * when a pipeline has more steps than fit on screen.
 */
export default function PipelineDetailPage({ id, navigate }) {
  const pipelineId = id && id !== 'undefined' ? id : null;

  const workflow = useLoad(() => (pipelineId ? api.workflow(pipelineId) : Promise.reject(new Error('Missing pipeline id'))), [pipelineId]);
  const steps = useLoad(() => (pipelineId ? api.steps(pipelineId) : null), [pipelineId]);
  const definition = useLoad(() => (workflow.data ? api.definition(workflow.data.workflow) : null), [workflow.data?.workflow]);

  const context = useLoad(() => (pipelineId ? api.context(pipelineId) : null), [pipelineId]);
  const metadata = useLoad(() => (pipelineId ? api.metadata(pipelineId) : null), [pipelineId]);
  const auditLog = useLoad(() => (pipelineId ? api.workflowLogs(pipelineId) : null), [pipelineId]);
  const infoLoaders = { context, metadata, logs: auditLog };
  const infoValues = { context: context.data?.context, metadata: metadata.data?.values, logs: auditLog.data };

  const [selectedStep, setSelectedStep] = useState(null);
  const [infoTab, setInfoTab] = useState(null);

  function selectStep(step) {
    setInfoTab(null);
    setSelectedStep(step);
  }

  function openInfoTab(id) {
    setSelectedStep(null);
    setInfoTab((current) => (current === id ? null : id));
  }

  function refresh() {
    workflow.reload();
    steps.reload();
  }

  if (workflow.loading) return <CenteredState><Loading /></CenteredState>;
  if (workflow.error) return <CenteredState><ErrorState error={workflow.error} retry={workflow.reload} /></CenteredState>;

  const activeInfoTab = INFO_TABS.find((tab) => tab.id === infoTab);
  // Static per-step configuration (async flag + effective retry policy) returned
  // alongside the routing graph by `/workflows/definitions/{workflow}`.
  const stepConfigs = definition.data?.stepConfigs || {};

  return (
    <div className="graph-page">
      <header className="graph-topbar">
        <button className="icon-button" onClick={() => navigate('/')} aria-label="Back to pipelines">
          <ArrowLeft size={16} />
        </button>

        <div className="graph-topbar-title">
          <strong>{workflow.data.workflow}</strong>
          <span className="mono">{pipelineId}</span>
        </div>

        <StatusBadge value={workflow.data.status} />

        <span className="graph-topbar-count">{steps.data?.length || 0} steps</span>

        <nav className="graph-topbar-tabs">
          {INFO_TABS.map(({ id, label, icon: Icon }) => (
            <button key={id} className={infoTab === id ? 'active' : ''} onClick={() => openInfoTab(id)}>
              <Icon size={14} />
              {label}
            </button>
          ))}
        </nav>

        <button className="icon-button" onClick={refresh} aria-label="Refresh">
          <RefreshCw size={16} />
        </button>
      </header>

      <div className="graph-canvas">
        {steps.loading ? (
          <Loading />
        ) : steps.error ? (
          <ErrorState error={steps.error} retry={steps.reload} />
        ) : (
          <WorkflowGraph
            steps={steps.data}
            definition={definition.data}
            selectedStepName={selectedStep?.stepName}
            onSelectStep={selectStep}
          />
        )}
      </div>

      {selectedStep && (
        <StepDetailsPanel
          pipelineId={pipelineId}
          step={selectedStep}
          stepConfig={stepConfigs[selectedStep.stepName]}
          onClose={() => setSelectedStep(null)}
          onReplayed={refresh}
        />
      )}

      {activeInfoTab && (
        <WorkflowInfoPanel
          tabId={activeInfoTab.id}
          title={activeInfoTab.label}
          subtitle={activeInfoTab.subtitle}
          load={{ ...infoLoaders[activeInfoTab.id], data: infoValues[activeInfoTab.id] }}
          onClose={() => setInfoTab(null)}
        />
      )}
    </div>
  );
}

function CenteredState({ children }) {
  return <div className="graph-page graph-page-centered">{children}</div>;
}

