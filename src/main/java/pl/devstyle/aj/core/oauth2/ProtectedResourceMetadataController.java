package pl.devstyle.aj.core.oauth2;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static java.util.Map.entry;

/**
 * RFC 9728 — OAuth 2.0 Protected Resource Metadata.
 * Allows MCP clients to discover this server's authorization requirements.
 */
@RestController
public class ProtectedResourceMetadataController {

    @GetMapping(value = "/.well-known/oauth-protected-resource", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> getProtectedResourceMetadata(HttpServletRequest request) {
        String baseUrl = getBaseUrl(request);

        return Map.ofEntries(
                entry("resource", baseUrl),
                entry("authorization_servers", new String[]{baseUrl}),
                entry("jwks_uri", baseUrl + "/oauth2/jwks.json"),
                entry("scopes_supported", new String[]{"mcp:read", "mcp:edit"}),
                entry("bearer_methods_supported", new String[]{"header"})
        );
    }

    private String getBaseUrl(HttpServletRequest request) {
        String scheme = request.getScheme();
        String serverName = request.getServerName();
        int serverPort = request.getServerPort();

        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        String forwardedHost = request.getHeader("X-Forwarded-Host");
        String forwardedPort = request.getHeader("X-Forwarded-Port");

        if (forwardedProto != null) scheme = forwardedProto;
        if (forwardedHost != null) serverName = forwardedHost;
        if (forwardedPort != null) serverPort = Integer.parseInt(forwardedPort);

        if ((scheme.equals("http") && serverPort == 80) || (scheme.equals("https") && serverPort == 443)) {
            return scheme + "://" + serverName;
        }
        if (forwardedProto != null && forwardedPort == null) {
            return scheme + "://" + serverName;
        }
        return scheme + "://" + serverName + ":" + serverPort;
    }
}
