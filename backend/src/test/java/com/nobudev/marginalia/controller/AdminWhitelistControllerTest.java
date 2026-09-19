package com.nobudev.marginalia.controller;

import com.nobudev.marginalia.entity.User;
import com.nobudev.marginalia.entity.WhitelistEntry;
import com.nobudev.marginalia.repository.UserRepository;
import com.nobudev.marginalia.repository.WhitelistRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class AdminWhitelistControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private WhitelistRepository whitelistRepository;

    @Autowired
    private UserRepository userRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        userRepository.save(new User("test@example.com", "Admin User", null));
        userRepository.save(new User("regular@example.com", "Regular User", null));
    }

    @AfterEach
    void tearDown() {
        whitelistRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void testGetWhitelistAsAdmin() throws Exception {
        whitelistRepository.save(new WhitelistEntry("guest@example.com", "Guest", "test@example.com"));

        mockMvc.perform(get("/api/admin/whitelist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(org.hamcrest.Matchers.greaterThanOrEqualTo(1))));
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void testAddEmailToWhitelistAsAdmin() throws Exception {
        String json = """
            {
                "email": "newmember@example.com",
                "note": "New Team Member"
            }
        """;

        mockMvc.perform(post("/api/admin/whitelist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email", is("newmember@example.com")))
                .andExpect(jsonPath("$.note", is("New Team Member")))
                .andExpect(jsonPath("$.addedBy", is("test@example.com")));
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void testRemoveEmailFromWhitelistAsAdmin() throws Exception {
        WhitelistEntry entry = whitelistRepository.save(new WhitelistEntry("temp@example.com", "Temp", "test@example.com"));

        mockMvc.perform(delete("/api/admin/whitelist/" + entry.getId()))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/admin/whitelist/" + entry.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void testSelfRemovalIsRejected() throws Exception {
        WhitelistEntry entry = whitelistRepository.save(new WhitelistEntry("test@example.com", "Self", "test@example.com"));

        mockMvc.perform(delete("/api/admin/whitelist/" + entry.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Cannot remove your own email from the whitelist")));
    }

    @Test
    @WithMockUser(username = "regular@example.com")
    void testNonAdminAccessIsForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/whitelist"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testUnauthenticatedAccessIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/whitelist"))
                .andExpect(status().isUnauthorized());
    }
}
