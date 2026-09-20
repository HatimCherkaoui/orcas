import { useState } from 'react';
import { Activity, Filter, Layers3, RefreshCw, RotateCcw, Server } from 'lucide-react';
import { api } from '../api';
import { useLoad } from '../hooks/useLoad';
import { Shell } from '../components/layout/Shell';
import { PageHeader } from '../components/layout/PageHeader';
import { Card, Loading, ErrorState } from '../components/common/States';
import { SearchBox, SelectField, DateField, StatCard, Pagination } from '../components/common/Inputs';
import { StatusBadge } from '../components/common/StatusBadge';

const STATUS_OPTIONS = ['STARTED', 'RUNNING', 'SUCCESS', 'FAILED', 'SUSPENDED', 'SKIPPED'];
const STEP_STATUS_OPTIONS = ['RUNNING', 'SUCCESS', 'FAILED', 'SUSPENDED', 'SKIPPED'];

const DEFAULT_FILTERS = {
  workflow: '',
  workflowId: '',
  status: '',
  stepName: '',
  stepStatus: '',
  createdFrom: '',
  createdTo: '',
  page: 0,
  size: 8,
};

function toInstant(dateValue, endOfDay) {
  if (!dateValue) return '';
  return `${dateValue}T${endOfDay ? '23:59:59.999' : '00:00:00.000'}Z`;
}

export default function PipelineListPage({ navigate }) {
  const [filters, setFilters] = useState(DEFAULT_FILTERS);
  const [batchReplay, setBatchReplay] = useState({ running: false, result: null, error: null });

  const query = {
    workflowId: filters.workflowId,
    workflow: filters.workflow,
    status: filters.status,
    stepName: filters.stepName,
    stepStatus: filters.stepStatus,
    createdFrom: toInstant(filters.createdFrom, false),
    createdTo: toInstant(filters.createdTo, true),
    page: filters.page,
    size: filters.size,
  };

  const load = useLoad(() => api.workflows(query), [JSON.stringify(query)]);
  const rows = load.data?.content || [];
  const suspendedFilterActive = filters.status === 'SUSPENDED' || filters.stepStatus === 'SUSPENDED';

  function updateFilter(key, value) {
    setBatchReplay({ running: false, result: null, error: null });
    setFilters((current) => ({ ...current, [key]: value, page: 0 }));
  }

  function setPage(page) {
    setFilters((current) => ({ ...current, page }));
  }

  async function replaySuspended() {
    setBatchReplay({ running: true, result: null, error: null });
    try {
      const result = await api.replaySuspendedSteps({
        workflowId: query.workflowId,
        workflow: query.workflow,
        status: query.status,
        stepName: query.stepName,
        stepStatus: query.stepStatus,
        createdFrom: query.createdFrom,
        createdTo: query.createdTo,
      });
      setBatchReplay({ running: false, result, error: null });
      load.reload();
    } catch (error) {
      setBatchReplay({ running: false, result: null, error });
    }
  }

  return (
    <Shell page="/" navigate={navigate}>
      <PageHeader
        eyebrow="Operations"
        title="Workflows"
        subtitle="Search workflow executions, inspect live state, and replay suspended steps."
        actions={
          <div style={{ display: 'flex', gap: 8 }}>
            {suspendedFilterActive && (
              <button
                className="button primary"
                onClick={replaySuspended}
                disabled={batchReplay.running || !(load.data?.totalElements > 0)}
              >
                <RotateCcw size={15} />
                {batchReplay.running ? 'Retrying suspended…' : 'Retry suspended list'}
              </button>
            )}
            <button className="button" onClick={load.reload}>
              <RefreshCw size={15} />
              Refresh
            </button>
          </div>
        }
      />

      <div className="content">
        <div className="stats-grid">
          <StatCard label="Executions" value={load.data?.totalElements ?? '—'} icon={Layers3} />
          <StatCard label="Running" value={rows.filter((row) => row.status === 'RUNNING').length} icon={Activity} />
          <StatCard
            label="Page"
            value={load.data ? `${load.data.page + 1}/${Math.max(load.data.totalPages, 1)}` : '—'}
            icon={Server}
          />
        </div>

        <Card className="filter-card">
          <div className="filter-title">
            <Filter size={16} />
            <strong>Filters</strong>
          </div>
          <div className="filter-grid">
            <SearchBox value={filters.workflow} onChange={(value) => updateFilter('workflow', value)} placeholder="Workflow name" />
            <SearchBox value={filters.workflowId} onChange={(value) => updateFilter('workflowId', value)} placeholder="Workflow id" />
            <SelectField label="Workflow status" value={filters.status} onChange={(value) => updateFilter('status', value)} options={STATUS_OPTIONS} />
            <SearchBox value={filters.stepName} onChange={(value) => updateFilter('stepName', value)} placeholder="Step name" />
            <SelectField label="Step status" value={filters.stepStatus} onChange={(value) => updateFilter('stepStatus', value)} options={STEP_STATUS_OPTIONS} />
            <DateField label="Created from" value={filters.createdFrom} onChange={(value) => updateFilter('createdFrom', value)} />
            <DateField label="Created to" value={filters.createdTo} onChange={(value) => updateFilter('createdTo', value)} />
          </div>
        </Card>

        {suspendedFilterActive && batchReplay.result && (
          <p className="muted-note">
            Batch replay submitted for {batchReplay.result.replayed} of {batchReplay.result.matched} suspended step(s)
            {batchReplay.result.failed ? ` (${batchReplay.result.failed} failed to submit).` : '.'}
          </p>
        )}
        {suspendedFilterActive && batchReplay.error && (
          <p className="muted-note">Unable to submit batch replay: {batchReplay.error.message}</p>
        )}

        {load.loading ? (
          <Loading />
        ) : load.error ? (
          <ErrorState error={load.error} retry={load.reload} />
        ) : (
          <Card>
            <div className="table-head">
              <span>Workflow</span>
              <span>Status</span>
              <span>Last step</span>
              <span>Created</span>
              <span>ID</span>
            </div>
            {rows.length ? (
              rows.map((row) => (
                <button
                  className="table-row"
                  key={row.workflowId}
                  onClick={() => navigate(`/${encodeURIComponent(row.workflowId)}`)}
                >
                  <strong title={row.workflow}>{row.workflow}</strong>
                  <StatusBadge value={row.status} />
                  <span title={row.currentStep || undefined}>{row.currentStep || '—'}</span>
                  <time>{row.dateCreated ? new Date(row.dateCreated).toLocaleString() : '—'}</time>
                  <code className="mono" title={row.workflowId}>{row.workflowId}</code>
                </button>
              ))
            ) : (
              <div className="empty-table muted-note">No workflows matched the current filters.</div>
            )}
            {load.data && (
              <Pagination
                page={load.data.page}
                totalPages={load.data.totalPages}
                totalElements={load.data.totalElements}
                onChange={setPage}
              />
            )}
          </Card>
        )}
      </div>
    </Shell>
  );
}


