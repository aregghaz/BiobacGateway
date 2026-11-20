package com.ai.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Boots the real gateway configuration. Only requests the gateway must reject are sent, so nothing is forwarded to
 * the routed services.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewaySecuritySmokeTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void rejectsARoutedRequestWithoutAToken() {
        webTestClient.get().uri("/api/warehouse/unit")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.message").isEqualTo("Authentication required");
    }

    @Test
    void rejectsACrossOriginRequestFromTheAdminUiWithoutAToken() {
        webTestClient.get().uri("/api/warehouse/unit")
                .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000");
    }

    @Test
    void hidesInternalServiceApis() {
        webTestClient.get().uri("/api/users/internal/users/1/permissions")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void refusesTheGatewayPublishEndpointToNonLocalProxiedCalls() {
        webTestClient.post().uri("/api/internal/ws/publish")
                .header("X-Forwarded-For", "203.0.113.9")
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("{}")
                .exchange()
                .expectStatus().isNotFound();
    }
}
