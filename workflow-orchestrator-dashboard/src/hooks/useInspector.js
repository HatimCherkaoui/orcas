import { useEffect, useRef } from 'react';

/** Restore the initiating control and keep mobile drawer keyboard focus visible. */
export function useInspector(onClose, enabled = true) {
  const ref = useRef(null);
  const closeRef = useRef(onClose);
  closeRef.current = onClose;
  useEffect(() => {
    if (!enabled) return;
    const previous = document.activeElement;
    const mobile = window.matchMedia('(max-width:600px)');
    const describe = () => {
      ref.current?.setAttribute('role', 'dialog');
      ref.current?.setAttribute('aria-modal', String(mobile.matches));
    };
    describe();
    mobile.addEventListener('change', describe);
    ref.current?.querySelector('button[aria-label^="Close"]')?.focus();
    const keyboard = (e) => {
      if (e.key === 'Escape') { e.stopPropagation(); closeRef.current(); return; }
      if (e.key !== 'Tab' || !mobile.matches) return;
      const controls = [...(ref.current?.querySelectorAll('button:not(:disabled), a[href], input:not(:disabled), select:not(:disabled), [tabindex="0"]') || [])].filter((control) => control.getClientRects().length);
      if (!controls.length) return;
      const first = controls[0];
      const last = controls[controls.length - 1];
      if (!ref.current.contains(document.activeElement) || (!e.shiftKey && document.activeElement === last)) { e.preventDefault(); first.focus(); }
      else if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus(); }
    };
    document.addEventListener('keydown', keyboard);
    return () => { mobile.removeEventListener('change', describe); document.removeEventListener('keydown', keyboard); if (previous?.isConnected) previous.focus(); };
  }, [enabled]);
  return ref;
}
