package com.genrl.gamemaster.simulator.service;

import com.genrl.gamemaster.simulator.domain.Experiment;
import com.genrl.gamemaster.simulator.dto.CreateExperimentRequest;
import com.genrl.gamemaster.simulator.dto.ExperimentResponse;
import com.genrl.gamemaster.simulator.repository.ExperimentRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExperimentService {

    private final ExperimentRepository experimentRepository;

    @Transactional
    public ExperimentResponse createExperiment(CreateExperimentRequest request) {
        log.debug("Creating experiment: {}", request.name());

        Map<String, Object> config = request.config() != null ? request.config() : Map.of();

        Experiment experiment = Experiment.builder()
                .name(request.name())
                .description(request.description())
                .simulatorType(request.simulatorType())
                .objectiveFunction(request.objectiveFunction())
                .config(config)
                .build();

        Experiment saved = experimentRepository.save(experiment);
        log.info("Created experiment id={} name={}", saved.getId(), saved.getName());
        return ExperimentResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<ExperimentResponse> listExperiments() {
        return experimentRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(ExperimentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ExperimentResponse getExperiment(UUID id) {
        Experiment experiment = experimentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Experiment not found: " + id));
        return ExperimentResponse.from(experiment);
    }

    @Transactional
    public ExperimentResponse updateExperimentStatus(UUID id, String status) {
        log.debug("Updating experiment {} status to {}", id, status);

        Experiment experiment = experimentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Experiment not found: " + id));

        experiment.setStatus(status);
        Experiment saved = experimentRepository.save(experiment);
        log.info("Updated experiment id={} status={}", saved.getId(), saved.getStatus());
        return ExperimentResponse.from(saved);
    }
}
