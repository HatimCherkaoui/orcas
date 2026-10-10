import { useEffect, useMemo, useState } from 'react';
import {
  Activity,
  AlertCircle,
  ArrowDown,
  ArrowUp,
  Check,
  CheckCircle2,
  CircleDot,
  Clock3,
  Copy,
  Filter,
  Layers3,
  Play,
  RefreshCw,
  RotateCcw,
  Search,
  X,
  Zap
} from 'lucide-react';
import { api } from '../api';
import { useLoad } from '../hooks/useLoad';
import { Shell } from '../components/layout/Shell';
import { PageHeader } from '../components/layout/PageHeader';
import { Card, Loading, ErrorState, Empty } from '../components/common/States';
import { SearchBox, SelectField, DateField, StatCard, Pagination, AutoRefresh } from '../components/common/Inputs';
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

function formatRelativeTime(value) {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.valueOf())) return '—';
  const diffSec = Math.floor((Date.now() - date.valueOf()) / 1000);
  if (diffSec < 60) return 'just now';
  if (diffSec < 3600) return `${Math.floor(diffSec / 60)}m ago`;
  if (diffSec < 86400) return `${Math.floor(diffSec / 3600)}h ago`;
  if (diffSec < 604800) return `${Math.floor(diffSec / 86400)}d ago`;
  return new Intl.DateTimeFormat(undefined, { month: 'short', day: '2-digit' }).format(date);
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
    let av = '';
    let bv = '';
    if (key === 'created') {
      av = new Date(a.dateCreated || a.created || a.createdAt || a.startTime || 0).valueOf();
      bv = new Date(b.dateCreated || b.created || b.createdAt || b.startTime || 0).valueOf();
    } else if (key === 'workflow') {
      av = String(a.workflow || a.workflowName || a.name || '').toLowerCase();
      bv = String(b.workflow || b.workflowName || b.name || '').toLowerCase();
    } else if (key === 'workflowId') {
      av = String(a.workflowId || a.id || '').toLowerCase();
      bv = String(b.workflowId || b.id || '').toLowerCase();
    } else if (key === 'currentStep') {
      av = String(a.currentStep || a.stepName || '').toLowerCase();
      bv = String(b.currentStep || b.stepName || '').toLowerCase();
    } else if (key === 'status') {
      av = String(a.status || a.state || '').toLowerCase();
      bv = String(b.status || b.state || '').toLowerCase();
    } else {
      av = String(a[key] ?? '').toLowerCase();
      bv = String(b[key] ?? '').toLowerCase();
    }
    const result = typeof av === 'number' && typeof bv === 'number' ? av - bv : String(av).localeCompare(String(bv));
    return direction === 'desc' ? -result : result;
  });
}

function SortButton({ label, active, direction, onClick }) {
  return (
    <button className={`sort-button ${active ? 'active' : ''}`} onClick={onClick}>
      {label}
      {active ? (direction === 'asc' ? <ArrowUp size={13} /> : <ArrowDown size={13} />) : null}
    </button>
  );
}

export default function PipelineListPage({ navigate }) {
  const [filters, setFilters] = useState(() => ({ ...DEFAULT_FILTERS, size: adaptivePageSize() }));
  const [autoPageSize, setAutoPageSize] = useState(true);
  const [filtersOpen, setFiltersOpen] = useState(false);
  const [sort, setSort] = useState({ key: 'created', direction: 'desc' });
  const [autoReload, setAutoReload] = useState(true);
  const [refreshInterval, setRefreshInterval] = useState(5000);
  const [batchReplay, setBatchReplay] = useState({ running: false, result: null, error: null });
  const [copiedId, setCopiedId] = useState(null);

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

  const load = useLoad(() => api.workflows(query), [JSON.stringify(query)], { interval: autoReload ? refreshInterval : 0 });
  const rawRows = Array.isArray(load.data) ? load.data : load.data?.content || load.data?.items || [];
  const rows = sortRows(rawRows, sort.key, sort.direction);
  const retryableFilterActive = ['SUSPENDED', 'FAILED'].includes(filters.status)
    || ['SUSPENDED', 'FAILED'].includes(filters.stepStatus);
  const activeFilterCount = Object.entries(filters).filter(([key, value]) => !['page', 'size'].includes(key) && value).length;

  function updateFilter(key, value) {
    setBatchReplay({ running: false, result: null, error: null });
    setFilters((current) => ({ ...current, [key]: value, page: 0 }));
  }

  function setQuickStatus(statusValue) {
    updateFilter('status', filters.status === statusValue ? '' : statusValue);
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

  function copyToClipboard(e, text) {
    e.stopPropagation();
    navigator.clipboard?.writeText(text);
    setCopiedId(text);
    setTimeout(() => setCopiedId(null), 1500);
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

  async function replaySingleRow(e, rowId) {
    e.stopPropagation();
    try {
      await api.replaySuspendedSteps({ workflowId: rowId });
      load.reload();
    } catch (err) {
      console.error('Failed to replay workflow', err);
    }
  }

  const runningCount = rawRows.filter((r) => ['RUNNING', 'STARTED'].includes((r.status || r.state || '').toUpperCase())).length;
  const suspendedCount = rawRows.filter((r) => (r.status || r.state || '').toUpperCase() === 'SUSPENDED').length;
  const failedCount = rawRows.filter((r) => (r.status || r.state || '').toUpperCase() === 'FAILED').length;
  const successCount = rawRows.filter((r) => (r.status || r.state || '').toUpperCase() === 'SUCCESS').length;

  return (
    <Shell page="/" navigate={navigate}>
      <div className="page page-list">
        <PageHeader
          eyebrow="Operations & Orchestration"
          title="Workflow Executions"
          subtitle="Real-time DAG monitoring, step diagnostics, retry states, and operator replay controls."
          actions={<>
            {retryableFilterActive && (
              <button className="button primary pulse-button" onClick={replaySuspended} disabled={batchReplay.running || !(load.data?.totalElements > 0)}>
                <RotateCcw size={15} className={batchReplay.running ? 'spin' : ''} />
                {batchReplay.running ? 'Retrying…' : 'Retry Failed / Suspended'}
              </button>
            )}
            <AutoRefresh enabled={autoReload} onEnabledChange={setAutoReload} interval={refreshInterval} onIntervalChange={setRefreshInterval} />
            <button className="button" onClick={load.reload}>
              <RefreshCw size={15} className={load.loading ? 'spin' : ''} />
              Refresh
            </button>
          </>}
        />

        {/* Stats Grid */}
        <div className="stats-grid">
          <StatCard label="Total Executions" value={load.data?.totalElements ?? rawRows.length ?? '—'} icon={Layers3} tone="default" />
          <StatCard label="Active / Running" value={runningCount} icon={Activity} tone="info" />
          <StatCard label="Suspended (Halted)" value={suspendedCount} icon={CircleDot} tone="danger" />
          <StatCard label="Failed" value={failedCount} icon={AlertCircle} tone={failedCount > 0 ? 'danger' : 'default'} />
        </div>

        {/* Quick Status Filter Toolbar */}
        <div className="quick-filter-bar">
          <div className="quick-filter-pills">
            <button
              className={`pill-button ${!filters.status ? 'active' : ''}`}
              onClick={() => updateFilter('status', '')}
            >
              All Workflows
            </button>
            <button
              className={`pill-button tone-running ${filters.status === 'RUNNING' ? 'active' : ''}`}
              onClick={() => setQuickStatus('RUNNING')}
            >
              <span className="pill-dot running-dot" />
              Running ({runningCount})
            </button>
            <button
              className={`pill-button tone-suspended ${filters.status === 'SUSPENDED' ? 'active' : ''}`}
              onClick={() => setQuickStatus('SUSPENDED')}
            >
              <span className="pill-dot suspended-dot" />
              Suspended ({suspendedCount})
            </button>
            <button
              className={`pill-button tone-failed ${filters.status === 'FAILED' ? 'active' : ''}`}
              onClick={() => setQuickStatus('FAILED')}
            >
              <span className="pill-dot failed-dot" />
              Failed ({failedCount})
            </button>
            <button
              className={`pill-button tone-success ${filters.status === 'SUCCESS' ? 'active' : ''}`}
              onClick={() => setQuickStatus('SUCCESS')}
            >
              <span className="pill-dot success-dot" />
              Success ({successCount})
            </button>
          </div>

          <div className="quick-filter-actions">
            <button
              className={`toolbar-filter ${filtersOpen ? 'active' : ''}`}
              onClick={() => setFiltersOpen((value) => !value)}
            >
              <Filter size={14} /> Advanced Filters {activeFilterCount > 0 && <span>{activeFilterCount}</span>}
            </button>
            {activeFilterCount > 0 && (
              <button className="link-button" onClick={clearFilters}>
                <X size={13} /> Reset
              </button>
            )}
          </div>
        </div>

        {/* Advanced Filters Drawer */}
        {filtersOpen && (
          <Card className="workflow-toolbar-expanded">
            <div className="filter-grid">
              <SearchBox label="Workflow Name" value={filters.workflow} onChange={(value) => updateFilter('workflow', value)} placeholder="Search name e.g. order-fulfillment…" />
              <SearchBox label="Workflow Execution ID" value={filters.workflowId} onChange={(value) => updateFilter('workflowId', value)} placeholder="Search ID e.g. wf-ord-9821…" />
              <SelectField label="Status" value={filters.status} onChange={(value) => updateFilter('status', value)} options={STATUS_OPTIONS} placeholder="All statuses" />
              <SearchBox label="Current Step" value={filters.stepName} onChange={(value) => updateFilter('stepName', value)} placeholder="Search step name…" />
              <SelectField label="Step Status" value={filters.stepStatus} onChange={(value) => updateFilter('stepStatus', value)} options={STEP_STATUS_OPTIONS} placeholder="All step statuses" />
              <DateField label="Created From" value={filters.createdFrom} onChange={(value) => updateFilter('createdFrom', value)} />
              <DateField label="Created To" value={filters.createdTo} onChange={(value) => updateFilter('createdTo', value)} />
            </div>
          </Card>
        )}

        {/* Notices */}
        {batchReplay.result && (
          <div className="inline-notice success">
            <CheckCircle2 size={15} /> Retry queued: {batchReplay.result.replayedCount ?? batchReplay.result.replayed ?? 'all'} failed or suspended step(s).
          </div>
        )}
        {batchReplay.error && (
          <div className="inline-notice danger">
            <AlertCircle size={15} /> Replay failed: {batchReplay.error.message}
          </div>
        )}

        {/* Workflows Table */}
        <Card className="workflow-table-card">
          <div className="table-head workflow-table-head">
            <SortButton label="Workflow Name" active={sort.key === 'workflow'} direction={sort.direction} onClick={() => changeSort('workflow')} />
            <SortButton label="Status" active={sort.key === 'status'} direction={sort.direction} onClick={() => changeSort('status')} />
            <SortButton label="Current Step" active={sort.key === 'currentStep'} direction={sort.direction} onClick={() => changeSort('currentStep')} />
            <SortButton label="Started" active={sort.key === 'created'} direction={sort.direction} onClick={() => changeSort('created')} />
            <SortButton label="Execution ID" active={sort.key === 'workflowId'} direction={sort.direction} onClick={() => changeSort('workflowId')} />
            <span className="actions-col-head">Actions</span>
          </div>

          {load.loading && !rows.length ? (
            <Loading message="Fetching workflow executions..." />
          ) : load.error && !rows.length ? (
            <ErrorState error={load.error} retry={load.reload} />
          ) : rows.length ? (
            rows.map((row) => {
              const workflowId = row.workflowId || row.id || row.executionId || '';
              const workflowName = row.workflow || row.workflowName || row.name || 'Unnamed workflow';
              const status = (row.status || row.state || 'UNKNOWN').toUpperCase();
              const currentStep = row.currentStep || row.stepName || '—';
              const dateCreated = row.dateCreated || row.created || row.createdAt || row.startTime;
              const isReplayable = ['SUSPENDED', 'FAILED'].includes(status);

              return (
                <div
                  className="table-row workflow-row"
                  key={workflowId || Math.random()}
                  onClick={() => workflowId && navigate(`/${encodeURIComponent(workflowId)}`)}
                >
                  <div className="workflow-cell">
                    <strong title={workflowName}>{workflowName}</strong>
                    <span className="workflow-meta-sub">
                      {row.metadata?.priority ? `[${row.metadata.priority}] ` : ''}
                      {row.metadata?.orderId || row.metadata?.batchId || ''}
                    </span>
                  </div>

                  <StatusBadge value={status} />

                  <div className="step-cell-container">
                    <span className="step-cell" title={currentStep}>
                      {currentStep}
                    </span>
                  </div>

                  <time dateTime={dateCreated} title={formatDate(dateCreated)} className="timestamp-cell">
                    {formatRelativeTime(dateCreated)}
                  </time>

                  <div className="id-cell-wrapper">
                    <code className="mono id-cell" title={workflowId}>{workflowId || '—'}</code>
                    {workflowId && (
                      <button
                        className="copy-mini-btn"
                        onClick={(e) => copyToClipboard(e, workflowId)}
                        title="Copy Execution ID"
                        aria-label="Copy Execution ID"
                      >
                        {copiedId === workflowId ? <Check size={11} className="copied-icon" /> : <Copy size={11} />}
                      </button>
                    )}
                  </div>

                  <div className="row-actions-cell" onClick={(e) => e.stopPropagation()}>
                    {isReplayable && (
                      <button
                        className="action-pill-btn"
                        onClick={(e) => replaySingleRow(e, workflowId)}
                        title="Retry failed or suspended steps in this workflow"
                      >
                        <RotateCcw size={12} />
                        <span>Retry</span>
                      </button>
                    )}
                    <button
                      className="inspect-btn"
                      onClick={() => workflowId && navigate(`/${encodeURIComponent(workflowId)}`)}
                    >
                      Inspect →
                    </button>
                  </div>
                </div>
              );
            })
          ) : (
            <Empty
              icon={Search}
              title="No workflows found"
              text="No workflow executions matched the active filters. Try clearing your search filters or resetting status."
            />
          )}

          {load.data && (
            <Pagination
              page={load.data.page ?? load.data.number ?? 0}
              totalPages={load.data.totalPages || 1}
              totalElements={load.data.totalElements ?? rawRows.length}
              size={filters.size}
              onChange={(page) => setFilters((current) => ({ ...current, page }))}
              onSizeChange={updateSize}
            />
          )}
        </Card>
      </div>
    </Shell>
  );
}
