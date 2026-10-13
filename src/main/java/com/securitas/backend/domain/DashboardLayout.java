package com.securitas.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Entity
@Table(name = "dashboard_layouts")
public class DashboardLayout {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "layout_json", nullable = false)
    private String layoutJson;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected DashboardLayout() {
    }

    public DashboardLayout(Long userId, String layoutJson) {
        this.userId = userId;
        this.layoutJson = layoutJson;
        this.updatedAt = OffsetDateTime.now();
    }

    public Long getUserId() {
        return userId;
    }

    public String getLayoutJson() {
        return layoutJson;
    }

    public void setLayoutJson(String layoutJson) {
        this.layoutJson = layoutJson;
        this.updatedAt = OffsetDateTime.now();
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
