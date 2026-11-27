package com.example.foodrescue.lot;

import com.example.foodrescue.IntegrationTest;
import com.example.foodrescue.common.LotRepository;
import com.example.foodrescue.common.LotStatus;
import com.example.foodrescue.common.SchedulingTestSupport;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class ExpireOverdueLotsJobIntegrationTest extends SchedulingTestSupport {

    private static final String LOCK_NAME = "expireOverdueLots";
    private static final int HOURS_IN_PAST = 10;

    @Autowired
    ExpireOverdueLotsJob job;

    @Autowired
    LotRepository lotRepository;

    @Autowired
    LockProvider lockProvider;

    @Autowired
    JdbcClient jdbc;

    @Test
    void run_ShouldExpireOnlyPublishedLotsWithPassedPickupWindow() {
        UUID overdue = saveLot(LotStatus.PUBLISHED, hoursAgo(HOURS_IN_PAST));
        UUID current = saveLot(LotStatus.PUBLISHED, Instant.now());
        UUID reservedOverdue = saveLot(LotStatus.RESERVED, hoursAgo(HOURS_IN_PAST));

        job.run();

        assertThat(statusOf(overdue)).isEqualTo("EXPIRED");
        assertThat(statusOf(current)).isEqualTo("PUBLISHED");
        assertThat(statusOf(reservedOverdue)).isEqualTo("RESERVED");
        assertThat(expiredHistoryCount(overdue)).isEqualTo(1);
    }

    @Test
    void run_CalledTwice_ShouldChangeNothingTheSecondTime() {
        UUID overdue = saveLot(LotStatus.PUBLISHED, hoursAgo(HOURS_IN_PAST));

        job.run();
        resetLocks();
        job.run();

        assertThat(statusOf(overdue)).isEqualTo("EXPIRED");
        assertThat(expiredHistoryCount(overdue)).isEqualTo(1);
    }

    @Test
    void run_WhenAnotherNodeHoldsTheLock_ShouldSkipUntilItIsReleased() {
        UUID overdue = saveLot(LotStatus.PUBLISHED, hoursAgo(HOURS_IN_PAST));
        Optional<SimpleLock> lock = lockProvider.lock(
                new LockConfiguration(Instant.now(), LOCK_NAME, Duration.ofMinutes(1), Duration.ZERO));
        assertThat(lock).isPresent();

        job.run();
        assertThat(statusOf(overdue)).isEqualTo("PUBLISHED");

        lock.get().unlock();
        job.run();
        assertThat(statusOf(overdue)).isEqualTo("EXPIRED");
    }

    private UUID saveLot(LotStatus status, Instant createdAt) {
        return lotRepository.save(LotTestData.lot(UUID.randomUUID(), status, createdAt, 1)).getId();
    }

    private Instant hoursAgo(int hours) {
        return Instant.now().minus(hours, ChronoUnit.HOURS);
    }

    private String statusOf(UUID id) {
        return jdbc.sql("select status from food_lots where id = ?").param(id).query(String.class).single();
    }

    private long expiredHistoryCount(UUID id) {
        return jdbc.sql("select count(*) from lot_status_history where lot_id = ? and to_status = 'EXPIRED'")
                .param(id).query(Long.class).single();
    }
}
