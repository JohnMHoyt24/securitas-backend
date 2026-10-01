package com.securitas.backend.detection;

import com.securitas.backend.domain.Alert;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Unsecured for now (SecurityConfig permits everything until Stage 6 adds JWT roles,
 * at which point this moves under ADMIN-only access).
 */
@RestController
@RequestMapping("/api/admin")
public class DetectionController {

    private final FraudDetectionService detectionService;

    public DetectionController(FraudDetectionService detectionService) {
        this.detectionService = detectionService;
    }

    @PostMapping("/scan-now")
    public List<Alert> scanNow() {
        return detectionService.scan();
    }
}
