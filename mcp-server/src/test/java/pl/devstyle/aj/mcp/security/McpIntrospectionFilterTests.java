package pl.devstyle.aj.mcp.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class McpIntrospectionFilterTests {

    private static final String CLIENT_ID = "mcp-client";
    private static final String CLIENT_SECRET = "mcp-secret";
    private static final String OAUTH_SERVER_URL = "http://localhost:8080";
    private static final String RESOURCE_METADATA_URL = "http://localhost:8081/.well-known/oauth-protected-resource";
    private static final String MCP_SERVER_URI = "http://localhost:8081";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private RestClient.Builder oauthRestClientBuilder;
    private MockRestServiceServer mockServer;
    private McpIntrospectionFilter filter;
    private TokenExchangeClient tokenExchangeClient;

    @BeforeEach
    void setUp() {
        oauthRestClientBuilder = RestClient.builder().baseUrl(OAUTH_SERVER_URL);
        mockServer = MockRestServiceServer.bindTo(oauthRestClientBuilder).build();

        tokenExchangeClient = mock(TokenExchangeClient.class);

        var oauthRestClient = oauthRestClientBuilder.build();
        filter = new McpIntrospectionFilter(
                oauthRestClient,
                tokenExchangeClient,
                new McpAuthenticationEntryPoint(RESOURCE_METADATA_URL),
                CLIENT_ID,
                CLIENT_SECRET,
                MCP_SERVER_URI
        );

        SecurityContextHolder.clearContext();
        ExchangedTokenHolder.clear();
    }

    @AfterEach
    void tearDown() {
        ExchangedTokenHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilter_successfulIntrospectionAndExchange_populatesSecurityContextAndStoresTokenB() throws ServletException, IOException {
        var introspectionResponse = Map.of(
                "active", true,
                "sub", "john",
                "scope", "mcp:read mcp:edit"
        );
        mockServer.expect(requestTo(OAUTH_SERVER_URL + "/oauth2/introspect"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andRespond(withSuccess(objectMapper.writeValueAsString(introspectionResponse), MediaType.APPLICATION_JSON));

        when(tokenExchangeClient.exchange("token-A")).thenReturn("token-B");

        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token-A");
        var response = new MockHttpServletResponse();
        var filterChain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);

        // SecurityContext and ExchangedTokenHolder are cleared in finally block after filter chain
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(ExchangedTokenHolder.get()).isNull();

        // Token-B still stored as request attribute
        assertThat(request.getAttribute("exchanged_token")).isEqualTo("token-B");

        mockServer.verify();
    }

    @Test
    void doFilter_successfulFlow_populatesExchangedTokenHolderDuringFilterChainExecution() throws ServletException, IOException {
        var introspectionResponse = Map.of(
                "active", true,
                "sub", "john",
                "scope", "mcp:read mcp:edit"
        );
        mockServer.expect(requestTo(OAUTH_SERVER_URL + "/oauth2/introspect"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(objectMapper.writeValueAsString(introspectionResponse), MediaType.APPLICATION_JSON));

        when(tokenExchangeClient.exchange("my-token")).thenReturn("exchanged-token-B");

        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer my-token");
        var response = new MockHttpServletResponse();

        var filterChain = mock(FilterChain.class);
        doAnswer(invocation -> {
            // During filter chain execution Token-B must be in the ThreadLocal
            assertThat(ExchangedTokenHolder.get()).isEqualTo("exchanged-token-B");

            var auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isNotNull();
            assertThat(auth.getName()).isEqualTo("john");
            var authorityNames = auth.getAuthorities().stream()
                    .map(Object::toString).toList();
            assertThat(authorityNames).containsExactlyInAnyOrder(
                    "PERMISSION_mcp:read", "PERMISSION_mcp:edit"
            );

            // Token-B also in request attribute
            var tokenB = ((MockHttpServletRequest) invocation.getArgument(0)).getAttribute("exchanged_token");
            assertThat(tokenB).isEqualTo("exchanged-token-B");
            return null;
        }).when(filterChain).doFilter(any(), any());

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        // After filter completes, both are cleared
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(ExchangedTokenHolder.get()).isNull();

        mockServer.verify();
    }

    @Test
    void doFilter_introspectionReturnsInactive_returns401WithWwwAuthenticate() throws ServletException, IOException {
        var introspectionResponse = Map.of("active", false);
        mockServer.expect(requestTo(OAUTH_SERVER_URL + "/oauth2/introspect"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(objectMapper.writeValueAsString(introspectionResponse), MediaType.APPLICATION_JSON));

        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer invalid-token");
        var response = new MockHttpServletResponse();
        var filterChain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate")).contains("resource_metadata");
        verifyNoInteractions(filterChain);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(ExchangedTokenHolder.get()).isNull();

        mockServer.verify();
    }

    @Test
    void doFilter_missingBearerToken_returns401() throws ServletException, IOException {
        var request = new MockHttpServletRequest();
        // No Authorization header
        var response = new MockHttpServletResponse();
        var filterChain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate")).contains("resource_metadata");
        verifyNoInteractions(filterChain);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilter_introspectionSucceedsButExchangeFails_returns502AndClearsHolders() throws ServletException, IOException {
        var introspectionResponse = Map.of(
                "active", true,
                "sub", "john",
                "scope", "mcp:read"
        );
        mockServer.expect(requestTo(OAUTH_SERVER_URL + "/oauth2/introspect"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(objectMapper.writeValueAsString(introspectionResponse), MediaType.APPLICATION_JSON));

        when(tokenExchangeClient.exchange("token-A")).thenThrow(new RuntimeException("Exchange failed"));

        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token-A");
        var response = new MockHttpServletResponse();
        var filterChain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(502);
        verifyNoInteractions(filterChain);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(ExchangedTokenHolder.get()).isNull();

        mockServer.verify();
    }

    @Test
    void doFilter_tokenWithMatchingAud_allowsRequest() throws ServletException, IOException {
        var introspectionResponse = Map.of(
                "active", true,
                "sub", "john",
                "scope", "mcp:read",
                "aud", MCP_SERVER_URI
        );
        mockServer.expect(requestTo(OAUTH_SERVER_URL + "/oauth2/introspect"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(objectMapper.writeValueAsString(introspectionResponse), MediaType.APPLICATION_JSON));

        when(tokenExchangeClient.exchange("token-A")).thenReturn("token-B");

        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token-A");
        var response = new MockHttpServletResponse();
        var filterChain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(filterChain).doFilter(request, response);
        mockServer.verify();
    }

    @Test
    void doFilter_tokenWithWrongAud_returns401() throws ServletException, IOException {
        var introspectionResponse = Map.of(
                "active", true,
                "sub", "john",
                "scope", "mcp:read",
                "aud", "https://some-other-server.example.com"
        );
        mockServer.expect(requestTo(OAUTH_SERVER_URL + "/oauth2/introspect"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(objectMapper.writeValueAsString(introspectionResponse), MediaType.APPLICATION_JSON));

        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token-A");
        var response = new MockHttpServletResponse();
        var filterChain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate")).contains("resource_metadata");
        verifyNoInteractions(filterChain);
        assertThat(ExchangedTokenHolder.get()).isNull();
        mockServer.verify();
    }

    @Test
    void doFilter_tokenWithNoAud_allowsRequest() throws ServletException, IOException {
        // Tokens without aud are not audience-restricted -- allowed for backward compatibility
        var introspectionResponse = Map.of(
                "active", true,
                "sub", "john",
                "scope", "mcp:read"
                // no "aud" key
        );
        mockServer.expect(requestTo(OAUTH_SERVER_URL + "/oauth2/introspect"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(objectMapper.writeValueAsString(introspectionResponse), MediaType.APPLICATION_JSON));

        when(tokenExchangeClient.exchange("token-A")).thenReturn("token-B");

        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token-A");
        var response = new MockHttpServletResponse();
        var filterChain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(filterChain).doFilter(request, response);
        mockServer.verify();
    }

    @Test
    void doFilter_introspectionServerError_returns401AsInactive() throws ServletException, IOException {
        mockServer.expect(requestTo(OAUTH_SERVER_URL + "/oauth2/introspect"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer some-token");
        var response = new MockHttpServletResponse();
        var filterChain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate")).contains("resource_metadata");
        verifyNoInteractions(filterChain);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        mockServer.verify();
    }

    @Test
    void doFilter_nonBearerAuthScheme_returns401() throws ServletException, IOException {
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");
        var response = new MockHttpServletResponse();
        var filterChain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate")).contains("resource_metadata");
        verifyNoInteractions(filterChain);
    }
}