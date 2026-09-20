package com.nobudev.marginalia.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UrlSafetyValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "https://example.com/feed.xml",
            "http://example.org/rss",
            "https://feeds.arstechnica.com/arstechnica/index",
            "https://techcrunch.com/feed/"
    })
    void testValidPublicUrls(String url) {
        assertThatCode(() -> UrlSafetyValidator.validate(url))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "   ",
            "not-a-url"
    })
    void testInvalidOrEmptyUrls(String url) {
        assertThatThrownBy(() -> UrlSafetyValidator.validate(url))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testNullUrl() {
        assertThatThrownBy(() -> UrlSafetyValidator.validate(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "file:///etc/passwd",
            "ftp://example.com/feed.xml",
            "gopher://example.com/test",
            "ldap://example.com/test",
            "javascript:alert(1)"
    })
    void testDisallowedSchemes(String url) {
        assertThatThrownBy(() -> UrlSafetyValidator.validate(url))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only http and https are permitted");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost",
            "http://localhost:8080/feed",
            "http://sub.localhost/feed",
            "http://127.0.0.1/feed",
            "http://127.0.0.2:9000/feed",
            "http://0.0.0.0:8080/feed"
    })
    void testLoopbackAndLocalhostBlocked(String url) {
        assertThatThrownBy(() -> UrlSafetyValidator.validate(url))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://169.254.169.254/latest/meta-data/",
            "http://169.254.1.1/feed"
    })
    void testCloudMetadataAndLinkLocalBlocked(String url) {
        assertThatThrownBy(() -> UrlSafetyValidator.validate(url))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://10.0.0.1/feed",
            "http://10.255.255.255/rss",
            "http://172.16.0.1/feed",
            "http://172.31.255.255/feed",
            "http://192.168.0.1/feed",
            "http://192.168.1.100/feed",
            "http://100.64.0.1/feed"
    })
    void testPrivateIpv4RangesBlocked(String url) {
        assertThatThrownBy(() -> UrlSafetyValidator.validate(url))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://[::1]/feed",
            "http://[::]/feed",
            "http://[fe80::1]/feed",
            "http://[fc00::1]/feed",
            "http://[fd12:3456:789a:1::1]/feed",
            "http://[::ffff:127.0.0.1]/feed",
            "http://[::ffff:169.254.169.254]/feed",
            "http://[::ffff:10.0.0.1]/feed",
            "http://[::ffff:192.168.1.1]/feed"
    })
    void testUnsafeIpv6Blocked(String url) {
        assertThatThrownBy(() -> UrlSafetyValidator.validate(url))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
