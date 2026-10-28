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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
        Map<String, Object> row = jdbc.sql("""
                        select full_name, email, phone, transport_type, activity_zone,
                               completed_deliveries, late_pickups, no_shows
                        from volunteers where id = ?
                        """)
                .param(id).query().singleRow();
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
