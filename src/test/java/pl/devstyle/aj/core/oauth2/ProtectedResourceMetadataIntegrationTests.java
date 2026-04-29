package pl.devstyle.aj.core.oauth2;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pl.devstyle.aj.TestcontainersConfiguration;
import pl.devstyle.aj.api.LoginRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class ProtectedResourceMetadataIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void getPRM_returnsOkWithRequiredFields() throws Exception {
        mockMvc.perform(get("/.well-known/oauth-protected-resource"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resource").value(notNullValue()))
                .andExpect(jsonPath("$.authorization_servers").isArray())
                .andExpect(jsonPath("$.authorization_servers[0]").value(notNullValue()))
                .andExpect(jsonPath("$.jwks_uri").value(containsString("/oauth2/jwks.json")))
                .andExpect(jsonPath("$.scopes_supported").isArray());
    }

    @Test
    void getPRM_isPubliclyAccessibleWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/.well-known/oauth-protected-resource"))
                .andExpect(status().isOk());
    }

    @Test
    void authorizationFlow_withResourceParam_includesAudInAccessToken() throws Exception {
        var userToken = loginAndGetToken("admin", "admin123");
        var redirectUri = "http://localhost:3000/callback";
        var resourceUri = "https://mcp.example.com/server";

        var registrationJson = registerClient(redirectUri);
        var registration = objectMapper.readTree(registrationJson);
        var clientId = registration.get("client_id").asText();

        var codeVerifier = generateCodeVerifier();
        var codeChallenge = generateCodeChallenge(codeVerifier);

        // Authorize with resource param
        var authorizeResult = mockMvc.perform(post("/oauth2/authorize")
                        .header("Authorization", "Bearer " + userToken)
                        .param("client_id", clientId)
                        .param("redirect_uri", redirectUri)
                        .param("response_type", "code")
                        .param("scope", "mcp:read")
                        .param("resource", resourceUri)
                        .param("code_challenge", codeChallenge)
                        .param("code_challenge_method", "S256"))
                .andExpect(status().isFound())
                .andReturn();

        var code = extractQueryParam(authorizeResult.getResponse().getHeader("Location"), "code");

        // Exchange code for token
        var tokenResult = mockMvc.perform(post("/oauth2/token")
                        .param("grant_type", "authorization_code")
                        .param("code", code)
                        .param("redirect_uri", redirectUri)
                        .param("client_id", clientId)
                        .param("code_verifier", codeVerifier))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").value(notNullValue()))
                .andReturn();

        // Verify the access token contains aud = resourceUri
        var tokenJson = objectMapper.readTree(tokenResult.getResponse().getContentAsString());
        var accessToken = tokenJson.get("access_token").asText();
        var parts = accessToken.split("\\.");
        var payload = new String(Base64.getUrlDecoder().decode(parts[1]));
        var payloadJson = objectMapper.readTree(payload);
        var aud = payloadJson.get("aud");
        org.assertj.core.api.Assertions.assertThat(aud).isNotNull();
        org.assertj.core.api.Assertions.assertThat(aud.toString()).contains(resourceUri);
    }

    private String loginAndGetToken(String username, String password) throws Exception {
        var request = new LoginRequest(username, password);
        var result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();
        var body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("token").asText();
    }

    private String registerClient(String redirectUri) throws Exception {
        var body = """
                {
                    "client_name": "Test PRM Client",
                    "grant_types": ["authorization_code", "refresh_token"],
                    "redirect_uris": ["%s"],
                    "response_types": ["code"],
                    "token_endpoint_auth_method": "none",
                    "scope": "mcp:read mcp:edit"
                }
                """.formatted(redirectUri);
        var result = mockMvc.perform(post("/oauth2/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return result.getResponse().getContentAsString();
    }

    private String generateCodeVerifier() {
        var bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String generateCodeChallenge(String codeVerifier) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256");
        var hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }

    private String extractQueryParam(String url, String paramName) {
        var queryStart = url.indexOf('?');
        if (queryStart == -1) return null;
        var query = url.substring(queryStart + 1);
        for (var param : query.split("&")) {
            var parts = param.split("=", 2);
            if (parts[0].equals(paramName)) {
                return parts.length > 1 ? parts[1] : "";
            }
        }
        return null;
    }
}
