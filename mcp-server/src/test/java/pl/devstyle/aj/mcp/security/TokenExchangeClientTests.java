package pl.devstyle.aj.mcp.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TokenExchangeClientTests {

    private static final String BASE_URL = "http://localhost:8080";
    private static final String CLIENT_ID = "mcp-client";
    private static final String CLIENT_SECRET = "mcp-secret";
    private static final String MCP_SERVER_URI = "http://localhost:8081";

    private MockRestServiceServer mockServer;
    private TokenExchangeClient tokenExchangeClient;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder().baseUrl(BASE_URL);
        mockServer = MockRestServiceServer.bindTo(builder).build();
        tokenExchangeClient = new TokenExchangeClient(builder.build(), CLIENT_ID, CLIENT_SECRET, MCP_SERVER_URI);
    }

    @Test
    void exchange_doesNotSendAudienceParameter_soTokenBHasNoAudAndBackendAcceptsIt() {
        mockServer.expect(requestTo(BASE_URL + "/oauth2/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("audience="))))
                .andRespond(withSuccess(
                        "{\"access_token\":\"token-B-value\",\"token_type\":\"Bearer\",\"expires_in\":900}",
                        MediaType.APPLICATION_JSON));

        var result = tokenExchangeClient.exchange("token-A");

        assertThat(result).isEqualTo("token-B-value");
        mockServer.verify();
    }
}
