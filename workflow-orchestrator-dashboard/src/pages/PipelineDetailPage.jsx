import { useState } from 'react';
import { ArrowLeft, Database, FileText, RefreshCw, ScrollText } from 'lucide-react';
import { api } from '../api';
import { useLoad } from '../hooks/useLoad';
import { Loading, ErrorState, Empty } from '../components/common/States';
import { StatusBadge } from '../components/common/StatusBadge';
import { WorkflowGraph } from '../components/graph/WorkflowGraph';
import { StepDetailsPanel } from '../components/graph/StepDetailsPanel';
import { WorkflowInfoPanel } from '../components/graph/WorkflowInfoPanel';

const INFO_TABS = [
  { id: 'context', label: 'Context', icon: FileText },
  { id: 'metadata', label: 'Metadata', icon: Database },
  { id: 'logs', label: 'Logs', icon: ScrollText },
];

export default function PipelineDetailPage({ id, navigate }) {
  const workflowId = id && id !== 'undefined' ? id : null;

  const workflow = useLoad(() => (workflowId ? api.workflow(workflowId) : null), [workflowId]);
  const steps = useLoad(() => (workflowId ? api.steps(workflowId) : null), [workflowId]);
  const definition = useLoad(() => (workflow.data?.workflow ? api.definition(workflow.data.workflow) : null), [workflow.data?.workflow]);
  const context = useLoad(() => (workflowId ? api.context(workflowId) : null), [workflowId]);
  const metadata = useLoad(() => (workflowId ? api.metadata(workflowId) : null), [workflowId]);
  const auditLog = useLoad(() => (workflowId ? api.workflowLogs(workflowId) : null), [workflowId]);

  const [selectedStep, setSelectedStep] = useState(null);
  const [infoTab, setInfoTab] = useState(null);

  const stepConfigs = definition.data?.stepConfigs || {};
  const activeInfoTab = INFO_TABS.find((tab) => tab.id === infoTab) || null;
  const infoLoads = {
    context: { ...context, data: context.data?.context },
    metadata: { ...metadata, data: metadata.data?.values },
    logs: auditLog,
  };

  function refresh() {
    workflow.reload();
    steps.reload();
    definition.reload();
    context.reload();
    metadata.reload();
    auditLog.reload();
  }

  function selectStep(step) {
    setSelectedStep(step);
    if (step) setInfoTab(null);
  }

  function openInfoTab(tabId) {
    setSelectedStep(null);
    setInfoTab((current) => (current === tabId ? null : tabId));
  }

  if (!workflowId) {
    return (
      <div className="graph-page">
        <div className="graph-empty">
          <Empty title="Missing workflow id" text="Go back to the workflow list and open a workflow to inspect its execution graph." />
          <button className="button" onClick={() => navigate('/')}>Back to workflows</button>
        </div>
      </div>
    );
  }

  return (
    <div className="graph-page">
      <header className="graph-topbar">
        <div className="graph-topbar-title">
          <button className="icon-button" onClick={() => navigate('/')} aria-label="Back to workflows">
            <ArrowLeft size={16} />
          </button>
          <div>
            <strong>{workflow.data?.workflow || 'Workflow'}</strong>
            <span className="mono">{workflowId}</span>
          </div>
        </div>

        {workflow.data?.status && <StatusBadge value={workflow.data.status} />}
        <span className="graph-topbar-count">{steps.data?.length || 0} steps</span>

        <nav className="graph-topbar-tabs">
          {INFO_TABS.map(({ id: tabId, label, icon: Icon }) => (
            <button key={tabId} className={infoTab === tabId ? 'active' : ''} onClick={() => openInfoTab(tabId)}>
              <Icon size={14} />
              {label}
            </button>
          ))}
        </nav>

        <button className="icon-button" onClick={refresh} aria-label="Refresh">
          <RefreshCw size={16} />
        </button>
      </header>

      <div className="graph-stage">
        {workflow.loading || steps.loading ? (
          <Loading />
        ) : workflow.error ? (
          <ErrorState error={workflow.error} retry={workflow.reload} />
        ) : steps.error ? (
          <ErrorState error={steps.error} retry={steps.reload} />
        ) : (
          <WorkflowGraph
            steps={steps.data || []}
            definition={definition.data}
            selectedStepName={selectedStep?.stepName}
            onSelectStep={selectStep}
          />
        )}
      </div>

      {selectedStep && (
        <StepDetailsPanel
          workflowId={workflowId}
          step={selectedStep}
          stepConfig={stepConfigs[selectedStep.stepName]}
          onClose={() => setSelectedStep(null)}
          onReplayed={refresh}
        />
      )}

      {!selectedStep && activeInfoTab && (
        <WorkflowInfoPanel
          tabId={activeInfoTab.id}
          title={activeInfoTab.label}
          subtitle={workflow.data?.workflow || workflowId}
          load={infoLoads[activeInfoTab.id]}
          onClose={() => setInfoTab(null)}
        />
      )}
    </div>
  );
}
