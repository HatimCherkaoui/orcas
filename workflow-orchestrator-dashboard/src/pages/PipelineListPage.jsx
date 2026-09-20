import { useEffect, useMemo, useState } from 'react';
import { Activity, ArrowDown, ArrowUp, CircleDot, Clock3, Filter, Layers3, RefreshCw, RotateCcw, Search, X } from 'lucide-react';
import { api } from '../api';
import { useLoad } from '../hooks/useLoad';
import { Shell } from '../components/layout/Shell';
import { PageHeader } from '../components/layout/PageHeader';
import { Card, Loading, ErrorState, Empty } from '../components/common/States';
import { SearchBox, SelectField, DateField, StatCard, Pagination } from '../components/common/Inputs';
import { StatusBadge } from '../components/common/StatusBadge';

const STATUS_OPTIONS = ['STARTED', 'RUNNING', 'SUCCESS', 'FAILED', 'SUSPENDED', 'SKIPPED'];
const STEP_STATUS_OPTIONS = ['RUNNING', 'SUCCESS', 'FAILED', 'SUSPENDED', 'SKIPPED'];
const DEFAULT_FILTERS = { workflow: '', workflowId: '', status: '', stepName: '', stepStatus: '', createdFrom: '', createdTo: '', page: 0, size: 10 };

function adaptivePageSize() {
  if (typeof window === 'undefined') return 10;
  return Math.max(8, Math.min(20, Math.floor((window.innerHeight - 400) / 52)));
}

function toInstant(dateValue, endOfDay) {
  if (!dateValue) return '';
  return `${dateValue}T${endOfDay ? '23:59:59.999' : '00:00:00.000'}Z`;
}

function formatDate(value) {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.valueOf())) return '—';
  return new Intl.DateTimeFormat(undefined, { month: 'short', day: '2-digit', hour: '2-digit', minute: '2-digit' }).format(date);
}

function sortRows(rows, key, direction) {
  if (!key) return rows;
  return [...rows].sort((a, b) => {
    const av = key === 'created' ? new Date(a.dateCreated || 0).valueOf() : String(a[key] ?? '').toLowerCase();
    const bv = key === 'created' ? new Date(b.dateCreated || 0).valueOf() : String(b[key] ?? '').toLowerCase();
    const result = typeof av === 'number' && typeof bv === 'number' ? av - bv : String(av).localeCompare(String(bv));
    return direction === 'desc' ? -result : result;
  });
}

function SortButton({ label, active, direction, onClick }) {
  return <button className={`sort-button ${active ? 'active' : ''}`} onClick={onClick}>{label}{active ? direction === 'asc' ? <ArrowUp size={13} /> : <ArrowDown size={13} /> : null}</button>;
}

export default function PipelineListPage({ navigate }) {
  const [filters, setFilters] = useState(() => ({ ...DEFAULT_FILTERS, size: adaptivePageSize() }));
  const [autoPageSize, setAutoPageSize] = useState(true);
  const [filtersOpen, setFiltersOpen] = useState(true);
  const [sort, setSort] = useState({ key: 'created', direction: 'desc' });
  const [batchReplay, setBatchReplay] = useState({ running: false, result: null, error: null });

  useEffect(() => {
    if (!autoPageSize) return undefined;
    const resize = () => setFilters((current) => ({ ...current, size: adaptivePageSize(), page: 0 }));
    window.addEventListener('resize', resize);
    return () => window.removeEventListener('resize', resize);
  }, [autoPageSize]);

  const query = useMemo(() => ({
    workflowId: filters.workflowId,
    workflow: filters.workflow,
    status: filters.status,
    stepName: filters.stepName,
    stepStatus: filters.stepStatus,
    createdFrom: toInstant(filters.createdFrom, false),
    createdTo: toInstant(filters.createdTo, true),
    page: filters.page,
    size: filters.size,
  }), [filters]);

  const load = useLoad(() => api.workflows(query), [JSON.stringify(query)], { interval: 8000 });
  const rawRows = load.data?.content || [];
  const rows = sortRows(rawRows, sort.key, sort.direction);
  const suspendedFilterActive = filters.status === 'SUSPENDED' || filters.stepStatus === 'SUSPENDED';
  const activeFilterCount = Object.entries(filters).filter(([key, value]) => !['page', 'size'].includes(key) && value).length;

  function updateFilter(key, value) {
    setBatchReplay({ running: false, result: null, error: null });
    setFilters((current) => ({ ...current, [key]: value, page: 0 }));
  }

  function updateSize(size) {
    setAutoPageSize(false);
    setFilters((current) => ({ ...current, size, page: 0 }));
  }

  function changeSort(key) {
    setSort((current) => current.key === key ? { key, direction: current.direction === 'asc' ? 'desc' : 'asc' } : { key, direction: 'asc' });
  }

  function clearFilters() {
    setFilters((current) => ({ ...DEFAULT_FILTERS, size: current.size }));
  }

  async function replaySuspended() {
    setBatchReplay({ running: true, result: null, error: null });
    try {
      const result = await api.replaySuspendedSteps({
        workflowId: query.workflowId, workflow: query.workflow, status: query.status,
        stepName: query.stepName, stepStatus: query.stepStatus,
        createdFrom: query.createdFrom, createdTo: query.createdTo,
      });
      setBatchReplay({ running: false, result, error: null });
      load.reload();
    } catch (error) {
      setBatchReplay({ running: false, result: null, error });
    }
  }

  return (
    <Shell page="/" navigate={navigate}>
      <div className="page page-list">
        <PageHeader
          eyebrow="Operations / Workflows"
          title="Workflow executions"
          subtitle="Monitor execution state, inspect steps and replay suspended work."
          actions={<>
            {suspendedFilterActive && <button className="button primary" onClick={replaySuspended} disabled={batchReplay.running || !(load.data?.totalElements > 0)}><RotateCcw size={15} />{batchReplay.running ? 'Replaying…' : 'Replay suspended'}</button>}
            <button className="button" onClick={load.reload}><RefreshCw size={15} className={load.loading ? 'spin' : ''} />Refresh</button>
          </>}
        />

        <div className="stats-grid">
          <StatCard label="Executions" value={load.data?.totalElements ?? '—'} icon={Layers3} />
          <StatCard label="Running now" value={rawRows.filter((row) => ['RUNNING', 'STARTED'].includes(row.status)).length} icon={Activity} tone="info" />
          <StatCard label="Suspended" value={rawRows.filter((row) => row.status === 'SUSPENDED').length} icon={CircleDot} tone="danger" />
          <StatCard label="Page" value={load.data ? `${load.data.page + 1}/${Math.max(load.data.totalPages, 1)}` : '—'} icon={Clock3} />
        </div>

        <Card className={`workflow-toolbar ${filtersOpen ? 'expanded' : ''}`}>
          <div className="toolbar-topline">
            <button className={`toolbar-filter ${filtersOpen ? 'active' : ''}`} onClick={() => setFiltersOpen((value) => !value)}><Filter size={15} /> Filters {activeFilterCount > 0 && <span>{activeFilterCount}</span>}</button>
            <div className="toolbar-summary">{load.data?.totalElements ?? '—'} executions · auto refresh 8s</div>
            {activeFilterCount > 0 && <button className="link-button" onClick={clearFilters}><X size={14} /> Clear</button>}
          </div>
          {filtersOpen && <div className="filter-grid compact-grid">
            <SearchBox label="Workflow" value={filters.workflow} onChange={(value) => updateFilter('workflow', value)} placeholder="Search workflow…" />
            <SearchBox label="Execution ID" value={filters.workflowId} onChange={(value) => updateFilter('workflowId', value)} placeholder="Paste execution ID…" />
            <SelectField label="Workflow status" value={filters.status} onChange={(value) => updateFilter('status', value)} options={STATUS_OPTIONS} placeholder="Any status" />
            <SearchBox label="Step" value={filters.stepName} onChange={(value) => updateFilter('stepName', value)} placeholder="Search step…" />
            <SelectField label="Step status" value={filters.stepStatus} onChange={(value) => updateFilter('stepStatus', value)} options={STEP_STATUS_OPTIONS} placeholder="Any status" />
            <DateField label="Created from" value={filters.createdFrom} onChange={(value) => updateFilter('createdFrom', value)} />
            <DateField label="Created to" value={filters.createdTo} onChange={(value) => updateFilter('createdTo', value)} />
          </div>}
        </Card>

        {batchReplay.result && <div className="inline-notice success"><CircleDot size={14} /> Replayed {batchReplay.result.replayed} of {batchReplay.result.matched} suspended step(s).</div>}
        {batchReplay.error && <div className="inline-notice danger"><CircleDot size={14} /> Replay failed: {batchReplay.error.message}</div>}

        <Card className="workflow-table-card">
          <div className="table-head workflow-table-head">
            <SortButton label="Workflow" active={sort.key === 'workflow'} direction={sort.direction} onClick={() => changeSort('workflow')} />
            <SortButton label="Status" active={sort.key === 'status'} direction={sort.direction} onClick={() => changeSort('status')} />
            <SortButton label="Last step" active={sort.key === 'currentStep'} direction={sort.direction} onClick={() => changeSort('currentStep')} />
            <SortButton label="Created" active={sort.key === 'created'} direction={sort.direction} onClick={() => changeSort('created')} />
            <span>Execution ID</span>
          </div>
          {load.loading && !rows.length ? <Loading /> : load.error && !rows.length ? <ErrorState error={load.error} retry={load.reload} /> : rows.length ? rows.map((row) => (
            <button className="table-row workflow-row" key={row.workflowId} onClick={() => navigate(`/${encodeURIComponent(row.workflowId)}`)}>
              <div className="workflow-cell"><strong title={row.workflow}>{row.workflow || 'Unnamed workflow'}</strong><span>{row.workflowId}</span></div>
              <StatusBadge value={row.status} />
              <span className="step-cell" title={row.currentStep || undefined}>{row.currentStep || '—'}</span>
              <time dateTime={row.dateCreated}>{formatDate(row.dateCreated)}</time>
              <code className="mono id-cell" title={row.workflowId}>{row.workflowId}</code>
            </button>
          )) : <Empty icon={Search} title="No workflows found" text="Try changing the filters or clearing the search." />}
          {load.data && <Pagination page={load.data.page || 0} totalPages={load.data.totalPages || 0} totalElements={load.data.totalElements || 0} size={filters.size} onChange={(page) => setFilters((current) => ({ ...current, page }))} onSizeChange={updateSize} />}
        </Card>
      </div>
    </Shell>
  );
}
