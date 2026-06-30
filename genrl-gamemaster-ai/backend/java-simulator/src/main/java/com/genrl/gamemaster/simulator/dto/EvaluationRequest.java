package com.genrl.gamemaster.simulator.dto;

import java.util.Map;

public record EvaluationRequest(
        String agentId,
        String experimentId,
        String gameType,
        Map<String, Object> genome,
        Map<String, Object> neuralWeights,
        int timeoutSeconds
) {}
