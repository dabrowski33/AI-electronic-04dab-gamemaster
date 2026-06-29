import { parseSseStream } from './sse-parser';

function makeStream(chunks: string[]): ReadableStream<Uint8Array> {
  const encoder = new TextEncoder();
  let i = 0;
  return new ReadableStream({
    pull(controller) {
      if (i < chunks.length) {
        controller.enqueue(encoder.encode(chunks[i++]));
      } else {
        controller.close();
      }
    },
  });
}

async function collectTokens(stream: ReadableStream<Uint8Array>): Promise<string[]> {
  const tokens: string[] = [];
  for await (const token of parseSseStream(stream)) {
    tokens.push(token);
  }
  return tokens;
}

// Tokens are JSON-encoded strings on the wire (e.g. `data:"Hello"`), mirroring the backend.
describe('parseSseStream', () => {
  it('should emit tokens in order and stop at done', async () => {
    const stream = makeStream([
      'data:"Hello"\n\ndata:" World"\n\nevent:done\ndata:\n\n',
    ]);
    const tokens = await collectTokens(stream);
    expect(tokens).toEqual(['Hello', ' World']);
  });

  it('should preserve meaningful leading/trailing spaces in tokens', async () => {
    const stream = makeStream([
      'data:"Chętnie"\n\ndata:" pomogę"\n\ndata:" Panu"\n\nevent:done\ndata:\n\n',
    ]);
    const tokens = await collectTokens(stream);
    // Joined tokens must reconstruct the original spacing exactly.
    expect(tokens.join('')).toBe('Chętnie pomogę Panu');
  });

  it('should preserve newlines inside a token', async () => {
    const stream = makeStream([
      'data:"line1\\nline2"\n\nevent:done\ndata:\n\n',
    ]);
    const tokens = await collectTokens(stream);
    expect(tokens).toEqual(['line1\nline2']);
  });

  it('should handle SSE frame split across two chunks', async () => {
    const stream = makeStream([
      'data:"Hel',
      'lo"\n\nevent:done\ndata:\n\n',
    ]);
    const tokens = await collectTokens(stream);
    expect(tokens).toEqual(['Hello']);
  });

  it('should throw on error event', async () => {
    const stream = makeStream([
      'event:error\ndata:"LLM_UNAVAILABLE"\n\n',
    ]);
    await expectAsync(collectTokens(stream)).toBeRejectedWithError('LLM_UNAVAILABLE');
  });

  it('should skip empty and whitespace-only frames', async () => {
    const stream = makeStream([
      '   \n\ndata:"token"\n\n\n\nevent:done\ndata:\n\n',
    ]);
    const tokens = await collectTokens(stream);
    expect(tokens).toEqual(['token']);
  });
});
