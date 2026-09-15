package com.example.foodrescue.volunteer;

import com.example.foodrescue.common.error.ConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(VolunteerController.class)
class VolunteerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private VolunteerService volunteerService;

    private VolunteerRequest validRequest() {
        return new VolunteerRequest(
                "Іван Петренко",
                "ivan@example.com",
                "+380501234567",
                TransportType.CAR,
                "Київ, центр");
    }

    // 1. Валідний запит → правильний статус і виклик сервісу
    @Test
    void createVolunteer_validRequest_returnsCreated_andCallsService() throws Exception {
        VolunteerProfile profile = new VolunteerProfile();
        profile.setId(UUID.randomUUID());
        given(volunteerService.create(any())).willReturn(profile);

        mockMvc.perform(post("/api/v1/volunteers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"));

        verify(volunteerService).create(any());
    }

    // 2. Невалідне тіло → 400, у відповіді errors
    @Test
    void createVolunteer_invalidBody_returnsBadRequestWithErrors() throws Exception {
        VolunteerRequest invalid = new VolunteerRequest(
                "І",                  // менше 2 символів
                "not-an-email",       // невірний формат
                "123",                // не +380XXXXXXXXX
                null,                 // обов'язкове
                "");                  // порожнє

        mockMvc.perform(post("/api/v1/volunteers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());
    }

    // 3. Сервіс кидає бізнес-виняток → правильний статус
    @Test
    void reserve_serviceThrowsConflict_returnsConflictStatus() throws Exception {
        UUID lotId = UUID.randomUUID();
        ReservationRequest req = new ReservationRequest(UUID.randomUUID());

        given(volunteerService.reserve(eq(lotId), any()))
                .willThrow(new ConflictException("Лот " + lotId + ": перехід RESERVED → RESERVED заборонено"));

        mockMvc.perform(post("/api/v1/lots/" + lotId + "/reservation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    // 4. Невідоме поле в JSON → 400
    @Test
    void createVolunteer_unknownJsonField_returnsBadRequest() throws Exception {
        String json = """
                {"unknownField": "value", "fullName": "Тест"}
                """;

        mockMvc.perform(post("/api/v1/volunteers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }
}
