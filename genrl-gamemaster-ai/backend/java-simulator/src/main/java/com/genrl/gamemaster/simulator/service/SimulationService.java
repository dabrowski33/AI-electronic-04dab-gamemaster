package com.genrl.gamemaster.simulator.service;

import com.genrl.gamemaster.simulator.domain.SystemEvent;
import com.genrl.gamemaster.simulator.dto.EvaluationRequest;
import com.genrl.gamemaster.simulator.dto.EvaluationResult;
import com.genrl.gamemaster.simulator.repository.SystemEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class SimulationService {

    private final SystemEventRepository systemEventRepository;

    @Transactional
    public EvaluationResult evaluate(EvaluationRequest req) {
        log.debug("Evaluating agent={} experiment={} game={}", req.agentId(), req.experimentId(), req.gameType());

        long startMs = System.currentTimeMillis();

        // Simulate game evaluation
        Random rnd = ThreadLocalRandom.current();
        double fitnessScore = rnd.nextDouble() * 100.0;
        int survivalTime = 100 + rnd.nextInt(4901);   // 100 – 5000
        int score = rnd.nextInt(10001);                // 0 – 10000
        boolean winStatus = score > 8000;

        // Simulate evaluation time — respect timeoutSeconds
        int effectiveTimeout = req.timeoutSeconds() > 0 ? req.timeoutSeconds() : 5;
        int simulatedMs = rnd.nextInt(Math.min(effectiveTimeout * 1000, 3000));
        try {
            Thread.sleep(simulatedMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Evaluation interrupted for agent={}", req.agentId());
        }

        long durationMs = System.currentTimeMillis() - startMs;

        String executionLog = buildExecutionLog(req, fitnessScore, survivalTime, score, winStatus, durationMs);

        // Persist audit event
        persistSystemEvent(req, fitnessScore, score, winStatus);

        EvaluationResult result = new EvaluationResult(
                req.agentId(),
                fitnessScore,
                survivalTime,
                score,
                winStatus,
                executionLog,
                durationMs
        );

        log.info("Evaluation complete agent={} fitness={:.4f} score={} win={} durationMs={}",
                req.agentId(), fitnessScore, score, winStatus, durationMs);

        return result;
    }

    private String buildExecutionLog(EvaluationRequest req, double fitnessScore, int survivalTime,
                                     int score, boolean winStatus, long durationMs) {
        return String.format(
                "[SimulationService] agent=%s game=%s fitness=%.4f survival=%d score=%d win=%b duration=%dms",
                req.agentId(), req.gameType(), fitnessScore, survivalTime, score, winStatus, durationMs
        );
    }

    private void persistSystemEvent(EvaluationRequest req, double fitnessScore, int score, boolean winStatus) {
        try {
            SystemEvent event = SystemEvent.builder()
                    .eventType("AGENT_EVALUATION_COMPLETE")
                    .severity("info")
                    .message(String.format("Agent %s evaluated: fitness=%.4f score=%d win=%b",
                            req.agentId(), fitnessScore, score, winStatus))
                    .metadata(Map.of(
                            "agentId", req.agentId() != null ? req.agentId() : "",
                            "experimentId", req.experimentId() != null ? req.experimentId() : "",
                            "gameType", req.gameType() != null ? req.gameType() : "",
                            "fitnessScore", fitnessScore,
                            "score", score,
                            "winStatus", winStatus
                    ))
                    .sourceService("java-simulator")
                    .build();

            systemEventRepository.save(event);
        } catch (Exception e) {
            log.warn("Failed to persist system event for agent={}: {}", req.agentId(), e.getMessage());
        }
    }
}
