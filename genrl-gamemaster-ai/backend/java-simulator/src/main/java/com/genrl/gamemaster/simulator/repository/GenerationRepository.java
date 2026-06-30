package com.genrl.gamemaster.simulator.repository;

import com.genrl.gamemaster.simulator.domain.Generation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GenerationRepository extends JpaRepository<Generation, UUID> {

    List<Generation> findByExperimentIdOrderByGenerationNumberAsc(UUID experimentId);

    Optional<Generation> findByExperimentIdAndGenerationNumber(UUID experimentId, int number);
}
