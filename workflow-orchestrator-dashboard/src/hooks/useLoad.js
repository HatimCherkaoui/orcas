import { useCallback, useEffect, useRef, useState } from 'react';

/**
 * Small async resource hook. It keeps the last successful value while refreshing,
 * which prevents dashboard flicker and makes polling feel instantaneous.
 */
export function useLoad(loader, deps = [], options = {}) {
  const { keepData = true, interval = 0 } = options;
  const requestId = useRef(0);
  const [state, setState] = useState({ loading: Boolean(loader), data: null, error: null });

  const load = useCallback(() => {
    if (!loader) {
      setState({ loading: false, data: null, error: null });
      return () => {};
    }

    const id = ++requestId.current;
    setState((current) => ({
      loading: true,
      data: keepData ? current.data : null,
      error: null,
    }));

    Promise.resolve()
      .then(loader)
      .then((data) => {
        if (id === requestId.current) setState({ loading: false, data, error: null });
      })
      .catch((error) => {
        if (id === requestId.current) setState((current) => ({ loading: false, data: keepData ? current.data : null, error }));
      });

    return () => {
      requestId.current += 1;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  useEffect(() => {
    const cancel = load();
    return cancel;
  }, [load]);

  useEffect(() => {
    if (!loader || !interval) return undefined;
    const timer = window.setInterval(load, interval);
    return () => window.clearInterval(timer);
  }, [loader, interval, load]);

  return { ...state, reload: load };
}
