import { test } from 'node:test';
import assert from 'node:assert/strict';
import { newIdentifier, copyText } from './observability.js';

test('uses native UUID generation when available', () => {
  assert.equal(newIdentifier({ randomUUID: () => 'native-id' }), 'native-id');
});
test('generates a version 4 UUID with random bytes on HTTP hosts', () => {
  const id = newIdentifier({ getRandomValues: bytes => bytes.fill(1) });
  assert.match(id, /^[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/);
});
test('awaits clipboard success before resolving', async () => {
  let copied;
  await copyText('correlation-42', { clipboard: { writeText: async text => { copied = text; } } });
  assert.equal(copied, 'correlation-42');
});
test('falls back on HTTP and cleans up even when copy fails', async () => {
  let removed = 0, focused = 0;
  const element = { style: {}, setAttribute() {}, select() {}, remove() { removed++; } };
  const document = { activeElement: { focus() { focused++; } }, createElement: () => element, body: { appendChild() {} }, execCommand: () => false };
  await assert.rejects(copyText('workflow-42', { clipboard: {}, document }), /Clipboard copy failed/);
  assert.equal(removed, 1); assert.equal(focused, 1);
});
