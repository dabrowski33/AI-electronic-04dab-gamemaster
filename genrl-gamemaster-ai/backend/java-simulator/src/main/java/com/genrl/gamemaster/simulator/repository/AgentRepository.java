package com.genrl.gamemaster.simulator.repository;

import com.genrl.gamemaster.simulator.domain.Agent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AgentRepository extends JpaRepository<Agent, UUID> {

    List<Agent> findByGenerationIdOrderByFitnessScoreDesc(UUID generationId);
}
