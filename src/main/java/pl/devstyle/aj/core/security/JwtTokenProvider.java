package pl.devstyle.aj.core.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Issues and validates JWTs signed with RS256.
 *
 * Two token families are supported:
 *  - User tokens (claims: "permissions") — signed and verified via jjwt + RSA public key.
 *  - OAuth2 tokens (claims: "scopes", optional "aud") — same key pair, kid="aj-rsa-key-1".
 *
 * The RSA key pair is ephemeral (generated at startup by {@link RsaKeyConfiguration}).
 */
@Component
@Slf4j
public class JwtTokenProvider {

    public static final String KID = "aj-rsa-key-1";
    private static final long OAUTH2_TOKEN_EXPIRATION_MS = 900_000; // 15 minutes

    private final RSAPrivateKey rsaPrivateKey;
    private final RSAPublicKey rsaPublicKey;
    private final long expirationMs;

    public JwtTokenProvider(RSAPrivateKey rsaPrivateKey,
                            RSAPublicKey rsaPublicKey,
                            @Value("${app.jwt.expiration-ms}") long expirationMs) {
        this.rsaPrivateKey = rsaPrivateKey;
        this.rsaPublicKey = rsaPublicKey;
        this.expirationMs = expirationMs;
    }

    public RSAPublicKey getRsaPublicKey() {
        return rsaPublicKey;
    }

    // -------------------------------------------------------------------------
    // User token (contains "permissions" claim, used by the app UI/API)
    // -------------------------------------------------------------------------

    public String generateToken(String username, Set<String> permissions) {
        return generateTokenWithExpiration(username, permissions, expirationMs);
    }

    public String generateTokenWithExpiration(String username, Set<String> permissions, long expirationMs) {
        var now = new Date();
        var expiry = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
                .header().keyId(KID).and()
                .subject(username)
                .claim("permissions", List.copyOf(permissions))
                .issuedAt(now)
                .expiration(expiry)
                .signWith(rsaPrivateKey)
                .compact();
    }

    // -------------------------------------------------------------------------
    // OAuth2 access token (contains "scopes" claim, optional "aud")
    // -------------------------------------------------------------------------

    public String generateOAuth2Token(String username, Set<String> scopes, String issuer) {
        return generateOAuth2Token(username, scopes, issuer, null);
    }

    public String generateOAuth2Token(String username, Set<String> scopes, String issuer, String audience) {
        var now = new Date();
        var expiry = new Date(now.getTime() + OAUTH2_TOKEN_EXPIRATION_MS);
        var builder = Jwts.builder()
                .header().keyId(KID).and()
                .issuer(issuer)
                .subject(username)
                .claim("scopes", List.copyOf(scopes))
                .issuedAt(now)
                .expiration(expiry)
                .signWith(rsaPrivateKey);
        if (audience != null) {
            builder.audience().add(audience);
        }
        return builder.compact();
    }

    // -------------------------------------------------------------------------
    // Parsing & validation
    // -------------------------------------------------------------------------

    public Optional<Claims> parseRawClaims(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(rsaPublicKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(claims);
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public record ParsedToken(String username, Set<String> permissions, List<String> audience) {}

    /**
     * Parse and validate a JWT token.
     * Works for both user tokens ("permissions" claim) and OAuth2 tokens ("scopes" claim).
     * The {@code audience} list is empty when the token carries no {@code aud} claim.
     */
    @SuppressWarnings("unchecked")
    public Optional<ParsedToken> parseToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(rsaPublicKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String username = claims.getSubject();
            List<String> permissions = claims.get("permissions", List.class);
            if (permissions == null) {
                permissions = claims.get("scopes", List.class);
            }
            if (permissions == null) {
                permissions = List.of();
            }
            // Normalise aud claim: jjwt returns Set<String> for audience
            var rawAud = claims.getAudience();
            List<String> audience = (rawAud != null && !rawAud.isEmpty()) ? List.copyOf(rawAud) : List.of();
            return Optional.of(new ParsedToken(username, Set.copyOf(permissions), audience));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public String getUsernameFromToken(String token) {
        return Jwts.parser()
                .verifyWith(rsaPublicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    @SuppressWarnings("unchecked")
    public Set<String> getPermissionsFromToken(String token) {
        var claims = Jwts.parser()
                .verifyWith(rsaPublicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        List<String> permissions = claims.get("permissions", List.class);
        return Set.copyOf(permissions);
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(rsaPublicKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
