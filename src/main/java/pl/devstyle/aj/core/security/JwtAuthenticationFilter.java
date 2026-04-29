package pl.devstyle.aj.core.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final String resourceUri;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, String resourceUri) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.resourceUri = resourceUri;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        var token = extractToken(request);

        if (token != null) {
            jwtTokenProvider.parseToken(token).ifPresent(parsed -> {
                // RFC 8707: when the token carries an aud claim, it must contain this server's
                // resource URI. Tokens without aud are accepted (user tokens never carry aud).
                var aud = parsed.audience();
                if (!aud.isEmpty() && !aud.contains(resourceUri)) {
                    return; // audience mismatch — do not authenticate
                }

                var authorities = parsed.permissions().stream()
                        .map(p -> new SimpleGrantedAuthority("PERMISSION_" + p))
                        .toList();

                var authentication = new UsernamePasswordAuthenticationToken(
                        parsed.username(), null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            });
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        var header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        // Support form-based token for OAuth2 authorize endpoint (native form POST)
        var formToken = request.getParameter("_token");
        if (formToken != null && !formToken.isBlank()) {
            return formToken;
        }
        return null;
    }
}
