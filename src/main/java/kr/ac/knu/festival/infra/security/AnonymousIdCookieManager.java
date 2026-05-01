package kr.ac.knu.festival.infra.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;

@Component
public class AnonymousIdCookieManager {

    private static final String COOKIE_NAME = "ANON_ID";
    private static final Duration COOKIE_MAX_AGE = Duration.ofDays(14);

    public String getOrCreateHashedAnonymousId(HttpServletRequest request, HttpServletResponse response) {
        String anonymousId = findCookieValue(request);
        if (anonymousId == null || anonymousId.isBlank()) {
            anonymousId = UUID.randomUUID().toString();
            ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, anonymousId)
                    .httpOnly(true)
                    .secure(false)
                    .sameSite("Lax")
                    .path("/")
                    .maxAge(COOKIE_MAX_AGE)
                    .build();
            response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        }
        return hash(anonymousId);
    }

    public String getHashedAnonymousId(HttpServletRequest request) {
        String anonymousId = findCookieValue(request);
        if (anonymousId == null || anonymousId.isBlank()) {
            return null;
        }
        return hash(anonymousId);
    }

    private String findCookieValue(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash anonymous id", e);
        }
    }
}
