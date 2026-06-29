package pl.nbp.copilot.dto;

import java.util.Map;

public record ErrorResponse(String code, String message, Map<String, String> fields) {}
