---
name: openai-sdk-patterns
description: Key API patterns for openai-java 4.41.0 used in this project — URL construction, message building, streaming, profile wiring
metadata:
  type: reference
---

## openai-java 4.41.0 — Critical API facts

### URL construction
The SDK appends paths directly to `baseUrl`. It does NOT add `/v1`. OpenRouter's baseUrl is
`https://openrouter.ai/api/v1` — the `/v1` is already in the baseUrl. When pointing at WireMock
(`http://localhost:PORT`), the request goes to `http://localhost:PORT/chat/completions`.
WireMock stubs must use `/chat/completions`, NOT `/v1/chat/completions`.

### Client builder
```java
OpenAIOkHttpClient.builder()
    .baseUrl(props.baseUrl())
    .apiKey(props.apiKey())
    .build();
```
Never use `.fromEnv()` — it leaks to api.openai.com.

### ChatCompletionMessageParam — union type
`ChatCompletionMessageParam` is a sealed Kotlin union; cannot implicitly cast from subtypes.
Use builder's typed `addMessage()` overloads:
- `builder.addMessage(ChatCompletionSystemMessageParam)` — works directly
- `builder.addMessage(ChatCompletionUserMessageParam)` — works directly
- `builder.addMessage(ChatCompletionAssistantMessageParam)` — works directly
Do NOT build a `List<ChatCompletionMessageParam>` manually — use `addMessage()` per item.

### Multi-part user message (text + image)
```java
builder.contentOfArrayOfContentParts(List.of(textPart, imagePart))
```
Not `content(List.of(...))` — the specific overload is `contentOfArrayOfContentParts`.

### Non-streaming
```java
client.chat().completions().create(params)
    .choices().get(0).message().content().orElseThrow()
```

### Streaming — createStreaming (no .stream(true) on builder)
```java
try (StreamResponse<ChatCompletionChunk> stream = client.chat().completions().createStreaming(params)) {
    stream.stream().forEach(chunk -> {
        String delta = chunk.choices().stream().findFirst()
            .flatMap(c -> c.delta().content()).orElse("");
        // emit delta
    });
}
```

### JSON mode
Use `ResponseFormatJsonObject.builder().build()` to force JSON output. Simpler than full JsonSchema.

### Streaming adds "stream":true in body
The SDK automatically adds `"stream": true` to the request body for `createStreaming()`.
