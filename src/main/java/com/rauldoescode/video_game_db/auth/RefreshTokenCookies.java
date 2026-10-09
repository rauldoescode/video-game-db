package com.rauldoescode.video_game_db.auth;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Writes the refresh token as two cookies. A cookie path is a prefix, so one cookie cannot
 * be limited to both {@code /api/auth/refresh} and {@code /api/auth/logout}. The browser
 * stores them as two cookies with the same name and value.
 */
@Component
public class RefreshTokenCookies {

    public static final String NAME = "refresh_token";
    public static final String REFRESH_PATH = "/api/auth/refresh";
    public static final String LOGOUT_PATH = "/api/auth/logout";

    private final Clock clock;

    /**
     * @param clock source of the cookie Max-Age, so it matches {@code expiresAt}
     */
    public RefreshTokenCookies(Clock clock) {
        this.clock = clock;
    }

    /**
     * @param response where the two {@code Set-Cookie} headers are added
     * @param issued   the raw token and the instant it stops being accepted
     */
    public void write(HttpServletResponse response, IssuedRefreshToken issued) {
        Duration maxAge = Duration.between(Instant.now(clock), issued.expiresAt());
        if (maxAge.isNegative()) {
            maxAge = Duration.ZERO;
        }
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(issued.value(), REFRESH_PATH, maxAge).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(issued.value(), LOGOUT_PATH, maxAge).toString());
    }

    /**
     * Expires both cookies. The browser only drops a cookie whose name and path match,
     * so each path gets its own {@code Max-Age=0} header.
     *
     * @param response where the two {@code Set-Cookie} headers are added
     */
    public static void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("", REFRESH_PATH, Duration.ZERO).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("", LOGOUT_PATH, Duration.ZERO).toString());
    }

    private static ResponseCookie cookie(String value, String path, Duration maxAge) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path(path)
                .maxAge(maxAge)
                .build();
    }
}
