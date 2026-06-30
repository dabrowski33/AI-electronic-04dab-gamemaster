package com.genrl.gamemaster.simulator.web;

import com.genrl.gamemaster.simulator.dto.CreateExperimentRequest;
import com.genrl.gamemaster.simulator.dto.ExperimentResponse;
import com.genrl.gamemaster.simulator.service.ExperimentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/experiments")
@RequiredArgsConstructor
@Slf4j
public class ExperimentController {

    private final ExperimentService experimentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExperimentResponse createExperiment(@Valid @RequestBody CreateExperimentRequest request) {
        return experimentService.createExperiment(request);
    }

    @GetMapping
    public List<ExperimentResponse> listExperiments() {
        return experimentService.listExperiments();
    }

    @GetMapping("/{id}")
    public ExperimentResponse getExperiment(@PathVariable UUID id) {
        return experimentService.getExperiment(id);
    }

    @PutMapping("/{id}/status")
    public ExperimentResponse updateStatus(@PathVariable UUID id,
                                           @RequestBody Map<String, String> body) {
        String status = body.get("status");
        return experimentService.updateExperimentStatus(id, status);
    }
}
