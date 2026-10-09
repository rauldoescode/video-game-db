package com.rauldoescode.video_game_db.auth;

import com.rauldoescode.video_game_db.dto.request.LoginRequest;
import com.rauldoescode.video_game_db.dto.request.RegisterRequest;
import com.rauldoescode.video_game_db.dto.response.UserResponse;
import com.rauldoescode.video_game_db.user.User;
import com.rauldoescode.video_game_db.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Register, login, refresh, and logout. Register and login start a refresh-token family;
 * refresh rotates within it; logout revokes it.
 */
@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenService accessTokens;
    private final RefreshTokenService refreshTokens;
    private final String dummyPasswordHash;

    /**
     * @param users           accounts
     * @param passwordEncoder BCrypt
     * @param accessTokens    15-minute bearer tokens
     * @param refreshTokens   opaque cookies
     */
    public AuthService(
            UserRepository users,
            PasswordEncoder passwordEncoder,
            AccessTokenService accessTokens,
            RefreshTokenService refreshTokens) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.accessTokens = accessTokens;
        this.refreshTokens = refreshTokens;
        // An unknown email still runs a hash compare, so it takes about as long as a real password check.
        this.dummyPasswordHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    /**
     * Stores a new account and signs it in.
     *
     * @param request username, email, and password
     * @return profile, access token, and the refresh token to put in the cookie
     * @throws AccountConflictException the username or email is already stored
     */
    @Transactional
    public AuthResult register(RegisterRequest request) {
        if (users.findByUsername(request.username()).isPresent()) {
            throw usernameTaken();
        }
        if (users.findByEmail(request.email()).isPresent()) {
            throw emailTaken();
        }
        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        return signIn(save(user));
    }

    /**
     * Checks the password and signs the account in. A miss and a wrong password throw the same exception.
     *
     * @param request email and password
     * @return profile, access token, and the refresh token to put in the cookie
     * @throws InvalidCredentialsException the email is unknown or the password does not match
     */
    @Transactional
    public AuthResult login(LoginRequest request) {
        User user = users.findByEmail(request.email()).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.password(), dummyPasswordHash);
            throw new InvalidCredentialsException();
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return signIn(user);
    }

    /**
     * Rotates the cookie and signs its account in again.
     * <p>
     * Not {@code @Transactional}: an outer transaction would roll back on
     * {@link InvalidRefreshTokenException} and undo the family revocation that
     * {@link RefreshTokenService#rotate} has to commit.
     *
     * @param rawToken the cookie value, or null if the request had none
     * @return profile, access token, and the replacement refresh token
     * @throws InvalidRefreshTokenException the cookie is missing, unknown, expired, or already revoked
     */
    public AuthResult refresh(String rawToken) {
        IssuedRefreshToken next = refreshTokens.rotate(rawToken);
        User user = users.findById(next.userId())
                .orElseThrow(() -> new InvalidRefreshTokenException(InvalidRefreshTokenException.Reason.UNKNOWN));
        return new AuthResult(profile(user), accessTokens.issue(user), next);
    }

    /**
     * Revokes the cookie's family. A missing or unknown cookie is already signed out, so it is not an error.
     *
     * @param rawToken the cookie value, or null if the request had none
     */
    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        try {
            refreshTokens.revokeFamily(rawToken);
        } catch (InvalidRefreshTokenException ignored) {
            // Nothing stored for this cookie.
        }
    }

    private User save(User user) {
        try {
            return users.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            // The insert lost a race. Postgres has already aborted this transaction, so the
            // constraint name on the exception is the field we can still name.
            throw conflict(ex);
        }
    }

    private AuthResult signIn(User user) {
        return new AuthResult(profile(user), accessTokens.issue(user), refreshTokens.issue(user));
    }

    private static AccountConflictException conflict(DataIntegrityViolationException ex) {
        String message = ex.getMostSpecificCause().getMessage();
        if (message != null && message.contains("username")) {
            return usernameTaken();
        }
        return emailTaken();
    }

    private static AccountConflictException usernameTaken() {
        return new AccountConflictException("username", "Username is already registered");
    }

    private static AccountConflictException emailTaken() {
        return new AccountConflictException("email", "Email is already registered");
    }

    private static UserResponse profile(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getBio(),
                user.getAvatarUrl(),
                user.isProfilePublic(),
                user.getCreatedAt());
    }
}
