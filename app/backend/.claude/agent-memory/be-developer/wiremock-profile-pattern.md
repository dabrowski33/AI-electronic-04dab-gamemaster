---
name: wiremock-profile-pattern
description: Profile and stub path pattern for WireMock integration tests with OpenRouterLlmGateway
metadata:
  type: feedback
---

Use `@ActiveProfiles("integration-test")` for WireMock gateway tests — NOT "test".

**Why:** `StubLlmGateway` is active on profiles "stub-llm" OR "test". `OpenRouterLlmGateway` 
profile changed to `!stub-llm`. If the WireMock test used `@ActiveProfiles("test")`, both 
gateways would be active, causing a bean conflict. "integration-test" profile activates neither
StubLlmGateway nor any conflicting beans — only the real gateway is created.

**How to apply:** Any WireMock integration test that needs the real `OpenRouterLlmGateway` bean
must use `@ActiveProfiles("integration-test")` and set `@DynamicPropertySource` to override
`app.llm.base-url`, `api-key`, `vision-model`, `text-model`, `app-url`, `app-title`.

The WireMock stub path for chat completions must be `/chat/completions` (NOT `/v1/chat/completions`)
because the SDK appends paths to baseUrl as-is, and WireMock baseUrl has no `/v1` prefix.
For streaming tests where the path may vary, use `urlPathMatching(".*/chat/completions")`.

**Related:** [[openai-sdk-patterns]]
