package com.securitas.backend.api;

import com.securitas.backend.metrics.MetricsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/metrics")
public class MetricsController {

    private final MetricsService metricsService;

    public MetricsController(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @GetMapping("/transactions-by-day")
    public List<MetricsService.DayCount> transactionsByDay() {
        return metricsService.transactionsByDay();
    }

    @GetMapping("/transaction-volume-by-day")
    public List<MetricsService.DayAmount> transactionVolumeByDay() {
        return metricsService.transactionVolumeByDay();
    }

    @GetMapping("/transactions-by-payment-format")
    public List<MetricsService.CategoryCount> transactionsByPaymentFormat() {
        return metricsService.transactionsByPaymentFormat();
    }

    @GetMapping("/accounts-by-bank")
    public List<MetricsService.CategoryCount> accountsByBank() {
        return metricsService.accountsByBank();
    }
}
