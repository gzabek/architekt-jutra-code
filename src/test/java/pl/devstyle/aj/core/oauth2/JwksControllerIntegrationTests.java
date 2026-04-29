package pl.devstyle.aj.core.oauth2;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pl.devstyle.aj.TestcontainersConfiguration;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class JwksControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getJwks_returnsOkWithPublicKeySet() throws Exception {
        mockMvc.perform(get("/oauth2/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys").isArray())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].use").value("sig"))
                .andExpect(jsonPath("$.keys[0].alg").value("RS256"))
                .andExpect(jsonPath("$.keys[0].kid").value("aj-rsa-key-1"))
                .andExpect(jsonPath("$.keys[0].n").value(notNullValue()))
                .andExpect(jsonPath("$.keys[0].e").value(notNullValue()));
    }

    @Test
    void getJwks_doesNotExposePrivateKeyMaterial() throws Exception {
        mockMvc.perform(get("/oauth2/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].d").doesNotExist())
                .andExpect(jsonPath("$.keys[0].p").doesNotExist())
                .andExpect(jsonPath("$.keys[0].q").doesNotExist());
    }

    @Test
    void getJwks_isPubliclyAccessibleWithoutAuthentication() throws Exception {
        // No Authorization header — should still return 200
        mockMvc.perform(get("/oauth2/jwks.json"))
                .andExpect(status().isOk());
    }

    @Test
    void getAuthorizationServerMetadata_includesJwksUri() throws Exception {
        mockMvc.perform(get("/.well-known/oauth-authorization-server"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jwks_uri").value(containsString("/oauth2/jwks.json")));
    }
}
