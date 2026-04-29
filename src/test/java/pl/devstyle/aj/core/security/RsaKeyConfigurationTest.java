package pl.devstyle.aj.core.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import pl.devstyle.aj.TestcontainersConfiguration;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Transactional
class RsaKeyConfigurationTest {

    @Autowired
    private RSAPublicKey rsaPublicKey;

    @Autowired
    private RSAPrivateKey rsaPrivateKey;

    @Test
    void rsaPublicKey_isNotNull_andIsRsa2048() {
        assertThat(rsaPublicKey).isNotNull();
        assertThat(rsaPublicKey.getAlgorithm()).isEqualTo("RSA");
        assertThat(rsaPublicKey.getModulus().bitLength()).isEqualTo(2048);
    }

    @Test
    void rsaPrivateKey_isNotNull_andIsRsa() {
        assertThat(rsaPrivateKey).isNotNull();
        assertThat(rsaPrivateKey.getAlgorithm()).isEqualTo("RSA");
    }

    @Test
    void publicAndPrivateKey_formAMatchingKeyPair() throws Exception {
        // Verify the keys are a matching pair by round-tripping a signature
        var signer = java.security.Signature.getInstance("SHA256withRSA");
        signer.initSign(rsaPrivateKey);
        signer.update("test-data".getBytes());
        byte[] signature = signer.sign();

        var verifier = java.security.Signature.getInstance("SHA256withRSA");
        verifier.initVerify(rsaPublicKey);
        verifier.update("test-data".getBytes());
        assertThat(verifier.verify(signature)).isTrue();
    }
}
