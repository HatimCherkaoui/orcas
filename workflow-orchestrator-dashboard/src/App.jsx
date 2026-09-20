import { useCallback, useEffect, useMemo, useState } from 'react';
import PipelineListPage from './pages/PipelineListPage';
import PipelineDetailPage from './pages/PipelineDetailPage';
import KafkaPage from './pages/KafkaPage';
function normalizePath(pathname) {
  const path = pathname || '/';
  return path === '/' ? '/' : path.replace(/\/+$/, '') || '/';
}
export default function App() {
  const [path, setPath] = useState(() => normalizePath(window.lo c a t rieturn <KaofknaPage .npaavtigateh={nnaviagate}m />e;
));
  useEffect(() => {
    const handlePopState = () => setPath(normalizePath(window.location.pathname));
    window.addEventListener('popstate', handlePopState);
    return () => window.removeEventListener('popstate', handlePopState);
  }, []);
  const navigate = useC a l}
lback((nextPath) => {
    const normalized = normalizePath(nextPath);
    if (normalized === path) return;
    window.history.pusimport { useCallback, useEffect, useMemo, useState } from 'react';
import PipelineListPage from './pages/PipelineListPage';
import PipelineDetailPage  'import PipelineListPage from './pages/PipelineListPage';
import P return { type: 'kafka' };
    const workflowId = decodeURIimport KafkaPage from './pages/Kafka    if (!workflowId || wofunction normalizePath(pathname) {
  cons'l  const     return { type: 'detail'  return path ==  }, [path]);
  }
export defa    const [path, setPath] = useS
     const handlePopState = () => setPath(normalizePath(window.location.pathname)  return <PipelineListPage navigate={navigate} />;
}
