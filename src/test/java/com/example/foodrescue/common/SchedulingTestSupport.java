package com.example.foodrescue.common;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

public abstract class SchedulingTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    protected void resetLocks() {
        jdbcTemplate.update("update shedlock set lock_until = timestamp '2000-01-01 00:00:00'");
    }
}
