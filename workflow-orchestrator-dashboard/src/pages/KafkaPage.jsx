import { useMemo, useState } from 'react';
import { Activity, Database, Layers3, RefreshCw, Server, TerminalSquare, Users, X } from 'lucide-react';
import { api } from '../api';
import { useLoad } from '../hooks/useLoad';
import { Shell } from '../components/layout/Shell';
import { PageHeader } from '../components/layout/PageHeader';
import { Card, Loading, Empty, ErrorState } from '../components/common/States';
import { SearchBox, StatCard } from '../components/common/Inputs';
import { StatusBadge } from '../components/common/StatusBadge';

export default function KafkaPage({ navigate }) {
  const topics = useLoad(api.topics, [], { interval: 8000 });
  const groups = useLoad(api.consumerGroups, [], { interval: 8000 });
  const [query, setQuery] = useState('');
  const [selected, setSelected] = useState(null);
  const [detailError, setDetailError] = useState(null);

  const topicRows = Array.isArray(topics.data) ? topics.data : topics.data?.topics || topics.data?.content || [];
  const groupRows = Array.isArray(groups.data) ? groups.data : groups.data?.groups || groups.data?.content || [];
  const filteredTopics = useMemo(() => topicRows.map((item) => typeof item === 'string' ? item : item.name).filter(Boolean).filter((name) => String(name).toLowerCase().includes(query.toLowerCase())), [topics.data, query]);
  const groupCount = groupRows.length;
  const connectionHealthy = !topics.error && !groups.error;

  async function openTopic(name) {
    setDetailError(null);
    try { setSelected({ type: 'topic', loading: true, name }); setSelected({ type: 'topic', data: await api.topic(name), name }); }
    catch (error) { setSelected(null); setDetailError(error); }
  }

  async function openGroup(groupId) {
    setDetailError(null);
    try { setSelected({ type: 'group', loading: true, groupId }); setSelected({ type: 'group', data: await api.consumerGroup(groupId), groupId }); }
    catch (error) { setSelected(null); setDetailError(error); }
  }

  function refresh() { topics.reload(); groups.reload(); }

  return (
    <Shell page="/kafka" navigate={navigate}>
      <div className="page kafka-page">
        <PageHeader
          eyebrow="Operations / Kafka"
          title="Kafka runtime"
          subtitle="Topics, consumer groups and offsets in one compact view."
          actions={<button className="button" onClick={refresh}><RefreshCw size={15} className={topics.loading || groups.loading ? 'spin' : ''} />Refresh</button>}
        />

        <div className="stats-grid">
          <StatCard label="Topics" value={topicRows.length || (topics.loading ? '—' : 0)} icon={Layers3} />
          <StatCard label="Consumer groups" value={groupCount || (groups.loading ? '—' : 0)} icon={Users} />
          <StatCard label="Connection" value={connectionHealthy ? 'Healthy' : 'Degraded'} icon={Activity} tone={connectionHealthy ? 'success' : 'danger'} />
          <StatCard label="API base" value={api.baseUrl} icon={Server} />
        </div>

        {detailError && <div className="inline-notice danger"><X size={14} /> {detailError.message}</div>}

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

        {selected && <aside className="floating-panel kafka-floating">
          <header className="floating-panel-header">
            <div><div className="eyebrow">{selected.type === 'topic' ? 'Topic' : 'Consumer group'}</div><h2 className="mono">{selected.name || selected.groupId}</h2></div>
            {selected.data && selected.type === 'group' && <StatusBadge value={selected.data.state} />}
            <button className="icon-button" onClick={() => setSelected(null)} aria-label="Close"><X size={16} /></button>
          </header>
          <div className="floating-panel-body">
            {selected.loading ? <Loading /> : selected.type === 'topic' ? <div className="stats-grid compact"><StatCard label="Partitions" value={selected.data?.partitions ?? '—'} icon={Layers3} /><StatCard label="Replication" value={selected.data?.replicationFactor ?? '—'} icon={Database} /></div> : Object.entries(selected.data?.offsets || {}).length ? Object.entries(selected.data.offsets).map(([key, value]) => <div className="kv-row" key={key}><strong className="mono" title={key}>{key}</strong><span>{value}</span></div>) : <p className="muted-note">No offsets recorded for this group.</p>}
          </div>
        </aside>}
      </div>
    </Shell>
  );
}
