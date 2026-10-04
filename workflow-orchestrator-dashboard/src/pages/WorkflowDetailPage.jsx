import { useMemo, useState } from 'react';
import { ArrowLeft, Database, FileText, RefreshCw, ScrollText, Sliders } from 'lucide-react';
import { api } from '../api';
import { useLoad } from '../hooks/useLoad';
import { Loading, ErrorState, Empty } from '../components/common/States';
import { StatusBadge } from '../components/common/StatusBadge';
import { Tabs } from '../components/layout/Tabs';
import { AutoRefresh } from '../components/common/Inputs';
import { WorkflowGraph } from '../components/graph/WorkflowGraph';
import { StepDetailsPanel } from '../components/graph/StepDetailsPanel';
import { WorkflowInfoPanel } from '../components/graph/WorkflowInfoPanel';
import { Shell } from '../components/layout/Shell';

const INFO_TABS = [
  { id: 'context', label: 'Context', icon: FileText },
  { id: 'metadata', label: 'Metadata', icon: Database },
  { id: 'logs', label: 'Logs', icon: ScrollText },
  { id: 'config', label: 'Configuration', icon: Sliders },
];

export default function WorkflowDetailPage({ id, navigate }) {
  const workflowId = id && id !== 'undefined' ? id : null;
  const [selectedStep, setSelectedStep] = useState(null);
  const [autoReload, setAutoReload] = useState(true);
  const [refreshInterval, setRefreshInterval] = useState(5000);
  const [infoTab, setInfoTab] = useState(null);

  const workflow = useLoad(() => (workflowId ? api.workflow(workflowId) : null), [workflowId], { interval: autoReload ? refreshInterval : 0 });
  const steps = useLoad(() => (workflowId ? api.steps(workflowId) : null), [workflowId], { interval: autoReload ? refreshInterval : 0 });
  const workflowName = workflow.data?.workflow || workflow.data?.workflowName || workflowId;
  const definition = useLoad(() => (workflowName && workflowName !== workflowId ? api.definition(workflowName) : null), [workflowName]);
  const context = useLoad(() => (workflowId ? api.context(workflowId) : null), [workflowId], { interval: autoReload ? Math.max(refreshInterval, 7000) : 0 });
  const metadata = useLoad(() => (workflowId ? api.metadata(workflowId) : null), [workflowId], { interval: autoReload ? Math.max(refreshInterval, 7000) : 0 });
  const auditLog = useLoad(() => (workflowId ? api.workflowLogs(workflowId) : null), [workflowId], { interval: autoReload ? Math.max(refreshInterval, 7000) : 0 });

  const stepRows = Array.isArray(steps.data) ? steps.data : steps.data?.content || steps.data?.items || [];
  const stepConfigs = definition.data?.stepConfigs || {};

  const infoLoads = useMemo(
    () => ({
      context: { ...context, data: context.data?.context },
      metadata: { ...metadata, data: metadata.data?.values },
      logs: auditLog,
      config: {
        loading: definition.loading,
        error: definition.error,
        data: definition.data || {
          workflow: workflowName,
          version: '1.0.0',
          stepConfigs,
          steps: stepRows,
        },
        reload: definition.reload,
      },
    }),
    [context, metadata, auditLog, definition, workflowName, stepConfigs, stepRows]
  );

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
      <Shell page="/" navigate={navigate} flush>
      <div className="graph-page">
        <div className="graph-empty">
          <Empty
            title="Missing workflow id"
            text="Go back to the workflow list and open a workflow to inspect its execution graph."
          />
          <button className="button" onClick={() => navigate('/')}>Back to workflows</button>
        </div>
      </div>
      </Shell>
    );
  }

  const activeInfoTab = INFO_TABS.find((tab) => tab.id === infoTab) || null;

  return (
    <Shell page="/" navigate={navigate} flush>
    <div className="graph-page">
      <header className="graph-topbar">
        <div className="graph-topbar-title">
          <button className="icon-button" onClick={() => navigate('/')} aria-label="Back to workflows">
            <ArrowLeft size={16} />
          </button>
          <div className="graph-title-copy">
            <div className="graph-title-line"><strong>{workflowName}</strong>{workflow.data?.status && <StatusBadge value={workflow.data.status} />}</div>
            <span className="mono">{workflowId}</span>
          </div>
        </div>

        <div className="graph-topbar-actions">
          <Tabs items={INFO_TABS} active={infoTab} onChange={openInfoTab} />
          <AutoRefresh enabled={autoReload} onEnabledChange={setAutoReload} interval={refreshInterval} onIntervalChange={setRefreshInterval} />
          <button className="button" onClick={refresh}>
            <RefreshCw size={15} className={workflow.loading || steps.loading ? 'spin' : ''} />
            Refresh
          </button>
        </div>
      </header>

      <div className="graph-stage">
        {(!workflow.data && workflow.loading) || (!steps.data && steps.loading) ? (
          <Loading />
        ) : workflow.error ? (
          <ErrorState error={workflow.error} retry={workflow.reload} />
        ) : (
          <WorkflowGraph
            steps={stepRows}
            definition={definition.data || null}
            selectedStepName={selectedStep?.stepName || null}
            onSelectStep={selectStep}
          />
        )}

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
            subtitle={workflowName}
            load={infoLoads[activeInfoTab.id]}
            onClose={() => setInfoTab(null)}
          />
        )}
      </div>
    </div>
    </Shell>
  );
}

