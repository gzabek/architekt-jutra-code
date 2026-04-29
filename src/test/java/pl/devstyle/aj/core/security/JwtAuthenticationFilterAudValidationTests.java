package pl.devstyle.aj.core.security;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pl.devstyle.aj.TestcontainersConfiguration;
import pl.devstyle.aj.api.LoginRequest;

import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class JwtAuthenticationFilterAudValidationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Value("${app.oauth2.resource-uri}")
    private String resourceUri;

    // -------------------------------------------------------------------------
    // R1a: user token (no aud claim) is accepted — backward-compatible
    // -------------------------------------------------------------------------

    @Test
    void request_withNoAudClaim_isAuthenticated() throws Exception {
        var userToken = loginAndGetToken("viewer", "viewer123");

        mockMvc.perform(get("/api/products")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());
    }

    // -------------------------------------------------------------------------
    // R1b: OAuth2 token whose aud matches this server's resource URI is accepted
    // -------------------------------------------------------------------------

    @Test
    void request_withMatchingAud_isAuthenticated() throws Exception {
        var oauth2Token = jwtTokenProvider.generateOAuth2Token(
                "admin", Set.of("mcp:read"), resourceUri, resourceUri);

        mockMvc.perform(get("/api/products")
                        .header("Authorization", "Bearer " + oauth2Token))
                .andExpect(status().isOk());
    }

    // -------------------------------------------------------------------------
    // R1c: OAuth2 token whose aud does NOT match this server is rejected
    // -------------------------------------------------------------------------

    @Test
    void request_withMismatchedAud_returns401() throws Exception {
        var tokenForOtherServer = jwtTokenProvider.generateOAuth2Token(
                "admin", Set.of("mcp:read"), resourceUri, "https://other-service.example.com");

        mockMvc.perform(get("/api/products")
                        .header("Authorization", "Bearer " + tokenForOtherServer))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String loginAndGetToken(String username, String password) throws Exception {
        var request = new LoginRequest(username, password);
        var result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();
        var body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("token").asText();
    }
}
