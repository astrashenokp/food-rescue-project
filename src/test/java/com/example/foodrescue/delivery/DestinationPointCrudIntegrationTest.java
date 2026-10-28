package com.example.foodrescue.delivery;

import com.example.foodrescue.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

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
@WithMockUser(roles = "COORDINATOR")
class DestinationPointCrudIntegrationTest {

    private static final String POINTS = "/api/v1/destination-points";

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired UserDetailsService users;

    private DeliveryTestFixtures fixtures() {
        return new DeliveryTestFixtures(mvc, jdbc, users);
    }

    @Test
    void createAndRead_ShouldReturnDtoAndPersistColumns() throws Exception {
        String name = "Їдальня " + UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        String json = """
                {"organizationId":"%s","name":"%s","address":"Київ, Центр 15",
                 "workingHours":"08:00-20:00","acceptedCategories":["BAKERY","GROCERY"]}
                """.formatted(organizationId, name);

        String location = mvc.perform(post(POINTS).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location");
        assertThat(location).startsWith(POINTS + "/");
        UUID id = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));

        mvc.perform(get(location)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.organizationId").value(organizationId.toString()))
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.address").value("Київ, Центр 15"))
                .andExpect(jsonPath("$.workingHours").value("08:00-20:00"))
                .andExpect(jsonPath("$.acceptedCategories.length()").value(2));

        assertThat(jdbc.sql("select name from destination_points where id = :id")
                .param("id", id).query(String.class).single()).isEqualTo(name);
        assertThat(jdbc.sql("select organization_id from destination_points where id = :id")
                .param("id", id).query(UUID.class).single()).isEqualTo(organizationId);
        assertThat(jdbc.sql("select address from destination_points where id = :id")
                .param("id", id).query(String.class).single()).isEqualTo("Київ, Центр 15");
        assertThat(jdbc.sql("select count(*) from destination_point_categories where point_id = :id")
                .param("id", id).query(Long.class).single()).isEqualTo(2L);
    }

    @Test
    void updateAndDelete_ShouldChangePersistedPointThenRemoveIt() throws Exception {
        UUID id = fixtures().createPoint("BAKERY");
        String newName = "Пункт оновлений " + UUID.randomUUID();
        String body = fixtures().pointBody(newName, "VEGETABLES");

        mvc.perform(put(POINTS + "/" + id).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(newName))
                .andExpect(jsonPath("$.acceptedCategories[0]").value("VEGETABLES"));
        assertThat(jdbc.sql("select name from destination_points where id = :id")
                .param("id", id).query(String.class).single()).isEqualTo(newName);
        assertThat(jdbc.sql("select category from destination_point_categories where point_id = :id")
                .param("id", id).query(String.class).single()).isEqualTo("VEGETABLES");

        mvc.perform(delete(POINTS + "/" + id)).andExpect(status().isNoContent());
        assertThat(countPointsWithId(id)).isZero();
    }

    @Test
    void create_InvalidFields_ShouldReturnProblemDetailWithoutInsert() throws Exception {
        long before = countPoints();
        String invalid = fixtures().pointBody("x", "BAKERY").replace("09:00-18:00", "9-18");
        mvc.perform(post(POINTS).contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.workingHours").exists());
        assertThat(countPoints()).isEqualTo(before);
    }

    @Test
    void create_MalformedJson_ShouldReturnBadRequest() throws Exception {
        long before = countPoints();
        mvc.perform(post(POINTS).contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
        assertThat(countPoints()).isEqualTo(before);
    }

    @Test
    void create_DuplicateName_ShouldReturnConflictAndNotInsert() throws Exception {
        String name = "Пункт " + UUID.randomUUID();
        String body = fixtures().pointBody(name, "BAKERY");
        mvc.perform(post(POINTS).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        long before = countPoints();
        mvc.perform(post(POINTS).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").exists());
        assertThat(countPoints()).isEqualTo(before);
    }

    @Test
    void getAndUpdate_MissingId_ShouldReturnNotFound() throws Exception {
        UUID missing = UUID.randomUUID();
        mvc.perform(get(POINTS + "/" + missing)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
        mvc.perform(put(POINTS + "/" + missing).contentType(MediaType.APPLICATION_JSON)
                        .content(fixtures().pointBody("Невідомий пункт", "BAKERY")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
        assertThat(countPointsWithId(missing)).isZero();
    }

    @Test
    void update_InvalidBody_ShouldReturnBadRequestAndPreserveExistingRow() throws Exception {
        UUID id = fixtures().createPoint("BAKERY");
        String original = jdbc.sql("select name from destination_points where id = :id")
                .param("id", id).query(String.class).single();
        mvc.perform(put(POINTS + "/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(fixtures().pointBody("x", "BAKERY")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        assertThat(jdbc.sql("select name from destination_points where id = :id")
                .param("id", id).query(String.class).single()).isEqualTo(original);
    }

    @Test
    void delete_UnknownPoint_ShouldReturnNotFound() throws Exception {
        mvc.perform(delete(POINTS + "/" + UUID.randomUUID()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void update_DuplicateName_ShouldReturn409AndKeepOriginal() throws Exception {
        UUID first = fixtures().createPoint("BAKERY");
        UUID second = fixtures().createPoint("BAKERY");
        String existingName = jdbc.sql("select name from destination_points where id = :id")
                .param("id", first).query(String.class).single();
        String previousName = jdbc.sql("select name from destination_points where id = :id")
                .param("id", second).query(String.class).single();

        mvc.perform(put(POINTS + "/" + second).contentType(MediaType.APPLICATION_JSON)
                        .content(fixtures().pointBody(existingName, "BAKERY")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));
        assertThat(jdbc.sql("select name from destination_points where id = :id")
                .param("id", second).query(String.class).single()).isEqualTo(previousName);
    }

    @Test
    void delete_PointInUse_ShouldReturn409AndPreservePoint() throws Exception {
        UUID lotId = fixtures().createDeliveredLot();
        UUID pointId = jdbc.sql("select destination_point_id from deliveries where lot_id = :id")
                .param("id", lotId).query(UUID.class).single();

        mvc.perform(delete(POINTS + "/" + pointId))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));
        assertThat(countPointsWithId(pointId)).isEqualTo(1L);
    }

    @Test
    void create_AsDonor_ShouldReturnForbiddenWithoutInsert() throws Exception {
        long before = countPoints();
        mvc.perform(post(POINTS).with(user("donor").roles("DONOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixtures().pointBody("Пункт " + UUID.randomUUID(), "BAKERY")))
                .andExpect(status().isForbidden());
        assertThat(countPoints()).isEqualTo(before);
    }

    @Test
    void create_WithoutAuthentication_ShouldReturnUnauthorized() throws Exception {
        long before = countPoints();
        mvc.perform(post(POINTS).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixtures().pointBody("Пункт " + UUID.randomUUID(), "BAKERY")))
                .andExpect(status().isUnauthorized());
        assertThat(countPoints()).isEqualTo(before);
    }

    private long countPoints() {
        return jdbc.sql("select count(*) from destination_points").query(Long.class).single();
    }

    private long countPointsWithId(UUID id) {
        return jdbc.sql("select count(*) from destination_points where id = :id")
                .param("id", id).query(Long.class).single();
    }
}
