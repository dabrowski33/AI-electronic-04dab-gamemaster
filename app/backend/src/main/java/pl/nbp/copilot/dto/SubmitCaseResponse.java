package pl.nbp.copilot.dto;

public record SubmitCaseResponse(
    String sessionId,
    DecisionDto decision,
    String firstMessage,
    CaseSummaryDto caseSummary
) {}
