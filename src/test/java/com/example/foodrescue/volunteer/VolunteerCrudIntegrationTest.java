package com.example.foodrescue.volunteer;

import com.example.foodrescue.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@WithMockUser(roles = "VOLUNTEER")
class VolunteerCrudIntegrationTest {

    private static final String VOLUNTEERS = "/api/v1/volunteers";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcClient jdbc;

    @Test
    void create_ValidRequest_ShouldReturnCreatedAndSaveRow() throws Exception {
        String email = uniqueEmail();

        String location = mockMvc.perform(post(VOLUNTEERS).contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(volunteerJson("Олена Коваль", email, "+380671112233")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");

        assertThat(location).startsWith(VOLUNTEERS + "/");
        UUID id = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
        Map<String, Object> row = volunteerRow(id);
        assertThat(row).containsEntry("full_name", "Олена Коваль")
                .containsEntry("email", email)
                .containsEntry("phone", "+380671112233")
                .containsEntry("transport_type", "BIKE")
                .containsEntry("activity_zone", "Київ, Поділ")
                .containsEntry("completed_deliveries", 0)
                .containsEntry("late_pickups", 0)
                .containsEntry("no_shows", 0);
    }

    @Test
    void create_InvalidFields_ShouldReturnBadRequestAndKeepTable() throws Exception {
        long before = countVolunteers();

        mockMvc.perform(post(VOLUNTEERS).contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(volunteerJson("І", "not-an-email", "123")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors.fullName").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.phone").exists());

        assertThat(countVolunteers()).isEqualTo(before);
    }

    @Test
    void create_MalformedJson_ShouldReturnBadRequestNotServerError() throws Exception {
        long before = countVolunteers();

        mockMvc.perform(post(VOLUNTEERS).contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));

        assertThat(countVolunteers()).isEqualTo(before);
    }

    @Test
    void create_DuplicateEmail_ShouldReturnConflictAndKeepSingleRow() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(post(VOLUNTEERS).contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(volunteerJson("Перший Волонтер", email, "+380671112233")))
                .andExpect(status().isCreated());

        mockMvc.perform(post(VOLUNTEERS).contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(volunteerJson("Другий Волонтер", email, "+380674445566")))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409));

        assertThat(jdbc.sql("select count(*) from volunteers where email = ?").param(email).query(Long.class).single())
                .isEqualTo(1L);
    }

    @Test
    @WithAnonymousUser
    void create_WithoutAuthentication_ShouldReturnUnauthorizedAndKeepTable() throws Exception {
        long before = countVolunteers();

        mockMvc.perform(post(VOLUNTEERS).contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(volunteerJson("Олена Коваль", uniqueEmail(), "+380671112233")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        assertThat(countVolunteers()).isEqualTo(before);
    }

    @Test
    void getById_ExistingVolunteer_ShouldReturnFieldsOfResponseDto() throws Exception {
        UUID id = UUID.randomUUID();
        VolunteerTestFixtures.insertVolunteer(jdbc, id);

        mockMvc.perform(get(VOLUNTEERS + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.fullName").value("Тестовий волонтер"))
                .andExpect(jsonPath("$.phone").value("+380501234567"))
                .andExpect(jsonPath("$.transportType").value("CAR"))
                .andExpect(jsonPath("$.responsibilityScore").value(100))
                .andExpect(jsonPath("$.restricted").value(false))
                .andExpect(jsonPath("$.preferredPoints").isEmpty());
    }

    @Test
    void getById_UnknownVolunteer_ShouldReturnNotFound() throws Exception {
        mockMvc.perform(get(VOLUNTEERS + "/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void getAll_WithExistingVolunteer_ShouldIncludeResponseDto() throws Exception {
        UUID id = UUID.randomUUID();
        VolunteerTestFixtures.insertVolunteer(jdbc, id);

        mockMvc.perform(get(VOLUNTEERS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '%s')].fullName".formatted(id))
                        .value(hasItem("Тестовий волонтер")));
    }

    @Test
    void update_ValidRequest_ShouldReturnOkAndUpdateDatabaseRow() throws Exception {
        UUID id = UUID.randomUUID();
        VolunteerTestFixtures.insertVolunteer(jdbc, id);
        String updatedEmail = uniqueEmail();

        mockMvc.perform(put(VOLUNTEERS + "/" + id).contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(volunteerJson("Марія Шевченко", updatedEmail, "+380679998877")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.fullName").value("Марія Шевченко"))
                .andExpect(jsonPath("$.email").value(updatedEmail))
                .andExpect(jsonPath("$.phone").value("+380679998877"))
                .andExpect(jsonPath("$.transportType").value("BIKE"))
                .andExpect(jsonPath("$.activityZone").value("Київ, Поділ"));

        assertThat(volunteerRow(id))
                .containsEntry("full_name", "Марія Шевченко")
                .containsEntry("email", updatedEmail)
                .containsEntry("phone", "+380679998877")
                .containsEntry("transport_type", "BIKE")
                .containsEntry("activity_zone", "Київ, Поділ")
                .containsEntry("completed_deliveries", 0)
                .containsEntry("late_pickups", 0)
                .containsEntry("no_shows", 0);
    }

    @Test
    void update_InvalidFields_ShouldReturnBadRequestAndKeepDatabaseRow() throws Exception {
        UUID id = UUID.randomUUID();
        VolunteerTestFixtures.insertVolunteer(jdbc, id);
        Map<String, Object> original = volunteerRow(id);

        mockMvc.perform(put(VOLUNTEERS + "/" + id).contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(volunteerJson("І", "not-an-email", "123")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors.fullName").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.phone").exists());

        assertThat(volunteerRow(id)).isEqualTo(original);
    }

    @Test
    void update_DuplicateEmail_ShouldReturnConflictAndKeepDatabaseRow() throws Exception {
        UUID id = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        VolunteerTestFixtures.insertVolunteer(jdbc, id);
        VolunteerTestFixtures.insertVolunteer(jdbc, otherId);
        Map<String, Object> original = volunteerRow(id);
        String otherEmail = (String) volunteerRow(otherId).get("email");

        mockMvc.perform(put(VOLUNTEERS + "/" + id).contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(volunteerJson("Марія Шевченко", otherEmail, "+380679998877")))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409));

        assertThat(volunteerRow(id)).isEqualTo(original);
        assertThat(jdbc.sql("select count(*) from volunteers where email = ?")
                .param(otherEmail).query(Long.class).single()).isEqualTo(1L);
    }

    @Test
    void update_UnknownVolunteer_ShouldReturnNotFoundAndKeepTable() throws Exception {
        long before = countVolunteers();

        mockMvc.perform(put(VOLUNTEERS + "/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(volunteerJson("Марія Шевченко", uniqueEmail(), "+380679998877")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404));

        assertThat(countVolunteers()).isEqualTo(before);
    }

    @Test
    void delete_ExistingVolunteer_ShouldReturnNoContentAndRemoveDatabaseRow() throws Exception {
        UUID id = UUID.randomUUID();
        VolunteerTestFixtures.insertVolunteer(jdbc, id);

        mockMvc.perform(delete(VOLUNTEERS + "/" + id))
                .andExpect(status().isNoContent());

        assertThat(jdbc.sql("select count(*) from volunteers where id = ?")
                .param(id).query(Long.class).single()).isZero();
    }

    @Test
    void delete_VolunteerWithReservedLot_ShouldReturnConflictAndKeepDatabaseRows() throws Exception {
        UUID id = UUID.randomUUID();
        VolunteerTestFixtures.insertVolunteer(jdbc, id);
        UUID lotId = VolunteerTestFixtures.insertLot(jdbc, VolunteerTestFixtures.uniqueTitle(), "RESERVED", id);

        mockMvc.perform(delete(VOLUNTEERS + "/" + id))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409));

        assertThat(jdbc.sql("select count(*) from volunteers where id = ?")
                .param(id).query(Long.class).single()).isEqualTo(1L);
        assertThat(jdbc.sql("select status from food_lots where id = ?")
                .param(lotId).query(String.class).single()).isEqualTo("RESERVED");
    }

    @Test
    void delete_UnknownVolunteer_ShouldReturnNotFoundAndKeepTable() throws Exception {
        long before = countVolunteers();

        mockMvc.perform(delete(VOLUNTEERS + "/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404));

        assertThat(countVolunteers()).isEqualTo(before);
    }

    private Map<String, Object> volunteerRow(UUID id) {
        return jdbc.sql("""
                        select full_name, email, phone, transport_type, activity_zone,
                               completed_deliveries, late_pickups, no_shows
                        from volunteers where id = ?
                        """)
                .param(id).query().singleRow();
    }

    private long countVolunteers() {
        return jdbc.sql("select count(*) from volunteers").query(Long.class).single();
    }

    private String uniqueEmail() {
        return "volunteer-" + UUID.randomUUID() + "@example.test";
    }

    private String volunteerJson(String fullName, String email, String phone) {
        return """
                {"fullName":"%s","email":"%s","phone":"%s","transportType":"BIKE","activityZone":"Київ, Поділ"}
                """.formatted(fullName, email, phone);
    }
}
