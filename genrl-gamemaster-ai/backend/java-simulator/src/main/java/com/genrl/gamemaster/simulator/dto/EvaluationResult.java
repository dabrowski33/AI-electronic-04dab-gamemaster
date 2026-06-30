package com.genrl.gamemaster.simulator.dto;

public record EvaluationResult(
        String agentId,
        double fitnessScore,
        int survivalTime,
        int score,
        boolean winStatus,
        String executionLog,
        long evaluationDurationMs
) {}
