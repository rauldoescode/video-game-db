package com.rauldoescode.video_game_db.auth;

import com.rauldoescode.video_game_db.dto.request.LoginRequest;
import com.rauldoescode.video_game_db.dto.request.RegisterRequest;
import com.rauldoescode.video_game_db.dto.response.AuthResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;
    private final RefreshTokenCookies cookies;

    public AuthController(AuthService auth, RefreshTokenCookies cookies) {
        this.auth = auth;
        this.cookies = cookies;
    }

    /**
     * Creates an account and signs it in.
     *
     * @param request  username, email, and password
     * @param response receives the refresh cookies
     * @return the profile and access token
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletResponse response) {
        return ResponseEntity.status(HttpStatus.CREATED).body(body(auth.register(request), response));
    }

    /**
     * Checks the password and signs the account in.
     *
     * @param request  email and password
     * @param response receives the refresh cookies
     * @return the profile and access token
     */
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        return body(auth.login(request), response);
    }

    /**
     * Trades the refresh cookie for a new access token and a rotated cookie.
     *
     * @param rawToken the refresh cookie, or null if the browser sent none
     * @param response receives the rotated refresh cookies
     * @return the profile and a new access token
     */
    @PostMapping("/refresh")
    public AuthResponse refresh(
            @CookieValue(name = RefreshTokenCookies.NAME, required = false) String rawToken,
            HttpServletResponse response) {
        return body(auth.refresh(rawToken), response);
    }

    /**
     * Revokes the refresh cookie's family and expires both cookies. Always 204.
     *
     * @param rawToken the refresh cookie, or null if the browser sent none
     * @param response receives the expired cookies
     * @return no content
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = RefreshTokenCookies.NAME, required = false) String rawToken,
            HttpServletResponse response) {
        auth.logout(rawToken);
        RefreshTokenCookies.clear(response);
        return ResponseEntity.noContent().build();
    }

    private AuthResponse body(AuthResult result, HttpServletResponse response) {
        cookies.write(response, result.refreshToken());
        return new AuthResponse(result.user(), result.accessToken());
    }
}
