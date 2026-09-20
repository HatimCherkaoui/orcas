import { useCallback, useEffect, useMemo, useState } from 'react';
import PipelineListPage from './pages/PipelineListPage';
import WorkflowDetailPage from './pages/WorkflowDetailPage';
import KafkaPage from './pages/KafkaPage';

function normalizePath(pathname) {
  const path = pathname || '/';
  return path === '/' ? '/' : path.replace(/\/+$/, '') || '/';
}

export default function DashboardApp() {
  const [path, setPath] = useState(() => normalizePath(window.location.pathname));

  useEffect(() => {
    const handlePopState = () => setPath(normalizePath(window.location.pathname));
    window.addEventListener('popstate', handlePopState);
    return () => window.removeEventListener('popstate', handlePopState);
  }, []);

  const navigate = useCallback((nextPath) => {
    const normalized = normalizePath(nextPath);
    if (normalized === path) return;
    window.history.pushState({}, '', normalized);
    setPath(normalized);
  }, [path]);

  const route = useMemo(() => {
    if (path === '/' || path === '') return { type: 'list' };
    if (path === '/kafka' || path.startsWith('/kafka/')) return { type: 'kafka' };

    const workflowId = decodeURIComponent(path.replace(/^\//, ''));
    if (!workflowId || workflowId === 'undefined') return { type: 'list' };
    return { type: 'detail', workflowId };
  }, [path]);

  if (route.type === 'kafka') {
    return <KafkaPage navigate={navigate} />;
  }

  if (route.type === 'detail') {
    return <WorkflowDetailPage id={route.workflowId} navigate={navigate} />;
  }

  return <PipelineListPage navigate={navigate} />;
}


