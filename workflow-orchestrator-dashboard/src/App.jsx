import { useEffect, useState } from 'react';
import PipelineListPage from './pages/PipelineListPage';
import PipelineDetailPage from './pages/PipelineDetailPage';
import KafkaPage from './pages/KafkaPage';

/** Minimal client-side router: no external dependency needed for two route shapes. */
export default function App() {
  const [path, setPath] = useState(window.location.pathname);

  useEffect(() => {
    const onPopState = () => setPath(window.location.pathname);
    window.addEventListener('popstate', onPopState);
    return () => window.removeEventListener('popstate', onPopState);
  }, []);

  function navigate(nextPath) {
    window.history.pushState({}, '', nextPath);
    setPath(nextPath);
  }

  if (path === '/kafka') return <KafkaPage navigate={navigate} />;
  if (path === '/' || path === '') return <PipelineListPage navigate={navigate} />;

  const pipelineId = decodeURIComponent(path.slice(1));
  if (!pipelineId || pipelineId === 'undefined') return <PipelineListPage navigate={navigate} />;
  return <PipelineDetailPage id={pipelineId} navigate={navigate} />;
}
