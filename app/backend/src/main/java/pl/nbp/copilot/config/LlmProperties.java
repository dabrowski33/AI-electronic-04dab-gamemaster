package pl.nbp.copilot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.llm")
public record LlmProperties(
    String baseUrl,
    String apiKey,
    String visionModel,
    String textModel,
    String appUrl,
    String appTitle
) {}
