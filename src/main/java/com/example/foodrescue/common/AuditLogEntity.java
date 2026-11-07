package com.example.foodrescue.common;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "audit_log")
public class AuditLogEntity {

    private static final int TEXT_LENGTH = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(nullable = false)
    private String methodName;

    @Column(length = TEXT_LENGTH)
    private String arguments;

    @Column(length = TEXT_LENGTH)
    private String result;

    @Column(nullable = false)
    private boolean success;

    @Column(length = TEXT_LENGTH)
    private String errorMessage;

    @Column(nullable = false)
    private long durationMs;

    @Column(nullable = false)
    private Instant createdAt;

    protected AuditLogEntity() {
    }

    public AuditLogEntity(String username, String action, String methodName, String arguments, String result,
                          boolean success, String errorMessage, long durationMs, Instant createdAt) {
        this.username = username;
        this.action = action;
        this.methodName = methodName;
        this.arguments = arguments;
        this.result = result;
        this.success = success;
        this.errorMessage = errorMessage;
        this.durationMs = durationMs;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getAction() {
        return action;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getArguments() {
        return arguments;
    }

    public String getResult() {
        return result;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AuditLogEntity other)) return false;
        return id != null && Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
