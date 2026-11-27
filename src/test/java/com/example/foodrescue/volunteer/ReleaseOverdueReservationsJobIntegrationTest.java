package com.example.foodrescue.volunteer;

import com.example.foodrescue.IntegrationTest;
import com.example.foodrescue.common.SchedulingTestSupport;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class ReleaseOverdueReservationsJobIntegrationTest extends SchedulingTestSupport {

    private static final String LOCK_NAME = "releaseOverdueReservations";
    private static final String RESERVED = "RESERVED";
    private static final String PUBLISHED = "PUBLISHED";

    @Autowired
    ReleaseOverdueReservationsJob job;

    @Autowired
    LockProvider lockProvider;

    @Autowired
    JdbcClient jdbc;

    private UUID volunteerId;

    @BeforeEach
    void prepareVolunteer() {
        volunteerId = UUID.randomUUID();
        VolunteerTestFixtures.insertVolunteer(jdbc, volunteerId);
    }

    @Test
    void run_ShouldReleaseOnlyOverdueReservationsAndIncrementNoShows() {
        Instant past = Instant.now().minus(Duration.ofHours(1));
        UUID overdue = lot(RESERVED, past);
        UUID secondOverdue = lot(RESERVED, past);
        UUID current = lot(RESERVED, Instant.now().plus(Duration.ofHours(1)));
        UUID withoutDeadline = lot(RESERVED, null);
        UUID pickedUp = lot("PICKED_UP", past);
        Map<String, Object> currentBefore = lotRow(current);
        Map<String, Object> withoutDeadlineBefore = lotRow(withoutDeadline);
        Map<String, Object> pickedUpBefore = lotRow(pickedUp);

        job.run();

        assertReleased(overdue);
        assertReleased(secondOverdue);
        assertThat(noShows()).isEqualTo(2);
        assertThat(lotRow(current)).isEqualTo(currentBefore);
        assertThat(lotRow(withoutDeadline)).isEqualTo(withoutDeadlineBefore);
        assertThat(lotRow(pickedUp)).isEqualTo(pickedUpBefore);
        assertThat(releasedHistoryCount(current)).isZero();
        assertThat(releasedHistoryCount(withoutDeadline)).isZero();
        assertThat(releasedHistoryCount(pickedUp)).isZero();
    }

    @Test
    void run_CalledTwice_ShouldNotIncrementNoShowsOrHistoryAgain() {
        UUID overdue = lot(RESERVED, Instant.now().minus(Duration.ofHours(1)));

        job.run();
        assertReleased(overdue);
        assertThat(noShows()).isEqualTo(1);
        Map<String, Object> afterFirstRun = lotRow(overdue);
        resetLocks();
        job.run();

        assertThat(lotRow(overdue)).isEqualTo(afterFirstRun);
        assertThat(noShows()).isEqualTo(1);
        assertThat(releasedHistoryCount(overdue)).isEqualTo(1);
    }

    @Test
    void run_WhenAnotherNodeHoldsTheLock_ShouldSkipUntilItIsReleased() {
        UUID overdue = lot(RESERVED, Instant.now().minus(Duration.ofHours(1)));
        Map<String, Object> before = lotRow(overdue);
        Optional<SimpleLock> lock = lockProvider.lock(
                new LockConfiguration(Instant.now(), LOCK_NAME, Duration.ofMinutes(1), Duration.ZERO));
        assertThat(lock).isPresent();

        try {
            job.run();

            assertThat(lotRow(overdue)).isEqualTo(before);
            assertThat(noShows()).isZero();
            assertThat(releasedHistoryCount(overdue)).isZero();
        } finally {
            lock.orElseThrow().unlock();
        }
        job.run();

        assertReleased(overdue);
        assertThat(noShows()).isEqualTo(1);
    }

    private UUID lot(String status, Instant reservedUntil) {
        UUID id = VolunteerTestFixtures.insertLot(jdbc, VolunteerTestFixtures.uniqueTitle(), status, volunteerId);
        jdbc.sql("update food_lots set reserved_until = ? where id = ?")
                .params(reservedUntil == null ? null : Timestamp.from(reservedUntil), id)
                .update();
        return id;
    }

    private Map<String, Object> lotRow(UUID id) {
        return jdbc.sql("select status, reserved_by_volunteer_id, reserved_until, version from food_lots where id = ?")
                .param(id).query().singleRow();
    }

    private int noShows() {
        return jdbc.sql("select no_shows from volunteers where id = ?")
                .param(volunteerId).query(Integer.class).single();
    }

    private long releasedHistoryCount(UUID id) {
        return jdbc.sql("select count(*) from lot_status_history where lot_id = ? and from_status = 'RESERVED' and to_status = 'PUBLISHED'")
                .param(id).query(Long.class).single();
    }

    private void assertReleased(UUID id) {
        assertThat(lotRow(id)).containsEntry("status", PUBLISHED)
                .containsEntry("reserved_by_volunteer_id", null)
                .containsEntry("reserved_until", null);
        assertThat(releasedHistoryCount(id)).isEqualTo(1);
    }
}
