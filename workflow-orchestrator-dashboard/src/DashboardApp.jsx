import { lazy, Suspense, useCallback, useEffect, useMemo, useState } from 'react';
import PipelineListPage from './pages/PipelineListPage';
import { Shell } from './components/layout/Shell';
import { Loading } from './components/common/States';

const WorkflowDetailPage = lazy(() => import('./pages/WorkflowDetailPage'));
const KafkaPage = lazy(() => import('./pages/KafkaPage'));

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

  return <Shell page={path} navigate={navigate} flush={route.type === 'detail'}>
    <Suspense fallback={<Loading variant={route.type === 'detail' ? 'graph' : 'table'} message="Opening console…"/>}>
      {route.type === 'kafka' ? <KafkaPage navigate={navigate} />
        : route.type === 'detail' ? <WorkflowDetailPage key={route.workflowId} id={route.workflowId} navigate={navigate} />
        : <PipelineListPage navigate={navigate} />}
    </Suspense>
  </Shell>;
}
