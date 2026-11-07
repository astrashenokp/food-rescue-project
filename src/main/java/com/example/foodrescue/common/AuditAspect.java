package com.example.foodrescue.common;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Arrays;

@Aspect
@Component
@Order(1)
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);
    private static final String ANONYMOUS = "anonymous";
    private static final int NANOS_IN_MILLI = 1_000_000;

    private final AuditLogWriter writer;

    public AuditAspect(AuditLogWriter writer) {
        this.writer = writer;
    }

    @Around("@annotation(auditAction)")
    public Object audit(ProceedingJoinPoint joinPoint, AuditAction auditAction) throws Throwable {
        Instant startedAt = Instant.now();
        long start = System.nanoTime();
        String username = currentUsername();
        String arguments = auditAction.logParameters() ? cut(Arrays.toString(joinPoint.getArgs())) : null;
        Object result = null;
        String errorMessage = null;
        boolean success = false;
        try {
            result = joinPoint.proceed();
            success = true;
            return result;
        } catch (Throwable ex) {
            errorMessage = cut(ex.getClass().getSimpleName() + ": " + ex.getMessage());
            throw ex;
        } finally {
            save(new AuditLogEntity(username, auditAction.action(), joinPoint.getSignature().toShortString(),
                    arguments, cut(String.valueOf(result)), success, errorMessage,
                    (System.nanoTime() - start) / NANOS_IN_MILLI, startedAt));
        }
    }

    private void save(AuditLogEntity entity) {
        try {
            writer.save(entity);
        } catch (RuntimeException ex) {
            log.error("Не вдалося зберегти запис аудиту {}", entity.getAction(), ex);
        }
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? ANONYMOUS : authentication.getName();
    }

    private String cut(String value) {
        return value.length() <= AuditLogEntity.TEXT_LENGTH ? value : value.substring(0, AuditLogEntity.TEXT_LENGTH);
    }
}
