package com.example.foodrescue.volunteer;

import com.example.foodrescue.common.ConflictException;
import com.example.foodrescue.volunteer.api.VolunteerController;
import com.example.foodrescue.volunteer.internal.exception.VolunteerHasActiveLotsException;
import com.example.foodrescue.volunteer.internal.exception.VolunteerNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VolunteerController.class)
@AutoConfigureMockMvc(addFilters = false)
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

    private VolunteerResponse demoResponse(UUID id) {
        return new VolunteerResponse(
                id, "Іван Петренко", "ivan@example.com", "+380501234567",
                TransportType.CAR, "Київ, центр", 100, false, 0, 0, 0, List.of());
    }

    @Test
    void createVolunteer_validRequest_returnsCreated_andCallsService() throws Exception {
        UUID id = UUID.randomUUID();
        given(volunteerService.create(any())).willReturn(demoResponse(id));

        mockMvc.perform(post("/api/v1/volunteers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"));

        verify(volunteerService).create(any());
    }

    @Test
    void createVolunteer_invalidBody_returnsBadRequestWithErrors() throws Exception {
        VolunteerRequest invalid = new VolunteerRequest(
                "І",
                "not-an-email",
                "123",
                null,
                "");

        mockMvc.perform(post("/api/v1/volunteers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());
    }

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

    @Test
    void getAllVolunteers_returnsOkWithList() throws Exception {
        UUID id = UUID.randomUUID();
        given(volunteerService.getAll()).willReturn(List.of(demoResponse(id)));

        mockMvc.perform(get("/api/v1/volunteers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fullName").value("Іван Петренко"))
                .andExpect(jsonPath("$[0].preferredPoints").isArray());

        verify(volunteerService).getAll();
    }

    @Test
    void getById_existingVolunteer_returnsOk() throws Exception {
        UUID id = UUID.randomUUID();
        given(volunteerService.getById(id)).willReturn(demoResponse(id));

        mockMvc.perform(get("/api/v1/volunteers/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Іван Петренко"))
                .andExpect(jsonPath("$.preferredPoints").isArray());

        verify(volunteerService).getById(id);
    }

    @Test
    void getById_unknownVolunteer_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        given(volunteerService.getById(id)).willThrow(new VolunteerNotFoundException(id));

        mockMvc.perform(get("/api/v1/volunteers/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateVolunteer_validRequest_returnsOk() throws Exception {
        UUID id = UUID.randomUUID();
        given(volunteerService.update(eq(id), any())).willReturn(demoResponse(id));

        mockMvc.perform(put("/api/v1/volunteers/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Іван Петренко"));

        verify(volunteerService).update(eq(id), any());
    }

    @Test
    void updateVolunteer_invalidBody_returnsBadRequest() throws Exception {
        UUID id = UUID.randomUUID();
        VolunteerRequest invalid = new VolunteerRequest("І", "bad", "123", null, "");

        mockMvc.perform(put("/api/v1/volunteers/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteVolunteer_noActiveLots_returnsNoContent() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/volunteers/" + id))
                .andExpect(status().isNoContent());

        verify(volunteerService).delete(id);
    }

    @Test
    void deleteVolunteer_hasActiveLots_returnsConflict() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new VolunteerHasActiveLotsException(id)).when(volunteerService).delete(id);

        mockMvc.perform(delete("/api/v1/volunteers/" + id))
                .andExpect(status().isConflict());
    }

    @Test
    void reserve_serviceThrowsConflict_returnsConflictStatus() throws Exception {
        UUID lotId = UUID.randomUUID();
        doThrow(new ConflictException("Лот " + lotId + ": перехід RESERVED → RESERVED заборонено"))
                .when(volunteerService).reserve(lotId);

        mockMvc.perform(post("/api/v1/lots/" + lotId + "/reservation"))
                .andExpect(status().isConflict());
    }

    @Test
    void addPreferredPoint_returnsOkWithResponse() throws Exception {
        UUID id = UUID.randomUUID();
        UUID pointId = UUID.randomUUID();
        given(volunteerService.addPreferredPoint(id, pointId))
                .willReturn(new PreferredPointResponse(pointId, "Центр допомоги"));

        mockMvc.perform(put("/api/v1/volunteers/" + id + "/preferred-points/" + pointId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Центр допомоги"));

        verify(volunteerService).addPreferredPoint(id, pointId);
    }

    @Test
    void getPreferredPoints_returnsOkWithList() throws Exception {
        UUID id = UUID.randomUUID();
        UUID pointId = UUID.randomUUID();
        given(volunteerService.getPreferredPoints(id))
                .willReturn(List.of(new PreferredPointResponse(pointId, "Центр допомоги")));

        mockMvc.perform(get("/api/v1/volunteers/" + id + "/preferred-points"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Центр допомоги"));
    }

    @Test
    void removePreferredPoint_returnsNoContent() throws Exception {
        UUID id = UUID.randomUUID();
        UUID pointId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/volunteers/" + id + "/preferred-points/" + pointId))
                .andExpect(status().isNoContent());

        verify(volunteerService).removePreferredPoint(id, pointId);
    }
}
