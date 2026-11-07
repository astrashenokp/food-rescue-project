package com.example.foodrescue.common;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AuditLogWriter {

    private final AuditLogRepository repository;

    public AuditLogWriter(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(AuditLogEntity entity) {
        repository.save(entity);
    }
}
