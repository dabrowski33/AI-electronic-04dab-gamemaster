package pl.nbp.copilot.dto;

import pl.nbp.copilot.model.DecisionCategory;

import java.util.List;

public record DecisionDto(
    DecisionCategory category,
    String justification,
    String nextSteps,
    List<String> missingInfo
) {}
