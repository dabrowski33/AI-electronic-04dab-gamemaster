package com.genrl.gamemaster.simulator.dto;

import com.genrl.gamemaster.simulator.domain.Experiment;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record ExperimentResponse(
        UUID id,
        String name,
        String description,
        String simulatorType,
        String objectiveFunction,
        String status,
        Map<String, Object> config,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime completedAt
) {

    public static ExperimentResponse from(Experiment experiment) {
        return new ExperimentResponse(
                experiment.getId(),
                experiment.getName(),
                experiment.getDescription(),
                experiment.getSimulatorType(),
                experiment.getObjectiveFunction(),
                experiment.getStatus(),
                experiment.getConfig(),
                experiment.getCreatedAt(),
                experiment.getUpdatedAt(),
                experiment.getCompletedAt()
        );
    }
}
