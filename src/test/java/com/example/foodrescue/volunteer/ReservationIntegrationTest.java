package com.example.foodrescue.volunteer;

import com.example.foodrescue.IntegrationTest;
import com.example.foodrescue.common.DemoData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@WithUserDetails("volunteer1")
class ReservationIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void prepareDemoVolunteer() {
        VolunteerTestFixtures.ensureDemoVolunteer(jdbc);
    }

    @Test
    void reserve_PublishedLot_ShouldReturnCreatedAndWriteReservation() throws Exception {
        UUID id = lot("PUBLISHED", null);

        mockMvc.perform(post(reservationUri(id)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", reservationUri(id)));

        Map<String, Object> row = lotRow(id);
        assertThat(row).containsEntry("status", "RESERVED")
                .containsEntry("reserved_by_volunteer_id", DemoData.DEMO_VOLUNTEER);
        assertThat(((Timestamp) row.get("reserved_until")).toInstant()).isAfter(Instant.now());
    }

    @Test
    void reserve_SameLotTwice_ShouldReturnConflictAndKeepFirstReservation() throws Exception {
        UUID id = lot("PUBLISHED", null);
        mockMvc.perform(post(reservationUri(id))).andExpect(status().isCreated());
        Object firstReservedUntil = lotRow(id).get("reserved_until");

        mockMvc.perform(post(reservationUri(id)))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409));

        assertThat(lotRow(id)).containsEntry("status", "RESERVED")
                .containsEntry("reserved_until", firstReservedUntil);
    }

    @Test
    void reserve_DraftLot_ShouldReturnConflictAndKeepDraft() throws Exception {
        UUID id = lot("DRAFT", null);

        mockMvc.perform(post(reservationUri(id)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        assertThat(lotRow(id)).containsEntry("status", "DRAFT")
                .containsEntry("reserved_by_volunteer_id", null);
    }

    @Test
    void reserve_UnknownLot_ShouldReturnNotFound() throws Exception {
        mockMvc.perform(post(reservationUri(UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void reserve_InvalidLotId_ShouldReturnBadRequestNotServerError() throws Exception {
        mockMvc.perform(post("/api/v1/lots/not-a-uuid/reservation"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void reserve_RestrictedVolunteerOnPreparedMeal_ShouldReturnForbiddenAndKeepLot() throws Exception {
        jdbc.sql("update volunteers set no_shows = 4 where id = ?").param(DemoData.DEMO_VOLUNTEER).update();
        UUID id = lot("PUBLISHED", null);
        jdbc.sql("update food_lots set category = 'PREPARED_MEAL' where id = ?").param(id).update();

        mockMvc.perform(post(reservationUri(id)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        assertThat(lotRow(id)).containsEntry("status", "PUBLISHED")
                .containsEntry("reserved_by_volunteer_id", null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DONOR", "COORDINATOR", "ADMIN"})
    void reserve_AsOtherRole_ShouldReturnForbiddenAndKeepLot(String role) throws Exception {
        UUID id = lot("PUBLISHED", null);

        mockMvc.perform(post(reservationUri(id)).with(user("someone").roles(role)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        assertThat(lotRow(id)).containsEntry("status", "PUBLISHED")
                .containsEntry("reserved_by_volunteer_id", null);
    }

    @Test
    @WithAnonymousUser
    void reserve_WithoutAuthentication_ShouldReturnUnauthorizedAndKeepLot() throws Exception {
        UUID id = lot("PUBLISHED", null);

        mockMvc.perform(post(reservationUri(id)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        assertThat(lotRow(id)).containsEntry("status", "PUBLISHED");
    }

    @Test
    void cancel_OwnReservation_ShouldReturnNoContentAndClearReservation() throws Exception {
        UUID id = lot("RESERVED", DemoData.DEMO_VOLUNTEER);

        mockMvc.perform(delete(reservationUri(id)))
                .andExpect(status().isNoContent());

        assertThat(lotRow(id)).containsEntry("status", "PUBLISHED")
                .containsEntry("reserved_by_volunteer_id", null)
                .containsEntry("reserved_until", null);
    }

    @Test
    void cancel_UnknownLot_ShouldReturnNotFound() throws Exception {
        mockMvc.perform(delete(reservationUri(UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void cancel_PublishedLot_ShouldReturnConflictAndKeepLot() throws Exception {
        UUID id = lot("PUBLISHED", null);

        mockMvc.perform(delete(reservationUri(id)))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409));

        assertThat(lotRow(id)).containsEntry("status", "PUBLISHED");
    }

    @Test
    void cancel_DraftLot_ShouldReturnConflictAndNotPublishDraft() throws Exception {
        UUID id = lot("DRAFT", null);

        mockMvc.perform(delete(reservationUri(id)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        assertThat(lotRow(id)).containsEntry("status", "DRAFT");
    }

    @Test
    void cancel_ReservationOfAnotherVolunteer_ShouldReturnForbiddenAndKeepReservation() throws Exception {
        UUID otherVolunteer = UUID.randomUUID();
        VolunteerTestFixtures.insertVolunteer(jdbc, otherVolunteer);
        UUID id = lot("RESERVED", otherVolunteer);

        mockMvc.perform(delete(reservationUri(id)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        assertThat(lotRow(id)).containsEntry("status", "RESERVED")
                .containsEntry("reserved_by_volunteer_id", otherVolunteer);
    }

    @Test
    void cancel_AsDonor_ShouldReturnForbiddenAndKeepReservation() throws Exception {
        UUID id = lot("RESERVED", DemoData.DEMO_VOLUNTEER);

        mockMvc.perform(delete(reservationUri(id)).with(user("donor").roles("DONOR")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        assertThat(lotRow(id)).containsEntry("status", "RESERVED")
                .containsEntry("reserved_by_volunteer_id", DemoData.DEMO_VOLUNTEER);
    }

    private UUID lot(String status, UUID reservedBy) {
        return VolunteerTestFixtures.insertLot(jdbc, VolunteerTestFixtures.uniqueTitle(), status, reservedBy);
    }

    private Map<String, Object> lotRow(UUID id) {
        return jdbc.sql("select status, reserved_by_volunteer_id, reserved_until from food_lots where id = ?")
                .param(id).query().singleRow();
    }

    private String reservationUri(UUID id) {
        return "/api/v1/lots/" + id + "/reservation";
    }
}
