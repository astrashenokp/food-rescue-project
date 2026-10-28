package com.example.foodrescue.lot;

import com.example.foodrescue.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@WithMockUser(roles = "DONOR")
class LotCrudIntegrationTest {

    private static final String LOTS = "/api/v1/lots";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcClient jdbc;

    @Test
    void create_ValidRequest_ShouldReturnCreatedAndSaveRows() throws Exception {
        String title = uniqueTitle();
        UUID donorOrgId = UUID.randomUUID();

        String location = mockMvc.perform(post(LOTS).contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(lotJson(donorOrgId, title, 2, 4)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");

        assertThat(location).startsWith(LOTS + "/");
        UUID id = idFrom(location);
        assertThat(jdbc.sql("select title from food_lots where id = ?").param(id).query(String.class).single())
                .isEqualTo(title);
        assertThat(jdbc.sql("select status from food_lots where id = ?").param(id).query(String.class).single())
                .isEqualTo("DRAFT");
        assertThat(jdbc.sql("select donor_org_id from food_lots where id = ?").param(id).query(UUID.class).single())
                .isEqualTo(donorOrgId);
        assertThat(jdbc.sql("select category from food_lots where id = ?").param(id).query(String.class).single())
                .isEqualTo("BAKERY");
        assertThat(jdbc.sql("select total_weight_kg from food_lots where id = ?").param(id).query(BigDecimal.class).single())
                .isEqualByComparingTo("5");
        assertThat(jdbc.sql("select count(*) from food_items where lot_id = ?").param(id).query(Long.class).single())
                .isEqualTo(1L);
        assertThat(jdbc.sql("select name from food_items where lot_id = ?").param(id).query(String.class).single())
                .isEqualTo("Хліб");
    }

    @Test
    void create_TooShortTitleAndEmptyItems_ShouldReturnBadRequestAndKeepTable() throws Exception {
        long before = countLots();
        String body = lotJson(UUID.randomUUID(), "ab", 2, 4).replaceAll("\"items\":\\[.*?\\]", "\"items\":[]");

        mockMvc.perform(post(LOTS).contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors.title").exists())
                .andExpect(jsonPath("$.errors.items").exists());

        assertThat(countLots()).isEqualTo(before);
    }

    @Test
    void create_PickupInThePast_ShouldReturnBadRequestAndKeepTable() throws Exception {
        long before = countLots();

        mockMvc.perform(post(LOTS).contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(lotJson(UUID.randomUUID(), uniqueTitle(), -4, -2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.pickupFrom").exists());

        assertThat(countLots()).isEqualTo(before);
    }

    @Test
    void create_WindowShorterThanMinimum_ShouldReturnUnprocessableEntityAndKeepTable() throws Exception {
        long before = countLots();
        Instant from = Instant.now().plus(2, ChronoUnit.HOURS);
        String body = lotJson(UUID.randomUUID(), uniqueTitle(), from, from.plus(30, ChronoUnit.MINUTES));

        mockMvc.perform(post(LOTS).contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422));

        assertThat(countLots()).isEqualTo(before);
    }

    @Test
    void create_UnknownJsonField_ShouldReturnBadRequest() throws Exception {
        mockMvc.perform(post(LOTS).contentType(MediaType.APPLICATION_JSON).content("{\"unknownField\":\"value\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_MalformedJson_ShouldReturnBadRequestNotServerError() throws Exception {
        mockMvc.perform(post(LOTS).contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_AsVolunteer_ShouldReturnForbiddenAndKeepTable() throws Exception {
        long before = countLots();

        mockMvc.perform(post(LOTS).with(user("volunteer").roles("VOLUNTEER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(lotJson(UUID.randomUUID(), uniqueTitle(), 2, 4)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        assertThat(countLots()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"COORDINATOR", "ADMIN"})
    void create_AsOtherRoles_ShouldReturnForbiddenAndKeepTable(String role) throws Exception {
        long before = countLots();

        mockMvc.perform(post(LOTS).with(user("someone").roles(role))
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(lotJson(UUID.randomUUID(), uniqueTitle(), 2, 4)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        assertThat(countLots()).isEqualTo(before);
    }

    @Test
    @WithAnonymousUser
    void create_WithoutToken_ShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(post(LOTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(lotJson(UUID.randomUUID(), uniqueTitle(), 2, 4)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getById_ExistingLot_ShouldReturnFieldsOfResponseDto() throws Exception {
        String title = uniqueTitle();
        UUID id = createLot(title);

        mockMvc.perform(get(LOTS + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.title").value(title))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.category").value("BAKERY"))
                .andExpect(jsonPath("$.items[0].name").value("Хліб"))
                .andExpect(jsonPath("$.version").doesNotExist());
    }

    @Test
    void getById_UnknownLot_ShouldReturnNotFound() throws Exception {
        mockMvc.perform(get(LOTS + "/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void findAll_ShouldContainCreatedLot() throws Exception {
        UUID id = createLot(uniqueTitle());

        mockMvc.perform(get(LOTS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + id + "')]").exists());
    }

    @Test
    void update_DraftLot_ShouldReturnOkAndChangeRow() throws Exception {
        UUID id = createLot(uniqueTitle());
        String newTitle = uniqueTitle();

        mockMvc.perform(put(LOTS + "/" + id).contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(lotJson(UUID.randomUUID(), newTitle, 3, 5)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value(newTitle));

        assertThat(titleOf(id)).isEqualTo(newTitle);
    }

    @Test
    void update_InvalidBody_ShouldReturnBadRequestAndKeepRow() throws Exception {
        String title = uniqueTitle();
        UUID id = createLot(title);

        mockMvc.perform(put(LOTS + "/" + id).contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(lotJson(UUID.randomUUID(), "ab", 3, 5)))
                .andExpect(status().isBadRequest());

        assertThat(titleOf(id)).isEqualTo(title);
    }

    @Test
    void update_UnknownLot_ShouldReturnNotFound() throws Exception {
        mockMvc.perform(put(LOTS + "/" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(lotJson(UUID.randomUUID(), uniqueTitle(), 3, 5)))
                .andExpect(status().isNotFound());
    }

    @Test
    void update_PublishedLot_ShouldReturnConflictAndKeepRow() throws Exception {
        String title = uniqueTitle();
        UUID id = createLot(title);
        mockMvc.perform(post(LOTS + "/" + id + "/publication")).andExpect(status().isOk());

        mockMvc.perform(put(LOTS + "/" + id).contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(lotJson(UUID.randomUUID(), uniqueTitle(), 3, 5)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        assertThat(titleOf(id)).isEqualTo(title);
    }

    @Test
    void update_AsVolunteer_ShouldReturnForbiddenAndKeepRow() throws Exception {
        String title = uniqueTitle();
        UUID id = createLot(title);

        mockMvc.perform(put(LOTS + "/" + id).with(user("volunteer").roles("VOLUNTEER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(lotJson(UUID.randomUUID(), uniqueTitle(), 3, 5)))
                .andExpect(status().isForbidden());

        assertThat(titleOf(id)).isEqualTo(title);
    }

    @Test
    void publication_DraftLot_ShouldReturnOkAndWriteStatusAndHistory() throws Exception {
        UUID id = createLot(uniqueTitle());

        mockMvc.perform(post(LOTS + "/" + id + "/publication"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        assertThat(statusOf(id)).isEqualTo("PUBLISHED");
        assertThat(jdbc.sql("select published_at from food_lots where id = ?").param(id)
                .query((rs, n) -> rs.getTimestamp(1)).single()).isNotNull();
        assertThat(jdbc.sql("select from_status || '>' || to_status from lot_status_history where lot_id = ?")
                .param(id).query(String.class).single()).isEqualTo("DRAFT>PUBLISHED");
    }

    @Test
    void publication_UnknownLot_ShouldReturnNotFound() throws Exception {
        mockMvc.perform(post(LOTS + "/" + UUID.randomUUID() + "/publication"))
                .andExpect(status().isNotFound());
    }

    @Test
    void publication_AlreadyPublished_ShouldReturnConflictAndKeepStatus() throws Exception {
        UUID id = createLot(uniqueTitle());
        mockMvc.perform(post(LOTS + "/" + id + "/publication")).andExpect(status().isOk());

        mockMvc.perform(post(LOTS + "/" + id + "/publication"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        assertThat(statusOf(id)).isEqualTo("PUBLISHED");
    }

    @Test
    void publication_AsVolunteer_ShouldReturnForbiddenAndKeepStatus() throws Exception {
        UUID id = createLot(uniqueTitle());

        mockMvc.perform(post(LOTS + "/" + id + "/publication").with(user("volunteer").roles("VOLUNTEER")))
                .andExpect(status().isForbidden());

        assertThat(statusOf(id)).isEqualTo("DRAFT");
    }

    @Test
    void cancellation_PublishedLot_ShouldReturnOkAndWriteStatus() throws Exception {
        UUID id = createLot(uniqueTitle());
        mockMvc.perform(post(LOTS + "/" + id + "/publication")).andExpect(status().isOk());

        mockMvc.perform(post(LOTS + "/" + id + "/cancellation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(statusOf(id)).isEqualTo("CANCELLED");
    }

    @Test
    void cancellation_UnknownLot_ShouldReturnNotFound() throws Exception {
        mockMvc.perform(post(LOTS + "/" + UUID.randomUUID() + "/cancellation"))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancellation_ReservedLot_ShouldReturnConflictAndKeepStatus() throws Exception {
        UUID id = createLot(uniqueTitle());
        jdbc.sql("update food_lots set status = 'RESERVED' where id = ?").param(id).update();

        mockMvc.perform(post(LOTS + "/" + id + "/cancellation"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        assertThat(statusOf(id)).isEqualTo("RESERVED");
    }

    @Test
    void delete_DraftLot_ShouldReturnNoContentAndRemoveRows() throws Exception {
        UUID id = createLot(uniqueTitle());

        mockMvc.perform(delete(LOTS + "/" + id)).andExpect(status().isNoContent());

        assertThat(jdbc.sql("select count(*) from food_lots where id = ?").param(id).query(Long.class).single())
                .isZero();
        assertThat(jdbc.sql("select count(*) from food_items where lot_id = ?").param(id).query(Long.class).single())
                .isZero();
    }

    @Test
    void delete_UnknownLot_ShouldReturnNotFound() throws Exception {
        mockMvc.perform(delete(LOTS + "/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void delete_PublishedLot_ShouldReturnConflictAndKeepRow() throws Exception {
        UUID id = createLot(uniqueTitle());
        mockMvc.perform(post(LOTS + "/" + id + "/publication")).andExpect(status().isOk());

        mockMvc.perform(delete(LOTS + "/" + id)).andExpect(status().isConflict());

        assertThat(statusOf(id)).isEqualTo("PUBLISHED");
    }

    private UUID createLot(String title) throws Exception {
        ResultActions result = mockMvc.perform(post(LOTS).contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(lotJson(UUID.randomUUID(), title, 2, 4)))
                .andExpect(status().isCreated());
        return idFrom(result.andReturn().getResponse().getHeader("Location"));
    }

    private UUID idFrom(String location) {
        return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    }

    private String uniqueTitle() {
        return "Лот " + UUID.randomUUID();
    }

    private long countLots() {
        return jdbc.sql("select count(*) from food_lots").query(Long.class).single();
    }

    private String titleOf(UUID id) {
        return jdbc.sql("select title from food_lots where id = ?").param(id).query(String.class).single();
    }

    private String statusOf(UUID id) {
        return jdbc.sql("select status from food_lots where id = ?").param(id).query(String.class).single();
    }

    private String lotJson(UUID donorOrgId, String title, int fromHours, int toHours) {
        Instant now = Instant.now();
        return lotJson(donorOrgId, title, now.plus(fromHours, ChronoUnit.HOURS), now.plus(toHours, ChronoUnit.HOURS));
    }

    private String lotJson(UUID donorOrgId, String title, Instant from, Instant to) {
        return """
                {"donorOrgId":"%s","title":"%s","category":"BAKERY",
                "items":[{"name":"Хліб","quantity":5,"unit":"KG"}],
                "totalWeightKg":5,"storageCondition":"ROOM","pickupAddress":"Київ, вул. Хлібна, 1",
                "pickupFrom":"%s","pickupTo":"%s"}
                """.formatted(donorOrgId, title, from, to);
    }
}
