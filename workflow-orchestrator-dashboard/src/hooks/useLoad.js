import { useEffect, useState, useCallback } from 'react';

/**
 * Generic async data-loading hook. Calls `loader()` whenever `deps` change and
 * tracks `{ loading, data, error }`. Pass a falsy `loader` to skip fetching
 * (useful when required parameters, e.g. an id, are not yet available).
 *
 * Returns `{ loading, data, error, reload }` where `reload` re-runs the loader
 * on demand (e.g. after a mutation or from a manual refresh button).
 */
export function useLoad(loader, deps = []) {
  const [state, setState] = useState({ loading: Boolean(loader), data: null, error: null });

  const load = useCallback(() => {
    if (!loader) {
      setState({ loading: false, data: null, error: null });
      return;
    }
    setState({ loading: true, data: null, error: null });
    Promise.resolve()
      .then(loader)
      .then((data) => setState({ loading: false, data, error: null }))
      .catch((error) => setState({ loading: false, data: null, error }));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  useEffect(load, [load]);

  return { ...state, reload: load };
}
