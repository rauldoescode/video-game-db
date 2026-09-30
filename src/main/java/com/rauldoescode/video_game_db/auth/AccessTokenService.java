package com.rauldoescode.video_game_db.auth;

import com.rauldoescode.video_game_db.user.User;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

@Service
public class AccessTokenService {

    private final JwtEncoder encoder;
    private final JwtProperties properties;
    private final Clock clock;

    /**
     * @param encoder signs the token with the shared HMAC secret
     * @param properties issuer and access-token lifetime
     * @param clock source of {@code iat} and {@code exp}, so tests can move time
     */
    public AccessTokenService(JwtEncoder encoder, JwtProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Signs a 15-minute access token for this user.
     * @param user account the token represents
     * @return the compact JWT ({@code header.payload.signature})
     */
    public String issue(User user) {
        Instant now = Instant.now(clock);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .issuedAt(now)
                .expiresAt(now.plus(properties.accessTtl()))
                .subject(user.getId().toString())
                .claim("username", user.getUsername())
                .claim("token_use", "access")
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
