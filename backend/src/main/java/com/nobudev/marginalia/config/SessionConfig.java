package com.nobudev.marginalia.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * Configures Spring Session JDBC for persistent, restart-surviving sessions.
 * Default session lifetime is 90 days.
 */
@Configuration
@EnableJdbcHttpSession(maxInactiveIntervalInSeconds = 7776000) // 90 days (90 * 24 * 3600)
public class SessionConfig {

    @Value("${app.auth.session.cookie-name:MARGINALIA_SESSION}")
    private String cookieName;

    @Value("${app.auth.session.secure-cookie:true}")
    private boolean secureCookie;

    @Bean
    public CookieSerializer cookieSerializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName(cookieName);
        serializer.setCookieMaxAge(7776000); // 90 days
        serializer.setCookiePath("/");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setSameSite("Lax");
        serializer.setUseSecureCookie(secureCookie);
        return serializer;
    }
}
