package pl.devstyle.aj.mcp.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import pl.devstyle.aj.mcp.security.ExchangedTokenHolder;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RestClientConfigTests {

    @Test
    void interceptor_addsAuthorizationHeader_whenTokenBPresentInExchangedTokenHolder() throws IOException {
        var interceptor = new RestClientConfig.TokenBForwardingInterceptor();

        ExchangedTokenHolder.set("token-B-value");
        try {
            var request = mock(HttpRequest.class);
            var headers = new HttpHeaders();
            when(request.getHeaders()).thenReturn(headers);
            var body = new byte[0];
            var execution = mock(ClientHttpRequestExecution.class);
            var response = mock(ClientHttpResponse.class);
            when(execution.execute(any(), any())).thenReturn(response);

            interceptor.intercept(request, body, execution);

            assertThat(headers.getFirst("Authorization")).isEqualTo("Bearer token-B-value");
            verify(execution).execute(request, body);
        } finally {
            ExchangedTokenHolder.clear();
        }
    }

    @Test
    void interceptor_skipsAuthorizationHeader_whenNoTokenInHolder() throws IOException {
        var interceptor = new RestClientConfig.TokenBForwardingInterceptor();
        ExchangedTokenHolder.clear();

        var request = mock(HttpRequest.class);
        var headers = new HttpHeaders();
        when(request.getHeaders()).thenReturn(headers);
        var body = new byte[0];
        var execution = mock(ClientHttpRequestExecution.class);
        var response = mock(ClientHttpResponse.class);
        when(execution.execute(any(), any())).thenReturn(response);

        interceptor.intercept(request, body, execution);

        assertThat(headers.getFirst("Authorization")).isNull();
        verify(execution).execute(request, body);
    }
}
