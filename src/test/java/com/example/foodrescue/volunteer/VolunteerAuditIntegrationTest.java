package com.example.foodrescue.volunteer;

import com.example.foodrescue.AuditTestSupport;
import com.example.foodrescue.IntegrationTest;
import com.example.foodrescue.common.DemoData;
import com.example.foodrescue.common.InvalidLotStateException;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.context.support.WithUserDetails;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
@WithUserDetails("volunteer1")
class VolunteerAuditIntegrationTest {

    private static final String VOLUNTEER = "volunteer1";
    private static final String RESERVE_LOT = "RESERVE_LOT";
    private static final String CANCEL_RESERVATION = "CANCEL_RESERVATION";

    @Autowired
    VolunteerService volunteerService;

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void prepareDemoVolunteer() {
        VolunteerTestFixtures.ensureDemoVolunteer(jdbc);
    }

    @Test
    void reserve_PublishedLot_ShouldWriteSuccessRecord() {
        UUID id = lot("PUBLISHED", null);

        volunteerService.reserve(id);

        Map<String, Object> row = singleRow(RESERVE_LOT);
        assertSucceeded(row, VOLUNTEER);
        assertThat((String) row.get("method_name")).contains("reserve");
        assertThat((String) row.get("arguments")).contains(id.toString());
        assertThat(row.get("result")).isEqualTo("null");
        assertThat(lotRow(id)).containsEntry("status", "RESERVED")
                .containsEntry("reserved_by_volunteer_id", DemoData.DEMO_VOLUNTEER);
    }

    @Test
    void cancelReservation_OwnReservation_ShouldWriteSuccessRecord() {
        UUID id = lot("RESERVED", DemoData.DEMO_VOLUNTEER);

        volunteerService.cancelReservation(id);

        Map<String, Object> row = singleRow(CANCEL_RESERVATION);
        assertSucceeded(row, VOLUNTEER);
        assertThat((String) row.get("method_name")).contains("cancelReservation");
        assertThat((String) row.get("arguments")).contains(id.toString());
        assertThat(lotRow(id)).containsEntry("status", "PUBLISHED")
                .containsEntry("reserved_by_volunteer_id", null)
                .containsEntry("reserved_until", null);
    }

    @Test
    void reserve_SameLotTwice_ShouldWriteFailedRecordAndKeepFirstReservation() {
        UUID id = lot("PUBLISHED", null);
        volunteerService.reserve(id);
        Object reservedUntil = lotRow(id).get("reserved_until");

        assertThatThrownBy(() -> volunteerService.reserve(id))
                .isInstanceOf(InvalidLotStateException.class);

        List<Map<String, Object>> rows = auditRows(RESERVE_LOT);
        assertThat(rows).hasSize(2);
        assertSucceeded(rows.getFirst(), VOLUNTEER);
        assertFailed(rows.getLast(), VOLUNTEER, "InvalidLotStateException");
        assertThat(lotRow(id)).containsEntry("status", "RESERVED")
                .containsEntry("reserved_by_volunteer_id", DemoData.DEMO_VOLUNTEER)
                .containsEntry("reserved_until", reservedUntil);
    }

    @Test
    void reserve_NullLotId_ShouldWriteValidationFailureAndRethrow() {
        long lotsBefore = countLots();

        assertThatThrownBy(() -> volunteerService.reserve(null))
                .isInstanceOf(ConstraintViolationException.class);

        assertFailed(singleRow(RESERVE_LOT), VOLUNTEER, "ConstraintViolationException");
        assertThat(countLots()).isEqualTo(lotsBefore);
    }

    @Test
    @WithMockUser(username = "donor1", roles = "DONOR")
    void reserve_ByDonor_ShouldWriteDeniedRecordAndKeepLot() {
        UUID id = lot("PUBLISHED", null);

        assertThatThrownBy(() -> volunteerService.reserve(id))
                .isInstanceOf(AuthorizationDeniedException.class);

        assertFailed(singleRow(RESERVE_LOT), "donor1", "AuthorizationDeniedException");
        assertThat(lotRow(id)).containsEntry("status", "PUBLISHED")
                .containsEntry("reserved_by_volunteer_id", null);
    }

    private UUID lot(String status, UUID reservedBy) {
        return VolunteerTestFixtures.insertLot(jdbc, VolunteerTestFixtures.uniqueTitle(), status, reservedBy);
    }

    private Map<String, Object> lotRow(UUID id) {
        return jdbc.sql("select status, reserved_by_volunteer_id, reserved_until from food_lots where id = ?")
                .param(id)
                .query()
                .singleRow();
    }

    private long countLots() {
        return jdbc.sql("select count(*) from food_lots").query(Long.class).single();
    }

    private List<Map<String, Object>> auditRows(String action) {
        return AuditTestSupport.auditRows(jdbc, action);
    }

    private Map<String, Object> singleRow(String action) {
        List<Map<String, Object>> rows = auditRows(action);
        assertThat(rows).hasSize(1);
        return rows.getFirst();
    }

    private void assertSucceeded(Map<String, Object> row, String username) {
        assertThat(row.get("username")).isEqualTo(username);
        assertThat(row.get("success")).isEqualTo(true);
        assertThat(row.get("error_message")).isNull();
        assertThat(row.get("created_at")).isNotNull();
    }

    private void assertFailed(Map<String, Object> row, String username, String exceptionName) {
        assertThat(row.get("username")).isEqualTo(username);
        assertThat(row.get("success")).isEqualTo(false);
        assertThat((String) row.get("error_message")).contains(exceptionName);
    }
}
