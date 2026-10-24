package com.example.foodrescue.lot;

import com.example.foodrescue.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@IntegrationTest
class LotFormIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcClient jdbc;

    @Test
    @WithMockUser(roles = "DONOR")
    void create_ValidForm_ShouldRedirectToListAndSaveRow() throws Exception {
        String title = uniqueTitle();

        mockMvc.perform(validForm(title).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/lots"));

        assertThat(jdbc.sql("select status from food_lots where title = ?").param(title).query(String.class).single())
                .isEqualTo("DRAFT");
        assertThat(jdbc.sql("select count(*) from food_items where lot_id in (select id from food_lots where title = ?)")
                .param(title).query(Long.class).single()).isEqualTo(1L);
    }

    @Test
    @WithMockUser(roles = "DONOR")
    void create_WithoutCsrfToken_ShouldReturnForbiddenAndKeepTable() throws Exception {
        String title = uniqueTitle();

        mockMvc.perform(validForm(title))
                .andExpect(status().isForbidden());

        assertThat(countByTitle(title)).isZero();
    }

    @Test
    @WithMockUser(roles = "DONOR")
    void create_InvalidForm_ShouldShowFormAgainAndKeepTable() throws Exception {
        long before = jdbc.sql("select count(*) from food_lots").query(Long.class).single();

        mockMvc.perform(post("/lots").with(csrf()).param("title", "").param("itemQuantity", "0"))
                .andExpect(status().isOk())
                .andExpect(view().name("lot/create-form"))
                .andExpect(model().attributeHasFieldErrors("form", "title", "category", "itemName", "itemUnit",
                        "totalWeightKg", "storageCondition", "pickupAddress", "pickupFrom", "pickupTo"));

        assertThat(jdbc.sql("select count(*) from food_lots").query(Long.class).single()).isEqualTo(before);
    }

    @Test
    @WithMockUser(roles = "DONOR")
    void create_PastPickupWindow_ShouldShowBusinessErrorAndKeepTable() throws Exception {
        String title = uniqueTitle();
        LocalDateTime past = LocalDateTime.now().minusDays(1);

        mockMvc.perform(form(title, past, past.plusHours(2)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("lot/create-form"))
                .andExpect(model().attributeHasErrors("form"));

        assertThat(countByTitle(title)).isZero();
    }

    @Test
    @WithMockUser(roles = "VOLUNTEER")
    void create_AsVolunteer_ShouldReturnForbiddenAndKeepTable() throws Exception {
        String title = uniqueTitle();

        mockMvc.perform(validForm(title).with(csrf()))
                .andExpect(status().isForbidden());

        assertThat(countByTitle(title)).isZero();
    }

    @Test
    @WithAnonymousUser
    void list_WithoutLogin_ShouldRedirectToLogin() throws Exception {
        mockMvc.perform(get("/lots"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    private MockHttpServletRequestBuilder validForm(String title) {
        LocalDateTime from = LocalDateTime.now().plusHours(2).truncatedTo(ChronoUnit.MINUTES);
        return form(title, from, from.plusHours(2));
    }

    private MockHttpServletRequestBuilder form(String title, LocalDateTime from, LocalDateTime to) {
        return post("/lots")
                .param("title", title)
                .param("category", "BAKERY")
                .param("itemName", "Хліб")
                .param("itemQuantity", "5")
                .param("itemUnit", "KG")
                .param("totalWeightKg", "5")
                .param("storageCondition", "ROOM")
                .param("pickupAddress", "Київ, вул. Хлібна, 1")
                .param("pickupFrom", from.truncatedTo(ChronoUnit.MINUTES).toString())
                .param("pickupTo", to.truncatedTo(ChronoUnit.MINUTES).toString());
    }

    private String uniqueTitle() {
        return "Лот " + UUID.randomUUID();
    }

    private long countByTitle(String title) {
        return jdbc.sql("select count(*) from food_lots where title = ?").param(title).query(Long.class).single();
    }
}
