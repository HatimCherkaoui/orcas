import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

/** One request at a time. Poll after completion and pause in background tabs. */
export function useLoad(loader, deps = [], { keepData = true, interval = 0 } = {}) {
  const loaderRef = useRef(loader);
  loaderRef.current = loader;
  const enabled = Boolean(loader);
  const resource = useMemo(() => ({}), deps);
  const active = useRef(null);
  const currentResource = useRef(resource);
  currentResource.current = resource;
  const [state, setState] = useState({ loading: enabled, data: null, error: null, resource });
  const load = useCallback(() => {
    if (!loaderRef.current || active.current) return active.current?.promise || Promise.resolve();
    const controller = new AbortController();
    const request = { controller, resource, promise: null };
    active.current = request;
    setState((s) => ({ loading: true, data: keepData && s.resource === resource ? s.data : null, error: null, resource }));
    const fetchResource = loaderRef.current;
    request.promise = Promise.resolve().then(() => controller.signal.aborted ? null : fetchResource({ signal: controller.signal }))
      .then((data) => { if (!controller.signal.aborted && currentResource.current === resource) setState({ loading: false, data, error: null, resource }); })
      .catch((error) => { if (!controller.signal.aborted && currentResource.current === resource) setState((s) => ({ loading: false, data: keepData ? s.data : null, error, resource })); })
      .finally(() => { if (active.current === request) active.current = null; });
    return request.promise;
  }, [resource, keepData]);
  useEffect(() => {
    if (!enabled) { setState({ loading: false, data: null, error: null, resource }); return; }
    load();
    return () => { active.current?.controller.abort(); active.current = null; };
  }, [load, enabled, resource]);
  useEffect(() => {
    if (!enabled || !interval) return;
    let timer;
    let disposed = false;
    const schedule = () => { clearTimeout(timer); if (!disposed && !document.hidden) timer = window.setTimeout(tick, interval); };
    const tick = async () => { if (document.hidden) return; await load(); schedule(); };
    const visibility = () => { clearTimeout(timer); if (!document.hidden) tick(); };
    // Initial loading is already in flight; schedule the next poll after it settles.
    Promise.resolve(active.current?.promise).then(schedule);
    document.addEventListener('visibilitychange', visibility);
    return () => { disposed = true; clearTimeout(timer); document.removeEventListener('visibilitychange', visibility); };
  }, [enabled, interval, load]);
  return { ...(state.resource === resource ? state : { loading: enabled, data: null, error: null }), reload: load };
}
