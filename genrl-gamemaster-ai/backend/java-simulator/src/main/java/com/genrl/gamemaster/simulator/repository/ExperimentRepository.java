package com.genrl.gamemaster.simulator.repository;

import com.genrl.gamemaster.simulator.domain.Experiment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExperimentRepository extends JpaRepository<Experiment, UUID> {

    List<Experiment> findAllByOrderByCreatedAtDesc();

    Optional<Experiment> findByIdAndStatus(UUID id, String status);
}
