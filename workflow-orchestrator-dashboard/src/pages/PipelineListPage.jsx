import { useState } from 'react';
import { Activity, Filter, Layers3, RefreshCw, Server } from 'lucide-react';
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
  status: '',
  stepName: '',
  stepStatus: '',
  createdFrom: '',
  createdTo: '',
  page: 0,
  size: 20,
};

/** Converts a `YYYY-MM-DD` date input value into an ISO instant, at the start or end of that day. */
function toInstant(dateValue, endOfDay) {
  if (!dateValue) return '';
  return `${dateValue}T${endOfDay ? '23:59:59.999' : '00:00:00.000'}Z`;
}

/** Landing page: a filterable, pageable table of pipeline executions, linking into the graph view. */
export default function PipelineListPage({ navigate }) {
  const [filters, setFilters] = useState(DEFAULT_FILTERS);

  const query = {
    ...filters,
    createdFrom: toInstant(filters.createdFrom, false),
    createdTo: toInstant(filters.createdTo, true),
  };
  const load = useLoad(() => api.workflows(query), [JSON.stringify(query)]);
  const rows = load.data?.content || [];

  const updateFilter = (key, value) => setFilters((current) => ({ ...current, [key]: value, page: 0 }));
  const setPage = (page) => setFilters((current) => ({ ...current, page }));

  return (
    <Shell page="/" navigate={navigate}>
      <PageHeader
        eyebrow="Operations"
        title="Pipelines"
        subtitle="Monitor executions and replay suspended activities"
        actions={
          <button className="button" onClick={load.reload}>
            <RefreshCw size={15} />
            Refresh
          </button>
        }
      />

      <div className="content">
        <div className="stats-grid">
          <StatCard label="Executions" value={load.data?.totalElements ?? '—'} icon={Layers3} />
          <StatCard label="Running" value={rows.filter((r) => r.status === 'RUNNING').length} icon={Activity} />
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
            <SearchBox value={filters.workflow} onChange={(v) => updateFilter('workflow', v)} placeholder="Workflow name" />
            <SelectField
              label="Workflow status"
              value={filters.status}
              onChange={(v) => updateFilter('status', v)}
              options={STATUS_OPTIONS}
            />
            <SearchBox value={filters.stepName} onChange={(v) => updateFilter('stepName', v)} placeholder="Step name" />
            <SelectField
              label="Step status"
              value={filters.stepStatus}
              onChange={(v) => updateFilter('stepStatus', v)}
              options={STEP_STATUS_OPTIONS}
            />
            <DateField label="Created from" value={filters.createdFrom} onChange={(v) => updateFilter('createdFrom', v)} />
            <DateField label="Created to" value={filters.createdTo} onChange={(v) => updateFilter('createdTo', v)} />
          </div>
        </Card>

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
            {rows.map((row) => (
              <button
                className="table-row"
                key={row.workflowId}
                onClick={() => navigate(`/${encodeURIComponent(row.workflowId)}`)}
              >
                <strong>{row.workflow}</strong>
                <StatusBadge value={row.status} />
                <span>{row.currentStep || '—'}</span>
                <time>{row.dateCreated ? new Date(row.dateCreated).toLocaleString() : '—'}</time>
                <code>{row.workflowId}</code>
              </button>
            ))}
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
