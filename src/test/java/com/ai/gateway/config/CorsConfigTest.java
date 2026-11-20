package com.ai.gateway.config;

import com.ai.gateway.security.AllowedOrigins;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.cors.reactive.CorsWebFilter;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CorsConfigTest {

    private final CorsWebFilter filter = new CorsConfig().corsWebFilter(
            new AllowedOrigins(List.of("http://localhost:3000", "https://kinomino.ru")));

    @Test
    void allowsThePreflightOfAnAdminUiOrigin() {
        MockServerWebExchange exchange = preflight("https://kinomino.ru");

        filter.filter(exchange, next -> Mono.empty()).block();

        assertThat(exchange.getResponse().getHeaders().getAccessControlAllowOrigin()).isEqualTo("https://kinomino.ru");
        assertThat(exchange.getResponse().getHeaders().getAccessControlAllowCredentials()).isTrue();
    }

    @Test
    void refusesThePreflightOfAnyOtherOrigin() {
        MockServerWebExchange exchange = preflight("https://evil.example");

        filter.filter(exchange, next -> Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exchange.getResponse().getHeaders().getAccessControlAllowOrigin()).isNull();
    }

    private MockServerWebExchange preflight(String origin) {
        return MockServerWebExchange.from(MockServerHttpRequest.options("http://localhost:8089/api/warehouse/unit")
                .header(HttpHeaders.ORIGIN, origin)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name())
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"));
    }
}
