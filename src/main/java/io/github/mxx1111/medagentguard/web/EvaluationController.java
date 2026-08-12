package io.github.mxx1111.medagentguard.web;

import io.github.mxx1111.medagentguard.domain.EvaluationRequest;
import io.github.mxx1111.medagentguard.domain.EvaluationResult;
import io.github.mxx1111.medagentguard.engine.EvaluationEngine;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class EvaluationController {

    private final EvaluationEngine engine;

    public EvaluationController(EvaluationEngine engine) {
        this.engine = engine;
    }

    @PostMapping("/evaluations")
    @ResponseStatus(HttpStatus.OK)
    public EvaluationResult evaluate(@Valid @RequestBody EvaluationRequest request) {
        return engine.evaluate(request);
    }

    @GetMapping("/rules")
    public List<EvaluationEngine.RuleDescriptor> rules() {
        return engine.rules();
    }
}
