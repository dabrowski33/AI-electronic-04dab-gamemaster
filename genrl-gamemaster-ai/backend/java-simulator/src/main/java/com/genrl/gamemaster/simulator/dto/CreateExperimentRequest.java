package com.genrl.gamemaster.simulator.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record CreateExperimentRequest(
        @NotBlank String name,
        String description,
        @NotBlank String simulatorType,
        @NotBlank String objectiveFunction,
        Map<String, Object> config
) {}
