import { copyText } from '../../utils/observability';
import { useState } from 'react';
import { Copy, Check, ExternalLink } from 'lucide-react';

/** Every value is independently copyable; custom code-defined identifiers use the same view. */
export function ExecutionIdentifiers({ identifiers = {}, workflowId, compact = false }) {
  const [copied, setCopied] = useState(null);
  const [error, setError] = useState('');
  const values = { ...(workflowId ? { workflowId } : {}), ...identifiers };
  const kibana = import.meta.env.VITE_KIBANA_URL;
  async function copy(key, value) {
    try {
      await copyText(String(value));
      setError(''); setCopied(key);
      setTimeout(() => setCopied(current => current === key ? null : current), 1800);
    } catch { setError('Copy failed. Select the identifier text to copy it.'); }
  }
  return <section className={`execution-identifiers ${compact ? 'compact' : ''}`} aria-label="Execution identifiers">
    {Object.entries(values).filter(([, value]) => value != null && value !== '').map(([key, value]) => {
      const query = `${key}: ${JSON.stringify(String(value))}`;
      const risonQuery = query.replace(/!/g, '!!').replace(/'/g, "!'");
      const state = encodeURIComponent(`(query:(language:kuery,query:'${risonQuery}'))`);
      return <div className="execution-identifier" key={key}>
        <span className="eyebrow">{key}</span>
        <code title={String(value)}>{String(value)}</code>
        <button className="icon-button" aria-label={`Copy ${key}`} title={`Copy ${key}`} onClick={() => copy(key, value)}>
          {copied === key ? <Check size={14} /> : <Copy size={14} />}
        </button>
        {kibana && <a className="icon-button" href={`${kibana.replace(/\/$/, '')}/app/discover#/view/orcas-execution-events?_a=${state}`} target="_blank" rel="noreferrer" aria-label={`Find ${key} in Kibana`} title="Find execution in Kibana"><ExternalLink size={14} /></a>}
      </div>;
    })}
    {error && <p role="alert" className="muted-note">{error}</p>}
    <span className="sr-only" role="status">{copied ? `${copied} copied` : ''}</span>
  </section>;
}
