export async function* parseSseStream(body: ReadableStream<Uint8Array>): AsyncGenerator<string> {
  const reader = body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      const frames = buffer.split('\n\n');
      buffer = frames.pop() ?? '';
      for (const frame of frames) {
        if (!frame.trim()) continue;
        const lines = frame.split('\n');
        const eventLine = lines.find(l => l.startsWith('event:'));
        const dataLine = lines.find(l => l.startsWith('data:'));
        const eventType = eventLine?.slice('event:'.length).trim();
        // Raw payload after the `data:` prefix. We must NOT trim it: each token is a JSON-encoded
        // string (e.g. `" pom"`), and trimming would destroy meaningful leading/trailing spaces.
        const rawData = dataLine?.slice('data:'.length) ?? '';
        if (eventType === 'done') return;
        if (eventType === 'error') throw new Error('LLM_UNAVAILABLE');
        if (!rawData) continue;
        // Each token is JSON-encoded by the backend so spaces, newlines and unicode survive intact.
        let token: string;
        try {
          token = JSON.parse(rawData);
        } catch {
          // Fallback for any non-JSON frame: emit the raw payload as-is.
          token = rawData;
        }
        if (token) yield token;
      }
    }
  } finally {
    reader.releaseLock();
  }
}
