package pl.nbp.copilot.model;

import java.util.List;

public record DecisionResult(
    DecisionCategory category,
    String justification,
    String nextSteps,
    List<String> missingInfo
) {}
