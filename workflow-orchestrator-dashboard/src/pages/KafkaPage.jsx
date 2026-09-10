import { useState } from 'react';
import { Activity, Database, Layers3, RefreshCw, Server, TerminalSquare, X } from 'lucide-react';
import { api } from '../api';
import { useLoad } from '../hooks/useLoad';
import { Shell } from '../components/layout/Shell';
import { PageHeader } from '../components/layout/PageHeader';
import { Card, Loading, Empty } from '../components/common/States';
import { SearchBox, StatCard } from '../components/common/Inputs';
import { StatusBadge } from '../components/common/StatusBadge';

/**
 * Kafka administration page: browse topics and inspect consumer group offsets.
 * The whole view is sized to fit a single screen - topic/group details open as
 * floating cards over the layout instead of pushing content further down, and
 * only the topic/group lists scroll internally if they overflow their panel.
 */
export default function KafkaPage({ navigate }) {
  const topics = useLoad(api.topics, []);
  const groups = useLoad(api.consumerGroups, []);
  const [query, setQuery] = useState('');
  const [topic, setTopic] = useState(null);
  const [group, setGroup] = useState(null);

  const filteredTopics = (topics.data || []).filter((name) => name.toLowerCase().includes(query.toLowerCase()));

  return (
    <Shell page="/kafka" navigate={navigate}>
      <div className="kafka-page">
        <PageHeader
          eyebrow="Runtime administration"
          title="Kafka"
          subtitle="Inspect topics, consumer groups and offsets"
          actions={
            <button
              className="button"
              onClick={() => {
                topics.reload();
                groups.reload();
              }}
            >
              <RefreshCw size={15} />
              Refresh
            </button>
          }
        />

        <div className="kafka-body">
          <div className="stats-grid">
            <StatCard label="Topics" value={topics.data?.length ?? '—'} icon={Layers3} />
            <StatCard label="Consumer groups" value={groups.data?.length ?? '—'} icon={Server} />
            <StatCard label="Connection" value={topics.error || groups.error ? 'Degraded' : 'Healthy'} icon={Activity} />
          </div>

          <div className="kafka-layout">
            <Card className="kafka-list">
              <div className="section-heading">
                <h2>Topics</h2>
                <span className="count">{filteredTopics.length}</span>
              </div>
              <SearchBox value={query} onChange={setQuery} placeholder="Filter topics" />
              <div className="kafka-scroll">
                {topics.loading ? (
                  <Loading />
                ) : filteredTopics.length ? (
                  <div className="resource-list">
                    {filteredTopics.map((name) => (
                      <button
                        className={`resource ${topic?.name === name ? 'active' : ''}`}
                        key={name}
                        onClick={() => api.topic(name).then(setTopic)}
                      >
                        <span className="resource-icon">
                          <TerminalSquare size={16} />
                        </span>
                        <strong title={name}>{name}</strong>
                      </button>
                    ))}
                  </div>
                ) : (
                  <Empty title="No topics" />
                )}
              </div>
            </Card>

            <Card className="kafka-groups">
              <div className="section-heading">
                <h2>Consumer groups</h2>
                <span className="count">{groups.data?.length || 0}</span>
              </div>
              <div className="kafka-scroll">
                {groups.loading ? (
                  <Loading />
                ) : groups.data?.length ? (
                  <div className="group-table">
                    <div className="group-head">
                      <span>Group</span>
                      <span>State</span>
                      <span>Partitions</span>
                    </div>
                    {groups.data.map((g) => (
                      <button
                        className={`group-row ${group?.groupId === g.groupId ? 'active' : ''}`}
                        key={g.groupId}
                        onClick={() => api.consumerGroup(g.groupId).then(setGroup)}
                      >
                        <strong className="mono" title={g.groupId}>{g.groupId}</strong>
                        <StatusBadge value={g.state} />
                        <span>{Object.keys(g.offsets || {}).length}</span>
                      </button>
                    ))}
                  </div>
                ) : (
                  <Empty title="No consumer groups" />
                )}
              </div>
            </Card>
          </div>
        </div>

        {topic && (
          <aside className="floating-panel kafka-floating">
            <header className="floating-panel-header">
              <div>
                <div className="eyebrow">Topic</div>
                <h2 className="mono">{topic.name}</h2>
              </div>
              <StatusBadge value="SUCCESS" />
              <button className="icon-button" onClick={() => setTopic(null)} aria-label="Close">
                <X size={16} />
              </button>
            </header>
            <div className="floating-panel-body">
              <div className="stats-grid compact">
                <StatCard label="Partitions" value={topic.partitions} icon={Layers3} />
                <StatCard label="Replication" value={topic.replicationFactor} icon={Database} />
              </div>
            </div>
          </aside>
        )}

        {group && (
          <aside className="floating-panel kafka-floating">
            <header className="floating-panel-header">
              <div>
                <div className="eyebrow">Consumer group</div>
                <h2 className="mono">{group.groupId}</h2>
              </div>
              <StatusBadge value={group.state} />
              <button className="icon-button" onClick={() => setGroup(null)} aria-label="Close">
                <X size={16} />
              </button>
            </header>
            <div className="floating-panel-body">
              {Object.entries(group.offsets || {}).length ? (
                Object.entries(group.offsets || {}).map(([key, value]) => (
                  <div className="kv-row" key={key}>
                    <strong className="mono" title={key}>{key}</strong>
                    <span title={String(value)}>{value}</span>
                  </div>
                ))
              ) : (
                <p className="muted-note">No offsets recorded for this group.</p>
              )}
            </div>
          </aside>
        )}
      </div>
    </Shell>
  );
}

