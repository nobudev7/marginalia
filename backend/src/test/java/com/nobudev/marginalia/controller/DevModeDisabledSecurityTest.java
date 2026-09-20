package com.nobudev.marginalia.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that dev-login is completely inaccessible when dev-mode is disabled (default production posture).
 */
@SpringBootTest
@TestPropertySource(properties = {
        "app.auth.dev-mode=false",
        "app.auth.whitelist-emails="
})
class DevModeDisabledSecurityTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    void testDevLoginPostReturns404WhenDevModeDisabled() throws Exception {
        mockMvc.perform(post("/api/auth/dev-login").param("email", "test@example.com"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testDevLoginGetReturns405MethodNotAllowed() throws Exception {
        mockMvc.perform(get("/api/auth/dev-login").param("email", "test@example.com"))
                .andExpect(status().isMethodNotAllowed());
    }
}
