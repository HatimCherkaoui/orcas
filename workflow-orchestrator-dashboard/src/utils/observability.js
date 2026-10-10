/** UUID generation also works on HTTP hosts where randomUUID is unavailable. */
export function newIdentifier(cryptoApi = globalThis.crypto) {
  if (cryptoApi.randomUUID) return cryptoApi.randomUUID();
  const bytes = cryptoApi.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = [...bytes].map(value => value.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0,8)}-${hex.slice(8,12)}-${hex.slice(12,16)}-${hex.slice(16,20)}-${hex.slice(20)}`;
}

export async function copyText(text, { clipboard = globalThis.navigator?.clipboard, document = globalThis.document } = {}) {
  if (clipboard?.writeText) {
    try { await clipboard.writeText(String(text)); return; } catch { /* Try the user-gesture fallback. */ }
  }
  const previous = document.activeElement;
  const textarea = document.createElement('textarea');
  textarea.value = String(text);
  textarea.setAttribute('readonly', '');
  textarea.style.position = 'fixed'; textarea.style.opacity = '0';
  document.body.appendChild(textarea);
  try {
    textarea.select();
    if (!document.execCommand('copy')) throw new Error('Clipboard copy failed');
  } finally { textarea.remove(); previous?.focus(); }
}
