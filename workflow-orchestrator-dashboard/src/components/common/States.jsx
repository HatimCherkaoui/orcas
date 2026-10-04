import { useState } from 'react';
import { RefreshCw, AlertTriangle, Inbox, Copy, Check, Server, Terminal } from 'lucide-react';

export function Card({ children, className = '' }) {
  return <section className={`card ${className}`}>{children}</section>;
}

export function Loading({ message = 'Loading…' }) {
  return (
    <div className="state-card">
      <RefreshCw className="spin" size={20} />
      <span>{message}</span>
    </div>
  );
}

export function ErrorState({ error, retry, title = 'Unable to load data' }) {
  const [copied, setCopied] = useState(false);
  const [showDetails, setShowDetails] = useState(false);

  const status = error?.status;
  const isNetworkError = status === 0 || !status;
  const statusLabel = isNetworkError ? 'Network Offline' : `HTTP ${status}`;

  const suggestion = isNetworkError
    ? 'Cannot reach backend API. Ensure Spring Boot is running on port 8080, or start the mock server with: npm run mock'
    : status === 404
    ? 'The requested workflow resource or definition could not be located on the server.'
    : status >= 500
    ? 'Internal server error occurred while processing the workflow request.'
    : 'Please verify the request parameters and network connection.';

  function copyDiagnostics() {
    const payload = JSON.stringify({
      status: error?.status,
      message: error?.message,
      endpoint: error?.endpoint,
      path: error?.path,
      timestamp: new Date().toISOString()
    }, null, 2);
    navigator.clipboard?.writeText(payload);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  }

  return (
    <div className="state-card error-card-modern">
      <div className="error-icon-col">
        <div className="error-badge-icon">
          <AlertTriangle size={22} />
        </div>
        <span className="error-status-pill">{statusLabel}</span>
      </div>

      <div className="error-content-col">
        <div className="error-header-line">
          <strong>{title}</strong>
          {error?.endpoint && <span className="mono error-endpoint">{error.endpoint}</span>}
        </div>
        <p className="error-message">{error?.message || 'An unexpected error interrupted this operation.'}</p>
        <div className="error-suggestion">
          <Server size={14} />
          <span>{suggestion}</span>
        </div>

        {showDetails && (
          <div className="error-diagnostics-box">
            <div className="error-diagnostics-header">
              <span className="mono">Diagnostic Payload</span>
              <button className="link-button" onClick={copyDiagnostics}>
                {copied ? <Check size={12} /> : <Copy size={12} />}
                {copied ? 'Copied' : 'Copy'}
              </button>
            </div>
            <pre className="mono">{JSON.stringify({ status: error?.status, endpoint: error?.endpoint, message: error?.message }, null, 2)}</pre>
          </div>
        )}
      </div>

      <div className="error-actions-col">
        {retry && (
          <button className="button primary" onClick={retry}>
            <RefreshCw size={14} /> Retry
          </button>
        )}
        <button className="button" onClick={() => setShowDetails(!showDetails)}>
          <Terminal size={14} /> {showDetails ? 'Hide Details' : 'Details'}
        </button>
      </div>
    </div>
  );
}

export function ErrorPage({ error, retry, onGoBack }) {
  return (
    <div className="page error-page-container">
      <div className="error-page-card">
        <ErrorState error={error} retry={retry} title="Dashboard Connection Error" />
        {onGoBack && (
          <div className="error-page-footer">
            <button className="button" onClick={onGoBack}>
              ← Return to Dashboard
            </button>
          </div>
        )}
      </div>
    </div>
  );
}

export function Empty({ icon: Icon = Inbox, title = 'Nothing here', text = '' }) {
  return (
    <div className="empty">
      <Icon size={27} />
      <strong>{title}</strong>
      {text && <p>{text}</p>}
    </div>
  );
}
