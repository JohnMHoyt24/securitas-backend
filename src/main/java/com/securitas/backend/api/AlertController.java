package com.securitas.backend.api;

import com.securitas.backend.domain.Alert;
import com.securitas.backend.domain.AlertRepository;
import com.securitas.backend.graph.SubgraphQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertRepository alertRepository;
    private final SubgraphQueryService subgraphQueryService;

    public AlertController(AlertRepository alertRepository, SubgraphQueryService subgraphQueryService) {
        this.alertRepository = alertRepository;
        this.subgraphQueryService = subgraphQueryService;
    }

    @GetMapping
    public List<Alert> listAlerts() {
        return alertRepository.findAll();
    }

    @GetMapping("/{id}/subgraph")
    public SubgraphQueryService.SubgraphResponse getSubgraph(@PathVariable Long id) {
        Alert alert = alertRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alert not found"));
        return subgraphQueryService.buildSubgraph(alert);
    }
}
