package pl.nbp.copilot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.image")
public record ImageProperties(int maxLongEdge, double jpegQuality) {}
