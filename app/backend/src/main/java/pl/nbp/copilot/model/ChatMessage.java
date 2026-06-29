package pl.nbp.copilot.model;

import java.time.Instant;

public record ChatMessage(String role, String content, Instant createdAt) {}
