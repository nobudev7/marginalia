package com.nobudev.marginalia.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SessionPersistenceTest {

    @Autowired
    private SessionRepository<? extends Session> sessionRepository;

    @Test
    @SuppressWarnings("unchecked")
    void testSessionIsPersistedInDatabase() {
        SessionRepository<Session> repo = (SessionRepository<Session>) sessionRepository;
        Session session = repo.createSession();
        session.setAttribute("auth_user", "tester@marginalia.local");
        repo.save(session);

        Session loaded = repo.findById(session.getId());
        assertThat(loaded).isNotNull();
        String authUser = loaded.getAttribute("auth_user");
        assertThat(authUser).isEqualTo("tester@marginalia.local");
        long maxInactive = loaded.getMaxInactiveInterval().getSeconds();
        assertThat(maxInactive).isEqualTo(7776000L); // 90 days

        repo.deleteById(session.getId());
        Session deleted = repo.findById(session.getId());
        assertThat(deleted).isNull();
    }
}
