import { useMemo, useState } from 'react';
import { Activity, Database, Layers3, RefreshCw, Server, TerminalSquare, Users, X } from 'lucide-react';
import { api } from '../api';
import { useInspector } from '../hooks/useInspector';
import { useLoad } from '../hooks/useLoad';
import { PageHeader } from '../components/layout/PageHeader';
import { Card, Loading, Empty, ErrorState } from '../components/common/States';
import { SearchBox, StatCard, AutoRefresh } from '../components/common/Inputs';
import { StatusBadge } from '../components/common/StatusBadge';

export default function KafkaPage({ navigate }) {
  const [autoReload, setAutoReload] = useState(true);
  const [refreshInterval, setRefreshInterval] = useState(5000);
  const topics = useLoad(api.topics, [], { interval: autoReload ? refreshInterval : 0 });
  const groups = useLoad(api.consumerGroups, [], { interval: autoReload ? refreshInterval : 0 });
  const [query, setQuery] = useState('');
  const [selection, setSelected] = useState(null);
  const detail = useLoad(selection ? (options) => selection.type === 'topic' ? api.topic(selection.name, options) : api.consumerGroup(selection.groupId, options) : null, [selection?.type, selection?.name, selection?.groupId], { interval: autoReload ? refreshInterval : 0 });
  const selected = selection ? { ...selection, data: detail.data, loading: detail.loading } : null;
  const panelRef = useInspector(() => setSelected(null), Boolean(selection));

  const topicRows = Array.isArray(topics.data) ? topics.data : topics.data?.topics || topics.data?.content || [];
  const groupRows = Array.isArray(groups.data) ? groups.data : groups.data?.groups || groups.data?.content || [];
  const filteredTopics = useMemo(() => topicRows.map((item) => typeof item === 'string' ? item : item.name).filter(Boolean).filter((name) => String(name).toLowerCase().includes(query.toLowerCase())), [topics.data, query]);
  const groupCount = groupRows.length;
  const connectionHealthy = !topics.error && !groups.error;

  function openTopic(name) { setSelected({ type: 'topic', name }); }
  function openGroup(groupId) { setSelected({ type: 'group', groupId }); }

  function refresh() { topics.reload(); groups.reload(); detail.reload(); }

  return (
    <>
      <div className="page kafka-page">
        <PageHeader
          eyebrow="Operations / Kafka"
          title="Kafka"
          subtitle="Streams, consumers, and committed offsets."
          actions={<><AutoRefresh enabled={autoReload} onEnabledChange={setAutoReload} interval={refreshInterval} onIntervalChange={setRefreshInterval} /><button className="button" onClick={refresh}><RefreshCw size={15} />Refresh</button></>}
        />

        <div className="stats-grid">
          <StatCard label="Topics" value={topicRows.length || (topics.loading ? '—' : 0)} icon={Layers3} />
          <StatCard label="Consumer groups" value={groupCount || (groups.loading ? '—' : 0)} icon={Users} />
          <StatCard label="Connection" value={!topics.data && !groups.data && connectionHealthy ? 'Connecting…' : connectionHealthy ? 'Connected' : 'Unavailable'} icon={Activity} tone={connectionHealthy ? 'success' : 'danger'} />
          <StatCard label="Refresh" value={autoReload ? `${refreshInterval / 1000} seconds` : 'Paused'} icon={Server} />
        </div>

        {(topics.error || groups.error) && (topics.data || groups.data) && <div className="inline-notice danger" role="alert">Some Kafka data could not refresh. Showing the last loaded data.</div>}

        <div className="kafka-layout">
          <Card className="kafka-list">
            <div className="section-heading"><div><div className="eyebrow">Event streams</div><h2>Topics</h2></div><span className="count">{filteredTopics.length}</span></div>
            <SearchBox label="Topic search" value={query} onChange={setQuery} placeholder="Filter topics…" />
            <div className="kafka-scroll">
              {topics.loading && !topics.data ? <Loading /> : topics.error && !topics.data ? <ErrorState error={topics.error} retry={topics.reload} /> : filteredTopics.length ? (
                <div className="resource-list">
                  {filteredTopics.map((name) => <button className={`resource ${selected?.name === name ? 'active' : ''}`} key={name} onClick={() => openTopic(name)}>
                    <span className="resource-icon"><TerminalSquare size={16} /></span><span className="resource-copy"><strong title={name}>{name}</strong><small>Kafka topic</small></span><span className="resource-chevron">›</span>
                  </button>)}
                </div>
              ) : <Empty title="No topics" text="No topic matches the current filter." />}
            </div>
          </Card>

          <Card className="kafka-groups">
            <div className="section-heading"><div><div className="eyebrow">Consumers</div><h2>Consumer groups</h2></div><span className="count">{groupCount}</span></div>
            <div className="kafka-scroll">
              {groups.loading && !groups.data ? <Loading /> : groups.error && !groups.data ? <ErrorState error={groups.error} retry={groups.reload} /> : groupRows.length ? (
                <div className="group-table">
                  <div className="group-head"><span>Group</span><span>State</span><span>Partitions</span></div>
                  {groupRows.map((group) => <button className={`group-row ${selected?.groupId === group.groupId ? 'active' : ''}`} key={group.groupId} onClick={() => openGroup(group.groupId)}>
                    <span className="group-name"><strong className="mono" title={group.groupId}>{group.groupId}</strong><small>consumer group</small></span><StatusBadge value={group.state} /><span className="partition-count">{Object.keys(group.offsets || {}).length}</span>
                  </button>)}
                </div>
              ) : <Empty title="No consumer groups" />}
            </div>
          </Card>
        </div>

        {selected && <aside ref={panelRef} role="region" aria-label="Kafka inspector" className="floating-panel kafka-floating">
          <header className="floating-panel-header">
            <div><div className="eyebrow">{selected.type === 'topic' ? 'Topic' : 'Consumer group'}</div><h2 className="mono">{selected.name || selected.groupId}</h2></div>
            {selected.data && selected.type === 'group' && <StatusBadge value={selected.data.state} />}
            <button className="icon-button" onClick={() => setSelected(null)} aria-label="Close"><X size={16} /></button>
          </header>
          <div className="floating-panel-body">
            {detail.error && selected.data && <div className="inline-notice danger" role="alert">Refresh failed. Showing the last loaded data.</div>}
            {selected.loading && !selected.data ? <Loading /> : detail.error && !selected.data ? <ErrorState error={detail.error} retry={detail.reload} /> : selected.type === 'topic' ? <div className="stats-grid compact"><StatCard label="Partitions" value={selected.data?.partitions ?? '—'} icon={Layers3} /><StatCard label="Replication" value={selected.data?.replicationFactor ?? '—'} icon={Database} /></div> : Object.entries(selected.data?.offsets || {}).length ? Object.entries(selected.data.offsets).map(([key, value]) => <div className="kv-row" key={key}><strong className="mono" title={key}>{key}</strong><span>{value}</span></div>) : <p className="muted-note">No offsets recorded for this group.</p>}
          </div>
        </aside>}
      </div>
    </>
  );
}
