package com.lending.platform.policyqa;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/policy-questions")
public class PolicyQuestionController {
    private final PolicyQuestionService service;
    public PolicyQuestionController(PolicyQuestionService service) { this.service = service; }
    @PostMapping
    public PolicyQuestionResponse ask(@Valid @RequestBody PolicyQuestionRequest request) { return service.ask(request); }
}
