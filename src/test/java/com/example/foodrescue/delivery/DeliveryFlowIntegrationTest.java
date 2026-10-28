package com.example.foodrescue.delivery;

import com.example.foodrescue.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class DeliveryFlowIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired UserDetailsService users;

    private DeliveryTestFixtures fixtures() {
        return new DeliveryTestFixtures(mvc, jdbc, users);
    }

    @Test
    void pickupDeliverConfirm_ShouldPersistStateAndHistory() throws Exception {
        UUID id = fixtures().reserveLot();
        fixtures().pickup(id);
        assertThat(statusOf(id)).isEqualTo("PICKED_UP");
        assertThat(jdbc.sql("select pickup_weight_kg from deliveries where lot_id = :id")
                .param("id", id).query(BigDecimal.class).single()).isEqualByComparingTo("5");

        UUID point = fixtures().createPoint("BAKERY");
        fixtures().deliver(id, point);
        assertThat(statusOf(id)).isEqualTo("DELIVERED");
        assertThat(jdbc.sql("select destination_point_id from deliveries where lot_id = :id")
                .param("id", id).query(UUID.class).single()).isEqualTo(point);

        fixtures().confirm(id);
        assertThat(statusOf(id)).isEqualTo("CONFIRMED");
        assertThat(jdbc.sql("select received_weight_kg from deliveries where lot_id = :id")
                .param("id", id).query(BigDecimal.class).single()).isEqualByComparingTo("5");
        assertThat(jdbc.sql("select confirmed_at from deliveries where lot_id = :id")
                .param("id", id).query(Object.class).single()).isNotNull();

        mvc.perform(get("/api/v1/lots/" + id + "/history").with(user("volunteer").roles("VOLUNTEER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].toStatus").value("PUBLISHED"))
                .andExpect(jsonPath("$[3].toStatus").value("DELIVERED"))
                .andExpect(jsonPath("$[4].toStatus").value("CONFIRMED"));
        assertThat(jdbc.sql("select count(*) from lot_status_history where lot_id = :id")
                .param("id", id).query(Long.class).single()).isEqualTo(5L);
    }

    @Test
    void pickup_InvalidWeight_ShouldReturnBadRequestWithoutDelivery() throws Exception {
        UUID id = fixtures().reserveLot();
        mvc.perform(post("/api/v1/lots/" + id + "/pickup")
                        .with(user(users.loadUserByUsername("volunteer1")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"actualWeightKg\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.actualWeightKg").exists());
        assertThat(countDeliveries(id)).isZero();
        assertThat(statusOf(id)).isEqualTo("RESERVED");
    }

    @Test
    void pickup_MissingLot_ShouldReturnNotFound() throws Exception {
        DeliveryTestFixtures.ensureDemoVolunteer(jdbc);
        UUID id = UUID.randomUUID();
        mvc.perform(post("/api/v1/lots/" + id + "/pickup")
                        .with(user(users.loadUserByUsername("volunteer1")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"actualWeightKg\":5}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
        assertThat(countDeliveries(id)).isZero();
    }

    @Test
    void pickup_Repeated_ShouldReturnConflictAndPreserveDelivery() throws Exception {
        UUID id = fixtures().reserveLot();
        fixtures().pickup(id);
        mvc.perform(post("/api/v1/lots/" + id + "/pickup")
                        .with(user(users.loadUserByUsername("volunteer1")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"actualWeightKg\":5}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));
        assertThat(countDeliveries(id)).isEqualTo(1L);
        assertThat(statusOf(id)).isEqualTo("PICKED_UP");
    }

    @Test
    void deliver_WithoutPickup_ShouldReturnNotFound() throws Exception {
        UUID id = fixtures().reserveLot();
        UUID point = fixtures().createPoint("BAKERY");
        mvc.perform(post("/api/v1/lots/" + id + "/delivery")
                        .with(user(users.loadUserByUsername("volunteer1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"destinationPointId\":\"" + point + "\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
        assertThat(statusOf(id)).isEqualTo("RESERVED");
    }

    @Test
    void deliver_InvalidJson_ShouldReturnBadRequestAndNotChangeStatus() throws Exception {
        UUID id = fixtures().reserveLot();
        fixtures().pickup(id);
        mvc.perform(post("/api/v1/lots/" + id + "/delivery")
                        .with(user(users.loadUserByUsername("volunteer1")))
                        .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
        assertThat(statusOf(id)).isEqualTo("PICKED_UP");
    }

    @Test
    void deliver_UnsupportedCategory_ShouldReturn422WithoutChangingDelivery() throws Exception {
        UUID id = fixtures().reserveLot();
        fixtures().pickup(id);
        UUID point = fixtures().createPoint("VEGETABLES");
        mvc.perform(post("/api/v1/lots/" + id + "/delivery")
                        .with(user(users.loadUserByUsername("volunteer1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"destinationPointId\":\"" + point + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422));
        assertThat(statusOf(id)).isEqualTo("PICKED_UP");
        assertThat(jdbc.sql("select destination_point_id from deliveries where lot_id = :id")
                .param("id", id).query(UUID.class).optional()).isEmpty();
    }

    @Test
    void deliver_UnknownPoint_ShouldReturn404AndKeepPickedUp() throws Exception {
        UUID id = fixtures().reserveLot();
        fixtures().pickup(id);
        mvc.perform(post("/api/v1/lots/" + id + "/delivery")
                        .with(user(users.loadUserByUsername("volunteer1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"destinationPointId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
        assertThat(statusOf(id)).isEqualTo("PICKED_UP");
    }

    @Test
    void deliver_Repeated_ShouldReturnConflictWithoutRewritingDestination() throws Exception {
        UUID id = fixtures().createDeliveredLot();
        UUID currentPoint = jdbc.sql("select destination_point_id from deliveries where lot_id = :id")
                .param("id", id).query(UUID.class).single();
        UUID otherPoint = fixtures().createPoint("BAKERY");
        mvc.perform(post("/api/v1/lots/" + id + "/delivery")
                        .with(user(users.loadUserByUsername("volunteer1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"destinationPointId\":\"" + otherPoint + "\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));
        assertThat(jdbc.sql("select destination_point_id from deliveries where lot_id = :id")
                .param("id", id).query(UUID.class).single()).isEqualTo(currentPoint);
    }

    @Test
    void confirm_WrongCode_ShouldReturn422AndPreserveStatus() throws Exception {
        UUID id = fixtures().createDeliveredLot();
        String realCode = fixtures().confirmationCode(id);
        String wrongCode = "000000".equals(realCode) ? "111111" : "000000";
        mvc.perform(post("/api/v1/lots/" + id + "/confirmation")
                        .with(user(users.loadUserByUsername("volunteer1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmationCode\":\"" + wrongCode + "\",\"receivedWeightKg\":5}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422));
        assertThat(statusOf(id)).isEqualTo("DELIVERED");
        assertThat(jdbc.sql("select failed_confirmation_attempts from deliveries where lot_id = :id")
                .param("id", id).query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    void confirm_WithoutDelivery_ShouldReturn404() throws Exception {
        UUID id = fixtures().reserveLot();
        mvc.perform(post("/api/v1/lots/" + id + "/confirmation")
                        .with(user(users.loadUserByUsername("volunteer1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmationCode\":\"123456\",\"receivedWeightKg\":5}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
        assertThat(statusOf(id)).isEqualTo("RESERVED");
    }

    @Test
    void confirm_InvalidWeight_ShouldReturn400WithoutChangingStatus() throws Exception {
        UUID id = fixtures().createDeliveredLot();
        mvc.perform(post("/api/v1/lots/" + id + "/confirmation")
                        .with(user(users.loadUserByUsername("volunteer1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmationCode\":\"" + fixtures().confirmationCode(id)
                                + "\",\"receivedWeightKg\":-1}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        assertThat(statusOf(id)).isEqualTo("DELIVERED");
    }

    @Test
    void confirm_WeightDispute_ShouldPersistDisputed() throws Exception {
        UUID id = fixtures().createDeliveredLot();
        mvc.perform(post("/api/v1/lots/" + id + "/confirmation")
                        .with(user(users.loadUserByUsername("volunteer1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmationCode\":\"" + fixtures().confirmationCode(id)
                                + "\",\"receivedWeightKg\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.lotStatus").value("DISPUTED"));
        assertThat(statusOf(id)).isEqualTo("DISPUTED");
    }

    @Test
    void confirm_Repeated_ShouldReturnConflict() throws Exception {
        UUID id = fixtures().createDeliveredLot();
        fixtures().confirm(id);
        mvc.perform(post("/api/v1/lots/" + id + "/confirmation")
                        .with(user(users.loadUserByUsername("volunteer1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmationCode\":\"" + fixtures().confirmationCode(id)
                                + "\",\"receivedWeightKg\":5}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));
        assertThat(statusOf(id)).isEqualTo("CONFIRMED");
    }

    @Test
    void pickup_AsDonor_ShouldReturn403WithoutDelivery() throws Exception {
        UUID id = fixtures().reserveLot();
        mvc.perform(post("/api/v1/lots/" + id + "/pickup")
                        .with(user("donor").roles("DONOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"actualWeightKg\":5}"))
                .andExpect(status().isForbidden());
        assertThat(countDeliveries(id)).isZero();
    }

    @Test
    void pickup_WithoutAuthentication_ShouldReturn401() throws Exception {
        UUID id = fixtures().reserveLot();
        mvc.perform(post("/api/v1/lots/" + id + "/pickup")
                        .with(anonymous())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"actualWeightKg\":5}"))
                .andExpect(status().isUnauthorized());
        assertThat(countDeliveries(id)).isZero();
    }

    @Test
    void getHistory_MissingLot_ShouldReturn404() throws Exception {
        mvc.perform(get("/api/v1/lots/" + UUID.randomUUID() + "/history")
                        .with(user("volunteer").roles("VOLUNTEER")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
    }

    private String statusOf(UUID id) {
        return jdbc.sql("select status from food_lots where id = :id")
                .param("id", id).query(String.class).single();
    }

    private long countDeliveries(UUID id) {
        return jdbc.sql("select count(*) from deliveries where lot_id = :id")
                .param("id", id).query(Long.class).single();
    }
}
