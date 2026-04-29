package pl.devstyle.aj.core.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

/**
 * Generates an ephemeral RSA-2048 key pair at startup.
 * Keys are held in memory only — they are NOT persisted.
 * Tokens issued before a restart will be invalid after restart,
 * which is acceptable for the current pre-alpha deployment model.
 */
@Configuration
public class RsaKeyConfiguration {

    @Bean
    public KeyPair rsaKeyPair() throws Exception {
        var keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        return keyGen.generateKeyPair();
    }

    @Bean
    public RSAPublicKey rsaPublicKey(KeyPair rsaKeyPair) {
        return (RSAPublicKey) rsaKeyPair.getPublic();
    }

    @Bean
    public RSAPrivateKey rsaPrivateKey(KeyPair rsaKeyPair) {
        return (RSAPrivateKey) rsaKeyPair.getPrivate();
    }
}
