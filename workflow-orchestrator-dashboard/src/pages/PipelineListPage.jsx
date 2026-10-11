import { useEffect, useMemo, useState } from 'react';
import {
  Activity,
  AlertCircle,
  ArrowDown,
  ArrowUp,
  Check,
  CheckCircle2,
  CircleDot,
  Copy,
  Filter,
  Layers3,
  RefreshCw,
  RotateCcw,
  Search,
  X
} from 'lucide-react';
import { api } from '../api';
import { copyText } from '../utils/observability';
import { useLoad } from '../hooks/useLoad';
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
    <div role="columnheader" aria-sort={active ? (direction === 'asc' ? 'ascending' : 'descending') : 'none'}><button title={`Sort this page by ${label}`} className={`sort-button ${active ? 'active' : ''}`} onClick={onClick}>
      {label}
      {active ? (direction === 'asc' ? <ArrowUp size={13} /> : <ArrowDown size={13} />) : null}
    </button></div>
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
  const [replayingId, setReplayingId] = useState(null);
  const [searchFilters, setSearchFilters] = useState(filters);
  useEffect(() => { const timer = setTimeout(() => setSearchFilters(filters), 250); return () => clearTimeout(timer); }, [filters.workflow, filters.workflowId, filters.stepName]);

  useEffect(() => {
    if (!autoPageSize) return undefined;
    let timer;
    const resize = () => { clearTimeout(timer); timer = setTimeout(() => setFilters((current) => current.size === adaptivePageSize() ? current : { ...current, size: adaptivePageSize(), page: 0 }), 150); };
    window.addEventListener('resize', resize);
    return () => { clearTimeout(timer); window.removeEventListener('resize', resize); };
  }, [autoPageSize]);

  const query = useMemo(() => ({
    workflowId: searchFilters.workflowId,
    workflow: searchFilters.workflow,
    status: filters.status,
    stepName: searchFilters.stepName,
    stepStatus: filters.stepStatus,
    createdFrom: toInstant(filters.createdFrom, false),
    createdTo: toInstant(filters.createdTo, true),
    page: filters.page,
    size: filters.size,
  }), [filters, searchFilters]);

  const load = useLoad((options) => api.workflows(query, options), [JSON.stringify(query)], { interval: autoReload ? refreshInterval : 0 });
  const rawRows = Array.isArray(load.data) ? load.data : load.data?.content || load.data?.items || [];
  const rows = useMemo(() => sortRows(rawRows, sort.key, sort.direction), [load.data, sort]);
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
    setSearchFilters(DEFAULT_FILTERS);
  }

  async function copyToClipboard(e, text) {
    e.stopPropagation();
    try { await copyText(text); } catch { return; }
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
    setReplayingId(rowId);
    try {
      await api.replaySuspendedSteps({ workflowId: rowId });
      load.reload();
    } catch (err) {
      setBatchReplay({ running: false, result: null, error: err });
    } finally { setReplayingId(null); }
  }

  const runningCount = rawRows.filter((r) => ['INIT', 'RUNNING', 'RUNNING_ASYNC', 'STARTED'].includes((r.status || r.state || '').toUpperCase())).length;
  const suspendedCount = rawRows.filter((r) => (r.status || r.state || '').toUpperCase() === 'SUSPENDED').length;
  const failedCount = rawRows.filter((r) => (r.status || r.state || '').toUpperCase() === 'FAILED').length;

  return (
    <>
      <div className="page page-list">
        <PageHeader
          eyebrow="Console"
          title="Workflows"
          subtitle="Execution history and live status."
          actions={<>
            {retryableFilterActive && (
              <button className="button primary" onClick={replaySuspended} disabled={batchReplay.running || !(load.data?.totalElements > 0)}>
                <RotateCcw size={15} />
                {batchReplay.running ? 'Retrying…' : 'Retry matching steps'}
              </button>
            )}
            <AutoRefresh enabled={autoReload} onEnabledChange={setAutoReload} interval={refreshInterval} onIntervalChange={setRefreshInterval} />
            <button className="button" onClick={load.reload} disabled={load.loading} aria-busy={load.loading}>
              <RefreshCw size={15} />
              Refresh
            </button>
          </>}
        />

        {/* Stats Grid */}
        <div className="stats-grid">
          <StatCard label="Matching executions" value={load.data ? (load.data.totalElements ?? rawRows.length).toLocaleString() : '—'} icon={Layers3} tone="default" />
          <StatCard label="Running · this page" value={load.data ? runningCount : '—'} icon={Activity} tone="info" />
          <StatCard label="Suspended · this page" value={load.data ? suspendedCount : '—'} icon={CircleDot} tone="danger" />
          <StatCard label="Failed · this page" value={load.data ? failedCount : '—'} icon={AlertCircle} tone={failedCount > 0 ? 'danger' : 'default'} />
        </div>

        {/* Quick Status Filter Toolbar */}
        <div className="quick-filter-bar">
          <div className="quick-filter-pills">
            <button
              aria-pressed={!filters.status} className={`pill-button ${!filters.status ? 'active' : ''}`}
              onClick={() => updateFilter('status', '')}
            >
              All
            </button>
            <button
              aria-pressed={filters.status === 'RUNNING'} className={`pill-button tone-running ${filters.status === 'RUNNING' ? 'active' : ''}`}
              onClick={() => setQuickStatus('RUNNING')}
            >
              <span className="pill-dot running-dot" />
              Running
            </button>
            <button
              aria-pressed={filters.status === 'SUSPENDED'} className={`pill-button tone-suspended ${filters.status === 'SUSPENDED' ? 'active' : ''}`}
              onClick={() => setQuickStatus('SUSPENDED')}
            >
              <span className="pill-dot suspended-dot" />
              Suspended
            </button>
            <button
              aria-pressed={filters.status === 'FAILED'} className={`pill-button tone-failed ${filters.status === 'FAILED' ? 'active' : ''}`}
              onClick={() => setQuickStatus('FAILED')}
            >
              <span className="pill-dot failed-dot" />
              Failed
            </button>
            <button
              aria-pressed={filters.status === 'SUCCESS'} className={`pill-button tone-success ${filters.status === 'SUCCESS' ? 'active' : ''}`}
              onClick={() => setQuickStatus('SUCCESS')}
            >
              <span className="pill-dot success-dot" />
              Succeeded
            </button>
          </div>

          <div className="quick-filter-actions">
            <button
              aria-expanded={filtersOpen} aria-controls="advanced-filters" className={`toolbar-filter ${filtersOpen ? 'active' : ''}`}
              onClick={() => setFiltersOpen((value) => !value)}
            >
              <Filter size={14} /> Filters {activeFilterCount > 0 && <span>{activeFilterCount}</span>}
            </button>
            {activeFilterCount > 0 && (
              <button className="link-button" onClick={clearFilters}>
                <X size={13} /> Reset
              </button>
            )}
          </div>
        </div>

        <div className="primary-search">
          <SearchBox label="Workflow name" value={filters.workflow} onChange={(value) => updateFilter('workflow', value)} placeholder="Search workflows…" />
          <SearchBox label="Execution ID" value={filters.workflowId} onChange={(value) => updateFilter('workflowId', value)} placeholder="Find an execution…" />
        </div>
        {filtersOpen && <div id="advanced-filters" className="filter-grid">
          <SelectField label="Workflow status" value={filters.status} onChange={(value) => updateFilter('status', value)} options={STATUS_OPTIONS} placeholder="All statuses" />
          <SearchBox label="Step name" value={filters.stepName} onChange={(value) => updateFilter('stepName', value)} placeholder="Search steps…" />
          <SelectField label="Step status" value={filters.stepStatus} onChange={(value) => updateFilter('stepStatus', value)} options={STEP_STATUS_OPTIONS} placeholder="All statuses" />
          <DateField label="From" value={filters.createdFrom} onChange={(value) => updateFilter('createdFrom', value)} />
          <DateField label="To" value={filters.createdTo} onChange={(value) => updateFilter('createdTo', value)} />
        </div>}

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

        {load.error && rows.length > 0 && <div className="inline-notice danger" role="alert">Refresh failed. Showing the last loaded executions. {load.error.message}</div>}
        {/* Workflows Table */}
        <Card className={`workflow-table-card ${load.loading && load.data ? 'is-refreshing' : ''}`}>
          <div className="workflow-table-grid" role="table" aria-label="Workflow executions">
          <div role="row" className="table-head workflow-table-head">
            <SortButton label="Workflow Name" active={sort.key === 'workflow'} direction={sort.direction} onClick={() => changeSort('workflow')} />
            <SortButton label="Status" active={sort.key === 'status'} direction={sort.direction} onClick={() => changeSort('status')} />
            <SortButton label="Current Step" active={sort.key === 'currentStep'} direction={sort.direction} onClick={() => changeSort('currentStep')} />
            <SortButton label="Started" active={sort.key === 'created'} direction={sort.direction} onClick={() => changeSort('created')} />
            <SortButton label="Execution ID" active={sort.key === 'workflowId'} direction={sort.direction} onClick={() => changeSort('workflowId')} />
            <span role="columnheader" className="actions-col-head">Actions</span>
          </div>

          <div role="rowgroup" className="workflow-table-body">
          {load.loading && !rows.length ? (
            <Loading variant="table" rows={filters.size} message="Loading workflow executions…" />
          ) : load.error && !rows.length ? (
            <ErrorState error={load.error} retry={load.reload} />
          ) : rows.length ? (
            rows.map((row, index) => {
              const workflowId = row.workflowId || row.id || row.executionId || '';
              const workflowName = row.workflow || row.workflowName || row.name || 'Unnamed workflow';
              const status = (row.status || row.state || 'UNKNOWN').toUpperCase();
              const currentStep = row.currentStep || row.stepName || '—';
              const dateCreated = row.dateCreated || row.created || row.createdAt || row.startTime;
              const isReplayable = ['SUSPENDED', 'FAILED'].includes(status);

              return (
                <div
                  role="row" className="table-row workflow-row"
                  key={workflowId || `${workflowName}-${index}`}
                  onClick={() => workflowId && navigate(`/${encodeURIComponent(workflowId)}`)}
                >
                  <div role="cell" className="workflow-cell">
                    <a className="workflow-name" href={`/${encodeURIComponent(workflowId)}`} onClick={(e) => { e.stopPropagation(); if (!e.metaKey && !e.ctrlKey) { e.preventDefault(); navigate(`/${encodeURIComponent(workflowId)}`); } }} title={workflowName}>{workflowName}</a>
                    <span className="workflow-meta-sub">
                      {row.metadata?.priority ? `[${row.metadata.priority}] ` : ''}
                      {row.metadata?.orderId || row.metadata?.batchId || ''}
                    </span>
                  </div>

                  <span role="cell" className="status-cell"><StatusBadge value={status} /></span>

                  <div role="cell" className="step-cell-container">
                    <span className="step-cell" title={currentStep}>
                      {currentStep}
                    </span>
                  </div>

                  <time role="cell" dateTime={dateCreated} title={formatDate(dateCreated)} className="timestamp-cell">
                    {formatRelativeTime(dateCreated)}
                  </time>

                  <div role="cell" className="id-cell-wrapper">
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

                  <div role="cell" className="row-actions-cell" onClick={(e) => e.stopPropagation()}>
                    {isReplayable && (
                      <button
                        className="action-pill-btn" disabled={Boolean(replayingId)}
                        onClick={(e) => replaySingleRow(e, workflowId)}
                        title="Retry failed or suspended steps in this workflow"
                      >
                        <RotateCcw size={12} />
                        <span>{replayingId === workflowId ? 'Queued…' : 'Retry'}</span>
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

          </div>
          </div>
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
    </>
  );
}
