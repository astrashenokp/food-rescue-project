package com.example.foodrescue.volunteer;

import com.example.foodrescue.common.DemoData;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

final class VolunteerTestFixtures {

    private VolunteerTestFixtures() {
    }

    static void ensureDemoVolunteer(JdbcClient jdbc) {
        long existing = jdbc.sql("select count(*) from volunteers where id = ?")
                .param(DemoData.DEMO_VOLUNTEER).query(Long.class).single();
        if (existing == 0L) {
            insertVolunteer(jdbc, DemoData.DEMO_VOLUNTEER);
        }
    }

    static void insertVolunteer(JdbcClient jdbc, UUID id) {
        jdbc.sql("""
                        insert into volunteers
                        (id, version, full_name, email, phone, transport_type, activity_zone,
                         completed_deliveries, late_pickups, no_shows)
                        values (?, 0, 'Тестовий волонтер', ?, '+380501234567', 'CAR', 'Київ', 0, 0, 0)
                        """)
                .params(id, "volunteer-" + UUID.randomUUID() + "@example.test")
                .update();
    }

    static UUID insertLot(JdbcClient jdbc, String title, String status, UUID reservedBy) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        Timestamp reservedUntil = reservedBy == null ? null : Timestamp.from(now.plus(30, ChronoUnit.MINUTES));
        jdbc.sql("""
                        insert into food_lots
                        (id, version, donor_org_id, title, category, total_weight_kg, storage_condition,
                         pickup_address, pickup_from, pickup_to, created_at, published_at,
                         reserved_by_volunteer_id, reserved_until, status)
                        values (?, 0, ?, ?, 'BAKERY', 5, 'ROOM', 'Київ, вул. Тестова, 1', ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(id, UUID.randomUUID(), title,
                        Timestamp.from(now.plus(2, ChronoUnit.HOURS)), Timestamp.from(now.plus(4, ChronoUnit.HOURS)),
                        Timestamp.from(now.minus(2, ChronoUnit.HOURS)), Timestamp.from(now.minus(1, ChronoUnit.HOURS)),
                        reservedBy, reservedUntil, status)
                .update();
        return id;
    }

    static String uniqueTitle() {
        return "Резерв " + UUID.randomUUID();
    }
}
