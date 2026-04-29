package pl.devstyle.aj.mcp.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;

/**
 * Security filter that validates incoming Bearer tokens via RFC 7662 introspection
 * against the backend, then exchanges them for backend-scoped Token-B via RFC 8693.
 * Replaces the old trust-and-forward McpJwtFilter.
 *
 * <p>Token-B is placed into {@link ExchangedTokenHolder} (ThreadLocal) so that the
 * {@code TokenBForwardingInterceptor} in RestClientConfig can attach it to outgoing
 * backend API calls without needing access to the servlet request.
 *
 * <p>The introspection response {@code aud} claim is validated against this server's
 * own URI to ensure Token-A was intended for the MCP server.
 */
@Slf4j
public class McpIntrospectionFilter extends OncePerRequestFilter {

    public static final String EXCHANGED_TOKEN_ATTRIBUTE = "exchanged_token";

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final RestClient oauthRestClient;
    private final TokenExchangeClient tokenExchangeClient;
    private final McpAuthenticationEntryPoint authenticationEntryPoint;
    private final String clientId;
    private final String clientSecret;
    private final String mcpServerUri;
    private final ObjectMapper objectMapper;

    public McpIntrospectionFilter(RestClient oauthRestClient,
                                  TokenExchangeClient tokenExchangeClient,
                                  McpAuthenticationEntryPoint authenticationEntryPoint,
                                  String clientId,
                                  String clientSecret,
                                  String mcpServerUri) {
        this.oauthRestClient = oauthRestClient;
        this.tokenExchangeClient = tokenExchangeClient;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.mcpServerUri = mcpServerUri;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/.well-known/")
                || path.startsWith("/actuator/")
                || path.equals("/error");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String authHeader = request.getHeader(AUTHORIZATION_HEADER);
            if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
                log.debug("No Bearer token in request to {}", request.getRequestURI());
                rejectUnauthorized(request, response);
                return;
            }

            String tokenA = authHeader.substring(BEARER_PREFIX.length());

            // Step 1: Introspect Token-A
            JsonNode introspectionResult = introspect(tokenA);
            if (introspectionResult == null || !introspectionResult.path("active").asBoolean(false)) {
                log.info("Token introspection returned inactive for request to {}", request.getRequestURI());
                rejectUnauthorized(request, response);
                return;
            }

            // Step 2: Validate aud claim from introspection response -- Token-A must be
            // intended for this MCP server to prevent token confusion attacks.
            JsonNode audNode = introspectionResult.path("aud");
            if (!audNode.isMissingNode() && !audNode.isNull()) {
                boolean audMatches = false;
                if (audNode.isArray()) {
                    for (JsonNode entry : audNode) {
                        if (mcpServerUri.equals(entry.asText())) {
                            audMatches = true;
                            break;
                        }
                    }
                } else {
                    audMatches = mcpServerUri.equals(audNode.asText());
                }
                if (!audMatches) {
                    log.warn("Token aud claim does not match this server ({}) -- rejecting", mcpServerUri);
                    rejectUnauthorized(request, response);
                    return;
                }
            }
            // If no aud claim is present, the token is not audience-restricted -- allow it through.
            // This preserves backward compatibility with tokens issued before audience enforcement.

            // Extract principal and authorities from introspection response
            String subject = introspectionResult.path("sub").asText();
            String scope = introspectionResult.path("scope").asText("");
            var authorities = Arrays.stream(scope.split(" "))
                    .filter(s -> !s.isBlank())
                    .map(s -> new SimpleGrantedAuthority("PERMISSION_" + s))
                    .toList();

            // Step 3: Exchange Token-A for Token-B
            String tokenB;
            try {
                tokenB = tokenExchangeClient.exchange(tokenA);
            } catch (Exception e) {
                log.error("Token exchange failed after successful introspection for sub={}", subject, e);
                response.setStatus(HttpServletResponse.SC_BAD_GATEWAY);
                return;
            }

            // Store Token-B as request attribute (for reference / testing) and in the
            // ThreadLocal so RestClient interceptor can attach it to backend API calls.
            request.setAttribute(EXCHANGED_TOKEN_ATTRIBUTE, tokenB);
            ExchangedTokenHolder.set(tokenB);

            // Set SecurityContext for downstream handlers
            var authentication = new UsernamePasswordAuthenticationToken(subject, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);

            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
            ExchangedTokenHolder.clear();
        }
    }

    private JsonNode introspect(String token) {
        var params = new LinkedMultiValueMap<String, String>();
        params.add("token", token);
        params.add("client_id", clientId);
        params.add("client_secret", clientSecret);

        try {
            var responseBody = oauthRestClient.post()
                    .uri("/oauth2/introspect")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(params)
                    .retrieve()
                    .body(String.class);

            return objectMapper.readTree(responseBody);
        } catch (Exception e) {
            log.error("Token introspection call failed", e);
            return null;
        }
    }

    private void rejectUnauthorized(HttpServletRequest request, HttpServletResponse response) throws IOException {
        authenticationEntryPoint.commence(request, response, null);
    }
}