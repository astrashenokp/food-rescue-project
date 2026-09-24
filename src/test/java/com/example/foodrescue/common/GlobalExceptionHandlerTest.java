package com.example.foodrescue.common;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void dataIntegrityViolation_returnsConflictProblemDetail() throws Exception {
        mockMvc.perform(get("/throw/integrity"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://api.foodrescue.local/errors/conflict"))
                .andExpect(jsonPath("$.title").value("Конфлікт стану"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("Запис пов'язаний з іншими даними"))
                .andExpect(jsonPath("$.instance").value("/throw/integrity"));
    }

    @Test
    void optimisticLockingFailure_returnsConflictProblemDetail() throws Exception {
        mockMvc.perform(get("/throw/lock"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://api.foodrescue.local/errors/conflict"))
                .andExpect(jsonPath("$.title").value("Конфлікт стану"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("Запис змінено паралельно, повторіть запит"))
                .andExpect(jsonPath("$.instance").value("/throw/lock"));
    }

    @RestController
    static class ThrowingController {

        @GetMapping("/throw/integrity")
        void integrity() {
            throw new DataIntegrityViolationException("foreign key violation");
        }

        @GetMapping("/throw/lock")
        void lock() {
            throw new ObjectOptimisticLockingFailureException(FoodLot.class, UUID.randomUUID());
        }
    }
}
