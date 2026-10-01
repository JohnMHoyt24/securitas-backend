package com.securitas.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Entity
@Table(name = "alerts")
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pattern_type", nullable = false)
    private String patternType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "subgraph_json", nullable = false)
    private String subgraphJson;

    @Column(name = "fingerprint", nullable = false)
    private String fingerprint;

    @Column(name = "narrative")
    private String narrative;

    @Column(name = "status", nullable = false)
    private String status = "NEW";

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Alert() {
    }

    public Alert(String patternType, String subgraphJson, String fingerprint) {
        this.patternType = patternType;
        this.subgraphJson = subgraphJson;
        this.fingerprint = fingerprint;
    }

    public Long getId() {
        return id;
    }

    public String getPatternType() {
        return patternType;
    }

    public String getSubgraphJson() {
        return subgraphJson;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public String getNarrative() {
        return narrative;
    }

    public void setNarrative(String narrative) {
        this.narrative = narrative;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
