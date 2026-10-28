package com.example.foodrescue.delivery;

import com.example.foodrescue.common.DemoData;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Test data is created through HTTP, with an explicit volunteer profile for the demo login. */
final class DeliveryTestFixtures {

    private static final String API = "/api/v1";

    private final MockMvc mvc;
    private final JdbcClient jdbc;
    private final UserDetailsService users;

    DeliveryTestFixtures(MockMvc mvc, JdbcClient jdbc, UserDetailsService users) {
        this.mvc = mvc;
        this.jdbc = jdbc;
        this.users = users;
    }

    static void ensureDemoVolunteer(JdbcClient jdbc) {
        if (jdbc.sql("select count(*) from volunteers where id = :id")
                .param("id", DemoData.DEMO_VOLUNTEER).query(Long.class).single() == 0L) {
            jdbc.sql("""
                    insert into volunteers
                    (id, version, full_name, email, phone, transport_type, activity_zone,
                     completed_deliveries, late_pickups, no_shows)
                    values (:id, 0, :name, :email, :phone, 'CAR', :zone, 0, 0, 0)
                    """)
                    .param("id", DemoData.DEMO_VOLUNTEER)
                    .param("name", "Тестовий волонтер")
                    .param("email", "delivery-" + UUID.randomUUID() + "@example.test")
                    .param("phone", "+380501234567")
                    .param("zone", "Київ")
                    .update();
        }
    }

    UUID createDraftLot() throws Exception {
        Instant now = Instant.now();
        String body = """
                {"donorOrgId":"%s","title":"Доставка %s","category":"BAKERY",
                 "items":[{"name":"Хліб","quantity":5,"unit":"KG"}],
                 "totalWeightKg":5,"storageCondition":"ROOM","pickupAddress":"Київ, Тестова 1",
                 "pickupFrom":"%s","pickupTo":"%s"}
                """.formatted(UUID.randomUUID(), UUID.randomUUID(),
                now.plus(2, ChronoUnit.HOURS), now.plus(4, ChronoUnit.HOURS));
        MvcResult result = mvc.perform(post(API + "/lots")
                        .with(user("donor").roles("DONOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(result.getResponse().getHeader("Location").replaceAll("^.*/", ""));
    }

    UUID createPublishedLot() throws Exception {
        UUID id = createDraftLot();
        mvc.perform(post(API + "/lots/" + id + "/publication")
                        .with(user("donor").roles("DONOR")))
                .andExpect(status().isOk());
        return id;
    }

    UUID reserveLot() throws Exception {
        ensureDemoVolunteer(jdbc);
        UUID id = createPublishedLot();
        mvc.perform(post(API + "/lots/" + id + "/reservation")
                        .with(user(users.loadUserByUsername("volunteer1"))))
                .andExpect(status().isCreated());
        return id;
    }

    String pointBody(String name, String category) {
        return """
                {"organizationId":"%s","name":"%s","address":"Київ, Доставка 2",
                 "workingHours":"09:00-18:00","acceptedCategories":["%s"]}
                """.formatted(UUID.randomUUID(), name, category);
    }

    UUID createPoint(String category) throws Exception {
        String name = "Пункт " + UUID.randomUUID();
        MvcResult result = mvc.perform(post(API + "/destination-points")
                        .with(user("coordinator").roles("COORDINATOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(pointBody(name, category)))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(result.getResponse().getHeader("Location").replaceAll("^.*/", ""));
    }

    MvcResult pickup(UUID id) throws Exception {
        return mvc.perform(post(API + "/lots/" + id + "/pickup")
                        .with(user(users.loadUserByUsername("volunteer1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actualWeightKg\":5}"))
                .andExpect(status().isOk()).andReturn();
    }

    MvcResult deliver(UUID id, UUID pointId) throws Exception {
        return mvc.perform(post(API + "/lots/" + id + "/delivery")
                        .with(user(users.loadUserByUsername("volunteer1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"destinationPointId\":\"" + pointId + "\"}"))
                .andExpect(status().isOk()).andReturn();
    }

    UUID createDeliveredLot() throws Exception {
        UUID id = reserveLot();
        pickup(id);
        deliver(id, createPoint("BAKERY"));
        return id;
    }

    String confirmationCode(UUID id) {
        return jdbc.sql("select confirmation_code from deliveries where lot_id = :id")
                .param("id", id).query(String.class).single();
    }

    MvcResult confirm(UUID id) throws Exception {
        return mvc.perform(post(API + "/lots/" + id + "/confirmation")
                        .with(user(users.loadUserByUsername("volunteer1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmationCode\":\"" + confirmationCode(id)
                                + "\",\"receivedWeightKg\":5}"))
                .andExpect(status().isOk()).andReturn();
    }
}
