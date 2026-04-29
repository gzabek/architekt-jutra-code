package pl.devstyle.aj.core.oauth2;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.devstyle.aj.core.security.JwtTokenProvider;

import java.security.interfaces.RSAPublicKey;

/**
 * Publishes the authorization server's public signing keys as a JWK Set (RFC 7517).
 * Only the public key material is exposed — private key is never included.
 */
@RestController
public class JwksController {

    private final String jwksJson;

    public JwksController(RSAPublicKey rsaPublicKey) {
        var rsaJwk = new RSAKey.Builder(rsaPublicKey)
                .keyID(JwtTokenProvider.KID)
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(com.nimbusds.jose.JWSAlgorithm.RS256)
                .build();
        this.jwksJson = new JWKSet(rsaJwk).toPublicJWKSet().toString();
    }

    @GetMapping(value = "/oauth2/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public String getJwks() {
        return jwksJson;
    }
}
