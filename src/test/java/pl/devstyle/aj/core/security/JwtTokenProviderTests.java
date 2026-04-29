package pl.devstyle.aj.core.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTests {

    private RSAPublicKey rsaPublicKey;
    private RSAPrivateKey rsaPrivateKey;
    private JwtTokenProvider provider;

    private static final long EXPIRATION_MS = 86400000L;

    @BeforeEach
    void setUp() throws Exception {
        var keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();
        rsaPublicKey = (RSAPublicKey) keyPair.getPublic();
        rsaPrivateKey = (RSAPrivateKey) keyPair.getPrivate();
        provider = new JwtTokenProvider(rsaPrivateKey, rsaPublicKey, EXPIRATION_MS);
    }

    @Test
    void generateToken_producesRs256SignedJwt() throws Exception {
        var token = provider.generateToken("alice", Set.of("READ", "EDIT"));

        var signed = SignedJWT.parse(token);
        assertThat(signed.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
        assertThat(signed.getJWTClaimsSet().getSubject()).isEqualTo("alice");
    }

    @Test
    void generateToken_includesKidHeader() throws Exception {
        var token = provider.generateToken("alice", Set.of("READ"));

        var signed = SignedJWT.parse(token);
        JWSHeader header = signed.getHeader();
        assertThat(header.getKeyID()).isEqualTo("aj-rsa-key-1");
    }

    @Test
    void generateToken_includesPermissionsClaim() throws Exception {
        var token = provider.generateToken("alice", Set.of("READ", "EDIT"));

        var signed = SignedJWT.parse(token);
        var claims = signed.getJWTClaimsSet();
        @SuppressWarnings("unchecked")
        List<String> permissions = (List<String>) claims.getClaim("permissions");
        assertThat(permissions).containsExactlyInAnyOrder("READ", "EDIT");
    }

    @Test
    void generateOAuth2Token_withAudience_producesJwtContainingAudClaim() throws Exception {
        var token = provider.generateOAuth2Token("alice", Set.of("mcp:read"), "https://issuer.example.com", "https://resource.example.com");

        var signed = SignedJWT.parse(token);
        var claims = signed.getJWTClaimsSet();
        assertThat(claims.getAudience()).contains("https://resource.example.com");
        assertThat(claims.getSubject()).isEqualTo("alice");
        assertThat(claims.getIssuer()).isEqualTo("https://issuer.example.com");
    }

    @Test
    void generateOAuth2Token_withNullAudience_producesJwtWithoutAudClaim() throws Exception {
        var token = provider.generateOAuth2Token("bob", Set.of("mcp:read"), "https://issuer.example.com", null);

        var signed = SignedJWT.parse(token);
        var claims = signed.getJWTClaimsSet();
        assertThat(claims.getAudience()).isEmpty();
        assertThat(claims.getSubject()).isEqualTo("bob");
    }

    @Test
    void generateOAuth2Token_isSignedWithRs256AndKid() throws Exception {
        var token = provider.generateOAuth2Token("charlie", Set.of("mcp:read"), "https://issuer.example.com", null);

        var signed = SignedJWT.parse(token);
        assertThat(signed.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
        assertThat(signed.getHeader().getKeyID()).isEqualTo("aj-rsa-key-1");
    }

    @Test
    void parseToken_validUserToken_returnsPermissions() {
        var token = provider.generateToken("alice", Set.of("READ"));

        var result = provider.parseToken(token);

        assertThat(result).isPresent();
        assertThat(result.get().username()).isEqualTo("alice");
        assertThat(result.get().permissions()).contains("READ");
    }

    @Test
    void parseRawClaims_validOAuth2Token_returnsAllClaims() {
        var token = provider.generateOAuth2Token("charlie", Set.of("mcp:read", "mcp:edit"), "https://issuer.example.com", "https://resource.example.com");

        var result = provider.parseRawClaims(token);

        assertThat(result).isPresent();
        Claims claims = result.get();
        assertThat(claims.getSubject()).isEqualTo("charlie");
        assertThat(claims.getIssuer()).isEqualTo("https://issuer.example.com");
        assertThat(claims.getIssuedAt()).isNotNull();
        assertThat(claims.getExpiration()).isNotNull();
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
        assertThat(claims.getAudience()).contains("https://resource.example.com");
        assertThat(claims.get("scopes", java.util.List.class)).containsExactlyInAnyOrder("mcp:read", "mcp:edit");
    }

    @Test
    void parseRawClaims_invalidToken_returnsEmpty() {
        var result = provider.parseRawClaims("not-a-valid-jwt-token");
        assertThat(result).isEmpty();
    }

    @Test
    void parseToken_invalidToken_returnsEmpty() {
        var result = provider.parseToken("not-a-valid-jwt");
        assertThat(result).isEmpty();
    }

    @Test
    void validateToken_validToken_returnsTrue() {
        var token = provider.generateToken("alice", Set.of("READ"));
        assertThat(provider.validateToken(token)).isTrue();
    }

    @Test
    void validateToken_invalidToken_returnsFalse() {
        assertThat(provider.validateToken("garbage")).isFalse();
    }

    @Test
    void rsaPublicKey_isExposedForJwksUsage() {
        assertThat(provider.getRsaPublicKey()).isNotNull();
        assertThat(provider.getRsaPublicKey()).isEqualTo(rsaPublicKey);
    }
}
