package com.genrl.gamemaster.simulator.web;

import com.genrl.gamemaster.simulator.dto.EvaluationRequest;
import com.genrl.gamemaster.simulator.dto.EvaluationResult;
import com.genrl.gamemaster.simulator.service.SimulationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/evaluate")
@RequiredArgsConstructor
@Slf4j
public class SimulatorController {

    private final SimulationService simulationService;

    @PostMapping
    public EvaluationResult evaluate(@RequestBody EvaluationRequest request) {
        return simulationService.evaluate(request);
    }
}
