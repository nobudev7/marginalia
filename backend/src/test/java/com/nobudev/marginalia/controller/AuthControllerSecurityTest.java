package com.nobudev.marginalia.controller;

import com.nobudev.marginalia.entity.User;
import com.nobudev.marginalia.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class AuthControllerSecurityTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    private MockMvc mockMvc;
    private User testUser;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        testUser = userRepository.save(new User("auth-test@marginalia.local", "Auth Tester", "https://example.com/pic.jpg"));
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
    }

    @Test
    void testUnauthenticatedApiAccessReturns401() throws Exception {
        mockMvc.perform(get("/api/feeds"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testUnauthenticatedAuthMeReturns401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testUnauthenticatedAuthStatusReturnsFalse() throws Exception {
        mockMvc.perform(get("/api/auth/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated", is(false)));
    }

    @Test
    void testGetAuthProvidersPermitAll() throws Exception {
        mockMvc.perform(get("/api/auth/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.google", is("/oauth2/authorization/google")))
                .andExpect(jsonPath("$.github", is("/oauth2/authorization/github")));
    }

    @Test
    @WithMockUser(username = "auth-test@marginalia.local")
    void testAuthenticatedAuthMeReturnsUserProfile() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is("auth-test@marginalia.local")))
                .andExpect(jsonPath("$.displayName", is("Auth Tester")))
                .andExpect(jsonPath("$.avatarUrl", is("https://example.com/pic.jpg")));
    }

    @Test
    @WithMockUser(username = "auth-test@marginalia.local")
    void testAuthenticatedAuthStatusReturnsTrue() throws Exception {
        mockMvc.perform(get("/api/auth/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated", is(true)))
                .andExpect(jsonPath("$.user.email", is("auth-test@marginalia.local")));
    }

    @Test
    @WithMockUser(username = "auth-test@marginalia.local")
    void testLogoutReturnsNoContent() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isNoContent());
    }
}
