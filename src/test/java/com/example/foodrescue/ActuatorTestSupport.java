package com.example.foodrescue;

import com.jayway.jsonpath.JsonPath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public abstract class ActuatorTestSupport {

    protected static final String HEALTH_PATH = "/actuator/health";
    protected static final String LOGGERS_PATH = "/actuator/loggers";
    protected static final String ADMIN_USER = "admin1";

    @Autowired
    protected MockMvc mvc;

    @Value("${foodrescue.demo.passwords.admin1}")
    protected String adminPassword;

    protected void setLevel(String logger, String level) throws Exception {
        mvc.perform(post(LOGGERS_PATH + "/" + logger).with(httpBasic(ADMIN_USER, adminPassword))
                        .contentType(MediaType.APPLICATION_JSON).content(levelBody(level)))
                .andExpect(status().isNoContent());
    }

    protected String effectiveLevel(String logger) throws Exception {
        String json = mvc.perform(get(LOGGERS_PATH + "/" + logger).with(httpBasic(ADMIN_USER, adminPassword)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.effectiveLevel");
    }

    protected String levelBody(String level) {
        return level == null ? "{\"configuredLevel\": null}" : "{\"configuredLevel\": \"" + level + "\"}";
    }
}
