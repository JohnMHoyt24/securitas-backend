package com.securitas.backend.api;

import com.securitas.backend.domain.DashboardLayout;
import com.securitas.backend.domain.DashboardLayoutRepository;
import com.securitas.backend.domain.User;
import com.securitas.backend.domain.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Each authenticated user has at most one saved dashboard layout, keyed by their user id
 * (resolved from the JWT's subject, not trusted from the request body). The layout's
 * internal shape (widgets, chart types, metrics) is opaque to the backend — same pattern
 * as Alert.subgraphJson — so the frontend can evolve it without a backend migration.
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardLayoutRepository dashboardLayoutRepository;
    private final UserRepository userRepository;

    public DashboardController(DashboardLayoutRepository dashboardLayoutRepository, UserRepository userRepository) {
        this.dashboardLayoutRepository = dashboardLayoutRepository;
        this.userRepository = userRepository;
    }

    public record SaveLayoutRequest(String layoutJson) {
    }

    @GetMapping
    public ResponseEntity<DashboardLayout> getLayout(Authentication authentication) {
        Long userId = currentUserId(authentication);
        return dashboardLayoutRepository.findById(userId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping
    public DashboardLayout saveLayout(Authentication authentication, @RequestBody SaveLayoutRequest request) {
        Long userId = currentUserId(authentication);
        DashboardLayout layout = dashboardLayoutRepository.findById(userId).orElse(null);
        if (layout == null) {
            layout = new DashboardLayout(userId, request.layoutJson());
        } else {
            layout.setLayoutJson(request.layoutJson());
        }
        return dashboardLayoutRepository.save(layout);
    }

    private Long currentUserId(Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));
        return user.getId();
    }
}
