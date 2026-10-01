package com.securitas.backend.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    boolean existsByFingerprint(String fingerprint);
}
